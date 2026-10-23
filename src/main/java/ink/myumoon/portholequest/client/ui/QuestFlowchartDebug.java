package ink.myumoon.portholequest.client.ui;

import com.google.gson.GsonBuilder;
import ink.myumoon.portholequest.PortholeQuest;
import ink.myumoon.portholequest.model.QuestFlowchartCase;
import ink.myumoon.portholequest.model.QuestFlowchartLayout;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.nio.file.Files;

/** Development-only replay evidence for comparing the running game with the simulator. */
public final class QuestFlowchartDebug {
    private QuestFlowchartDebug() {}

    public static void capture(QuestLogSnapshot snapshot, long chapterId, QuestFlowchartLayout.Graph graph) {
        if (FMLEnvironment.production) return;
        var path = Minecraft.getInstance().gameDirectory.toPath().resolve("debug/portholequest-flowchart.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create()
                    .toJson(QuestFlowchartCase.capture(snapshot, chapterId, graph)));
            PortholeQuest.LOGGER.info("[PortholeQuest R3.3] Flowchart revision={} nodes={} edges={} replay={}",
                    QuestFlowchartLayout.REVISION, graph.nodes().size(), graph.edges().size(), path.toAbsolutePath());
        } catch (IOException | RuntimeException exception) {
            PortholeQuest.LOGGER.warn("Could not save development flowchart replay", exception);
        }
    }
}
