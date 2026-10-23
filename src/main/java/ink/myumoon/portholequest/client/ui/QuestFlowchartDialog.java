package ink.myumoon.portholequest.client.ui;

import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.icon.Icon;
import ink.myumoon.portholequest.model.QuestFlowchartLayout;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Transient, read-only quest dependency preview. Its viewport is discarded on close. */
public final class QuestFlowchartDialog {
    private static final int LEFT = 18;
    private static final int TOP = 4;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 200;
    private static final int VIEW_LEFT = 4;
    private static final int VIEW_TOP = 20;
    private static final int VIEW_WIDTH = 292;
    private static final int VIEW_HEIGHT = 174;
    private static final int SCROLL_STEP = 24;
    private final QuestFlowchartLayout.Graph graph;
    private final Set<Long> chapterQuestIds;
    private double scrollX;
    private double scrollY;
    private DragTarget dragTarget = DragTarget.NONE;
    private double lastMouseX;
    private double lastMouseY;

    public QuestFlowchartDialog(QuestFlowchartLayout.Graph graph, Set<Long> chapterQuestIds) {
        this.graph = graph;
        this.chapterQuestIds = Set.copyOf(chapterQuestIds);
        scrollX = clamp(graph.initialScrollX(), 0, maxScrollX());
        scrollY = clamp(graph.initialScrollY(), 0, maxScrollY());
    }

    public void scroll(double mouseX, double mouseY, double amount, boolean horizontal,
                       int frameLeft, int frameTop, float scale) {
        double x = (mouseX - frameLeft) / scale - LEFT;
        double y = (mouseY - frameTop) / scale - TOP;
        if (!inViewport(x, y)) return;
        if (horizontal && maxScrollX() > 0) scrollX = clamp(scrollX - amount * SCROLL_STEP, 0, maxScrollX());
        else if (!horizontal && maxScrollY() > 0) scrollY = clamp(scrollY - amount * SCROLL_STEP, 0, maxScrollY());
    }

    public void move(int horizontal, int vertical) {
        scrollX = clamp(scrollX + horizontal * SCROLL_STEP, 0, maxScrollX());
        scrollY = clamp(scrollY + vertical * SCROLL_STEP, 0, maxScrollY());
    }

