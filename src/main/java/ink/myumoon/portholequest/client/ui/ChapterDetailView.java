package ink.myumoon.portholequest.client.ui;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import ink.myumoon.portholequest.client.screen.ScreenViewState;
import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.model.QuestLogSnapshot;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Read-only Chapter Detail body. The content element owns its own AUI scroll model. */
public final class ChapterDetailView {
    private ChapterDetailView() {}

    public static void bind(Document document, QuestLogSnapshot snapshot, ListFilter filter,
                            ScreenViewState state, BooleanSupplier currentBinding,
                            Consumer<QuestLogSnapshot.Entry> openQuest) {
        Element panel = require(document, "#detail-panel");
        if (state.kind() != ScreenViewState.Kind.CHAPTER) return;
        Element content = document.querySelector("#detail-content");
        if (content == null) {
            content = document.createElement("div");
            content.setAttribute("id", "detail-content");
            content.setAttribute("aria-label", "chapter-detail");
            int headerHeight = 64;
            try {
                headerHeight = Integer.parseInt(panel.getAttribute("data-detail-header-height"));
            } catch (NumberFormatException ignored) {
                // Keep the CSS fallback when an older document has no header measurement.
            }
            content.setInlineStyleProperty("top", headerHeight + "px");
            content.setInlineStyleProperty("height", Math.max(0, 176 - headerHeight) + "px");
            // AUI may replace a generic element with its registered DIV implementation;
            // all subsequent children and listeners must target the attached node.
            content = panel.appendChild(content);
        }
        content.replaceChildren();

        QuestLogSnapshot.Chapter chapter = findChapter(filter.project(snapshot), state.chapterId());
        if (chapter == null || chapter.entries().isEmpty()) {
            append(document, content, "detail-empty", "暂无可见任务", 0);
            return;
        }
        for (var entry : chapter.entries()) {
            String stateName = switch (entry.state()) {
                case REWARD -> "reward";
                case COMPLETED -> "complete";
                case STARTED, AVAILABLE -> "available";
                case LOCKED -> "locked";
            };
            String marker = switch (stateName) {
                case "reward" -> "!";
                case "complete" -> "*";
                case "available" -> ">";
                default -> "x";
            };
            Element row = append(document, content, "chapter-detail-row", "", entry.entryId());
            row.setAttribute("data-quest-id", Long.toUnsignedString(entry.questId(), 16));
            row.setAttribute("data-state", stateName);
            append(document, row, "row-marker state-" + stateName, marker, 0);
            append(document, row, "chapter-detail-title", entry.title(), 0);
            row.addEventListener("click", event -> {
                if (currentBinding.getAsBoolean() && event instanceof MouseEvent mouse && mouse.button == 0) {
                    openQuest.accept(entry);
                }
            });
        }
    }

    private static QuestLogSnapshot.Chapter findChapter(List<QuestLogSnapshot.Group> groups, long chapterId) {
        for (var group : groups) for (var chapter : group.chapters()) {
            if (chapter.id() == chapterId) return chapter;
        }
        return null;
    }

    private static Element append(Document document, Element parent, String cssClass, String text, long id) {
        Element element = document.createElement("div");
        element.setAttribute("class", cssClass);
        element.setAttribute("data-entry-id", Long.toUnsignedString(id, 16));
        element.setTextContent(text);
        return parent.appendChild(element);
    }

    private static Element require(Document document, String selector) {
        Element element = document.querySelector(selector);
        if (element == null) throw new IllegalStateException("Missing journal element: " + selector);
        return element;
    }
}
