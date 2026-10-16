package ink.myumoon.portholequest;

import ink.myumoon.portholequest.client.probe.ApricityProbe;
import ink.myumoon.portholequest.client.input.PortholeKeyMappings;
import ink.myumoon.portholequest.client.sync.QuestLogController;
import ink.myumoon.portholequest.ftbq.FtbqBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = PortholeQuest.MODID, dist = Dist.CLIENT)
public final class PortholeQuestClient {
    public PortholeQuestClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modEventBus.addListener(PortholeKeyMappings::register);
        var controller = new QuestLogController(new FtbqBridge());
        NeoForge.EVENT_BUS.addListener(controller::onClientTick);
        NeoForge.EVENT_BUS.addListener(controller::onJournalUse);
        NeoForge.EVENT_BUS.addListener(controller::onLogout);
        if (!FMLEnvironment.production) {
            NeoForge.EVENT_BUS.addListener(ApricityProbe::registerClientCommands);
        }
    }
}
