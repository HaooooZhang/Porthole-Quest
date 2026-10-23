package ink.myumoon.portholequest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Detached client data. No FTBQ Quest, Task, Reward or TeamData object crosses this boundary. */
public record QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups,
                               Map<Long, ChapterHeader> chapterHeaders, Map<Long, QuestHeader> questHeaders,
                               Map<Long, QuestDetail> questDetails, String bookTitle, String bookIconJson,
                               List<GraphQuest> graphQuests) {
    public QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups) {
        this(fileGeneration, teamId, pauseGame, groups, Map.of(), Map.of(), Map.of(), "Quest Journal",
                "{\"id\":\"ftbquests:textures/item/book.png\"}", List.of());
    }

    public QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups,
                            Map<Long, ChapterHeader> chapterHeaders, Map<Long, QuestHeader> questHeaders) {
        this(fileGeneration, teamId, pauseGame, groups, chapterHeaders, questHeaders, Map.of(), "Quest Journal",
                "{\"id\":\"ftbquests:textures/item/book.png\"}", List.of());
    }

    public QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups,
                            Map<Long, ChapterHeader> chapterHeaders, Map<Long, QuestHeader> questHeaders,
                            Map<Long, QuestDetail> questDetails) {
        this(fileGeneration, teamId, pauseGame, groups, chapterHeaders, questHeaders, questDetails,
                "Quest Journal", "{\"id\":\"ftbquests:textures/item/book.png\"}", List.of());
    }

    public QuestLogSnapshot(long fileGeneration, UUID teamId, boolean pauseGame, List<Group> groups,
                            Map<Long, ChapterHeader> chapterHeaders, Map<Long, QuestHeader> questHeaders,
                            Map<Long, QuestDetail> questDetails, String bookTitle, String bookIconJson) {
        this(fileGeneration, teamId, pauseGame, groups, chapterHeaders, questHeaders, questDetails,
                bookTitle, bookIconJson, List.of());
    }

    public QuestLogSnapshot {
        Objects.requireNonNull(teamId);
        groups = List.copyOf(groups);
        chapterHeaders = Map.copyOf(chapterHeaders);
        questHeaders = Map.copyOf(questHeaders);
        questDetails = Map.copyOf(questDetails);
        graphQuests = List.copyOf(graphQuests);
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
                              boolean complexDescription, boolean paginated, Description description,
                              List<TaskDetail> tasks, List<RewardDetail> rewards) {
        public QuestDetail(List<String> descriptionLines, boolean descriptionHidden,
                           boolean complexDescription, boolean paginated,
                           List<TaskDetail> tasks, List<RewardDetail> rewards) {
            this(descriptionLines, descriptionHidden, complexDescription, paginated,
                    Description.textOnly(descriptionLines), tasks, rewards);
        }

        public QuestDetail {
            descriptionLines = List.copyOf(descriptionLines);
            Objects.requireNonNull(description);
            tasks = List.copyOf(tasks);
            rewards = List.copyOf(rewards);
        }
    }

    public record Description(List<DescriptionPage> pages) {
        private static final String NEWLINE_COMPONENT_JSON = "\"\\n\"";

        public Description {
            pages = List.copyOf(pages);
        }

        public List<DescriptionPart> overviewParts() {
            var result = new ArrayList<DescriptionPart>();
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                if (pageIndex > 0 && !endsWithLineBreak(result)) result.add(lineBreakPart());
                for (DescriptionPart part : pages.get(pageIndex).parts()) {
                    if (part.kind() == DescriptionPart.Kind.IMAGE) {
                        if (!result.isEmpty() && !endsWithLineBreak(result)) result.add(lineBreakPart());
                    } else {
                        result.add(part);
                    }
                }
            }
            return List.copyOf(result);
        }

        private static boolean endsWithLineBreak(List<DescriptionPart> parts) {
            return !parts.isEmpty() && parts.getLast().kind() == DescriptionPart.Kind.TEXT
                    && parts.getLast().componentJson().equals(NEWLINE_COMPONENT_JSON);
        }

        public boolean hasImages() {
            return pages.stream().flatMap(page -> page.parts().stream())
                    .anyMatch(part -> part.kind() == DescriptionPart.Kind.IMAGE);
        }

        public static Description textOnly(List<String> lines) {
            var parts = new ArrayList<DescriptionPart>();
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                if (lineIndex > 0) parts.add(lineBreakPart());
                parts.add(DescriptionPart.text(new com.google.gson.JsonPrimitive(lines.get(lineIndex)).toString()));
            }
            return new Description(List.of(new DescriptionPage(parts)));
        }

        private static DescriptionPart lineBreakPart() {
            return DescriptionPart.text(NEWLINE_COMPONENT_JSON);
        }
    }

    public static boolean descriptionNeedsDialog(boolean hasImages, boolean paginated, int visibleRows,
                                                 int dialogLineThreshold) {
        return hasImages || paginated || visibleRows >= Math.max(1, dialogLineThreshold);
    }

    public record DescriptionPage(List<DescriptionPart> parts) {
        public DescriptionPage {
            parts = List.copyOf(parts);
        }
    }

    public record DescriptionPart(Kind kind, String componentJson, String imageIconJson,
                                  int imageWidth, int imageHeight, String imageAlign, boolean imageFit) {
        public enum Kind { TEXT, IMAGE }

        public DescriptionPart {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(componentJson);
            Objects.requireNonNull(imageIconJson);
            Objects.requireNonNull(imageAlign);
        }

        public static DescriptionPart text(String componentJson) {
            return new DescriptionPart(Kind.TEXT, componentJson, "{}", 0, 0, "CENTER", false);
        }

        public static DescriptionPart image(String iconJson, int width, int height, String align, boolean fit) {
            return new DescriptionPart(Kind.IMAGE, "{}", iconJson, width, height, align, fit);
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

    public record GraphQuest(long questId, long chapterId, long groupId, String title, String iconJson,
                             boolean detailsAvailable, boolean started, boolean completed, boolean canStart,
                             int progressPercent, boolean unclaimedRewards, boolean pinned,
                             int stableOrder, List<Long> dependencyQuestIds, List<Long> dependantQuestIds) {
        public GraphQuest {
            Objects.requireNonNull(title);
            Objects.requireNonNull(iconJson);
            dependencyQuestIds = List.copyOf(dependencyQuestIds);
            dependantQuestIds = List.copyOf(dependantQuestIds);
        }
    }
}
