package ink.myumoon.portholequest.client.ui;

import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.icon.Icon;
import net.minecraft.client.gui.GuiGraphics;

/** A view-owned decoded icon. Invalid media uses the same placeholder in every journal view. */
final class PreparedIcon {
    private Icon icon;

    private PreparedIcon(Icon icon) {
        this.icon = icon;
    }

    static PreparedIcon parse(String json) {
        try {
            return new PreparedIcon(Icon.getIcon(JsonParser.parseString(json)));
        } catch (RuntimeException exception) {
            return new PreparedIcon(null);
        }
    }

    void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        if (icon != null) {
            graphics.pose().pushPose();
            try {
                icon.draw(graphics, x, y, width, height);
                return;
            } catch (RuntimeException exception) {
                icon = null;
            } finally {
                graphics.pose().popPose();
            }
        }
        int insetX = width / 8;
        int insetY = height / 8;
        graphics.fill(x + insetX, y + insetY, x + width - insetX, y + height - insetY, 0xffa79779);
    }
}
