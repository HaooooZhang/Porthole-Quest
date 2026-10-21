package ink.myumoon.portholequest.client.ui;

import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

final class DescriptionTextLayout {
    private DescriptionTextLayout() {
    }

    static Component compose(List<QuestLogSnapshot.DescriptionPart> parts) {
        MutableComponent result = Component.empty();
        var registryAccess = Minecraft.getInstance().level.registryAccess();
        for (var part : parts) {
            if (part.kind() != QuestLogSnapshot.DescriptionPart.Kind.TEXT) continue;
            Component component = Component.Serializer.fromJson(part.componentJson(), registryAccess);
            if (component != null && !component.getString().isEmpty()) result.append(component);
        }
        return result;
    }

    static List<FormattedCharSequence> wrap(Component component, Font font, int width) {
        if (component.getString().isEmpty()) return List.of();
        return font.split(component, width);
    }

    static Style styleAtWidth(FormattedCharSequence line, double targetWidth, Font font) {
        Style[] result = {null};
        double[] width = {0};
        line.accept((index, style, codePoint) -> {
            String glyph = new String(Character.toChars(codePoint));
            double next = width[0] + font.width(Component.literal(glyph).withStyle(style));
            if (targetWidth < next) {
                result[0] = style;
                return false;
            }
            width[0] = next;
            return true;
        });
        return result[0];
    }

    static FormattedCharSequence highlightLink(FormattedCharSequence line, ClickEvent target) {
        if (target == null) return line;
        return sink -> line.accept((index, style, codePoint) -> {
            ClickEvent click = style.getClickEvent();
            Style rendered = target.equals(click)
                    ? style.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                    : style;
            return sink.accept(index, rendered, codePoint);
        });
    }
}
