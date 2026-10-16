package ink.myumoon.portholequest.client.ui;

import com.mojang.math.Axis;
import ink.myumoon.portholequest.model.ChapterGroupTabs.Slot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Native decoration outside the AUI body clip; every tab uses the same slot geometry. */
public final class ChapterGroupTabView {
    // AUI 1.2.7's manually rendered body starts at Z=1, with local overlays up to 250.25.
    // Keep selected tabs above that body and below Minecraft's tooltip plane at Z=400.
    private static final int SELECTED_Z = 300;

    private ChapterGroupTabView() {}

    public static void draw(GuiGraphics graphics, Slot slot, boolean selected, int left, int top, float scale) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, selected ? SELECTED_Z : 0);
        pose.scale(scale, scale, 1);
        pose.translate(slot.x(), slot.y(), 0);
        switch (slot.edge()) {
            case LEFT -> { }
            case RIGHT -> { pose.translate(32, 24, 0); pose.mulPose(Axis.ZP.rotationDegrees(180)); }
            case TOP -> { pose.translate(24, 0, 0); pose.mulPose(Axis.ZP.rotationDegrees(90)); }
            case BOTTOM -> { pose.translate(0, 32, 0); pose.mulPose(Axis.ZP.rotationDegrees(-90)); }
        }
        String name = slot.color() < 0 ? "all" : "slot" + (slot.color() + 1);
        var texture = ResourceLocation.fromNamespaceAndPath("portholequest",
                "textures/gui/tab/tab_" + name + (selected ? "_selected.png" : "_normal.png"));
        graphics.blit(texture, 0, 0, 0, 0, 32, 24, 32, 24);
        pose.popPose();
    }
}
