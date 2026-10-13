package ink.myumoon.portholequest;

import ink.myumoon.portholequest.client.probe.ApricityProbe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = PortholeQuest.MODID, dist = Dist.CLIENT)
public final class PortholeQuestClient {
    public PortholeQuestClient() {
        if (!FMLEnvironment.production) {
            NeoForge.EVENT_BUS.addListener(ApricityProbe::registerClientCommands);
        }
    }
}
