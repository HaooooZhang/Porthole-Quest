package ink.myumoon.portholequest.client.screen;

import ink.myumoon.portholequest.client.ui.DescriptionDialog;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Navigation history for one open journal Screen; never part of session restoration. */
public final class ScreenStateHistory {
    private final Deque<Frame> frames = new ArrayDeque<>();
    private int limit;

    public ScreenStateHistory(int limit) {
        setLimit(limit);
    }

    public void setLimit(int limit) {
        if (limit < 1) throw new IllegalArgumentException("History limit must be positive");
        this.limit = limit;
        trimOldest();
    }

    public void push(ScreenViewState state, double detailScroll) {
        push(state, detailScroll, false, null);
    }

    public void push(ScreenViewState state, double detailScroll, boolean descriptionExpanded) {
        push(state, detailScroll, descriptionExpanded, null);
    }

    public void push(ScreenViewState state, double detailScroll, boolean descriptionExpanded,
                     DescriptionDialog.State descriptionReturn) {
        frames.addLast(new Frame(state, detailScroll, descriptionExpanded,
                Optional.ofNullable(descriptionReturn)));
        trimOldest();
    }

    public Frame popValid(Predicate<ScreenViewState> isValid) {
        Objects.requireNonNull(isValid);
        while (!frames.isEmpty()) {
            Frame frame = frames.removeLast();
            if (isValid.test(frame.state())) return frame;
        }
        return null;
    }

    public void retainValid(Predicate<ScreenViewState> isValid) {
        Objects.requireNonNull(isValid);
        frames.removeIf(frame -> !isValid.test(frame.state()));
    }

    public void clear() {
        frames.clear();
    }

    public int size() {
        return frames.size();
    }

    private void trimOldest() {
        while (frames.size() > limit) frames.removeFirst();
    }

    public record Frame(ScreenViewState state, double detailScroll, boolean descriptionExpanded,
                        Optional<DescriptionDialog.State> descriptionReturn) {
        public Frame {
            Objects.requireNonNull(state);
            Objects.requireNonNull(descriptionReturn);
            detailScroll = Double.isFinite(detailScroll) ? Math.max(0, detailScroll) : 0;
            descriptionExpanded = descriptionExpanded && state.kind() == ScreenViewState.Kind.QUEST;
            descriptionReturn = descriptionReturn.filter(saved -> state.kind() == ScreenViewState.Kind.QUEST
                    && saved.questId() == state.questId());
        }

        public Frame(ScreenViewState state, double detailScroll) {
            this(state, detailScroll, false, Optional.empty());
        }

        public Frame(ScreenViewState state, double detailScroll, boolean descriptionExpanded) {
            this(state, detailScroll, descriptionExpanded, Optional.empty());
        }
    }
}
