package ink.myumoon.portholequest.client.screen;

import ink.myumoon.portholequest.model.QuestLogSnapshot;
import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.client.ui.DescriptionDialog;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScreenStateHistoryCheck {
    public static void main(String[] args) {
        ScreenStateHistory history = new ScreenStateHistory(32);
        ScreenViewState blank = ScreenViewState.BLANK;
        ScreenViewState chapter = ScreenViewState.chapter(12);
        ScreenViewState quest = new ScreenViewState(ScreenViewState.Kind.QUEST, 12, 34, 56);
        QuestLogSnapshot visibleSnapshot = new QuestLogSnapshot(1, UUID.randomUUID(), false,
                List.of(new QuestLogSnapshot.Group(90, "test", 0, List.of(
                        new QuestLogSnapshot.Chapter(12, 90, "chapter", List.of(
                                new QuestLogSnapshot.Entry(34, 56, 12, "quest", false,
                                        false, false, false, 0, false, false)))))),
                Map.of(12L, new QuestLogSnapshot.ChapterHeader("", "{}", false, false)),
                Map.of(56L, new QuestLogSnapshot.QuestHeader("", "{}", true, false)));

        require(ListFilter.ACTIVE.project(visibleSnapshot).isEmpty()
                        && ListFilter.COMPLETED.project(visibleSnapshot).isEmpty(),
                "test quest is excluded from both non-ALL list projections");
        require(quest.equals(quest.retainValid(visibleSnapshot)),
                "visible quest remains valid when no list filter displays it");
        QuestLogSnapshot hiddenQuestSnapshot = new QuestLogSnapshot(1, UUID.randomUUID(), false,
                visibleSnapshot.groups(), visibleSnapshot.chapterHeaders(),
                Map.of(56L, new QuestLogSnapshot.QuestHeader("", "{}", false, false)));
        require(quest.retainValid(hiddenQuestSnapshot).equals(blank), "FTBQ-hidden quest is invalidated");

        history.push(blank, 0);
        history.push(chapter, 17.5);
        history.push(quest, 31);
        history.retainValid(state -> !state.equals(chapter));
        require(history.size() == 2, "invalid cached history frame is removed");

        ScreenStateHistory.Frame previous = history.popValid(state -> !state.equals(quest));
        require(previous != null && previous.state().equals(blank), "pop skips invalid frames and returns the latest valid state");
        require(previous.detailScroll() == 0, "restored scroll belongs to the returned state");
        require(history.size() == 0, "popped frames are removed");

        var dialogState = new DescriptionDialog.State(56, 2, 15.5);
        history.push(quest, 17.5, true, dialogState);
        ScreenStateHistory.Frame restored = history.popValid(state -> true);
        require(restored != null && restored.state().equals(quest), "valid detail frame is restored");
        require(restored.detailScroll() == 17.5, "detail scroll position is preserved");
        require(restored.descriptionExpanded(), "history restores the page-specific description expansion state");
        require(restored.descriptionReturn().orElseThrow().equals(dialogState),
                "history restores dialog page and scroll state with its detail frame");

        history.push(quest, 0, false, dialogState);
        require(history.popValid(state -> !state.equals(quest)) == null,
                "invalid detail state and its associated dialog return are discarded together");

        history.push(chapter, 17.5, true);
        ScreenStateHistory.Frame chapterFrame = history.popValid(state -> true);
        require(chapterFrame != null && !chapterFrame.descriptionExpanded(),
                "non-quest history frames cannot carry description expansion");

        history.push(quest, Double.NaN);
        ScreenStateHistory.Frame sanitized = history.popValid(state -> true);
        require(sanitized.detailScroll() == 0, "invalid scroll values are sanitized");
        history.push(blank, 0);
        history.clear();
        require(history.size() == 0 && history.popValid(state -> true) == null, "closing clears the screen-local history");

        ScreenStateHistory capped = new ScreenStateHistory(2);
        capped.push(blank, 0);
        capped.push(chapter, 17.5);
        capped.push(quest, 31, false, dialogState);
        require(capped.size() == 2, "history stays within configured capacity");
        ScreenStateHistory.Frame newest = capped.popValid(state -> true);
        ScreenStateHistory.Frame olderRetained = capped.popValid(state -> true);
        require(newest.state().equals(quest) && olderRetained.state().equals(chapter),
                "overflow evicts the oldest frame and preserves recent back steps");
        require(newest.descriptionReturn().orElseThrow().equals(dialogState),
                "dialog return is retained only as part of its bounded detail frame");

        capped.push(blank, 0);
        capped.push(chapter, 17.5);
        capped.push(quest, 31);
        capped.setLimit(1);
        require(capped.size() == 1 && capped.popValid(state -> true).state().equals(quest),
                "lowering the configured capacity immediately trims oldest frames");

        System.out.println("ScreenStateHistory checks PASS: prune, valid pop, scroll restore, clear, capacity");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
