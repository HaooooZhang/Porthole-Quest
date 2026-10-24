package ink.myumoon.portholequest.client.mixin;

import dev.ftb.mods.ftbquests.client.FTBQuestsNetClient;
import ink.myumoon.portholequest.client.sync.QuestLogController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = FTBQuestsNetClient.class, remap = false)
public abstract class FtbqNetClientMixin {
    @Inject(method = "togglePinned", at = @At("TAIL"), remap = false)
    private static void portholequest$afterTrackingUpdate(long questId, boolean pinned, CallbackInfo callback) {
        QuestLogController.onFtbqClientStateChanged();
    }

    @Inject(method = "claimReward", at = @At("TAIL"), remap = false)
    private static void portholequest$afterRewardUpdate(UUID teamId, UUID playerId, long rewardId,
                                                        CallbackInfo callback) {
        QuestLogController.onFtbqClientStateChanged();
    }

    @Inject(method = "updateTaskProgress", at = @At("TAIL"), remap = false)
    private static void portholequest$afterTaskProgressUpdate(UUID teamId, long taskId, long progress,
                                                              CallbackInfo callback) {
        QuestLogController.onFtbqClientStateChanged();
    }
}
