package ink.myumoon.portholequest.client.ui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.MouseEvent;
import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** R2 list projection. Detail navigation is added by the later read-only block. */
public final class QuestLogView {
    private QuestLogView() {}

    public static void render(Document document, List<QuestLogSnapshot.Group> groups, String groupTitle, ListFilter filter,
                              Map<Long, Boolean> expanded, boolean defaultExpanded,
                              BooleanSupplier currentBinding, Consumer<ListFilter> changeFilter,
                              LongConsumer toggleChapter) {
        Element list = require(document, "#list-entries");
        Element switches = require(document, "#list-switches");
        Element blankTitle = require(document, "#blank-title");
        list.replaceChildren();
        // Replacing controls also discards their old listeners on snapshot/DOM rebuild.
        switches.replaceChildren();
        require(document, "#list-heading").setTextContent(groupTitle);
        blankTitle.setTextContent(Component.translatable("ui.portholequest.blank_title").getString());

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
            return;
        }
        for (var group : groups) {
            for (var chapter : group.chapters()) {
                boolean open = expanded.getOrDefault(chapter.id(), defaultExpanded);
                Element chapterRow = append(document, list, "chapter-row", "", chapter.id());
                chapterRow.setAttribute("data-chapter-id", Long.toUnsignedString(chapter.id(), 16));
                chapterRow.setAttribute("aria-expanded", Boolean.toString(open));
                append(document, chapterRow, "row-marker expand-marker", open ? "-" : "+", 0);
                append(document, chapterRow, "chapter-title", chapter.title(), 0);
                chapterRow.addEventListener("click", event -> {
                    if (currentBinding.getAsBoolean() && event instanceof MouseEvent mouse && mouse.button == 0) toggleChapter.accept(chapter.id());
                });
                if (!open) continue;
                for (var entry : chapter.entries()) {
                    Element row = append(document, list, "quest-row", "", entry.entryId());
                    row.setAttribute("data-quest-id", Long.toUnsignedString(entry.questId(), 16));
                    row.setAttribute("data-chapter-id", Long.toUnsignedString(entry.chapterId(), 16));
                    String state = entry.completed() ? (entry.unclaimedRewards() ? "reward" : "complete")
                            : (entry.canStart() ? "available" : "locked");
                    row.setAttribute("data-state", state);
                    String marker = switch (state) {
                        case "reward" -> "!";
                        case "complete" -> "*";
                        case "available" -> ">";
                        default -> "x";
                    };
                    append(document, row, "row-marker state-" + state, marker, 0);
                    append(document, row, "quest-title", entry.title(), 0);
                }
            }
        }
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
