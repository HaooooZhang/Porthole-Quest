package ink.myumoon.portholequest.client.screen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
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
        frames.addLast(new Frame(state, detailScroll));
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

    public record Frame(ScreenViewState state, double detailScroll) {
        public Frame {
            Objects.requireNonNull(state);
            detailScroll = Double.isFinite(detailScroll) ? Math.max(0, detailScroll) : 0;
        }
    }
}
