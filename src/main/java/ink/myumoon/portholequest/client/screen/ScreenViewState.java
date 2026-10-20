package ink.myumoon.portholequest.client.screen;

import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import ink.myumoon.portholequest.client.session.GuiSessionCache.DetailState;

/** Basic detail identity. R3 adds history; session restoration stores its detached form. */
public record ScreenViewState(Kind kind, long chapterId, long entryId, long questId) {
    public enum Kind { BLANK, CHAPTER, QUEST }
    public static final ScreenViewState BLANK = new ScreenViewState(Kind.BLANK, 0, 0, 0);

    public static ScreenViewState chapter(long chapterId) {
        return new ScreenViewState(Kind.CHAPTER, chapterId, 0, 0);
    }

    public static ScreenViewState quest(QuestLogSnapshot.Entry entry) {
        return new ScreenViewState(Kind.QUEST, entry.chapterId(), entry.entryId(), entry.questId());
    }

    public static ScreenViewState fromSession(DetailState state) {
        return switch (state.kind()) {
            case BLANK -> BLANK;
            case CHAPTER -> chapter(state.sourceChapterId());
            case QUEST -> new ScreenViewState(Kind.QUEST, state.sourceChapterId(), state.entryId(), state.questId());
        };
    }

    public DetailState toSession() {
        return switch (kind) {
            case BLANK -> DetailState.BLANK;
            case CHAPTER -> DetailState.chapter(chapterId);
            case QUEST -> DetailState.quest(chapterId, entryId, questId);
        };
    }

    public ScreenViewState retainValid(QuestLogSnapshot snapshot, ListFilter filter) {
        if (kind == Kind.BLANK) return BLANK;
        for (var group : filter.project(snapshot)) for (var chapter : group.chapters()) {
            if (chapter.id() != chapterId) continue;
            if (kind == Kind.CHAPTER) return snapshot.chapterHeaders().containsKey(chapterId) ? this : BLANK;
            var header = snapshot.questHeaders().get(questId);
            if (header == null || !header.detailsAvailable()) return BLANK;
            for (var entry : chapter.entries()) {
                if (entry.entryId() == entryId && entry.questId() == questId) return this;
            }
        }
        return BLANK;
    }

    public boolean marks(QuestLogSnapshot.Entry entry) {
        return kind == Kind.QUEST && chapterId == entry.chapterId() && entryId == entry.entryId()
                && questId == entry.questId();
    }
}
