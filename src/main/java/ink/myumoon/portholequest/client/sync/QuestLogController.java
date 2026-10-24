package ink.myumoon.portholequest.client.sync;

import ink.myumoon.portholequest.PortholeQuest;
import ink.myumoon.portholequest.client.input.PortholeKeyMappings;
import ink.myumoon.portholequest.client.screen.PortholeQuestScreen;
import ink.myumoon.portholequest.client.session.GuiSessionCache;
import ink.myumoon.portholequest.client.sync.QuestAction;
import ink.myumoon.portholequest.event.QuestJournalOpenEvent;
import ink.myumoon.portholequest.ftbq.FtbqBridge;
import ink.myumoon.portholequest.model.QuestLogReadResult;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Coordinates journal entry; snapshot lifecycle belongs to QuestLogRefreshCoordinator. */
public final class QuestLogController {
    private static QuestLogController activeController;
    private final FtbqBridge bridge;
    private final QuestLogRefreshCoordinator refreshCoordinator;

    public QuestLogController(FtbqBridge bridge) {
        activeController = this;
        this.bridge = bridge;
        this.refreshCoordinator = new QuestLogRefreshCoordinator(bridge);
    }

    public static void onFtbqClientStateChanged() {
        QuestLogController controller = activeController;
        if (controller != null) controller.refreshCoordinator.onFtbqClientStateChanged();
    }

    public void onJournalUse(QuestJournalOpenEvent event) {
        if (event.player() == Minecraft.getInstance().player) open("item");
    }

    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (PortholeKeyMappings.OPEN.consumeClick()) open("key");
        refreshCoordinator.onClientTick(minecraft);
    }

    public void dispatch(PortholeQuestScreen screen, QuestAction action) {
        refreshCoordinator.dispatch(screen, action);
    }

    private void open(String source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || refreshCoordinator.isAwaitingTeamData()) return;
        PortholeQuestScreen screen = null;
        try {
            QuestLogReadResult result = bridge.capture();
            if (result.snapshot().isEmpty()) {
                refreshCoordinator.reportUnavailable(result.state(), source);
                return;
            }
            var snapshot = result.snapshot().orElseThrow();
            screen = new PortholeQuestScreen(snapshot,
                    refreshCoordinator.restoreSession(), refreshCoordinator::saveSession,
                    action -> refreshCoordinator.dispatch((PortholeQuestScreen) Minecraft.getInstance().screen, action));
            minecraft.setScreen(screen);
            PortholeQuest.LOGGER.info("[PortholeQuest R4] Open source={} fileGeneration={} team={} groups={} chapters={} entries={} pauseGame={}",
                    source, snapshot.fileGeneration(), snapshot.teamId(), snapshot.groups().size(),
                    snapshot.chapterCount(), snapshot.entryCount(), snapshot.pauseGame());
        } catch (RuntimeException exception) {
            PortholeQuest.LOGGER.error("[PortholeQuest R4] Journal open failed", exception);
            if (screen != null && minecraft.screen == screen) minecraft.setScreen(null);
            var player = minecraft.player;
            if (player != null) player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.portholequest.open_failed"), true);
        }
    }

    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        refreshCoordinator.onLogout(Minecraft.getInstance());
    }
}
