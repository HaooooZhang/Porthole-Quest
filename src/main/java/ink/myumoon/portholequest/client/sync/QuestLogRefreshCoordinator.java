package ink.myumoon.portholequest.client.sync;

import ink.myumoon.portholequest.Config;
import ink.myumoon.portholequest.PortholeQuest;
import ink.myumoon.portholequest.client.screen.PortholeQuestScreen;
import ink.myumoon.portholequest.client.session.GuiSessionCache;
import ink.myumoon.portholequest.ftbq.FtbqBridge;
import ink.myumoon.portholequest.model.QuestLogReadResult;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import ink.myumoon.portholequest.ftbq.FtbqActionDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Coordinates authoritative FTBQ snapshot refresh and Screen replacement. */
public final class QuestLogRefreshCoordinator {
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int PENDING_REFRESH_INTERVAL_TICKS = 5;

    private final FtbqBridge bridge;
    private final GuiSessionCache session = new GuiSessionCache();
    private final PendingQuestActions pendingActions = new PendingQuestActions();
    private final FtbqActionDispatcher actionDispatcher = new FtbqActionDispatcher();
    private int refreshTicks;
    private long awaitingFileGeneration = -1;
    private boolean ftbqStateDirty;

    public QuestLogRefreshCoordinator(FtbqBridge bridge) {
        this.bridge = bridge;
    }

    public boolean isAwaitingTeamData() {
        return awaitingFileGeneration >= 0;
    }

    public GuiSessionCache.ListState restoreSession() {
        return session.restore(Config.DEFAULT_LIST_FILTER.get(), Config.DEFAULT_CHAPTER_EXPANDED.get());
    }

    public void saveSession(GuiSessionCache.ListState state) {
        session.save(state);
    }

    public void reportUnavailable(QuestLogReadResult.State state, String source) {
        unavailable(state, source);
    }

