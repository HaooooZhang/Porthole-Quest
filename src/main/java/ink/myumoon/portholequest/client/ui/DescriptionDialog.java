package ink.myumoon.portholequest.client.ui;

import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.icon.Icon;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** A journal-owned modal; it does not create a nested Minecraft Screen. */
public final class DescriptionDialog {
    private static final int WIDTH = 205;
    private static final int HEIGHT = 220;
    private static final int LEFT = (336 - WIDTH) / 2;
    private static final int TOP = (208 - HEIGHT) / 2;
    private static final int TEXT_LEFT = LEFT + 9;
    private static final int TEXT_TOP = TOP + 27;
    private static final int TEXT_WIDTH = WIDTH - 18;
    private static final int TEXT_HEIGHT = 155;
    private static final int TITLE_LEFT = 8;
    private static final int TITLE_RIGHT = WIDTH - 22;

    private final long questId;
    private final String questTitle;
    private final QuestLogSnapshot.Description description;
    private final long openedAt = System.currentTimeMillis();
    private int page;
    private double scroll;
    private double contentHeight;
    private List<PlacedText> placedText = List.of();
    private Style hoveredStyle;

    public DescriptionDialog(long questId, String questTitle, QuestLogSnapshot.Description description) {
        this.questId = questId;
        this.questTitle = questTitle;
        this.description = description;
    }

    public DescriptionDialog(long questId, String questTitle, QuestLogSnapshot.Description description, State state) {
        this(questId, questTitle, description);
        if (state != null && state.questId() == questId) {
            page = clamp(state.page(), 0, Math.max(0, description.pages().size() - 1));
            scroll = Math.max(0, state.scroll());
        }
    }

    public long questId() {
        return questId;
    }

    public int page() {
        return page;
    }

    public double scroll() {
        return scroll;
    }

    public State state() {
        return new State(questId, page, scroll);
    }

    public void scroll(double amount) {
        scroll = clamp(scroll - amount * 18, 0, Math.max(0, contentHeight - TEXT_HEIGHT));
    }

    public void page(int delta) {
        int next = clamp(page + delta, 0, Math.max(0, description.pages().size() - 1));
        if (next != page) {
            page = next;
            scroll = 0;
        }
    }

    public void pageTo(int pageIndex) {
        int next = clamp(pageIndex, 0, Math.max(0, description.pages().size() - 1));
        if (next != page) {
            page = next;
            scroll = 0;
        }
    }

