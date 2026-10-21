package ink.myumoon.portholequest;

import com.mojang.logging.LogUtils;
import ink.myumoon.portholequest.registry.PQItems;
import ink.myumoon.portholequest.registry.PQRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(PortholeQuest.MODID)
public final class PortholeQuest {
    public static final String MODID = "portholequest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PortholeQuest(IEventBus modEventBus, ModContainer modContainer) {
        PQRegistries.register(modEventBus);

        modEventBus.addListener(this::buildCreativeTabContent);


        LOGGER.info("PortholeQuest initialized");
    }

    public void buildCreativeTabContent(BuildCreativeModeTabContentsEvent event){
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PQItems.QUEST_JOURNAL.get());
        }
    }
}
