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
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PortholeQuestScreen extends ApricityScreen {
    private QuestLogSnapshot snapshot;
    private Document boundDocument;
    private long boundGeneration = -1;
    private int frameLeft;
    private int frameTop;
    private Position previousMouse;

    public PortholeQuestScreen(QuestLogSnapshot snapshot) {
        super("screens/quest_log.html");
        this.snapshot = snapshot;
        setPauseGame(snapshot.pauseGame());
    }

    @Override
    protected void init() {
        super.init();
        bindCurrentDocument();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
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
        MouseEvent event = pointerEvent("wheel", mouseX, mouseY, -1);
        // Match the pinned AUI wheel step; zoom is disabled by the journal viewport.
        event.deltaY = event.scrollDelta = -scrollY * 50;
        event.cancelable = true;
        return dispatchPointer(event);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return dispatchPointer(pointerEvent("mousedown", mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return dispatchPointer(pointerEvent("mouseup", mouseX, mouseY, button));
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
            }
            ResourcePreviewDialog.draw(graphics.pose(), document);
            Client.drawPersistentScreenDocuments(graphics, document);
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
        Document document = getLinkedDocument();
        if (document != boundDocument || document != null && document.getRefreshGeneration() != boundGeneration) {
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
        snapshot = updated;
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
        QuestLogView.render(document, snapshot);
        document.clearAllTextSelections();
        updateFrameTransform();
        boundDocument = document;
        boundGeneration = document.getRefreshGeneration();
        PortholeQuest.LOGGER.info("[PortholeQuest R1] Bound documentGeneration={} fileGeneration={} groups={} chapters={} entries={} viewport={}x{} renderScale={} frameOrigin={}x{} gui={}x{}",
                boundGeneration, snapshot.fileGeneration(), snapshot.groups().size(), snapshot.chapterCount(),
                snapshot.entryCount(), document.getViewport().layoutWidth(), document.getViewport().layoutHeight(),
                document.getViewport().renderScale(), frameLeft, frameTop, width, height);
    }

    @Override
    public void removed() {
        boundDocument = null;
        boundGeneration = -1;
        previousMouse = null;
        super.removed();
    }
}
