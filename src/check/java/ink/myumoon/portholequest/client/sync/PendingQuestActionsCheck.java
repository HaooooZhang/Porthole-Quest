package ink.myumoon.portholequest.client.sync;

import ink.myumoon.portholequest.model.QuestLogSnapshot;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PendingQuestActionsCheck {
    public static void main(String[] args) {
        PendingQuestActions pending = new PendingQuestActions();
        QuestAction first = new QuestAction(QuestAction.Type.SUBMIT_TASK, 11);
        QuestAction second = new QuestAction(QuestAction.Type.SUBMIT_TASK, 12);
        QuestLogSnapshot before = snapshot(3, false);
        require(pending.begin(first, before, 100), "first target starts");
        require(!pending.begin(first, before, 200), "same target is deduplicated");
        require(pending.begin(second, before, 200), "different target runs concurrently");
        require(!pending.confirmed(first, before), "unrelated snapshot is not confirmation");
        require(pending.confirmed(first, snapshot(4, false)), "target progress change confirms");
        require(!pending.isExpired(first, 100 + PendingQuestActions.TIMEOUT_NANOS - 1), "waits until deadline");
        require(pending.isExpired(first, 100 + PendingQuestActions.TIMEOUT_NANOS), "expires at deadline");
        pending.remove(first);
        require(pending.begin(first, before, 300), "expired action can be retried");
        System.out.println("Pending quest action checks PASS: target dedupe, parallel targets, authoritative change, deadline, retry");
    }

    private static QuestLogSnapshot snapshot(long progress, boolean completed) {
        var task = new QuestLogSnapshot.TaskDetail(11, "ftbquests:checkmark", "Checkmark", "Task", "{}",
                progress, 10, completed, false, false, false, false,
                QuestLogSnapshot.TaskAction.SUBMIT, true);
        var detail = new QuestLogSnapshot.QuestDetail(List.of(), false, false, false,
                List.of(task), List.of());
        return new QuestLogSnapshot(1, UUID.fromString("00000000-0000-0000-0000-000000000001"), false,
                List.of(), Map.of(), Map.of(), Map.of(21L, detail));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
