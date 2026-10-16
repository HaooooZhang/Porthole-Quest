package ink.myumoon.portholequest.client.session;

import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.model.GroupSelection;

import java.util.Map;
import java.util.Objects;

/** One world connection, memory only. Contains no DOM or FTBQ objects. */
public final class GuiSessionCache {
    private ListState listState;

    public ListState restore(ListFilter defaultFilter, boolean defaultExpanded) {
        return listState != null ? listState : new ListState(defaultFilter, defaultExpanded, Map.of(), 0);
    }

    public void save(ListState state) {
        listState = state;
    }

    public void clear() {
        listState = null;
    }

    public record ListState(ListFilter filter, boolean defaultExpanded, Map<Long, Boolean> expanded, double scrollTop,
                            GroupSelection group) {
        public ListState(ListFilter filter, boolean defaultExpanded, Map<Long, Boolean> expanded, double scrollTop) {
            this(filter, defaultExpanded, expanded, scrollTop, GroupSelection.ALL);
        }

        public ListState {
            Objects.requireNonNull(filter);
            Objects.requireNonNull(group);
            expanded = Map.copyOf(expanded);
            scrollTop = Double.isFinite(scrollTop) ? Math.max(0, scrollTop) : 0;
        }
    }
}
