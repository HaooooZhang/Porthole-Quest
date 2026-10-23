package ink.myumoon.portholequest.client.ui;

import com.google.gson.JsonParser;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import dev.ftb.mods.ftblibrary.icon.Icon;
import ink.myumoon.portholequest.client.screen.ScreenViewState;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import ink.myumoon.portholequest.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/** AUI owns header layout and subtitle wrapping; native MC draws Library icons and a pixel-aligned marquee. */
public final class DetailHeaderView {
    private static final int BOOK_TITLE_SCALE = 2;
    private final String title;
    private final Icon icon;
    private final Element iconAnchor;
    private final Element titleAnchor;
    private final long openedAt;
    private final Config.BlankDetailMode blankMode;
    private final ResourceLocation blankTexture;
    private final int blankWidth;
    private final int blankHeight;
    private final int textureWidth;
    private final int textureHeight;
    private final int bookTitleColor;

    private DetailHeaderView(String title, String iconJson, Element iconAnchor, Element titleAnchor, long openedAt,
                             Config.BlankDetailMode blankMode, ResourceLocation blankTexture,
                             int blankWidth, int blankHeight, int textureWidth, int textureHeight) {
        this.title = title;
        this.icon = iconJson == null ? null : Icon.getIcon(JsonParser.parseString(iconJson));
        this.iconAnchor = iconAnchor;
        this.titleAnchor = titleAnchor;
        this.openedAt = openedAt;
        this.blankMode = blankMode;
        this.blankTexture = blankTexture;
        this.blankWidth = blankWidth;
        this.blankHeight = blankHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.bookTitleColor = blankMode == Config.BlankDetailMode.BOOK
                ? 0xff000000 | Integer.parseInt(Config.BLANK_DETAIL_BOOK_TITLE_COLOR.get().substring(1), 16) : 0;
    }

    public static DetailHeaderView bind(Document document, QuestLogSnapshot snapshot, ScreenViewState state,
                                        long openedAt, BooleanSupplier currentBinding, Runnable openFlowchart) {
        Element panel = document.querySelector("#detail-panel");
        panel.replaceChildren();
        if (state.kind() == ScreenViewState.Kind.BLANK) {
            Config.BlankDetailMode mode = Config.BLANK_DETAIL_MODE.get();
            Element image = null;
            if (mode == Config.BlankDetailMode.IMAGE) {
                image = append(document, panel, "blank-image", "");
                image.setInlineStyleProperty("width", Config.BLANK_DETAIL_IMAGE_WIDTH.get() + "px");
                image.setInlineStyleProperty("height", Config.BLANK_DETAIL_IMAGE_HEIGHT.get() + "px");
                image.setInlineStyleProperty("left", ((192 - Config.BLANK_DETAIL_IMAGE_WIDTH.get()) / 2) + "px");
                image.setInlineStyleProperty("top", ((192 - Config.BLANK_DETAIL_IMAGE_HEIGHT.get()) / 2) + "px");
            } else if (mode == Config.BlankDetailMode.BOOK) {
                Element header = append(document, panel, "blank-book", "");
                Element iconAnchor = append(document, header, "blank-book-icon", "");
                Element titleAnchor = append(document, header, "blank-book-title", "");
                titleAnchor.setAttribute("data-title", snapshot.bookTitle());
                return new DetailHeaderView(snapshot.bookTitle(), snapshot.bookIconJson(),
                        iconAnchor, titleAnchor, openedAt, mode, null, 0, 0, 0, 0);
            }
            return new DetailHeaderView("", null, image, null, openedAt, mode,
                    mode == Config.BlankDetailMode.IMAGE ? resource(Config.BLANK_DETAIL_IMAGE.get()) : null,
                    Config.BLANK_DETAIL_IMAGE_WIDTH.get(), Config.BLANK_DETAIL_IMAGE_HEIGHT.get(),
                    Config.BLANK_DETAIL_TEXTURE_WIDTH.get(), Config.BLANK_DETAIL_TEXTURE_HEIGHT.get());
        }
        String title = "";
        for (var group : snapshot.groups()) for (var chapter : group.chapters()) {
            if (chapter.id() != state.chapterId()) continue;
            if (state.kind() == ScreenViewState.Kind.CHAPTER) title = chapter.title();
            else for (var entry : chapter.entries()) if (state.marks(entry)) title = entry.title();
        }
        String iconJson;
        String subtitle;
        String status = "";
        if (state.kind() == ScreenViewState.Kind.CHAPTER) {
            var header = snapshot.chapterHeaders().get(state.chapterId());
            iconJson = header.iconJson();
            subtitle = header.subtitle();
            status = Component.translatable("ui.portholequest.chapter_state."
                    + (header.completed() ? "completed" : header.started() ? "started" : "not_started")).getString();
        } else {
            var header = snapshot.questHeaders().get(state.questId());
            iconJson = header.iconJson();
            subtitle = header.subtitle();
        }
        Element header = append(document, panel, "detail-header", "");
        Element iconAnchor = append(document, header, "detail-icon", "");
        Element titleAnchor = append(document, header, "detail-title", "");
        titleAnchor.setAttribute("data-title", title);
        int subtitleLines = 0;
        if (!subtitle.isBlank()) {
            Element subtitleElement = append(document, header, "detail-subtitle", "");
            String[] lines = subtitle.split("\\R", -1);
            Font font = Minecraft.getInstance().font;
            for (String line : lines) {
                Element lineElement = document.createElement("div");
                lineElement.setAttribute("class", "detail-subtitle-line");
                lineElement.setTextContent(line);
                subtitleElement.appendChild(lineElement);
                subtitleLines += Math.max(1, font.split(Component.literal(line), 176).size());
            }
        }
        if (!status.isBlank()) append(document, header, "detail-status", status);
        int headerHeight = Math.min(160, Math.max(64, 51 + subtitleLines * 9));
        header.setInlineStyleProperty("height", headerHeight + "px");
        panel.setAttribute("data-detail-header-height", Integer.toString(headerHeight));
        appendActionFooter(document, panel, currentBinding, openFlowchart);
        return new DetailHeaderView(title, iconJson, iconAnchor, titleAnchor, openedAt,
                null, null, 0, 0, 0, 0);
    }

