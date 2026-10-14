package ink.myumoon.portholequest.client.sync;

import ink.myumoon.portholequest.PortholeQuest;
import ink.myumoon.portholequest.client.input.PortholeKeyMappings;
import ink.myumoon.portholequest.client.screen.PortholeQuestScreen;
import ink.myumoon.portholequest.event.QuestJournalOpenEvent;
import ink.myumoon.portholequest.ftbq.FtbqBridge;
import ink.myumoon.portholequest.model.QuestLogReadResult;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Coordinates entry and a small R1 refresh loop. UI never receives live FTBQ objects. */
public final class QuestLogController {
    private final FtbqBridge bridge;
    private int refreshTicks;

    public QuestLogController(FtbqBridge bridge) {
        this.bridge = bridge;
    }

    public void onJournalUse(QuestJournalOpenEvent event) {
        if (event.player() == Minecraft.getInstance().player) open("item");
    }

    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (PortholeKeyMappings.OPEN.consumeClick()) open("key");
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            bridge.reset();
            refreshTicks = 0;
            if (minecraft.screen instanceof PortholeQuestScreen) minecraft.setScreen(null);
            return;
        }
        if (!(minecraft.screen instanceof PortholeQuestScreen screen)) {
            refreshTicks = 0;
            return;
        }
        // Full action/sync coordination belongs to R4; R1 merely retires stale read-only views.
        if (++refreshTicks < 20) return;
        refreshTicks = 0;
        PortholeQuestScreen activeScreen = screen;
        try {
            QuestLogReadResult result = bridge.capture();
            if (result.snapshot().isEmpty()) {
                minecraft.setScreen(null);
                unavailable(result.state(), "refresh");
                return;
            }
            var snapshot = result.snapshot().orElseThrow();
            if (!screen.hasFileGeneration(snapshot.fileGeneration())) {
                activeScreen = new PortholeQuestScreen(snapshot);
                minecraft.setScreen(activeScreen);
                PortholeQuest.LOGGER.info("[PortholeQuest R1] Rebuilt Screen fileGeneration={}", snapshot.fileGeneration());
            } else {
                screen.updateSnapshot(snapshot);
            }
        } catch (RuntimeException exception) {
            fail(activeScreen, exception);
        }
    }

    private void open(String source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        PortholeQuestScreen screen = null;
        try {
            QuestLogReadResult result = bridge.capture();
            if (result.snapshot().isEmpty()) {
                unavailable(result.state(), source);
                return;
            }
            var snapshot = result.snapshot().orElseThrow();
            screen = new PortholeQuestScreen(snapshot);
            minecraft.setScreen(screen);
            refreshTicks = 0;
            PortholeQuest.LOGGER.info("[PortholeQuest R1] Open source={} fileGeneration={} team={} groups={} chapters={} entries={} pauseGame={}",
                    source, snapshot.fileGeneration(), snapshot.teamId(), snapshot.groups().size(),
                    snapshot.chapterCount(), snapshot.entryCount(), snapshot.pauseGame());
        } catch (RuntimeException exception) {
            fail(screen, exception);
        }
    }

    private static void unavailable(QuestLogReadResult.State state, String source) {
        PortholeQuest.LOGGER.info("[PortholeQuest R1] Unavailable source={} state={}", source, state);
        if (state == QuestLogReadResult.State.NO_WORLD) return;
        String key = switch (state) {
            case MISSING_QUESTS -> "message.portholequest.missing_quests";
            case LOADING_TEAM -> "message.portholequest.loading_team";
            case LOCKED -> "message.portholequest.locked";
            default -> throw new IllegalArgumentException("Unexpected unavailable state: " + state);
        };
        feedback(key);
    }

    private static void fail(PortholeQuestScreen screen, RuntimeException exception) {
        PortholeQuest.LOGGER.error("[PortholeQuest R1] Journal read/open failed", exception);
        Minecraft minecraft = Minecraft.getInstance();
        if (screen != null && minecraft.screen == screen) minecraft.setScreen(null);
        feedback("message.portholequest.open_failed");
    }

    private static void feedback(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable(key), true);
    }

    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        bridge.reset();
        refreshTicks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PortholeQuestScreen) minecraft.setScreen(null);
    }
}
