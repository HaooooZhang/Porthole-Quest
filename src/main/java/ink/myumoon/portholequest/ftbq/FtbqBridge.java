package ink.myumoon.portholequest.ftbq;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import dev.ftb.mods.ftbquests.quest.reward.RewardClaimType;
import ink.myumoon.portholequest.model.QuestLogReadResult;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import ink.myumoon.portholequest.model.ChapterEntryOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static ink.myumoon.portholequest.model.QuestLogReadResult.State;

/** Physical-client adapter in the integration package. Never load from a common/server entrypoint. */
public final class FtbqBridge {
    private ClientQuestFile observedFile;
    private long fileGeneration;

    public QuestLogReadResult capture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("Read FTBQ client state on the client thread");
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            reset();
            return QuestLogReadResult.unavailable(State.NO_WORLD);
        }
        if (!ClientQuestFile.exists()) {
            reset();
            return QuestLogReadResult.unavailable(State.MISSING_QUESTS);
        }
        ClientQuestFile file = ClientQuestFile.INSTANCE;
        if (file != observedFile) {
            observedFile = file;
            fileGeneration++;
        }
        TeamData data = file.selfTeamData;
        // onReplaced's Loading instance is not registered; syncTeamData registers before assigning it.
        if (data == null || data.getFile() != file || file.getNullableTeamData(data.getTeamId()) != data) {
            return QuestLogReadResult.unavailable(State.LOADING_TEAM);
        }
        if (data.isLocked()) return QuestLogReadResult.unavailable(State.LOCKED);

        UUID playerId = minecraft.player.getUUID();
        var groups = new ArrayList<QuestLogSnapshot.Group>();
        var chapterHeaders = new HashMap<Long, QuestLogSnapshot.ChapterHeader>();
        var questHeaders = new HashMap<Long, QuestLogSnapshot.QuestHeader>();
        var questDetails = new HashMap<Long, QuestLogSnapshot.QuestDetail>();
        int groupIndex = 0;
        for (var group : file.getChapterGroups()) {
            int fileOrder = groupIndex++;
            if (!group.isValid() || !group.isVisible(data)) continue;
            var chapters = new ArrayList<QuestLogSnapshot.Chapter>();
            for (Chapter chapter : group.getVisibleChapters(data)) {
                // Editor privileges must not reveal content hidden from the journal's player view.
                if (!chapter.isValid() || !chapter.isVisible(data) || !chapter.hasAnyVisibleChildren()) continue;
                var definitions = new ArrayList<ChapterEntryOrder.Definition>();
                var visibleEntries = new HashMap<Long, QuestLogSnapshot.Entry>();
                for (Quest quest : chapter.getQuests()) {
                    if (!quest.isValid()) continue;
                    definitions.add(definition(quest.id, quest));
                    if (quest.isVisible(data)) {
                        visibleEntries.put(quest.id, entry(quest.id, chapter.id, quest, false, data, playerId));
                    }
                }
                for (var link : chapter.getQuestLinks()) {
                    if (!link.isValid()) continue;
                    link.getQuest().filter(Quest::isValid).ifPresent(quest -> {
                        definitions.add(definition(link.id, quest));
                        if (link.isVisible(data) && quest.isVisible(data)) {
                            visibleEntries.put(link.id, entry(link.id, chapter.id, quest, true, data, playerId));
                        }
                    });
                }
                var entries = ChapterEntryOrder.sort(definitions).stream()
                        .map(definition -> visibleEntries.get(definition.entryId()))
                        .filter(java.util.Objects::nonNull).toList();
                if (!entries.isEmpty()) {
                    chapters.add(new QuestLogSnapshot.Chapter(chapter.id, group.id,
                            chapter.getTitle().getString(), entries));
                    chapterHeaders.put(chapter.id, new QuestLogSnapshot.ChapterHeader(
                            String.join("\n", chapter.getRawSubtitle()), chapter.getIcon().getJson().toString(),
                            data.isStarted(chapter), data.isCompleted(chapter)));
                    for (var entry : entries) {
                        if (questHeaders.containsKey(entry.questId())) continue;
                        Quest quest = file.getQuest(entry.questId());
                        if (quest == null) continue;
                        // Native QuestButton exposes subtitle in its Tooltip independently of description hiding.
                        // The D3 detail snapshot applies hideTextUntilComplete to the body below.
                        boolean nonBinary = quest.getTasks().size() > 1
                                || quest.getTasks().stream().anyMatch(task -> task.getMaxProgress() > 1);
                        boolean detailsAvailable = !quest.hideDetailsUntilStartable() || data.canStartTasks(quest);
                        questHeaders.put(quest.id, new QuestLogSnapshot.QuestHeader(
                                quest.getSubtitle().getString(),
                                quest.getIcon().getJson().toString(), detailsAvailable,
                                QuestLogSnapshot.hasIntermediateProgress(entry.started(), entry.completed(),
                                        entry.progressPercent(), nonBinary)));
                        // Do not copy hidden descriptions, Tasks or Rewards into the
                        // detached UI snapshot when FTBQ denies opening the details.
                        if (detailsAvailable) questDetails.put(quest.id,
                                detail(quest, chapter, data, playerId));
                    }
                }
            }
            if (!chapters.isEmpty()) {
                groups.add(new QuestLogSnapshot.Group(group.id,
                        group.getTitle().getString(), fileOrder, chapters));
            }
        }
        return QuestLogReadResult.ready(new QuestLogSnapshot(fileGeneration, data.getTeamId(), file.isPauseGame(), groups,
                chapterHeaders, questHeaders, questDetails, file.getTitle().getString(), file.getIcon().getJson().toString()));
    }

    private static QuestLogSnapshot.QuestDetail detail(Quest quest, Chapter chapter,
                                                       TeamData data, UUID playerId) {
        boolean hideText = quest.getHideTextUntilComplete().get(chapter.isHideTextUntilComplete())
                && !data.isCompleted(quest);
        var rawDescription = quest.getRawDescription();
        boolean paginated = rawDescription.stream().anyMatch("{@pagebreak}"::equals);
        boolean complexDescription = rawDescription.stream().anyMatch(line ->
                line.contains("{image:") || line.contains("{@image:") || line.contains("{click:")
                        || line.contains("{@link:") || line.contains("{@quest:"));
        var description = hideText ? List.<String>of() : quest.getDescription().stream()
                .map(Component::getString)
                .filter(line -> !line.isBlank())
                .toList();

        var tasks = new ArrayList<QuestLogSnapshot.TaskDetail>();
        boolean sequential = quest.getRequireSequentialTasks();
        for (Task task : quest.getTasks()) {
            boolean completed = data.isCompleted(task);
            String typeTitle = task.getType().getDisplayName().getString();
            String title = task.getAltTitle().getString();
            if (title.isBlank()) title = typeTitle;
            tasks.add(new QuestLogSnapshot.TaskDetail(task.id, task.getType().getTypeId().toString(), typeTitle, title,
                    task.getAltIcon().getJson().toString(), data.getProgress(task), task.getMaxProgress(), completed,
                    task.isOptionalForProgression(data), task.autoSubmitOnPlayerTick() > 0, task.hideProgressNumbers(),
                    task.consumesResources()));
            // This mirrors ViewQuestPanel: sequential quests expose the first
            // unfinished task and do not leak later conditions into the journal.
            if (sequential && !completed) break;
        }

        var rewards = new ArrayList<QuestLogSnapshot.RewardDetail>();
        for (Reward reward : quest.getRewards()) {
            RewardAutoClaim autoClaim = reward.getAutoClaimType();
            if (autoClaim == RewardAutoClaim.INVISIBLE || data.isRewardBlocked(reward)) continue;
            RewardClaimType claimType = data.getClaimType(playerId, reward);
            QuestLogSnapshot.RewardState state = switch (claimType) {
                case CAN_CLAIM -> QuestLogSnapshot.RewardState.CAN_CLAIM;
                case CLAIMED -> QuestLogSnapshot.RewardState.CLAIMED;
                default -> QuestLogSnapshot.RewardState.CANT_CLAIM;
            };
            String typeTitle = reward.getType().getDisplayName().getString();
            String title = reward.getAltTitle().getString();
            if (title.isBlank()) title = typeTitle;
            rewards.add(new QuestLogSnapshot.RewardDetail(reward.id, reward.getType().getTypeId().toString(),
                    typeTitle, title, reward.getAltIcon().getJson().toString(), state, autoClaim.name()));
        }
        return new QuestLogSnapshot.QuestDetail(description, hideText, complexDescription, paginated, tasks, rewards);
    }

    private static ChapterEntryOrder.Definition definition(long entryId, Quest quest) {
        // Non-Quest dependencies still govern FTBQ unlocks, but do not create list rows.
        return new ChapterEntryOrder.Definition(entryId, quest.id,
                quest.streamDependencies().filter(Quest.class::isInstance).map(dependency -> dependency.id).toList());
    }

    private static QuestLogSnapshot.Entry entry(long entryId, long chapterId, Quest quest,
                                                 boolean link, TeamData data, UUID playerId) {
        return new QuestLogSnapshot.Entry(entryId, quest.id, chapterId,
                quest.getTitle().getString(), link,
                data.isStarted(quest), data.isCompleted(quest), data.canStartTasks(quest),
                data.getRelativeProgress(quest), data.hasUnclaimedRewards(playerId, quest),
                ClientQuestFile.isQuestPinned(quest.id));
    }

    public void reset() {
        observedFile = null;
    }
}