    public void draw(GuiGraphics graphics, Font font, int frameLeft, int frameTop, float scale,
                     int screenWidth, int screenHeight) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        try {
            drawAtOverlayLayer(graphics, font, frameLeft, frameTop, scale, screenWidth, screenHeight);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void drawAtOverlayLayer(GuiGraphics graphics, Font font, int frameLeft, int frameTop, float scale,
                                    int screenWidth, int screenHeight) {
        graphics.fill(0, 0, screenWidth, screenHeight, 0x99000000);
        int x = frameLeft + Math.round(LEFT * scale);
        int y = frameTop + Math.round(TOP * scale);
        int w = Math.round(WIDTH * scale);
        int h = Math.round(HEIGHT * scale);
        graphics.fill(x, y, x + w, y + h, 0xffeee2c5);
        graphics.fill(x, y, x + w, y + Math.max(1, Math.round(scale)), 0xff80543b);
        graphics.fill(x, y + h - Math.max(1, Math.round(scale)), x + w, y + h, 0xff80543b);
        graphics.fill(x, y, x + Math.max(1, Math.round(scale)), y + h, 0xff80543b);
        graphics.fill(x + w - Math.max(1, Math.round(scale)), y, x + w, y + h, 0xff80543b);
        int titleX = x + Math.round(TITLE_LEFT * scale);
        int titleY = y + Math.round(8 * scale);
        int titleWidth = Math.round((TITLE_RIGHT - TITLE_LEFT) * scale);
        int titleOverflow = Math.max(0, font.width(questTitle) - TITLE_RIGHT + TITLE_LEFT);
        int titleOffset = DetailHeaderView.marqueeOffset(titleOverflow,
                Math.max(0, System.currentTimeMillis() - openedAt));
        graphics.enableScissor(titleX, y, titleX + titleWidth, y + Math.round(22 * scale));
        try {
            graphics.drawString(font, Component.literal(questTitle), titleX - Math.round(titleOffset * scale),
                    titleY, 0xff423027, false);
        } finally {
            graphics.disableScissor();
        }

        int clipLeft = frameLeft + Math.round(TEXT_LEFT * scale);
        int clipTop = frameTop + Math.round(TEXT_TOP * scale);
        int clipRight = clipLeft + Math.round(TEXT_WIDTH * scale);
        int clipBottom = clipTop + Math.round(TEXT_HEIGHT * scale);
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        graphics.pose().pushPose();
        try {
            int textOriginX = frameLeft + Math.round(TEXT_LEFT * scale);
            int textOriginY = frameTop + Math.round(TEXT_TOP * scale);
            double currentScroll = scroll;
            graphics.pose().translate(textOriginX, textOriginY, 1);
            graphics.pose().scale(scale, scale, 1);
            renderPage(graphics, font, scale, textOriginX, textOriginY, currentScroll);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }

        String pageLabel = Component.translatable("ui.portholequest.description_page", page + 1,
                Math.max(1, description.pages().size())).getString();
        graphics.drawString(font, pageLabel, x + Math.round(8 * scale), y + h - Math.round(17 * scale),
                0xff776c60, false);
        drawButton(graphics, font, x + Math.round(116 * scale), y + h - Math.round(20 * scale),
                Math.round(34 * scale), Math.round(14 * scale), "<", page > 0);
        drawButton(graphics, font, x + Math.round(153 * scale), y + h - Math.round(20 * scale),
                Math.round(34 * scale), Math.round(14 * scale), ">", page + 1 < description.pages().size());
        drawButton(graphics, font, x + w - Math.round(18 * scale), y + Math.round(3 * scale),
                Math.round(14 * scale), Math.round(14 * scale), "x", true);
    }

    private void renderPage(GuiGraphics graphics, Font font, float scale, int textOriginX, int textOriginY,
                           double currentScroll) {
        var parts = currentParts();
        var text = new ArrayList<PlacedText>();
        var textRun = new ArrayList<QuestLogSnapshot.DescriptionPart>();
        double y = 0;
        for (var part : parts) {
            if (part.kind() == QuestLogSnapshot.DescriptionPart.Kind.TEXT) {
                textRun.add(part);
            } else {
                y = drawTextRun(graphics, font, text, textRun, y, currentScroll);
                int drawWidth = Math.max(1, part.imageWidth());
                int drawHeight = Math.max(1, part.imageHeight());
                int x = 0;
                if (part.imageFit()) {
                    double fitScale = (double) TEXT_WIDTH / drawWidth;
                    drawWidth = TEXT_WIDTH;
                    drawHeight = Math.max(1, (int) (drawHeight * fitScale));
                } else {
                    x = switch (part.imageAlign()) {
                        case "LEFT" -> 0;
                        case "RIGHT" -> TEXT_WIDTH - drawWidth;
                        default -> (TEXT_WIDTH - drawWidth) / 2;
                    };
                }
                int targetX = Math.round(textOriginX + x * scale);
                int targetY = Math.round(textOriginY + (float) (y - currentScroll) * scale);
                try {
                    Icon icon = Icon.getIcon(JsonParser.parseString(part.imageIconJson()));
                    graphics.pose().pushPose();
                    graphics.pose().translate((targetX - textOriginX) / scale,
                            (targetY - textOriginY) / scale, 0);
                    icon.draw(graphics, 0, 0, drawWidth, drawHeight);
                    graphics.pose().popPose();
                } catch (RuntimeException exception) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0, (targetY - textOriginY) / scale, 0);
                    graphics.drawString(font, Component.translatable("ui.portholequest.description_image_unavailable"),
                            0, 0, 0xff776c60, false);
                    graphics.pose().popPose();
                }
                y += drawHeight + 3;
            }
        }
        y = drawTextRun(graphics, font, text, textRun, y, currentScroll);
        contentHeight = y;
        scroll = clamp(scroll, 0, Math.max(0, contentHeight - TEXT_HEIGHT));
        placedText = List.copyOf(text);
    }

    private double drawTextRun(GuiGraphics graphics, Font font, List<PlacedText> placements,
                               List<QuestLogSnapshot.DescriptionPart> parts, double y, double currentScroll) {
        if (parts.isEmpty()) return y;
        Component component = DescriptionTextLayout.compose(parts);
        parts.clear();
        List<FormattedCharSequence> lines = DescriptionTextLayout.wrap(component, font, TEXT_WIDTH);
        if (lines.isEmpty()) return y;
        placements.add(new PlacedText(component, lines, y));
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, y - currentScroll + lineIndex * 9, 0);
            FormattedCharSequence line = DescriptionTextLayout.highlightLink(lines.get(lineIndex),
                    hoveredStyle == null ? null : hoveredStyle.getClickEvent());
            graphics.drawString(font, line, 0, 0, 0xff423027, false);
            graphics.pose().popPose();
        }
        return y + lines.size() * 9;
    }

    public Style styleAt(double mouseX, double mouseY, int frameLeft, int frameTop, float scale) {
        double textOriginX = frameLeft + Math.round(TEXT_LEFT * scale);
        double textOriginY = frameTop + Math.round(TEXT_TOP * scale);
        double x = (mouseX - textOriginX) / scale;
        double visibleY = (mouseY - textOriginY) / scale;
        if (x < 0 || x >= TEXT_WIDTH || visibleY < 0 || visibleY >= TEXT_HEIGHT) return null;
        double y = visibleY + scroll;
        for (var placed : placedText) {
            int height = Math.max(1, placed.lines().size()) * 9;
            if (y < placed.y() || y >= placed.y() + height) continue;
            int row = (int) ((y - placed.y()) / 9);
            return DescriptionTextLayout.styleAtWidth(placed.lines().get(row), x, Minecraft.getInstance().font);
        }
        return null;
    }

    public void setHoveredStyle(Style style) {
        hoveredStyle = style != null && style.getClickEvent() != null ? style : null;
    }

    public Component controlTooltipAt(double mouseX, double mouseY, int frameLeft, int frameTop, float scale) {
        double x = (mouseX - frameLeft) / scale;
        double y = (mouseY - frameTop) / scale;
        if (x >= LEFT + WIDTH - 18 && x < LEFT + WIDTH - 4 && y >= TOP + 3 && y < TOP + 17) {
            return Component.translatable("ui.portholequest.description_close");
        }
        if (y >= TOP + HEIGHT - 20 && y < TOP + HEIGHT - 6) {
            if (x >= LEFT + 116 && x < LEFT + 150 && page > 0) {
                return Component.translatable("ui.portholequest.description_previous_page");
            }
            if (x >= LEFT + 153 && x < LEFT + 187 && page + 1 < description.pages().size()) {
                return Component.translatable("ui.portholequest.description_next_page");
            }
        }
        return null;
    }

    public ClickResult mouseClicked(double mouseX, double mouseY, int button, int frameLeft, int frameTop, float scale) {
        if (button != 0) return ClickResult.NONE;
        double x = (mouseX - frameLeft) / scale;
        double y = (mouseY - frameTop) / scale;
        if (x >= LEFT + WIDTH - 18 && x < LEFT + WIDTH - 4 && y >= TOP + 3 && y < TOP + 17) {
            return ClickResult.CLOSE;
        }
        if (y >= TOP + HEIGHT - 20 && y < TOP + HEIGHT - 6) {
            if (x >= LEFT + 116 && x < LEFT + 150) {
                if (page > 0) {
                    page--;
                    scroll = 0;
                }
                return ClickResult.CONTROL;
            }
            if (x >= LEFT + 153 && x < LEFT + 187) {
                if (page + 1 < description.pages().size()) {
                    page++;
                    scroll = 0;
                }
                return ClickResult.CONTROL;
            }
        }
        return ClickResult.NONE;
    }

    private List<QuestLogSnapshot.DescriptionPart> currentParts() {
        if (description.pages().isEmpty()) return List.of();
        return description.pages().get(Math.min(page, description.pages().size() - 1)).parts();
    }

    private static void drawButton(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                   String label, boolean enabled) {
        graphics.fill(x, y, x + width, y + height, enabled ? 0xffd0bb95 : 0xffd9cfb6);
        graphics.drawString(font, label, x + 3, y + 3, enabled ? 0xff423027 : 0xffa79779, false);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PlacedText(Component component, List<FormattedCharSequence> lines, double y) {
        private PlacedText {
            lines = List.copyOf(lines);
        }
    }

    public record State(long questId, int page, double scroll) {
        public State {
            page = Math.max(0, page);
            scroll = Double.isFinite(scroll) ? Math.max(0, scroll) : 0;
        }
    }

    public enum ClickResult {
        CLOSE,
        CONTROL,
        NONE
    }
}
