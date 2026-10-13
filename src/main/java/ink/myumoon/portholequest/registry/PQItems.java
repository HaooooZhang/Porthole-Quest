package ink.myumoon.portholequest.registry;

import ink.myumoon.portholequest.PortholeQuest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PQItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PortholeQuest.MODID);

    public static final DeferredItem<Item> QUEST_JOURNAL =
            ITEMS.register("quest_journal", () -> new Item((new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)))) ;
}
