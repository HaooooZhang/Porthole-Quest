package ink.myumoon.portholequest.client.ui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.MouseEvent;
import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import ink.myumoon.portholequest.client.screen.ScreenViewState;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** R2 list projection. Detail navigation is added by the later read-only block. */
public final class QuestLogView {
    private QuestLogView() {}

    public static List<HoverTarget> render(Document document, List<QuestLogSnapshot.Group> groups, String groupTitle, ListFilter filter,
                              Map<Long, Boolean> expanded, boolean defaultExpanded,
                              BooleanSupplier currentBinding, Consumer<ListFilter> changeFilter,
                              LongConsumer clickChapter, Consumer<QuestLogSnapshot.Entry> openQuest, ScreenViewState detail) {
        var hoverTargets = new ArrayList<HoverTarget>();
        Element list = require(document, "#list-entries");
        Element switches = require(document, "#list-switches");
        list.replaceChildren();
        // Replacing controls also discards their old listeners on snapshot/DOM rebuild.
        switches.replaceChildren();
        require(document, "#list-heading").setTextContent(groupTitle);

        for (ListFilter mode : ListFilter.values()) {
            Element button = append(document, switches, "filter-button" + (filter == mode ? " selected" : ""),
                    Component.translatable("ui.portholequest.filter." + mode.name().toLowerCase(java.util.Locale.ROOT)).getString(), 0);
            button.setAttribute("id", "filter-" + mode.name().toLowerCase(java.util.Locale.ROOT));
            button.setAttribute("role", "button");
            button.setAttribute("aria-pressed", Boolean.toString(filter == mode));
            button.addEventListener("click", event -> {
                if (currentBinding.getAsBoolean() && event instanceof MouseEvent mouse && mouse.button == 0) changeFilter.accept(mode);
            });
        }

        if (groups.isEmpty()) {
            append(document, list, "empty-row", Component.translatable("ui.portholequest.empty_list").getString(), 0);
            return List.of();
        }
        for (var group : groups) {
            for (var chapter : group.chapters()) {
                boolean open = expanded.getOrDefault(chapter.id(), defaultExpanded);
                Element chapterRow = append(document, list, "chapter-row", "", chapter.id());
                chapterRow.setAttribute("data-chapter-id", Long.toUnsignedString(chapter.id(), 16));
                chapterRow.setAttribute("aria-expanded", Boolean.toString(open));
                append(document, chapterRow, "row-marker expand-marker", open ? "-" : "+", 0);
                append(document, chapterRow, "chapter-title", chapter.title(), 0);
                if (detail.kind() == ScreenViewState.Kind.CHAPTER && detail.chapterId() == chapter.id()) {
                    append(document, chapterRow, "detail-context", "", 0);
                }
                chapterRow.addEventListener("click", event -> {
                    if (currentBinding.getAsBoolean() && event instanceof MouseEvent mouse && mouse.button == 0) clickChapter.accept(chapter.id());
                });
                if (!open) continue;
                for (var entry : chapter.entries()) {
                    Element row = append(document, list, "quest-row", "", entry.entryId());
                    row.setAttribute("data-quest-id", Long.toUnsignedString(entry.questId(), 16));
                    row.setAttribute("data-chapter-id", Long.toUnsignedString(entry.chapterId(), 16));
                    String state = switch (entry.state()) {
                        case REWARD -> "reward";
                        case COMPLETED -> "complete";
                        case STARTED, AVAILABLE -> "available";
                        case LOCKED -> "locked";
                    };
                    row.setAttribute("data-state", state);
                    String marker = switch (state) {
                        case "reward" -> "!";
                        case "complete" -> "*";
                        case "available" -> ">";
                        default -> "x";
                    };
                    append(document, row, "row-marker state-" + state, marker, 0);
                    Element title = append(document, row, "quest-title", entry.title(), 0);
                    hoverTargets.add(new HoverTarget(entry, title));
                    if (detail.marks(entry)) append(document, row, "detail-context", "", 0);
                    row.addEventListener("click", event -> {
                        if (currentBinding.getAsBoolean() && event instanceof MouseEvent mouse && mouse.button == 0) openQuest.accept(entry);
                    });
                }
            }
        }
        return List.copyOf(hoverTargets);
    }

    public record HoverTarget(QuestLogSnapshot.Entry entry, Element title) {}

    /** Visual bounds include scroll offsets; intersect with the list viewport before showing native Tooltip. */
    public static QuestLogSnapshot.Entry tooltipAt(Document document, List<HoverTarget> targets, double x, double y) {
        if (!contains(require(document, "#list-entries").getBoundingClientRect(), x, y)) return null;
        for (var target : targets) if (contains(target.title().getBoundingClientRect(), x, y)) return target.entry();
        return null;
    }

    private static boolean contains(Element.DOMRect rect, double x, double y) {
        return x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom;
    }

    private static Element append(Document document, Element parent, String cssClass, String text, long id) {
        Element row = document.createElement("div");
        row.setAttribute("class", cssClass);
        row.setAttribute("data-entry-id", Long.toUnsignedString(id, 16));
        // Plain text avoids interpreting quest titles as markup or script.
        row.setTextContent(text);
        // AUI may replace a generic Element with its registered DIV implementation.
        // Children, attributes and listeners must target the returned attached node.
        return parent.appendChild(row);
    }

    private static Element require(Document document, String selector) {
        Element element = document.querySelector(selector);
        if (element == null) throw new IllegalStateException("Missing journal element: " + selector);
        return element;
    }
}
