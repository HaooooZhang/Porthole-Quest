package ink.myumoon.portholequest.client.screen;

public final class ScreenRefreshGuardCheck {
    private static final long SECOND = 1_000_000_000L;

    public static void main(String[] args) {
        var guard = new ScreenRefreshGuard();
        require(!guard.tryBeginRefresh(0, true), "initial view must be presented first");
        guard.beginFrame(0);
        require(!guard.tryBeginRefresh(SECOND, true), "no refresh during a slow render");
        guard.finishFrame(2 * SECOND, true);
        require(!guard.tryBeginRefresh(3 * SECOND, true), "slow frame gets a matching quiet interval");
        require(!guard.tryBeginRefresh(4 * SECOND, false), "unfinished document blocks capture");
        require(guard.tryBeginRefresh(4 * SECOND, true), "ready view can refresh after backoff");
        require(!guard.tryBeginRefresh(8 * SECOND, true), "no second capture while refresh is running");
        guard.viewChanged();
        guard.finishRefresh(7 * SECOND);
        require(!guard.tryBeginRefresh(20 * SECOND, true), "overdue ticks cannot refresh an unpresented view");
        guard.beginFrame(20 * SECOND);
        guard.finishFrame(21 * SECOND, false);
        require(!guard.tryBeginRefresh(30 * SECOND, true), "partial layout is not presentation");
        guard.beginFrame(30 * SECOND);
        guard.finishFrame(31 * SECOND, true);
        require(!guard.tryBeginRefresh(33 * SECOND, true), "slow capture also extends interval after presentation");
        require(guard.tryBeginRefresh(34 * SECOND, true), "slow work does not permanently starve refresh");
        guard.finishRefresh(34 * SECOND);
        require(!guard.tryBeginRefresh(34 * SECOND, true), "catch-up ticks do not burst captures");
        require(guard.tryBeginRefresh(35 * SECOND, true), "unchanged snapshots resume normal pacing");
        guard.finishRefresh(35 * SECOND);
        guard.close();
        require(!guard.tryBeginRefresh(99 * SECOND, true), "closed screen cannot refresh");
        guard.initializeView();
        require(!guard.tryBeginRefresh(99 * SECOND, true), "reused screen waits for its new view to render");
        guard.beginFrame(99 * SECOND);
        guard.finishFrame(99 * SECOND, true);
        require(guard.tryBeginRefresh(100 * SECOND, true), "returning to a reused Screen resumes refresh");
        System.out.println("Screen refresh guard checks PASS: slow frames, readiness, no overlap/catch-up, backoff, close");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
