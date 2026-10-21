package ink.myumoon.portholequest.client.session;

import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.model.GroupSelection;

import java.util.Map;
import java.util.Objects;

/** One world connection, memory only. Contains no DOM or FTBQ objects. */
public final class GuiSessionCache {
    private ListState listState;

    public ListState restore(ListFilter defaultFilter, boolean defaultExpanded) {
        return listState != null ? listState : new ListState(defaultFilter, defaultExpanded, Map.of(), 0,
                GroupSelection.ALL, DetailState.BLANK, 0);
    }

    public void save(ListState state) {
        listState = state;
    }

    public void clear() {
        listState = null;
    }

    public record ListState(ListFilter filter, boolean defaultExpanded, Map<Long, Boolean> expanded, double scrollTop,
                            GroupSelection group, DetailState detail, double detailScroll,
                            boolean descriptionExpanded) {
        public ListState(ListFilter filter, boolean defaultExpanded, Map<Long, Boolean> expanded, double scrollTop) {
            this(filter, defaultExpanded, expanded, scrollTop, GroupSelection.ALL, DetailState.BLANK, 0, false);
        }

        public ListState(ListFilter filter, boolean defaultExpanded, Map<Long, Boolean> expanded, double scrollTop,
                         GroupSelection group) {
            this(filter, defaultExpanded, expanded, scrollTop, group, DetailState.BLANK, 0, false);
        }

        public ListState(ListFilter filter, boolean defaultExpanded, Map<Long, Boolean> expanded, double scrollTop,
                         GroupSelection group, DetailState detail, double detailScroll) {
            this(filter, defaultExpanded, expanded, scrollTop, group, detail, detailScroll, false);
        }

        public ListState {
            Objects.requireNonNull(filter);
            Objects.requireNonNull(group);
            Objects.requireNonNull(detail);
            expanded = Map.copyOf(expanded);
            scrollTop = Double.isFinite(scrollTop) ? Math.max(0, scrollTop) : 0;
            detailScroll = Double.isFinite(detailScroll) ? Math.max(0, detailScroll) : 0;
        }
    }

    /** Stable detail identity kept by the client session, without a Screen dependency. */
    public record DetailState(Kind kind, long sourceChapterId, long entryId, long questId) {
        public enum Kind { BLANK, CHAPTER, QUEST }
        public static final DetailState BLANK = new DetailState(Kind.BLANK, 0, 0, 0);

        public DetailState {
            Objects.requireNonNull(kind);
        }

        public static DetailState chapter(long chapterId) {
            return new DetailState(Kind.CHAPTER, chapterId, 0, 0);
        }

        public static DetailState quest(long sourceChapterId, long entryId, long questId) {
            return new DetailState(Kind.QUEST, sourceChapterId, entryId, questId);
        }
    }
}
