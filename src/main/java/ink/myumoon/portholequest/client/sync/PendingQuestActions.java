package ink.myumoon.portholequest.client.sync;

import ink.myumoon.portholequest.model.QuestLogSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Client-only action confirmation state; snapshots remain authoritative. */
public final class PendingQuestActions {
    public static final long TIMEOUT_NANOS = 5_000_000_000L;
    private final Map<QuestAction, Pending> pending = new HashMap<>();

    public boolean begin(QuestAction action, QuestLogSnapshot snapshot, long nowNanos) {
        if (pending.containsKey(action)) return false;
        pending.put(action, new Pending(nowNanos + TIMEOUT_NANOS, snapshot.fileGeneration(), snapshot.teamId(),
                fingerprint(action, snapshot)));
        return true;
    }

    public boolean isPending(QuestAction action) { return pending.containsKey(action); }
    public Set<QuestAction> actions() { return Set.copyOf(pending.keySet()); }

    public boolean confirmed(QuestAction action, QuestLogSnapshot snapshot) {
        Pending state = pending.get(action);
        return state != null && state.fileGeneration == snapshot.fileGeneration()
                && state.teamId.equals(snapshot.teamId())
                && !state.fingerprint.equals(fingerprint(action, snapshot));
    }

    public boolean remove(QuestAction action) { return pending.remove(action) != null; }
    public boolean isExpired(QuestAction action, long nowNanos) {
        Pending state = pending.get(action);
        return state != null && nowNanos >= state.deadlineNanos;
    }

    public void clear() { pending.clear(); }

    private static Object fingerprint(QuestAction action, QuestLogSnapshot snapshot) {
        return switch (action.type()) {
            case SUBMIT_TASK -> snapshot.questDetails().values().stream().flatMap(q -> q.tasks().stream())
                    .filter(task -> task.id() == action.targetId()).findFirst()
                    .map(task -> new TaskState(task.progress(), task.completed())).orElse(null);
            case CLAIM_REWARD -> snapshot.questDetails().values().stream().flatMap(q -> q.rewards().stream())
                    .filter(reward -> reward.id() == action.targetId()).findFirst()
                    .map(QuestLogSnapshot.RewardDetail::state).orElse(null);
            case TOGGLE_TRACKING -> snapshot.graphQuests().stream()
                    .filter(quest -> quest.questId() == action.targetId()).findFirst()
                    .map(QuestLogSnapshot.GraphQuest::pinned).orElse(null);
        };
    }

    private record Pending(long deadlineNanos, long fileGeneration, java.util.UUID teamId, Object fingerprint) {}
    private record TaskState(long progress, boolean completed) {}
}
