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
import ink.myumoon.portholequest.client.input.ChapterClickSequence;
import ink.myumoon.portholequest.model.ChapterGroupTabs;
import ink.myumoon.portholequest.model.GroupSelection;
import ink.myumoon.portholequest.client.session.GuiSessionCache.ListState;
import ink.myumoon.portholequest.model.ListFilter;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

public final class PortholeQuestScreen extends ApricityScreen {
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
    private DetailHeaderView detailHeader;
    private QuestDetailView questDetail;
    private List<QuestLogView.HoverTarget> hoverTargets = List.of();
    private final ChapterClickSequence chapterClicks = new ChapterClickSequence();
    private boolean chapterClickedThisRelease;
    private long detailOpenedAt;

    public PortholeQuestScreen(QuestLogSnapshot snapshot, ListState state, Consumer<ListState> saveState) {
        super("screens/quest_log.html");
        this.snapshot = snapshot;
        this.filter = state.filter();
        this.defaultExpanded = state.defaultExpanded();
        this.expanded = new HashMap<>(state.expanded());
        this.listScroll = state.scrollTop();
        this.detailScroll = state.detailScroll();
        this.detail = ScreenViewState.fromSession(state.detail()).retainValid(snapshot, filter);
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
        var tab = tabAt(mouseX, mouseY);
        if (tab != null) {
            chapterClicks.reset();
            if (button == 0) pressedTab = tab;
            return true;
        }
        return dispatchPointer(pointerEvent("mousedown", mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
    public void mouseMoved(double mouseX, double mouseY) {
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
                    questDetail.draw(graphics, frameLeft, frameTop, viewport.renderScale());
                }
            }
            for (var tab : tabs) if (tab.selection().equals(effectiveGroup)) {
                ChapterGroupTabView.draw(graphics, tab, true, frameLeft, frameTop, tabScale);
            }
            graphics.flush();
            ResourcePreviewDialog.draw(graphics.pose(), document);
            Client.drawPersistentScreenDocuments(graphics, document);
            graphics.flush();
            var hovered = tabAt(mouseX, mouseY);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500);
            try {
                if (hovered != null) graphics.renderTooltip(font, Component.literal(hovered.title()), mouseX, mouseY);
                else {
                    drawQuestTooltip(graphics, mouseX, mouseY);
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
    public void tick() {
        super.tick();
        captureListScroll();
        captureDetailScroll();
        refreshTabs();
        Document document = getLinkedDocument();
        if (listDirty || document != boundDocument || document != null && document.getRefreshGeneration() != boundGeneration) {
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
        detail = detail.retainValid(snapshot, filter);
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
            detailHeader = DetailHeaderView.bind(document, snapshot, detail, detailOpenedAt);
            questDetail = null;
        }
        if (detail.kind() == ScreenViewState.Kind.QUEST && rebindDetail) {
            questDetail = QuestDetailView.bind(document, snapshot, detail,
                    () -> getLinkedDocument() == document && document.isCurrentGeneration(generation) && bindingVersion == version,
                    this::openQuest);
        } else if (detail.kind() == ScreenViewState.Kind.CHAPTER && (rebindDetail || chapterDetailDirty)) {
            ChapterDetailView.bind(document, snapshot, filter, detail,
                    () -> getLinkedDocument() == document && document.isCurrentGeneration(generation) && bindingVersion == version,
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

    private void selectDetail(ScreenViewState requested) {
        ScreenViewState next = requested.retainValid(snapshot, filter);
        // Native FTBQ blocks clicks on quests whose details are hidden until startable.
        if (!requested.equals(next) || next.equals(detail)) return;
        captureListScroll();
        captureDetailScroll();
        detail = next;
        detailScroll = 0;
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
        ScreenViewState validDetail = detail.retainValid(snapshot, filter);
        return new ListState(filter, defaultExpanded, expanded, listScroll, requestedGroup,
                validDetail.toSession(), detailScroll);
    }

    @Override
    public void removed() {
        saveState.accept(captureListState());
        bindingVersion++;
        boundDocument = null;
        boundGeneration = -1;
        previousMouse = null;
        pressedTab = null;
        tabs = List.of();
        hoverTargets = List.of();
        detailHeader = null;
        questDetail = null;
        chapterClicks.reset();
        super.removed();
    }
}