    /** Visual-only D2 action affordances. R4 will add listeners and server actions. */
    private static void appendActionFooter(Document document, Element panel,
                                           BooleanSupplier currentBinding, Runnable openFlowchart) {
        Element footer = append(document, panel, "detail-footer", "");
        Element track = appendClass(document, footer, "detail-action-button detail-action-track", "");
        track.setAttribute("aria-disabled", "true");
        track.setAttribute("data-action", "track");
        appendClass(document, track, "detail-action-label", Component.translatable("ui.portholequest.action.track").getString());
        if (!Config.FLOWCHART_ENABLED.get()) return;
        Element flowchart = appendClass(document, footer, "detail-action-button detail-action-flowchart", "");
        flowchart.setAttribute("aria-disabled", "false");
        flowchart.setAttribute("data-action", "flowchart");
        appendClass(document, flowchart, "detail-action-label", Component.translatable("ui.portholequest.action.flowchart").getString());
        flowchart.addEventListener("click", event -> {
            if (currentBinding.getAsBoolean()
                    && event instanceof com.sighs.apricityui.event.MouseEvent mouse && mouse.button == 0) {
                openFlowchart.run();
            }
        });
    }

    private static Element append(Document document, Element parent, String id, String text) {
        Element element = document.createElement("div");
        element.setAttribute("id", id);
        element.setTextContent(text);
        return parent.appendChild(element);
    }

    private static Element appendClass(Document document, Element parent, String cssClass, String text) {
        Element element = document.createElement("div");
        element.setAttribute("class", cssClass);
        element.setTextContent(text);
        return parent.appendChild(element);
    }

