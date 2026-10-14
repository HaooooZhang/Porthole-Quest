package ink.myumoon.portholequest.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** Posted by the shared item on the logical client; the client entrypoint owns opening the Screen. */
public final class QuestJournalOpenEvent extends Event {
    private final Player player;

    public QuestJournalOpenEvent(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }
}