    public void mouseDragged(double mouseX, double mouseY, int button, float scale) {
        if (dragTarget == DragTarget.NONE || button != 0) return;
        double dx = (mouseX - lastMouseX) / scale;
        double dy = (mouseY - lastMouseY) / scale;
        switch (dragTarget) {
            case CANVAS -> {
                scrollX = clamp(scrollX - dx, 0, maxScrollX());
                scrollY = clamp(scrollY - dy, 0, maxScrollY());
            }
            case HORIZONTAL_SCROLLBAR -> {
                Scrollbar bar = horizontalScrollbar();
                scrollX = clamp(scrollX + bar.scrollDelta(dx), 0, maxScrollX());
            }
            case VERTICAL_SCROLLBAR -> {
                Scrollbar bar = verticalScrollbar();
                scrollY = clamp(scrollY + bar.scrollDelta(dy), 0, maxScrollY());
            }
            default -> { }
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public void mouseReleased() {
        dragTarget = DragTarget.NONE;
    }

    public ClickResult mouseClicked(double mouseX, double mouseY, int button,
                                    int frameLeft, int frameTop, float scale) {
        if (button != 0) return new ClickResult(ClickKind.CONSUMED, 0);
        double x = (mouseX - frameLeft) / scale - LEFT;
        double y = (mouseY - frameTop) / scale - TOP;
        if (x >= WIDTH - 19 && x < WIDTH - 4 && y >= 2 && y < 17) return new ClickResult(ClickKind.CLOSE, 0);
        DragTarget scrollbar = scrollbarAt(x, y);
        if (scrollbar != DragTarget.NONE) {
            // Scrollbars cover the canvas, so handle them before any node underneath.
            Scrollbar bar = scrollbar == DragTarget.HORIZONTAL_SCROLLBAR ? horizontalScrollbar() : verticalScrollbar();
            double position = scrollbar == DragTarget.HORIZONTAL_SCROLLBAR ? x - VIEW_LEFT : y - VIEW_TOP;
            if (position < bar.offset() || position >= bar.offset() + bar.thumbSize()) {
                double value = clamp(bar.scrollDelta(position - bar.thumbSize() / 2), 0, bar.maxScroll());
                if (scrollbar == DragTarget.HORIZONTAL_SCROLLBAR) scrollX = value;
                else scrollY = value;
            }
            dragTarget = scrollbar;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return new ClickResult(ClickKind.CONSUMED, 0);
        }
        if (!inViewport(x, y)) return new ClickResult(ClickKind.CONSUMED, 0);
        double graphX = x - VIEW_LEFT + scrollX;
        double graphY = y - VIEW_TOP + scrollY;
        for (var node : graph.nodes()) {
            if (graphX >= node.x() && graphX < node.right() && graphY >= node.y() && graphY < node.bottom()) {
                return node.quest().detailsAvailable()
                        ? new ClickResult(ClickKind.OPEN_QUEST, node.quest().questId())
                        : new ClickResult(ClickKind.CONSUMED, 0);
            }
        }
        dragTarget = DragTarget.CANVAS;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return new ClickResult(ClickKind.CONSUMED, 0);
    }

    public List<Component> tooltipAt(double mouseX, double mouseY, int frameLeft, int frameTop, float scale) {
        var node = nodeAt(mouseX, mouseY, frameLeft, frameTop, scale);
        if (node == null) return List.of();
        var quest = node.quest();
        var tooltip = new ArrayList<Component>();
        tooltip.add(Component.literal(quest.title()));
        tooltip.add(Component.translatable(statusKey(quest)));
        if (!chapterQuestIds.contains(quest.questId())) {
            tooltip.add(Component.translatable("ui.portholequest.flowchart.external"));
        }
        if (quest.pinned()) tooltip.add(Component.translatable("ui.portholequest.flowchart.tracked"));
        if (!quest.detailsAvailable()) tooltip.add(Component.translatable("ui.portholequest.flowchart.details_hidden"));
        return List.copyOf(tooltip);
    }

    private QuestFlowchartLayout.Node nodeAt(double mouseX, double mouseY, int frameLeft, int frameTop, float scale) {
        double x = (mouseX - frameLeft) / scale - LEFT;
        double y = (mouseY - frameTop) / scale - TOP;
        if (!inViewport(x, y) || scrollbarAt(x, y) != DragTarget.NONE) return null;
        double graphX = x - VIEW_LEFT + scrollX;
        double graphY = y - VIEW_TOP + scrollY;
        for (var node : graph.nodes()) {
            if (graphX < node.x() || graphX >= node.right() || graphY < node.y() || graphY >= node.bottom()) continue;
            return node;
        }
        return null;
    }

    public void draw(GuiGraphics graphics, Font font, int frameLeft, int frameTop, float scale,
                     int screenWidth, int screenHeight, double mouseX, double mouseY) {
        var hovered = nodeAt(mouseX, mouseY, frameLeft, frameTop, scale);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        try {
            drawAtOverlayLayer(graphics, font, frameLeft, frameTop, scale, screenWidth, screenHeight, hovered);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void drawAtOverlayLayer(GuiGraphics graphics, Font font, int frameLeft, int frameTop, float scale,
                                    int screenWidth, int screenHeight, QuestFlowchartLayout.Node hovered) {
        int left = frameLeft + Math.round(LEFT * scale);
        int top = frameTop + Math.round(TOP * scale);
        int right = left + Math.round(WIDTH * scale);
        int bottom = top + Math.round(HEIGHT * scale);
        graphics.fill(0, 0, screenWidth, screenHeight, 0x99000000);
        graphics.fill(left, top, right, bottom, 0xffe7d9b7);
        graphics.fill(left, top, right, top + Math.max(1, Math.round(scale)), 0xff80543b);
        graphics.fill(left, bottom - Math.max(1, Math.round(scale)), right, bottom, 0xff80543b);
        graphics.fill(left, top, left + Math.max(1, Math.round(scale)), bottom, 0xff80543b);
        graphics.fill(right - Math.max(1, Math.round(scale)), top, right, bottom, 0xff80543b);
        graphics.drawString(font, Component.translatable("ui.portholequest.flowchart.title"),
                left + Math.round(6 * scale), top + Math.round(6 * scale), 0xff423027, false);
        drawCloseButton(graphics, font, left, top, scale);

        int clipLeft = frameLeft + Math.round((LEFT + VIEW_LEFT) * scale);
        int clipTop = frameTop + Math.round((TOP + VIEW_TOP) * scale);
        int clipRight = clipLeft + Math.round(VIEW_WIDTH * scale);
        int clipBottom = clipTop + Math.round(VIEW_HEIGHT * scale);
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(clipLeft, clipTop, 1);
            graphics.pose().scale(scale, scale, 1);
            graphics.pose().translate(-scrollX, -scrollY, 0);
            drawEdges(graphics, hovered);
            graphics.flush();
            drawNodes(graphics, font);
        } finally {
            // Item icons submit separate render batches; finish them while the canvas clip is active.
            graphics.flush();
            graphics.pose().popPose();
            graphics.disableScissor();
        }
        graphics.pose().pushPose();
        try {
            // Item rendering adds its own depth. Scrollbars must remain above the whole canvas.
            graphics.pose().translate(0, 0, 400);
            drawScrollbars(graphics, left, top, scale);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void drawEdges(GuiGraphics graphics, QuestFlowchartLayout.Node hovered) {
        for (var edge : graph.edges()) drawEdge(graphics, edge, 0xff786953, 1);
        if (hovered == null) return;
        // Paint connected paths last so shared runs cannot cover the highlight.
        for (var edge : graph.edges()) {
            if (edge.from() == hovered || edge.to() == hovered) drawEdge(graphics, edge, 0xffd17824, 2);
        }
    }

    private void drawEdge(GuiGraphics graphics, QuestFlowchartLayout.PositionedEdge edge, int color, int thickness) {
        var points = graph.route(edge);
        for (int index = 1; index < points.size() - 1; index++) {
            var from = points.get(index - 1);
            var to = points.get(index);
            graphics.fill(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()),
                    Math.max(from.x(), to.x()) + thickness, Math.max(from.y(), to.y()) + thickness, color);
        }
        var previous = points.get(points.size() - 2);
        var last = points.getLast();
        if (previous.y() == last.y()) {
            drawHorizontalArrow(graphics, previous.x(), last.y(), last.x(), color, thickness);
        } else {
            drawVerticalArrow(graphics, last.x(), previous.y(), last.y(), color, thickness);
        }
    }

    private static void drawHorizontalArrow(GuiGraphics graphics, int startX, int y, int endX, int color, int thickness) {
        int arrowX = endX - 4;
        graphics.fill(startX, y, arrowX, y + thickness, color);
        graphics.fill(arrowX, y - 3, arrowX + 1, y - 2, color);
        graphics.fill(arrowX + 1, y - 2, arrowX + 2, y - 1, color);
        graphics.fill(arrowX + 2, y - 1, arrowX + 3, y + 1, color);
        graphics.fill(arrowX + 1, y + 1, arrowX + 2, y + 2, color);
        graphics.fill(arrowX, y + 2, arrowX + 1, y + 3, color);
    }

    private static void drawVerticalArrow(GuiGraphics graphics, int x, int startY, int endY, int color, int thickness) {
        boolean pointsDown = endY > startY;
        int tipY = endY;
        int baseY = tipY + (pointsDown ? -4 : 4);
        graphics.fill(x, Math.min(startY, baseY), x + thickness, Math.max(startY, baseY), color);
        for (int offset = 1; offset <= 3; offset++) {
            int y = tipY + (pointsDown ? -offset : offset);
            graphics.fill(x - offset, y, x - offset + 1, y + 1, color);
            graphics.fill(x + offset, y, x + offset + 1, y + 1, color);
        }
    }

    private void drawNodes(GuiGraphics graphics, Font font) {
        for (var node : graph.nodes()) {
            graphics.fill(node.x(), node.y(), node.right(), node.bottom(), borderColor(node.quest()));
            graphics.fill(node.x() + 2, node.y() + 2, node.right() - 2, node.bottom() - 2, 0xfff2e8d4);
            if (!chapterQuestIds.contains(node.quest().questId())) drawExternalBorder(graphics, node);
            try {
                Icon.getIcon(JsonParser.parseString(node.quest().iconJson())).draw(graphics,
                        node.x() + (QuestFlowchartLayout.NODE_WIDTH - 16) / 2,
                        node.y() + (QuestFlowchartLayout.NODE_HEIGHT - 16) / 2, 16, 16);
            } catch (RuntimeException ignored) {
                graphics.fill(node.x() + 10, node.y() + 10, node.x() + 22, node.y() + 22, 0xffa79779);
            }
        }
    }

    private static void drawExternalBorder(GuiGraphics graphics, QuestFlowchartLayout.Node node) {
        // A distinct dashed outer rim identifies chapter scope; the inner rim keeps task status.
        int color = 0xff8064ad;
        graphics.fill(node.x(), node.y(), node.right(), node.y() + 1, 0xfff2e8d4);
        graphics.fill(node.x(), node.bottom() - 1, node.right(), node.bottom(), 0xfff2e8d4);
        graphics.fill(node.x(), node.y(), node.x() + 1, node.bottom(), 0xfff2e8d4);
        graphics.fill(node.right() - 1, node.y(), node.right(), node.bottom(), 0xfff2e8d4);
        for (int offset = 0; offset < QuestFlowchartLayout.NODE_WIDTH; offset += 6) {
            int end = Math.min(offset + 4, QuestFlowchartLayout.NODE_WIDTH);
            graphics.fill(node.x() + offset, node.y(), node.x() + end, node.y() + 1, color);
            graphics.fill(node.x() + offset, node.bottom() - 1, node.x() + end, node.bottom(), color);
            graphics.fill(node.x(), node.y() + offset, node.x() + 1, node.y() + end, color);
            graphics.fill(node.right() - 1, node.y() + offset, node.right(), node.y() + end, color);
        }
    }

    private void drawScrollbars(GuiGraphics graphics, int left, int top, float scale) {
        int trackY = top + Math.round((HEIGHT - 5) * scale);
        int trackX = left + Math.round((WIDTH - 5) * scale);
        int hTrackLeft = left + Math.round(VIEW_LEFT * scale);
        int hTrackRight = hTrackLeft + Math.round(VIEW_WIDTH * scale);
        int vTrackTop = top + Math.round(VIEW_TOP * scale);
        int vTrackBottom = vTrackTop + Math.round(VIEW_HEIGHT * scale);
        if (maxScrollX() > 0) {
            Scrollbar bar = horizontalScrollbar();
            int width = Math.round((float) (bar.thumbSize() * scale));
            int offset = Math.round((float) (bar.offset() * scale));
            graphics.fill(hTrackLeft, trackY, hTrackRight, trackY + Math.max(1, Math.round(scale)), 0xffbda57f);
            graphics.fill(hTrackLeft + offset, trackY - Math.round(scale), hTrackLeft + offset + width,
                    trackY + Math.round(2 * scale), 0xff806d52);
        }
        if (maxScrollY() > 0) {
            Scrollbar bar = verticalScrollbar();
            int height = Math.round((float) (bar.thumbSize() * scale));
            int offset = Math.round((float) (bar.offset() * scale));
            graphics.fill(trackX, vTrackTop, trackX + Math.max(1, Math.round(scale)), vTrackBottom, 0xffbda57f);
            graphics.fill(trackX - Math.round(scale), vTrackTop + offset, trackX + Math.round(2 * scale),
                    vTrackTop + offset + height, 0xff806d52);
        }
    }

    private static void drawCloseButton(GuiGraphics graphics, Font font, int left, int top, float scale) {
        int x = left + Math.round((WIDTH - 19) * scale);
        int y = top + Math.round(2 * scale);
        int size = Math.round(15 * scale);
        graphics.fill(x, y, x + size, y + size, 0xffd0bb95);
        graphics.drawString(font, "x", x + Math.round(5 * scale), y + Math.round(3 * scale), 0xff423027, false);
    }

    private static String statusKey(QuestLogSnapshot.GraphQuest quest) {
        if (quest.completed() && quest.unclaimedRewards()) return "ui.portholequest.flowchart.state.reward";
        if (quest.completed()) return "ui.portholequest.flowchart.state.completed";
        if (quest.started()) return "ui.portholequest.flowchart.state.started";
        return quest.canStart() ? "ui.portholequest.flowchart.state.available" : "ui.portholequest.flowchart.state.locked";
    }

    private static int borderColor(QuestLogSnapshot.GraphQuest quest) {
        if (quest.completed() && quest.unclaimedRewards()) return 0xffc99b43;
        if (quest.completed()) return 0xff638b58;
        if (quest.started()) return 0xff50879a;
        if (quest.canStart()) return 0xffa48a55;
        return 0xff776d68;
    }

    private boolean inViewport(double x, double y) {
        return x >= VIEW_LEFT && x < VIEW_LEFT + VIEW_WIDTH && y >= VIEW_TOP && y < VIEW_TOP + VIEW_HEIGHT;
    }

    private DragTarget scrollbarAt(double x, double y) {
        if (maxScrollX() > 0 && x >= VIEW_LEFT && x < VIEW_LEFT + VIEW_WIDTH
                && y >= HEIGHT - 6 && y < HEIGHT - 2) return DragTarget.HORIZONTAL_SCROLLBAR;
        if (maxScrollY() > 0 && y >= VIEW_TOP && y < VIEW_TOP + VIEW_HEIGHT
                && x >= WIDTH - 6 && x < WIDTH - 2) return DragTarget.VERTICAL_SCROLLBAR;
        return DragTarget.NONE;
    }

    private Scrollbar horizontalScrollbar() {
        return scrollbar(VIEW_WIDTH, contentWidth(), scrollX, 18);
    }

    private Scrollbar verticalScrollbar() {
        return scrollbar(VIEW_HEIGHT, contentHeight(), scrollY, 14);
    }

    private static Scrollbar scrollbar(double length, double content, double scroll, double minimumThumb) {
        double maxScroll = Math.max(0, content - length);
        double thumbSize = maxScroll == 0 ? length : Math.max(minimumThumb, length * length / content);
        double travel = length - thumbSize;
        return new Scrollbar(thumbSize, maxScroll == 0 ? 0 : travel * scroll / maxScroll, travel, maxScroll);
    }

    private record Scrollbar(double thumbSize, double offset, double travel, double maxScroll) {
        double scrollDelta(double distance) { return travel > 0 ? distance * maxScroll / travel : 0; }
    }

    private enum DragTarget { NONE, CANVAS, HORIZONTAL_SCROLLBAR, VERTICAL_SCROLLBAR }

    private double contentWidth() {
        return graph.nodes().stream().mapToInt(QuestFlowchartLayout.Node::right).max().orElse(VIEW_WIDTH - 12) + 12;
    }

    private double contentHeight() {
        return graph.nodes().stream().mapToInt(QuestFlowchartLayout.Node::bottom).max().orElse(VIEW_HEIGHT - 12) + 12;
    }

    private double maxScrollX() { return Math.max(0, contentWidth() - VIEW_WIDTH); }
    private double maxScrollY() { return Math.max(0, contentHeight() - VIEW_HEIGHT); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    public enum ClickKind { CLOSE, CONSUMED, OPEN_QUEST }

    public record ClickResult(ClickKind kind, long questId) {
    }
}
