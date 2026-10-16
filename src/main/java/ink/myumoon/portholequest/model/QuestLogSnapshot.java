package ink.myumoon.portholequest.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Detached R1 list data. Rich detail/task/reward content is added by later slices. */
public record QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups) {
    public QuestLogSnapshot {
        Objects.requireNonNull(teamId);
        groups = List.copyOf(groups);
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
