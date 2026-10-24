package ink.myumoon.portholequest.client.screen;

/** Client-thread pacing: one refresh at a time, after the last committed view has been drawn. */
public final class ScreenRefreshGuard {
    private static final long MIN_INTERVAL_NANOS = 1_000_000_000L;
    private boolean awaitingFrame = true;
    private boolean refreshing;
    private boolean rendering;
    private boolean closed;
    private long refreshStarted;
    private long frameStarted;
    private long refreshCost;
    private long frameCost;
    private long nextRefresh;

    public boolean tryBeginRefresh(long now, boolean viewReady) {
        return tryBeginRefresh(now, viewReady, false);
    }

    public boolean tryBeginImmediateRefresh(long now, boolean viewReady) {
        return tryBeginRefresh(now, viewReady, true);
    }

    private boolean tryBeginRefresh(long now, boolean viewReady, boolean immediate) {
        if (closed || refreshing || rendering || awaitingFrame || !viewReady
                || !immediate && now - nextRefresh < 0) return false;
        refreshing = true;
        refreshStarted = now;
        return true;
    }

    public void finishRefresh(long now) {
        if (!refreshing) return;
        refreshing = false;
        refreshCost = Math.max(0, now - refreshStarted);
        nextRefresh = now + interval();
    }

    public void viewChanged() { awaitingFrame = true; }

    public void initializeView() {
        closed = false;
        awaitingFrame = true;
    }

    public void beginFrame(long now) {
        rendering = true;
        frameStarted = now;
    }

    public void finishFrame(long now, boolean viewReady) {
        rendering = false;
        frameCost = Math.max(0, now - frameStarted);
        if (!viewReady) {
            awaitingFrame = true;
        } else if (awaitingFrame) {
            awaitingFrame = false;
            // Start the quiet period after presentation, never from an overdue tick deadline.
            nextRefresh = now + interval();
        }
    }

    private long interval() {
        return Math.max(MIN_INTERVAL_NANOS, Math.max(refreshCost, frameCost));
    }

    public void close() { closed = true; }
}