    public void draw(GuiGraphics graphics, Font font, int left, int top, float scale, long now) {
        if (blankMode != null) {
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(left, top, 300);
                pose.scale(scale, scale, 1);
                if (blankMode == Config.BlankDetailMode.IMAGE && blankTexture != null) {
                    // DOM bounds include the frame border and Detail panel offset.
                    var imageRect = iconAnchor.getBoundingClientRect();
                    pose.translate(Math.round(imageRect.x), Math.round(imageRect.y), 0);
                    pose.scale((float) blankWidth / textureWidth, (float) blankHeight / textureHeight, 1);
                    graphics.blit(blankTexture, 0, 0, 0, 0,
                            textureWidth, textureHeight, textureWidth, textureHeight);
                } else if (blankMode == Config.BlankDetailMode.BOOK) {
                    drawBook(graphics, font, left, top, scale, now);
                }
            } finally {
                pose.popPose();
            }
            return;
        }
        if (iconAnchor == null || titleAnchor == null || icon == null) return;
        var pose = graphics.pose();
        var iconRect = iconAnchor.getBoundingClientRect();
        var titleRect = titleAnchor.getBoundingClientRect();
        pose.pushPose();
        try {
            pose.translate(left, top, 300);
            pose.scale(scale, scale, 1);
            icon.draw(graphics, (int) Math.round(iconRect.x), (int) Math.round(iconRect.y), 16, 16);
            graphics.flush();
            int x = (int) Math.round(titleRect.x);
            int y = (int) Math.round(titleRect.y);
            int width = (int) Math.round(titleRect.width);
            int overflow = Math.max(0, font.width(title) - width);
            int offset = marqueeOffset(overflow, Math.max(0, now - openedAt));
            graphics.enableScissor((int) Math.floor(left + x * scale), (int) Math.floor(top + y * scale),
                    (int) Math.ceil(left + (x + width) * scale), (int) Math.ceil(top + (y + 10) * scale));
            try {
                graphics.drawString(font, title, x + (overflow == 0 ? (width - font.width(title)) / 2 : -offset),
                        y, 0xff423027, false);
                graphics.flush();
            } finally {
                graphics.disableScissor();
            }
        } finally {
            pose.popPose();
        }
    }

    private void drawBook(GuiGraphics graphics, Font font, int left, int top, float scale, long now) {
        var iconRect = iconAnchor.getBoundingClientRect();
        var titleRect = titleAnchor.getBoundingClientRect();
        icon.draw(graphics, (int) Math.round(iconRect.x), (int) Math.round(iconRect.y), 32, 32);
        graphics.flush();
        int x = (int) Math.round(titleRect.x);
        int y = (int) Math.round(titleRect.y);
        int width = (int) Math.round(titleRect.width);
        int textWidth = font.width(title) * BOOK_TITLE_SCALE;
        int overflow = Math.max(0, textWidth - width);
        int offset = overflow == 0 ? (width - textWidth) / 2
                : -marqueeOffset(overflow, Math.max(0, now - openedAt));
        graphics.enableScissor((int) Math.floor(left + x * scale), (int) Math.floor(top + y * scale),
                (int) Math.ceil(left + (x + width) * scale),
                (int) Math.ceil(top + (y + titleRect.height) * scale));
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + offset, y, 0);
            pose.scale(BOOK_TITLE_SCALE, BOOK_TITLE_SCALE, 1);
            graphics.drawString(font, title, 0, 0, bookTitleColor, false);
            graphics.flush();
        } finally {
            pose.popPose();
            graphics.disableScissor();
        }
    }

    /** Hold at each end, then move in integer logical pixels at 20 px/s. */
    static int marqueeOffset(int overflow, long elapsed) {
        if (overflow <= 0) return 0;
        long travel = overflow * 50L;
        long phase = elapsed % (travel * 2 + 2000);
        if (phase < 1000) return 0;
        if (phase < 1000 + travel) return (int) ((phase - 1000) / 50);
        if (phase < 2000 + travel) return overflow;
        return overflow - (int) ((phase - 2000 - travel) / 50);
    }

    private static ResourceLocation resource(String value) {
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException ignored) {
            return ResourceLocation.fromNamespaceAndPath("portholequest",
                    "textures/gui/detail/blank_detail_logo.png");
        }
    }
}
