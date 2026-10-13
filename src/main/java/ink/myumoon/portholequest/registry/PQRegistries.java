package ink.myumoon.portholequest.registry;

import net.neoforged.bus.api.IEventBus;

public class PQRegistries {

    public static void register(IEventBus modEventBus) {
        PQItems.ITEMS.register(modEventBus);
    }
}
