package ink.myumoon.portholequest.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.client.Client;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.style.Cursor;
import ink.myumoon.portholequest.PortholeQuest;
import ink.myumoon.portholequest.client.ui.QuestLogView;
import ink.myumoon.portholequest.client.ui.ChapterGroupTabView;
import ink.myumoon.portholequest.client.ui.DetailHeaderView;
import ink.myumoon.portholequest.client.ui.ChapterDetailView;
import ink.myumoon.portholequest.client.ui.QuestDetailView;
import ink.myumoon.portholequest.client.ui.DescriptionDialog;
import ink.myumoon.portholequest.client.ui.QuestFlowchartDialog;
import ink.myumoon.portholequest.client.ui.QuestFlowchartDebug;
import ink.myumoon.portholequest.client.input.ChapterClickSequence;
import ink.myumoon.portholequest.client.input.PortholeKeyMappings;
import ink.myumoon.portholequest.model.QuestFlowchartLayout;
import ink.myumoon.portholequest.model.ChapterGroupTabs;
import ink.myumoon.portholequest.model.GroupSelection;
import ink.myumoon.portholequest.client.session.GuiSessionCache.ListState;
import ink.myumoon.portholequest.Config;
import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class PortholeQuestScreen extends ApricityScreen {
    private static final double BACK_SWATCH_LEFT = 136;
    private static final double BACK_SWATCH_TOP = 0;
    private static final double BACK_SWATCH_WIDTH = 32;
    private static final double BACK_SWATCH_HEIGHT = 8;
    private QuestLogSnapshot snapshot;
    private Document boundDocument;
    private long boundGeneration = -1;
    private int frameLeft;
    private int frameTop;
    private Position previousMouse;
    private ListFilter filter;
    private final boolean defaultExpanded;
    private final Map<Long, Boolean> expanded;
    private final Consumer<ListState> saveState;
    private double listScroll;
    private double detailScroll;
    private boolean listDirty;
    private boolean detailDirty;
    private boolean chapterDetailDirty;
    private long bindingVersion;
    private GroupSelection requestedGroup;
    private GroupSelection effectiveGroup = GroupSelection.ALL;
    private List<ChapterGroupTabs.Slot> tabs = List.of();
    private ChapterGroupTabs.Slot pressedTab;
    private ScreenViewState detail = ScreenViewState.BLANK;
    private final ScreenStateHistory detailHistory;
    private DetailHeaderView detailHeader;
    private boolean boundFlowchartEnabled;
    private QuestDetailView questDetail;
    private DescriptionDialog descriptionDialog;
    private QuestFlowchartDialog flowchartDialog;
    private DescriptionDialog.State pendingDescriptionReturn;
    private List<QuestLogView.HoverTarget> hoverTargets = List.of();
    private final ChapterClickSequence chapterClicks = new ChapterClickSequence();
    private boolean chapterClickedThisRelease;
    private boolean pressedFrameBack;
    private long detailOpenedAt;
    private boolean descriptionExpanded;

    public PortholeQuestScreen(QuestLogSnapshot snapshot, ListState state, Consumer<ListState> saveState) {
        super("screens/quest_log.html");
        this.snapshot = snapshot;
        this.detailHistory = new ScreenStateHistory(Config.MAX_DETAIL_HISTORY.get());
        this.filter = state.filter();
        this.defaultExpanded = state.defaultExpanded();
        this.expanded = new HashMap<>(state.expanded());
        this.listScroll = state.scrollTop();
        this.detailScroll = state.detailScroll();
        this.descriptionExpanded = state.descriptionExpanded();
        this.detail = ScreenViewState.fromSession(state.detail()).retainValid(snapshot);
        this.saveState = saveState;
        this.requestedGroup = state.group().retainValid(snapshot);
        this.effectiveGroup = this.requestedGroup;
        pruneChapterState();
        setPauseGame(snapshot.pauseGame());
    }

    @Override
    protected void init() {
        captureListScroll();
        captureDetailScroll();
        super.init();
        bindCurrentDocument();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        captureDetailScroll();
        chapterClicks.reset();
        super.resize(minecraft, width, height);
        // ApricityScreen reapplies the viewport after init, resetting its input offsets.
        updateFrameTransform();
    }

    private void updateFrameTransform() {
        Document document = getLinkedDocument();
        if (document == null) return;
        var viewport = document.getViewport();
        double scale = viewport.renderScale();
        // Integer MC GUI origins keep texture pixels crisp, including odd-sized windows.
        frameLeft = (int) Math.floor((width - viewport.layoutWidth() * scale) / 2);
        frameTop = (int) Math.floor((height - viewport.layoutHeight() * scale) / 2);
        document.setViewportTransform(scale, scale, frameLeft, frameTop);
    }

    private boolean isFrameBorder(double mouseX, double mouseY) {
        Document document = getLinkedDocument();
        if (document == null || document.isDisposed()) return false;
        updateFrameTransform();
        double scale = document.getViewport().renderScale();
        double x = (mouseX - frameLeft) / scale;
        double y = (mouseY - frameTop) / scale;
        return x >= BACK_SWATCH_LEFT && x < BACK_SWATCH_LEFT + BACK_SWATCH_WIDTH
                && y >= BACK_SWATCH_TOP && y < BACK_SWATCH_TOP + BACK_SWATCH_HEIGHT;
    }

    private MouseEvent pointerEvent(String type, double mouseX, double mouseY, int button) {
        MouseEvent event = new MouseEvent(type, new Position(mouseX, mouseY), button);
        event.setTrusted(true);
        return event;
    }

    private boolean dispatchPointer(MouseEvent event) {
        Document document = getLinkedDocument();
        if (document == null || document.isDisposed()) return false;
        updateFrameTransform();
        double scale = document.getViewport().renderScale();
        Position local = document.guiToDocumentPosition(new Position(event.clientX, event.clientY));
        event.clientX = event.pageX = local.x;
        event.clientY = event.pageY = local.y;
        event.movementX /= scale;
        event.movementY /= scale;
        event.deltaX /= scale;
        event.deltaY /= scale;
        event.scrollDelta /= scale;
        // AUI 1.2.7 skips ALL coordinate adaptation at unit scale, including offsets.
        // Dispatch already-local coordinates with an identity transform, then restore
        // the surface transform. AUI still owns hit testing, bubbling and defaults.
        document.setViewportTransform(1, 1, 0, 0);
        try {
            MouseEvent.tiggerEvent(event, document);
        } finally {
            document.setViewportTransform(scale, scale, frameLeft, frameTop);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (flowchartDialog != null) {
            float scale = getLinkedDocument() == null ? 1 : getLinkedDocument().getViewport().renderScale();
            flowchartDialog.scroll(mouseX, mouseY, scrollY, hasShiftDown(), frameLeft, frameTop, scale);
            return true;
        }
        if (descriptionDialog != null) {
            descriptionDialog.scroll(scrollY);
            updateDescriptionDialogHover(mouseX, mouseY);
            return true;
        }
        chapterClicks.reset();
        if (tabAt(mouseX, mouseY) != null) return true;
        MouseEvent event = pointerEvent("wheel", mouseX, mouseY, -1);
        // Match the pinned AUI wheel step; zoom is disabled by the journal viewport.
        event.deltaY = event.scrollDelta = -scrollY * 50;
        event.cancelable = true;
        return dispatchPointer(event);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (flowchartDialog != null) {
            float scale = getLinkedDocument() == null ? 1 : getLinkedDocument().getViewport().renderScale();
            QuestFlowchartDialog.ClickResult result = flowchartDialog.mouseClicked(
                    mouseX, mouseY, button, frameLeft, frameTop, scale);
            if (result.kind() == QuestFlowchartDialog.ClickKind.CLOSE) {
                flowchartDialog = null;
            } else if (result.kind() == QuestFlowchartDialog.ClickKind.OPEN_QUEST) {
                QuestLogSnapshot.Entry entry = findQuestEntry(result.questId());
                flowchartDialog = null;
                if (entry != null) openQuest(entry);
            }
            return true;
        }
        if (descriptionDialog != null) {
            float scale = getLinkedDocument() == null ? 1 : getLinkedDocument().getViewport().renderScale();
            DescriptionDialog.ClickResult result = descriptionDialog.mouseClicked(
                    mouseX, mouseY, button, frameLeft, frameTop, scale);
            if (result == DescriptionDialog.ClickResult.CLOSE) {
                descriptionDialog = null;
            } else if (result == DescriptionDialog.ClickResult.NONE && button == 0) {
                Style style = descriptionDialog.styleAt(mouseX, mouseY, frameLeft, frameTop, scale);
                if (style != null) handleDescriptionClick(style);
            }
            if (descriptionDialog != null) descriptionDialog.setHoveredStyle(null);
            return true;
        }
        if (button == 0 && questDetail != null && detail.kind() == ScreenViewState.Kind.QUEST) {
            Document document = getLinkedDocument();
            if (document != null && !document.isDisposed()) {
                Position local = document.guiToDocumentPosition(new Position(mouseX, mouseY));
                Style style = questDetail.styleAt(local.x, local.y);
                if (style != null && style.getClickEvent() != null) {
                    handleDescriptionClick(style);
                    return true;
                }
            }
        }
        pressedFrameBack = false;
        var tab = tabAt(mouseX, mouseY);
        if (tab != null) {
            chapterClicks.reset();
            if (button == 0) pressedTab = tab;
            return true;
        }
        if (button == 0 && isFrameBorder(mouseX, mouseY)) {
            pressedFrameBack = true;
            return true;
        }
        return dispatchPointer(pointerEvent("mousedown", mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (flowchartDialog != null) {
            flowchartDialog.mouseReleased();
            return true;
        }
        if (descriptionDialog != null) return true;
        if (button == 0 && pressedFrameBack) {
            pressedFrameBack = false;
            if (isFrameBorder(mouseX, mouseY)) returnFromHistory();
            return true;
        }
        if (button == 0 && pressedTab != null) {
            var pressed = pressedTab;
            pressedTab = null;
            var tab = tabAt(mouseX, mouseY);
            if (pressed.equals(tab)) changeGroup(tab.selection());
            return true;
        }
        if (tabAt(mouseX, mouseY) != null) return true;
        chapterClickedThisRelease = false;
        boolean handled = dispatchPointer(pointerEvent("mouseup", mouseX, mouseY, button));
        if (!chapterClickedThisRelease) chapterClicks.reset();
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (flowchartDialog != null) {
            float scale = getLinkedDocument() == null ? 1 : getLinkedDocument().getViewport().renderScale();
            flowchartDialog.mouseDragged(mouseX, mouseY, button, scale);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (flowchartDialog != null) {
            previousMouse = new Position(mouseX, mouseY);
            return;
        }
        if (descriptionDialog != null) {
            Style style = descriptionDialog.styleAt(mouseX, mouseY, frameLeft, frameTop,
                    getLinkedDocument() == null ? 1 : getLinkedDocument().getViewport().renderScale());
            descriptionDialog.setHoveredStyle(style);
            previousMouse = new Position(mouseX, mouseY);
            return;
        }
        Style descriptionStyle = null;
        if (questDetail != null && detail.kind() == ScreenViewState.Kind.QUEST) {
            Document document = getLinkedDocument();
            if (document != null && !document.isDisposed()) {
                Position local = document.guiToDocumentPosition(new Position(mouseX, mouseY));
                descriptionStyle = questDetail.styleAt(local.x, local.y);
            }
        }
        if (questDetail != null) questDetail.setHoveredDescriptionStyle(descriptionStyle);
        MouseEvent event = pointerEvent("mousemove", mouseX, mouseY, -1);
        if (previousMouse != null) {
            event.movementX = mouseX - previousMouse.x;
            event.movementY = mouseY - previousMouse.y;
        }
        previousMouse = new Position(mouseX, mouseY);
        dispatchPointer(event);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateFrameTransform();
        // ApricityScreen 1.2.7 draws at (0,0). Apply our offset only to this document;
        // persistent documents, the cursor, and development overlays stay in screen space.
        FrameTimingHud.beginFrame();
        try {
            if (isShowDefaultBackground()) renderBackground(graphics, mouseX, mouseY, partialTick);
            Document document = getLinkedDocument();
            float tabScale = document == null ? 1 : document.getViewport().renderScale();
            for (var tab : tabs) if (!tab.selection().equals(effectiveGroup)) {
                ChapterGroupTabView.draw(graphics, tab, false, frameLeft, frameTop, tabScale);
            }
            graphics.flush();
            if (document != null) {
                var viewport = document.getViewport();
                // drawScreenDocument resets Mask and discards surface offsets. The embedded
                // draw path preserves this explicit local-to-GUI clipping transform.
                Mask.resetDepth(viewport.layoutWidth(), viewport.layoutHeight());
                Mask.pushSurfaceClip(graphics.pose(), viewport.layoutWidth(), viewport.layoutHeight(),
                        frameLeft, frameTop, viewport.renderScale(), viewport.renderScale());
                graphics.pose().pushPose();
                graphics.pose().translate(frameLeft, frameTop, 0);
                graphics.pose().scale(viewport.renderScale(), viewport.renderScale(), 1);
                Mask.pushScissorScale(viewport.scissorScale(), graphics.pose());
                try {
                    Base.drawEmbeddedDocument(graphics.pose(), document, document);
                } finally {
                    Mask.popScissorScale();
                    graphics.pose().popPose();
                    Mask.popSurfaceClip();
                }
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
                // Tab/chapter-list refreshes set listDirty, but they do not replace
                // the native detail header. Keep drawing the current header until
                // the detail itself is dirty; otherwise the title flashes for the
                // frame(s) between the click and the list-only rebind.
                if (document == boundDocument && document.isCurrentGeneration(boundGeneration) && !detailDirty
                        && detailHeader != null) {
                    detailHeader.draw(graphics, font, frameLeft, frameTop, viewport.renderScale(), Util.getMillis());
                }
                if (document == boundDocument && document.isCurrentGeneration(boundGeneration) && !detailDirty
                        && questDetail != null) {
                    Position localMouse = document.guiToDocumentPosition(new Position(mouseX, mouseY));
                    questDetail.setHoveredDescriptionStyle(questDetail.styleAt(localMouse.x, localMouse.y));
                    questDetail.draw(graphics, frameLeft, frameTop, viewport.renderScale());
                }
            }
            ResourcePreviewDialog.draw(graphics.pose(), document);
            Client.drawPersistentScreenDocuments(graphics, document);
            graphics.flush();
            drawFrameBack(graphics, mouseX, mouseY, tabScale);
            for (var tab : tabs) if (tab.selection().equals(effectiveGroup)) {
                ChapterGroupTabView.draw(graphics, tab, true, frameLeft, frameTop, tabScale);
            }
            if (descriptionDialog != null) {
                graphics.flush();
                descriptionDialog.draw(graphics, font, frameLeft, frameTop, tabScale, width, height);
                graphics.flush();
            }
            if (flowchartDialog != null) {
                graphics.flush();
                flowchartDialog.draw(graphics, font, frameLeft, frameTop, tabScale, width, height, mouseX, mouseY);
                graphics.flush();
            }
            var hovered = tabAt(mouseX, mouseY);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, descriptionDialog == null && flowchartDialog == null ? 500 : 700);
            try {
                if (descriptionDialog != null) {
                    Style style = descriptionDialog.styleAt(mouseX, mouseY, frameLeft, frameTop, tabScale);
                    if (style != null) graphics.renderComponentHoverEffect(font, style, mouseX, mouseY);
                    else {
                        Component controlTooltip = descriptionDialog.controlTooltipAt(
                                mouseX, mouseY, frameLeft, frameTop, tabScale);
                        if (controlTooltip != null) graphics.renderTooltip(font, controlTooltip, mouseX, mouseY);
                    }
                } else if (flowchartDialog != null) {
                    List<Component> tooltip = flowchartDialog.tooltipAt(
                            mouseX, mouseY, frameLeft, frameTop, tabScale);
                    if (!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                } else if (hovered != null) graphics.renderTooltip(font, Component.literal(hovered.title()), mouseX, mouseY);
                else {
                    drawQuestTooltip(graphics, mouseX, mouseY);
                    if (questDetail != null) {
                        Position local = document == null ? new Position(0, 0)
                                : document.guiToDocumentPosition(new Position(mouseX, mouseY));
                        Style style = questDetail.styleAt(local.x, local.y);
                        if (style != null) graphics.renderComponentHoverEffect(font, style, mouseX, mouseY);
                    }
                    drawQuestDetailTooltip(graphics, mouseX, mouseY);
                }
            } finally {
                graphics.pose().popPose();
            }
            graphics.flush();
            Cursor.drawPseudoCursor(graphics.pose());
            graphics.flush();
        } finally {
            FrameTimingHud.endFrame();
            Client.drawFrameTimingHud(graphics);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (flowchartDialog != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) flowchartDialog = null;
            else switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> flowchartDialog.move(-1, 0);
                case GLFW.GLFW_KEY_RIGHT -> flowchartDialog.move(1, 0);
                case GLFW.GLFW_KEY_UP -> flowchartDialog.move(0, -1);
                case GLFW.GLFW_KEY_DOWN -> flowchartDialog.move(0, 1);
                default -> { }
            }
            return true;
        }
        if (descriptionDialog != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                descriptionDialog = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                descriptionDialog.page(-1);
                descriptionDialog.setHoveredStyle(null);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                descriptionDialog.page(1);
                descriptionDialog.setHoveredStyle(null);
                return true;
            }
            return true;
        }
        if (Config.FLOWCHART_ENABLED.get() && detail.kind() == ScreenViewState.Kind.QUEST
                && PortholeKeyMappings.FLOWCHART.matches(keyCode, scanCode)) {
            openFlowchart();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawFrameBack(GuiGraphics graphics, double mouseX, double mouseY, float scale) {
        int left = (int) Math.floor(frameLeft + BACK_SWATCH_LEFT * scale);
        int top = (int) Math.floor(frameTop + BACK_SWATCH_TOP * scale);
        int right = (int) Math.ceil(frameLeft + (BACK_SWATCH_LEFT + BACK_SWATCH_WIDTH) * scale);
        int bottom = (int) Math.ceil(frameTop + (BACK_SWATCH_TOP + BACK_SWATCH_HEIGHT) * scale);
        int color = pressedFrameBack ? 0xff21877b
                : isFrameBorder(mouseX, mouseY) ? 0xff65d7c6 : 0xff33b5a4;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 275);
        graphics.fill(left, top, right, bottom, color);
        graphics.pose().popPose();
        graphics.flush();
    }

    @Override
    public void tick() {
        super.tick();
        detailHistory.setLimit(Config.MAX_DETAIL_HISTORY.get());
        boolean flowchartEnabled = Config.FLOWCHART_ENABLED.get();
        if (!flowchartEnabled) flowchartDialog = null;
        if (flowchartEnabled != boundFlowchartEnabled) detailDirty = true;
        captureListScroll();
        captureDetailScroll();
        refreshTabs();
        Document document = getLinkedDocument();
        if (listDirty || detailDirty || chapterDetailDirty || document != boundDocument
                || document != null && document.getRefreshGeneration() != boundGeneration) {
            try {
                bindCurrentDocument();
            } catch (RuntimeException exception) {
                PortholeQuest.LOGGER.error("[PortholeQuest R1] Document rebuild failed", exception);
                onClose();
                var player = Minecraft.getInstance().player;
                if (player != null) player.displayClientMessage(Component.translatable("message.portholequest.open_failed"), true);
            }
        }
        updateFrameTransform();
    }

    public void updateSnapshot(QuestLogSnapshot updated) {
        if (snapshot.equals(updated)) return;
        captureListScroll();
        captureDetailScroll();
        snapshot = updated;
        flowchartDialog = null;
        if (!detail.equals(detail.retainValid(updated))) descriptionExpanded = false;
        if (descriptionDialog != null) {
            QuestLogSnapshot.QuestDetail current = updated.questDetails().get(descriptionDialog.questId());
            if (current == null || current.descriptionHidden()) descriptionDialog = null;
            else {
                QuestLogSnapshot.Entry entry = findQuestEntry(descriptionDialog.questId());
                descriptionDialog = new DescriptionDialog(descriptionDialog.questId(),
                        entry == null ? "" : entry.title(), current.description(), descriptionDialog.state());
            }
        }
        pruneDetailHistory();
        detailDirty = true;
        chapterClicks.reset();
        pruneChapterState();
        requestedGroup = requestedGroup.retainValid(updated);
        setPauseGame(updated.pauseGame());
        bindCurrentDocument();
    }

    public boolean hasFileGeneration(long generation) {
        return snapshot.fileGeneration() == generation;
    }

    private void bindCurrentDocument() {
        Document document = getLinkedDocument();
        if (document == null || document.isDisposed()) throw new IllegalStateException("Journal document is unavailable");
        // Our centered host renders and routes pointer input itself. Exclude this
        // document from AUI's global pointer route so events cannot be sent twice.
        document.setManuallyRendered(true);
        previousMouse = null;
        long generation = document.getRefreshGeneration();
        boolean documentChanged = document != boundDocument || generation != boundGeneration;
        if (documentChanged) chapterClicks.reset();
        long version = ++bindingVersion;
        ScreenViewState previousDetail = detail;
        detail = detail.retainValid(snapshot);
        pruneDetailHistory();
        if (!previousDetail.equals(detail)) {
            detailScroll = 0;
            detailDirty = true;
        }
        boolean rebindDetail = detailDirty || documentChanged;
        refreshTabs();
        var visibleGroups = filter.project(snapshot);
        var listGroups = ChapterGroupTabs.select(visibleGroups, effectiveGroup);
        String groupTitle = effectiveGroup.all() ? allGroupTitle() : listGroups.getFirst().title();
        hoverTargets = QuestLogView.render(document, listGroups, groupTitle, filter, expanded, defaultExpanded,
                () -> getLinkedDocument() == document && document.isCurrentGeneration(generation) && bindingVersion == version,
                this::changeFilter, this::clickChapter, this::openQuest, detail);
        if (rebindDetail) {
            boundFlowchartEnabled = Config.FLOWCHART_ENABLED.get();
            detailHeader = DetailHeaderView.bind(document, snapshot, detail, detailOpenedAt,
                    () -> getLinkedDocument() == document && document.isCurrentGeneration(generation)
                            && detail.kind() != ScreenViewState.Kind.BLANK,
                    this::openFlowchart);
            questDetail = null;
        }
        if (detail.kind() == ScreenViewState.Kind.QUEST && rebindDetail) {
            questDetail = QuestDetailView.bind(document, snapshot, detail,
                    () -> getLinkedDocument() == document && document.isCurrentGeneration(generation),
                    this::openQuest, this::openDescription, this::setDescriptionExpanded,
                    descriptionExpanded);
        } else if (detail.kind() == ScreenViewState.Kind.CHAPTER && (rebindDetail || chapterDetailDirty)) {
            ChapterDetailView.bind(document, snapshot, filter, detail,
                    () -> getLinkedDocument() == document && document.isCurrentGeneration(generation),
                    this::openQuest);
        }
        document.commitRenderState();
        var entries = document.querySelector("#list-entries");
        entries.setScrollTop(listScroll);
        listScroll = entries.getTargetScrollTop();
        // A list-only rebuild can still make AUI recalculate the shared layout and
        // reset the independent detail scroll model. Restore the Java-owned value
        // after every commit; the detail DOM itself is only rebuilt when rebindDetail
        // is true.
        var detailContent = document.querySelector("#detail-content");
        if (detailContent != null) {
            detailContent.setScrollTop(detailScroll);
            detailScroll = detailContent.getTargetScrollTop();
        } else {
            detailScroll = 0;
        }
        listDirty = false;
        detailDirty = false;
        chapterDetailDirty = false;
        document.clearAllTextSelections();
        updateFrameTransform();
        boundDocument = document;
        boundGeneration = document.getRefreshGeneration();
        PortholeQuest.LOGGER.info("[PortholeQuest R1] Bound documentGeneration={} fileGeneration={} groups={} chapters={} entries={} viewport={}x{} renderScale={} frameOrigin={}x{} gui={}x{}",
                boundGeneration, snapshot.fileGeneration(), snapshot.groups().size(), snapshot.chapterCount(),
                snapshot.entryCount(), document.getViewport().layoutWidth(), document.getViewport().layoutHeight(),
                document.getViewport().renderScale(), frameLeft, frameTop, width, height);
        int visibleChapters = listGroups.stream().mapToInt(group -> group.chapters().size()).sum();
        int visibleEntries = listGroups.stream().flatMap(group -> group.chapters().stream())
                .mapToInt(chapter -> chapter.entries().size()).sum();
        PortholeQuest.LOGGER.info("[PortholeQuest R2] List filter={} groups={} chapters={} entries={} scroll={}",
                filter, visibleGroups.size(), visibleChapters, visibleEntries, listScroll);
        PortholeQuest.LOGGER.info("[PortholeQuest R2] Tabs candidates={} placed={} requested={} effective={}",
                visibleGroups.size() + 1, tabs.size(), requestedGroup, effectiveGroup);
        PortholeQuest.LOGGER.info("[PortholeQuest D1] Detail kind={} chapter={} entry={} quest={}",
                detail.kind(), Long.toUnsignedString(detail.chapterId(), 16),
                Long.toUnsignedString(detail.entryId(), 16), Long.toUnsignedString(detail.questId(), 16));
        var detailContentForLog = document.querySelector("#detail-content");
        PortholeQuest.LOGGER.info("[PortholeQuest D2] Chapter detail rebound={} scroll={} content={} rows={}", rebindDetail, detailScroll,
                detailContentForLog != null,
                detailContentForLog == null ? 0 : detailContentForLog.getChildElementCount());
    }

    private static String allGroupTitle() {
        return Component.translatable("ui.portholequest.filter.all").getString();
    }

    private void refreshTabs() {
        Document document = getLinkedDocument();
        if (document == null || document.isDisposed()) return;
        var next = ChapterGroupTabs.layout(filter.project(snapshot), allGroupTitle(), width, height,
                document.getViewport().renderScale());
        var selected = ChapterGroupTabs.resolve(requestedGroup, next);
        if (!tabs.equals(next)) {
            tabs = next;
            pressedTab = null;
            listDirty = true;
        }
        if (!effectiveGroup.equals(selected)) {
            effectiveGroup = selected;
            listScroll = 0;
            listDirty = true;
        }
    }

    private ChapterGroupTabs.Slot tabAt(double mouseX, double mouseY) {
        Document document = getLinkedDocument();
        if (document == null || document.isDisposed()) return null;
        updateFrameTransform();
        double scale = document.getViewport().renderScale();
        double x = (mouseX - frameLeft) / scale;
        double y = (mouseY - frameTop) / scale;
        for (var tab : tabs) if (tab.contains(x, y)) return tab;
        return null;
    }

    private void changeGroup(GroupSelection group) {
        chapterClicks.reset();
        captureDetailScroll();
        // Clicking ALL during fallback intentionally replaces the remembered preference.
        if (requestedGroup.equals(group)) return;
        requestedGroup = group;
        if (!effectiveGroup.equals(group)) listScroll = 0;
        listDirty = true;
    }

    private void changeFilter(ListFilter mode) {
        chapterClicks.reset();
        if (filter == mode) return;
        captureDetailScroll();
        filter = mode;
        pruneDetailHistory();
        chapterDetailDirty = detail.kind() == ScreenViewState.Kind.CHAPTER;
        listScroll = 0;
        listDirty = true;
    }

    private void toggleChapter(long chapterId) {
        captureListScroll();
        captureDetailScroll();
        expanded.put(chapterId, !expanded.getOrDefault(chapterId, defaultExpanded));
        // Rebuild next tick, after AUI has finished the click event and its followups.
        listDirty = true;
    }

    private void clickChapter(long chapterId) {
        chapterClickedThisRelease = true;
        if (chapterClicks.click(chapterId, Util.getMillis())) {
            selectDetail(ScreenViewState.chapter(chapterId));
        } else {
            toggleChapter(chapterId);
        }
    }

    private void openQuest(QuestLogSnapshot.Entry entry) {
        chapterClicks.reset();
        selectDetail(ScreenViewState.quest(entry));
    }

    private void openFlowchart() {
        if (!Config.FLOWCHART_ENABLED.get()) return;
        QuestFlowchartLayout.Graph graph;
        if (detail.kind() == ScreenViewState.Kind.CHAPTER) {
            graph = QuestFlowchartLayout.forChapter(snapshot, detail.chapterId());
        } else if (detail.kind() == ScreenViewState.Kind.QUEST) {
            graph = QuestFlowchartLayout.forQuest(snapshot, detail.chapterId(), detail.questId());
        } else return;
        var chapterQuestIds = new HashSet<Long>();
        for (var group : snapshot.groups()) for (var chapter : group.chapters()) {
            if (chapter.id() == detail.chapterId()) {
                chapter.entries().forEach(entry -> chapterQuestIds.add(entry.questId()));
            }
        }
        flowchartDialog = new QuestFlowchartDialog(graph, chapterQuestIds);
        QuestFlowchartDebug.capture(snapshot, detail.chapterId(), graph);
    }

    private void openDescription(long questId) {
        QuestLogSnapshot.QuestDetail quest = snapshot.questDetails().get(questId);
        if (quest != null && !quest.descriptionHidden()) {
            QuestLogSnapshot.Entry entry = findQuestEntry(questId);
            descriptionDialog = new DescriptionDialog(questId, entry == null ? "" : entry.title(), quest.description());
        }
    }

    private void updateDescriptionDialogHover(double mouseX, double mouseY) {
        if (descriptionDialog == null) return;
        Document document = getLinkedDocument();
        float scale = document == null ? 1 : document.getViewport().renderScale();
        descriptionDialog.setHoveredStyle(descriptionDialog.styleAt(mouseX, mouseY, frameLeft, frameTop, scale));
    }

    private void setDescriptionExpanded(long questId, boolean value) {
        if (detail.kind() != ScreenViewState.Kind.QUEST || detail.questId() != questId || questDetail == null) return;
        descriptionExpanded = value;
        questDetail.setDescriptionExpanded(value);
        Document document = getLinkedDocument();
        if (document == null || document.isDisposed()) return;
        captureDetailScroll();
        document.commitRenderState();
        var detailContent = document.querySelector("#detail-content");
        if (detailContent != null) {
            detailContent.setScrollTop(detailScroll);
            detailScroll = detailContent.getTargetScrollTop();
        }
    }

    private void handleDescriptionClick(Style style) {
        ClickEvent click = style.getClickEvent();
        if (click != null && click.getAction() == ClickEvent.Action.CHANGE_PAGE) {
            String[] parts = click.getValue().split("/");
            try {
                long targetId = Long.parseUnsignedLong(parts[0], 16);
                if (descriptionDialog != null && targetId == descriptionDialog.questId()) {
                    if (parts.length > 1) descriptionDialog.pageTo(Integer.parseInt(parts[1]) - 1);
                    return;
                }
                if (descriptionDialog == null && detail.kind() == ScreenViewState.Kind.QUEST
                        && targetId == detail.questId() && parts.length > 1) {
                    openDescription(targetId);
                    if (descriptionDialog != null) descriptionDialog.pageTo(Integer.parseInt(parts[1]) - 1);
                    return;
                }
                QuestLogSnapshot.Entry target = findQuestEntry(targetId);
                if (target != null && !ScreenViewState.quest(target).equals(detail)) {
                    if (descriptionDialog != null) {
                        pendingDescriptionReturn = descriptionDialog.state();
                        descriptionDialog = null;
                    }
                    openQuest(target);
                    if (parts.length > 1) {
                        QuestLogSnapshot.QuestDetail targetDetail = snapshot.questDetails().get(targetId);
                        if (targetDetail != null && !targetDetail.descriptionHidden()) {
                            openDescription(targetId);
                            if (descriptionDialog != null) {
                                descriptionDialog.pageTo(Integer.parseInt(parts[1]) - 1);
                            }
                        }
                    }
                    pendingDescriptionReturn = null;
                }
            } catch (NumberFormatException ignored) {
                PortholeQuest.LOGGER.warn("Ignoring invalid FTBQ description link: {}", click.getValue());
            }
            return;
        }
        handleComponentClicked(style);
    }

    private QuestLogSnapshot.Entry findQuestEntry(long questId) {
        QuestLogSnapshot.Entry linkedFallback = null;
        for (var group : snapshot.groups()) for (var chapter : group.chapters()) {
            for (var entry : chapter.entries()) {
                if (entry.questId() != questId) continue;
                if (!entry.link()) return entry;
                if (linkedFallback == null) linkedFallback = entry;
            }
        }
        return linkedFallback;
    }

    private void selectDetail(ScreenViewState requested) {
        ScreenViewState next = requested.retainValid(snapshot);
        // Native FTBQ blocks clicks on quests whose details are hidden until startable.
        if (!requested.equals(next) || next.equals(detail)) return;
        captureListScroll();
        captureDetailScroll();
        detailHistory.push(detail, detailScroll, descriptionExpanded, pendingDescriptionReturn);
        pendingDescriptionReturn = null;
        detail = next;
        detailScroll = 0;
        descriptionExpanded = false;
        detailDirty = true;
        detailOpenedAt = Util.getMillis();
        listDirty = true;
    }

    private void pruneDetailHistory() {
        detailHistory.retainValid(state -> state.equals(state.retainValid(snapshot)));
    }

    private void returnFromHistory() {
        chapterClicks.reset();
        captureListScroll();
        captureDetailScroll();
        ScreenStateHistory.Frame previous = detailHistory.popValid(
                state -> state.equals(state.retainValid(snapshot)));
        ScreenViewState next = previous == null ? ScreenViewState.BLANK : previous.state();
        double nextScroll = previous == null ? 0 : previous.detailScroll();
        boolean nextDescriptionExpanded = previous != null && previous.descriptionExpanded();
        Optional<DescriptionDialog.State> descriptionReturn = previous == null
                ? Optional.empty() : previous.descriptionReturn();
        if (descriptionReturn.isPresent() && next.kind() == ScreenViewState.Kind.QUEST
                && descriptionReturn.get().questId() == next.questId()) {
            DescriptionDialog.State state = descriptionReturn.get();
            QuestLogSnapshot.QuestDetail source = snapshot.questDetails().get(state.questId());
            if (source != null && !source.descriptionHidden()) {
                QuestLogSnapshot.Entry entry = findQuestEntry(state.questId());
                descriptionDialog = new DescriptionDialog(state.questId(), entry == null ? "" : entry.title(),
                        source.description(), state);
            }
        }
        if (detail.equals(next) && Double.compare(detailScroll, nextScroll) == 0
                && descriptionExpanded == nextDescriptionExpanded) return;
        detail = next;
        detailScroll = nextScroll;
        descriptionExpanded = nextDescriptionExpanded;
        detailDirty = true;
        detailOpenedAt = Util.getMillis();
        listDirty = true;
    }

    private void drawQuestTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Document document = getLinkedDocument();
        if (listDirty || document == null || document != boundDocument || !document.isCurrentGeneration(boundGeneration)) return;
        Position local = document.guiToDocumentPosition(new Position(mouseX, mouseY));
        var entry = QuestLogView.tooltipAt(document, hoverTargets, local.x, local.y);
        if (entry == null) return;
        var header = snapshot.questHeaders().get(entry.questId());
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(entry.title()));
        if (header != null && !header.subtitle().isBlank()) {
            lines.add(Component.literal(header.subtitle()).withStyle(ChatFormatting.GRAY));
        }
        if (header != null && header.showProgress()) {
            lines.add(Component.translatable("ui.portholequest.progress", entry.progressPercent()).withStyle(ChatFormatting.GRAY));
        }
        if (entry.unclaimedRewards()) {
            lines.add(Component.translatable("ui.portholequest.unclaimed_rewards").withStyle(ChatFormatting.YELLOW));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void drawQuestDetailTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Document document = getLinkedDocument();
        if (listDirty || detailDirty || questDetail == null || document == null || document != boundDocument
                || !document.isCurrentGeneration(boundGeneration)) return;
        Position local = document.guiToDocumentPosition(new Position(mouseX, mouseY));
        var lines = questDetail.tooltipAt(local.x, local.y);
        if (!lines.isEmpty()) graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void pruneChapterState() {
        var valid = new HashSet<Long>();
        for (var group : snapshot.groups()) for (var chapter : group.chapters()) valid.add(chapter.id());
        expanded.keySet().retainAll(valid);
    }

    private void captureListScroll() {
        Document document = getLinkedDocument();
        // A refreshed DOM has lost its old scroll; preserve the Java copy instead.
        if (listDirty || document == null || document != boundDocument || document.isDisposed()
                || document.getRefreshGeneration() != boundGeneration) return;
        var entries = document.querySelector("#list-entries");
        if (entries != null) listScroll = entries.getTargetScrollTop();
    }

    private void captureDetailScroll() {
        Document document = getLinkedDocument();
        if (listDirty || document == null || document != boundDocument || document.isDisposed()
                || document.getRefreshGeneration() != boundGeneration) return;
        var content = document.querySelector("#detail-content");
        if (content != null) detailScroll = content.getTargetScrollTop();
    }

    public ListState captureListState() {
        captureListScroll();
        captureDetailScroll();
        ScreenViewState validDetail = detail.retainValid(snapshot);
        return new ListState(filter, defaultExpanded, expanded, listScroll, requestedGroup,
                validDetail.toSession(), detailScroll,
                validDetail.kind() == ScreenViewState.Kind.QUEST && descriptionExpanded);
    }

    @Override
    public void removed() {
        saveState.accept(captureListState());
        bindingVersion++;
        boundDocument = null;
        boundGeneration = -1;
        previousMouse = null;
        pressedTab = null;
        pressedFrameBack = false;
        tabs = List.of();
        hoverTargets = List.of();
        detailHeader = null;
        questDetail = null;
        detailHistory.clear();
        pendingDescriptionReturn = null;
        descriptionDialog = null;
        flowchartDialog = null;
        chapterClicks.reset();
        super.removed();
    }

}
