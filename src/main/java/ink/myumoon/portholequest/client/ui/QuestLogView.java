package ink.myumoon.portholequest.client.ui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.network.chat.Component;

/** R1 static, all-expanded text projection. Selection and full list behavior belong to R2. */
public final class QuestLogView {
    private QuestLogView() {}

    public static void render(Document document, QuestLogSnapshot snapshot) {
        Element list = require(document, "#chapter-list");
        Element blankTitle = require(document, "#blank-title");
        list.replaceChildren();
        blankTitle.setTextContent(Component.translatable("ui.portholequest.blank_title").getString());

        if (snapshot.groups().isEmpty()) {
            append(document, list, "empty-row", Component.translatable("ui.portholequest.empty_list").getString(), 0);
            return;
        }
        for (var group : snapshot.groups()) {
            append(document, list, "group-row", group.title(), group.id());
            for (var chapter : group.chapters()) {
                append(document, list, "chapter-row", chapter.title(), chapter.id());
                for (var entry : chapter.entries()) {
                    Element row = append(document, list, entry.link() ? "quest-row link-row" : "quest-row", entry.title(), entry.entryId());
                    row.setAttribute("data-quest-id", Long.toUnsignedString(entry.questId(), 16));
                    row.setAttribute("data-chapter-id", Long.toUnsignedString(entry.chapterId(), 16));
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
        parent.appendChild(row);
        return row;
    }

    private static Element require(Document document, String selector) {
        Element element = document.querySelector(selector);
        if (element == null) throw new IllegalStateException("Missing journal element: " + selector);
        return element;
    }
}
