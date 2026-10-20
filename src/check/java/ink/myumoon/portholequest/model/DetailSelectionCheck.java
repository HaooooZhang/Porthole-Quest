package ink.myumoon.portholequest.model;

import ink.myumoon.portholequest.client.input.ChapterClickSequence;
import ink.myumoon.portholequest.client.screen.ScreenViewState;
import ink.myumoon.portholequest.client.session.GuiSessionCache;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DetailSelectionCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var original = new QuestLogSnapshot.Entry(1, 1, 10, "Quest", false, true, false, true, 50, false, false);
        var link = new QuestLogSnapshot.Entry(2, 1, 20, "Quest", true, true, false, true, 50, false, false);
        var otherLink = new QuestLogSnapshot.Entry(3, 1, 20, "Quest", true, true, false, true, 50, false, false);
        var group = new QuestLogSnapshot.Group(0, "Group", 0, List.of(
                new QuestLogSnapshot.Chapter(10, 0, "One", List.of(original)),
                new QuestLogSnapshot.Chapter(20, 0, "Two", List.of(link, otherLink))));
        var chapterHeaders = Map.of(10L, new QuestLogSnapshot.ChapterHeader("\"\"", true, false),
                20L, new QuestLogSnapshot.ChapterHeader("\"\"", true, false));
        var headers = Map.of(1L, new QuestLogSnapshot.QuestHeader("Subtitle", "\"\"", true, true));
        var snapshot = new QuestLogSnapshot(1, UUID.randomUUID(), false, List.of(group), chapterHeaders, headers);
        var selected = ScreenViewState.quest(link);
        check(selected.retainValid(snapshot).equals(selected), "Link is valid in the available snapshot");
        check(selected.marks(link) && !selected.marks(original) && !selected.marks(otherLink), "Mark only source entry");
        check(ScreenViewState.chapter(20).retainValid(snapshot).kind() == ScreenViewState.Kind.CHAPTER,
                "Chapter identity independent of expansion");
        var replaced = new QuestLogSnapshot(2, snapshot.teamId(), false,
                List.of(new QuestLogSnapshot.Group(0, "Group", 0, List.of(
                        new QuestLogSnapshot.Chapter(10, 0, "One", List.of(original)),
                        new QuestLogSnapshot.Chapter(20, 0, "Two", List.of(otherLink))))), chapterHeaders, headers);
        check(selected.retainValid(replaced).equals(ScreenViewState.BLANK),
                "Deleted link must not restore another same-target entry");
        var hidden = new QuestLogSnapshot(1, snapshot.teamId(), false, List.of(group), chapterHeaders,
                Map.of(1L, new QuestLogSnapshot.QuestHeader("", "\"\"", false, false)));
        check(selected.retainValid(hidden).equals(ScreenViewState.BLANK), "Hidden detail not selectable");
        var finished = new QuestLogSnapshot.Entry(2, 1, 20, "Quest", true, true, true, true, 100, false, false);
        var filtered = new QuestLogSnapshot(1, snapshot.teamId(), false, List.of(new QuestLogSnapshot.Group(0, "", 0,
                List.of(new QuestLogSnapshot.Chapter(20, 0, "", List.of(finished))))), chapterHeaders, headers);
        check(selected.retainValid(filtered).equals(selected), "List filter does not invalidate selected quest");
        check(ScreenViewState.chapter(20).retainValid(filtered).kind() == ScreenViewState.Kind.CHAPTER,
                "List filter does not invalidate selected chapter");
        var sessionDetail = selected.toSession();
        check(ScreenViewState.fromSession(sessionDetail).equals(selected), "Session preserves QuestLink source identity");
        var restored = new GuiSessionCache.ListState(ListFilter.ACTIVE, true, Map.of(), 17,
                GroupSelection.group(999), sessionDetail, 23);
        check(restored.detailScroll() == 23 && restored.detail().entryId() == 2,
                "Session stores independent detail scroll and entry identity");
        check(QuestLogSnapshot.hasIntermediateProgress(true, false, 50, true), "Intermediate nonbinary progress");
        check(!QuestLogSnapshot.hasIntermediateProgress(true, false, 50, false), "Binary progress not shown");
        check(!QuestLogSnapshot.hasIntermediateProgress(true, false, 0, true)
                && !QuestLogSnapshot.hasIntermediateProgress(true, true, 100, true), "No endpoints");
        var clicks = new ChapterClickSequence();
        check(!clicks.click(20, 0) && clicks.click(20, 499), "Stable-ID double click across DOM replacement");
        check(!clicks.click(20, 500), "Third click begins new sequence");
        check(!clicks.click(10, 600), "Different chapter resets sequence");
        clicks.reset();
        check(!clicks.click(10, 700), "Unrelated action resets sequence");
        check(!clicks.click(10, 1200), "Outside double click window");
        System.out.println("Detail selection and Chapter click checks passed");
    }
}
