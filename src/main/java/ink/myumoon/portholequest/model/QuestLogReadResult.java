package ink.myumoon.portholequest.model;

import java.util.Objects;
import java.util.Optional;

public record QuestLogReadResult(State state, Optional<QuestLogSnapshot> snapshot) {
    public QuestLogReadResult {
        Objects.requireNonNull(state);
        Objects.requireNonNull(snapshot);
        if ((state == State.READY) != snapshot.isPresent()) {
            throw new IllegalArgumentException("Only a ready result may carry a snapshot");
        }
    }

    public static QuestLogReadResult unavailable(State state) {
        return new QuestLogReadResult(state, Optional.empty());
    }

    public static QuestLogReadResult ready(QuestLogSnapshot snapshot) {
        return new QuestLogReadResult(State.READY, Optional.of(snapshot));
    }

    public enum State {
        NO_WORLD, MISSING_QUESTS, LOADING_TEAM, LOCKED, READY
    }
}
