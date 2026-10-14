package ink.myumoon.portholequest.ftbq;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import ink.myumoon.portholequest.model.QuestLogReadResult;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
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
        for (var group : file.getChapterGroups()) {
            if (!group.isValid() || !group.isVisible(data)) continue;
            var chapters = new ArrayList<QuestLogSnapshot.Chapter>();
            for (Chapter chapter : group.getVisibleChapters(data)) {
                // Editor privileges must not reveal content hidden from the journal's player view.
                if (!chapter.isValid() || !chapter.isVisible(data) || !chapter.hasAnyVisibleChildren()) continue;
                var entries = new ArrayList<QuestLogSnapshot.Entry>();
                for (Quest quest : chapter.getQuests()) {
                    if (quest.isValid() && quest.isVisible(data)) {
                        entries.add(entry(quest.id, chapter.id, quest, false, data, playerId));
                    }
                }
                for (var link : chapter.getQuestLinks()) {
                    if (!link.isValid() || !link.isVisible(data)) continue;
                    link.getQuest().filter(quest -> quest.isValid() && quest.isVisible(data))
                            .ifPresent(quest -> entries.add(entry(link.id, chapter.id, quest, true, data, playerId)));
                }
                if (!entries.isEmpty()) {
                    chapters.add(new QuestLogSnapshot.Chapter(chapter.id, group.id,
                            chapter.getTitle().getString(), entries));
                }
            }
            if (!chapters.isEmpty()) {
                groups.add(new QuestLogSnapshot.Group(group.id, group.getTitle().getString(), chapters));
            }
        }
        return QuestLogReadResult.ready(new QuestLogSnapshot(fileGeneration, data.getTeamId(), file.isPauseGame(), groups));
    }

    private static QuestLogSnapshot.Entry entry(long entryId, long chapterId, Quest quest, boolean link,
                                                 TeamData data, UUID playerId) {
        return new QuestLogSnapshot.Entry(entryId, quest.id, chapterId, quest.getTitle().getString(), link,
                data.isStarted(quest), data.isCompleted(quest), data.canStartTasks(quest),
                data.getRelativeProgress(quest), data.hasUnclaimedRewards(playerId, quest),
                ClientQuestFile.isQuestPinned(quest.id));
    }

    public void reset() {
        observedFile = null;
    }
}
