package ink.myumoon.portholequest.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class PortholeKeyMappings {
    private static final String CATEGORY = "key.categories.portholequest";
    public static final KeyMapping OPEN = new KeyMapping("key.portholequest.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping FLOWCHART = new KeyMapping("key.portholequest.flowchart", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping TRACK = new KeyMapping("key.portholequest.track", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);

    private PortholeKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
        event.register(FLOWCHART);
        event.register(TRACK);
    }
}
