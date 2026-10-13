package ink.myumoon.portholequest.client.probe;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class ApricityProbe {
    private ApricityProbe() {}

    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("pq_probe")
                .executes(context -> open(context.getSource(), "screens/probe.html"))
                .then(Commands.literal("browser")
                        .executes(context -> open(context.getSource(), "screens/probe.html")))
                .then(Commands.literal("gui")
                        .executes(context -> open(context.getSource(), "screens/probe_gui.html")))
                .then(Commands.literal("fixed")
                        .executes(context -> open(context.getSource(), "screens/probe_fixed.html")))
                .then(Commands.literal("font")
                        .executes(context -> open(context.getSource(), "screens/probe_font.html"))));
    }

    private static int open(CommandSourceStack source, String path) {
        Minecraft.getInstance().setScreen(new ApricityProbeScreen(path));
        source.sendSuccess(() -> Component.literal("Opened ApricityUI probe: " + path), false);
        return 1;
    }
}
