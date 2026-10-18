package ink.myumoon.portholequest.client.input;

/** Survives the list's DOM replacement, but not unrelated clicks or lifecycle changes. */
public final class ChapterClickSequence {
    // Same 500 ms window as pinned AUI 1.2.7 MouseEvent.
    private static final long WINDOW_MS = 500;
    private long chapterId;
    private long lastClick;
    private boolean pending;

    public boolean click(long id, long now) {
        if (pending && chapterId == id && now >= lastClick && now - lastClick < WINDOW_MS) {
            reset();
            return true;
        }
        pending = true;
        chapterId = id;
        lastClick = now;
        return false;
    }

    public void reset() { pending = false; }
}