    public void onClientTick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            reset(minecraft);
            return;
        }
        if (ftbqStateDirty) {
            refreshAfterFtbqStateChange(minecraft);
            return;
        }
        reconcilePending(minecraft);
        if (isAwaitingTeamData()) {
            pollForReplacementData(minecraft);
            return;
        }
        if (!(minecraft.screen instanceof PortholeQuestScreen screen)) {
            refreshTicks = 0;
            return;
        }

        int refreshInterval = pendingActions.actions().isEmpty()
                ? REFRESH_INTERVAL_TICKS : PENDING_REFRESH_INTERVAL_TICKS;
        refreshTicks = Math.min(refreshInterval, refreshTicks + 1);
        if (refreshTicks < refreshInterval || !screen.beginSnapshotRefresh()) return;
        refreshTicks = 0;
        try {
            QuestLogReadResult result = bridge.capture();
            if (result.snapshot().isEmpty()) {
                handleUnavailable(minecraft, screen, result.state());
                return;
            }
            replaceOrUpdate(minecraft, screen, result.snapshot().orElseThrow());
        } catch (RuntimeException exception) {
            fail(minecraft, screen, exception);
        } finally {
            screen.finishSnapshotRefresh();
        }
    }

    public void onFtbqClientStateChanged() {
        ftbqStateDirty = true;
    }

    private void refreshAfterFtbqStateChange(Minecraft minecraft) {
        if (!(minecraft.screen instanceof PortholeQuestScreen screen)) {
            ftbqStateDirty = false;
            return;
        }
        if (!screen.beginImmediateSnapshotRefresh()) return;
        ftbqStateDirty = false;
        refreshTicks = 0;
        try {
            QuestLogReadResult result = bridge.capture();
            if (result.snapshot().isEmpty()) {
                handleUnavailable(minecraft, screen, result.state());
                return;
            }
            replaceOrUpdate(minecraft, screen, result.snapshot().orElseThrow());
        } catch (RuntimeException exception) {
            fail(minecraft, screen, exception);
        } finally {
            screen.finishSnapshotRefresh();
        }
        reconcilePending(minecraft);
    }

    public void dispatch(PortholeQuestScreen screen, QuestAction action) {
        if (Minecraft.getInstance().screen != screen) return;
        if (!pendingActions.begin(action, screen.snapshot(), System.nanoTime())) return;
        refreshTicks = Math.min(refreshTicks, PENDING_REFRESH_INTERVAL_TICKS - 1);
        try {
            actionDispatcher.send(action);
            screen.setPendingActions(pendingActions.actions());
        } catch (RuntimeException exception) {
            pendingActions.remove(action);
            screen.setPendingActions(pendingActions.actions());
            PortholeQuest.LOGGER.error("[PortholeQuest R4] Action send failed action={}", action, exception);
            feedback("message.portholequest.action_failed");
        }
    }

    private void reconcilePending(Minecraft minecraft) {
        if (pendingActions.actions().isEmpty()) return;
        PortholeQuestScreen screen = minecraft.screen instanceof PortholeQuestScreen active ? active : null;
        long now = System.nanoTime();
        for (QuestAction action : pendingActions.actions()) {
            if (screen != null && !pendingActions.isExpired(action, now)
                    && pendingActions.confirmed(action, screen.snapshot())) {
                pendingActions.remove(action);
                screen.setPendingActions(pendingActions.actions());
                continue;
            }
            if (!pendingActions.isExpired(action, now)) continue;
            QuestLogReadResult result = null;
            boolean refreshed = false;
            boolean refreshGuardAcquired = screen == null || screen.beginImmediateSnapshotRefresh();
            if (!refreshGuardAcquired) continue;
            {
                try {
                    result = bridge.capture();
                    if (result.snapshot().isPresent()) {
                        QuestLogSnapshot snapshot = result.snapshot().orElseThrow();
                        if (screen == null) refreshed = true;
                        else if (screen.hasFileGeneration(snapshot.fileGeneration())) {
                            screen.updateSnapshot(snapshot);
                            refreshed = true;
                        }
                    }
                } catch (RuntimeException exception) {
                    PortholeQuest.LOGGER.error("[PortholeQuest R4] Action confirmation refresh failed action={}", action, exception);
                } finally {
                    if (screen != null) screen.finishSnapshotRefresh();
                }
            }
            boolean confirmed = refreshed && pendingActions.confirmed(action, result.snapshot().orElseThrow());
            pendingActions.remove(action);
            if (screen != null) screen.setPendingActions(pendingActions.actions());
            if (!confirmed) feedback(refreshed ? "message.portholequest.action_failed" : "message.portholequest.action_unconfirmed");
        }
    }

    public void onLogout(Minecraft minecraft) {
        bridge.reset();
        refreshTicks = 0;
        awaitingFileGeneration = -1;
        ftbqStateDirty = false;
        pendingActions.clear();
        if (minecraft.screen instanceof PortholeQuestScreen) minecraft.setScreen(null);
        session.clear();
    }

    private void handleUnavailable(Minecraft minecraft, PortholeQuestScreen screen,
                                   QuestLogReadResult.State state) {
        if (state == QuestLogReadResult.State.LOADING_TEAM) {
            awaitingFileGeneration = bridge.fileGeneration();
            session.save(screen.captureListState());
            minecraft.setScreen(null);
            PortholeQuest.LOGGER.info("[PortholeQuest R4] Waiting for authoritative team data fileGeneration={}",
                    awaitingFileGeneration);
            return;
        }
        minecraft.setScreen(null);
        unavailable(state, "refresh");
    }

    private void pollForReplacementData(Minecraft minecraft) {
        if (minecraft.screen != null) return;
        refreshTicks = Math.min(REFRESH_INTERVAL_TICKS, refreshTicks + 1);
        if (refreshTicks < REFRESH_INTERVAL_TICKS) return;
        refreshTicks = 0;
        try {
            QuestLogReadResult result = bridge.capture();
            if (result.snapshot().isEmpty()) {
                if (result.state() == QuestLogReadResult.State.LOADING_TEAM) return;
                awaitingFileGeneration = -1;
                if (result.state() != QuestLogReadResult.State.NO_WORLD) unavailable(result.state(), "replacement");
                return;
            }
            QuestLogSnapshot snapshot = result.snapshot().orElseThrow();
            if (snapshot.fileGeneration() < awaitingFileGeneration) return;
            awaitingFileGeneration = -1;
            var screen = new PortholeQuestScreen(snapshot, restoreSession(), session::save,
                    action -> dispatch((PortholeQuestScreen) Minecraft.getInstance().screen, action));
            minecraft.setScreen(screen);
            screen.setPendingActions(pendingActions.actions());
            reconcilePending(minecraft);
            PortholeQuest.LOGGER.info("[PortholeQuest R4] Rebuilt Screen after synchronization fileGeneration={} team={}",
                    snapshot.fileGeneration(), snapshot.teamId());
        } catch (RuntimeException exception) {
            awaitingFileGeneration = -1;
            fail(minecraft, null, exception);
        }
    }

    private void replaceOrUpdate(Minecraft minecraft, PortholeQuestScreen screen, QuestLogSnapshot snapshot) {
        if (!screen.hasFileGeneration(snapshot.fileGeneration())) {
            clearPendingActions(true);
            GuiSessionCache.ListState state = screen.captureListState();
            session.save(state);
            minecraft.setScreen(new PortholeQuestScreen(snapshot, state, session::save,
                    action -> dispatch((PortholeQuestScreen) Minecraft.getInstance().screen, action)));
            PortholeQuest.LOGGER.info("[PortholeQuest R4] Rebuilt Screen fileGeneration={} team={}",
                    snapshot.fileGeneration(), snapshot.teamId());
        } else {
            screen.updateSnapshot(snapshot);
        }
    }

    private void clearPendingActions(boolean reportUnconfirmed) {
        if (reportUnconfirmed && !pendingActions.actions().isEmpty()) feedback("message.portholequest.action_unconfirmed");
        pendingActions.clear();
    }

    private void reset(Minecraft minecraft) {
        bridge.reset();
        refreshTicks = 0;
        awaitingFileGeneration = -1;
        ftbqStateDirty = false;
        pendingActions.clear();
        if (minecraft.screen instanceof PortholeQuestScreen) minecraft.setScreen(null);
        session.clear();
    }

    private static void feedback(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable(key), true);
    }

    private static void unavailable(QuestLogReadResult.State state, String source) {
        PortholeQuest.LOGGER.info("[PortholeQuest R4] Unavailable source={} state={}", source, state);
        if (state == QuestLogReadResult.State.NO_WORLD) return;
        String key = switch (state) {
            case MISSING_QUESTS -> "message.portholequest.missing_quests";
            case LOADING_TEAM -> "message.portholequest.loading_team";
            case LOCKED -> "message.portholequest.locked";
            default -> throw new IllegalArgumentException("Unexpected unavailable state: " + state);
        };
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable(key), true);
    }

    private static void fail(Minecraft minecraft, PortholeQuestScreen screen, RuntimeException exception) {
        PortholeQuest.LOGGER.error("[PortholeQuest R4] Snapshot refresh failed", exception);
        if (screen != null && minecraft.screen == screen) minecraft.setScreen(null);
        var player = minecraft.player;
        if (player != null) player.displayClientMessage(Component.translatable("message.portholequest.open_failed"), true);
    }
}
