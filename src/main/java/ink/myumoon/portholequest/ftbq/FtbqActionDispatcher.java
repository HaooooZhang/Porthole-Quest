package ink.myumoon.portholequest.ftbq;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbquests.net.ClaimRewardMessage;
import dev.ftb.mods.ftbquests.net.SubmitTaskMessage;
import dev.ftb.mods.ftbquests.net.TogglePinnedMessage;
import ink.myumoon.portholequest.client.sync.QuestAction;

/** Sends player intent through FTBQ's own client messages. */
public final class FtbqActionDispatcher {
    public void send(QuestAction action) {
        switch (action.type()) {
            case SUBMIT_TASK -> NetworkManager.sendToServer(new SubmitTaskMessage(action.targetId()));
            case CLAIM_REWARD -> NetworkManager.sendToServer(new ClaimRewardMessage(action.targetId(), true));
            case TOGGLE_TRACKING -> NetworkManager.sendToServer(new TogglePinnedMessage(action.targetId()));
        }
    }
}
