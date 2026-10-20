package ink.myumoon.portholequest.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Detached client data. No FTBQ Quest, Task, Reward or TeamData object crosses this boundary. */
public record QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups,
                               Map<Long, ChapterHeader> chapterHeaders, Map<Long, QuestHeader> questHeaders,
                               Map<Long, QuestDetail> questDetails, String bookTitle, String bookIconJson) {
    public QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups) {
        this(fileGeneration, teamId, pauseGame, groups, Map.of(), Map.of(), Map.of(), "Quest Journal",
                "{\"id\":\"ftbquests:textures/item/book.png\"}");
    }

    public QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups,
                            Map<Long, ChapterHeader> chapterHeaders, Map<Long, QuestHeader> questHeaders) {
        this(fileGeneration, teamId, pauseGame, groups, chapterHeaders, questHeaders, Map.of(), "Quest Journal",
                "{\"id\":\"ftbquests:textures/item/book.png\"}");
    }

    public QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups,
                            Map<Long, ChapterHeader> chapterHeaders, Map<Long, QuestHeader> questHeaders,
                            Map<Long, QuestDetail> questDetails) {
        this(fileGeneration, teamId, pauseGame, groups, chapterHeaders, questHeaders, questDetails,
                "Quest Journal", "{\"id\":\"ftbquests:textures/item/book.png\"}");
    }

    public QuestLogSnapshot {
        Objects.requireNonNull(teamId);
        groups = List.copyOf(groups);
        chapterHeaders = Map.copyOf(chapterHeaders);
        questHeaders = Map.copyOf(questHeaders);
        questDetails = Map.copyOf(questDetails);
        Objects.requireNonNull(bookTitle);
        Objects.requireNonNull(bookIconJson);
    }

    /** JSON is FTB Library's detached icon representation, never a live quest/icon object. */
    public record ChapterHeader(String subtitle, String iconJson, boolean started, boolean completed) {
        public ChapterHeader(String iconJson, boolean started, boolean completed) {
            this("", iconJson, started, completed);
        }

        public ChapterHeader {
            Objects.requireNonNull(subtitle);
            Objects.requireNonNull(iconJson);
        }
    }

    public record QuestHeader(String subtitle, String iconJson, boolean detailsAvailable, boolean showProgress) {
        public QuestHeader {
            Objects.requireNonNull(subtitle);
            Objects.requireNonNull(iconJson);
        }
    }

    public record QuestDetail(List<String> descriptionLines, boolean descriptionHidden,
                              boolean complexDescription, boolean paginated,
                              List<TaskDetail> tasks, List<RewardDetail> rewards) {
        public QuestDetail {
            descriptionLines = List.copyOf(descriptionLines);
            tasks = List.copyOf(tasks);
            rewards = List.copyOf(rewards);
        }
    }

    public record TaskDetail(long id, String typeId, String typeTitle, String title, String iconJson,
                             long progress, long maxProgress, boolean completed, boolean optional,
                             boolean automatic, boolean progressHidden, boolean consumesResources) {
        public TaskDetail {
            Objects.requireNonNull(typeId);
            Objects.requireNonNull(typeTitle);
            Objects.requireNonNull(title);
            Objects.requireNonNull(iconJson);
        }
    }

    public enum RewardState {
        CANT_CLAIM,
        CAN_CLAIM,
        CLAIMED
    }

    public record RewardDetail(long id, String typeId, String typeTitle, String title, String iconJson,
                               RewardState state, String autoClaim) {
        public RewardDetail {
            Objects.requireNonNull(typeId);
            Objects.requireNonNull(typeTitle);
            Objects.requireNonNull(title);
            Objects.requireNonNull(iconJson);
            Objects.requireNonNull(state);
            Objects.requireNonNull(autoClaim);
        }

        public boolean automatic() {
            return "ENABLED".equals(autoClaim) || "NO_TOAST".equals(autoClaim);
        }
    }

    public static boolean hasIntermediateProgress(boolean started, boolean completed, int percent,
                                                   boolean nonBinary) {
        return nonBinary && started && !completed && percent > 0 && percent < 100;
    }

    public int chapterCount() {
        return groups.stream().mapToInt(group -> group.chapters().size()).sum();
    }

    public int entryCount() {
        return groups.stream().flatMap(group -> group.chapters().stream())
                .mapToInt(chapter -> chapter.entries().size()).sum();
    }

    /** File order precedes visibility/filter projection, keeping a group's tab color stable. */
    public record Group(long id, String title, int fileOrder, List<Chapter> chapters) {
        public Group {
            Objects.requireNonNull(title);
            chapters = List.copyOf(chapters);
        }
    }

    public record Chapter(long id, long groupId, String title, List<Entry> entries) {
        public Chapter {
            Objects.requireNonNull(title);
            entries = List.copyOf(entries);
        }
    }

    public record Entry(long entryId, long questId, long chapterId, String title, boolean link,
                        boolean started, boolean completed, boolean canStart,
                        int progressPercent, boolean unclaimedRewards, boolean pinned) {
        public Entry {
            Objects.requireNonNull(title);
        }
    }
}
