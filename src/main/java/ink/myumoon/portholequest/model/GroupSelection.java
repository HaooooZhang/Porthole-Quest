package ink.myumoon.portholequest.model;

/** ALL is independent of every FTBQ ID, including the default group ID zero. */
public record GroupSelection(boolean all, long groupId) {
    public static final GroupSelection ALL = new GroupSelection(true, 0);

    public GroupSelection {
        if (all) groupId = 0;
    }

    public static GroupSelection group(long id) {
        return new GroupSelection(false, id);
    }

    public GroupSelection retainValid(QuestLogSnapshot snapshot) {
        return all || snapshot.groups().stream().anyMatch(group -> group.id() == groupId) ? this : ALL;
    }
}
