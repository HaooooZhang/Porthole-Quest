package ink.myumoon.portholequest.client.sync;

public record QuestAction(Type type, long targetId) {
    public enum Type { SUBMIT_TASK, CLAIM_REWARD, TOGGLE_TRACKING }
}
