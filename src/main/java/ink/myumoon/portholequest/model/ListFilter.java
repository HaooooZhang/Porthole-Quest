package ink.myumoon.portholequest.model;

import java.util.ArrayList;
import java.util.List;

/** Applied only after the bridge has enforced FTBQ visibility. */
public enum ListFilter {
    ALL, COMPLETED, ACTIVE;

    public boolean includes(QuestLogSnapshot.Entry entry) {
        return switch (this) {
            case ALL -> true;
            // Preserve the formal design: executable quests also appear in this mode.
            case COMPLETED -> entry.canStart() || entry.completed();
            case ACTIVE -> !entry.completed() && entry.canStart() || entry.unclaimedRewards();
        };
    }

    public List<QuestLogSnapshot.Group> project(QuestLogSnapshot snapshot) {
        var groups = new ArrayList<QuestLogSnapshot.Group>();
        for (var group : snapshot.groups()) {
            var chapters = new ArrayList<QuestLogSnapshot.Chapter>();
            for (var chapter : group.chapters()) {
                var entries = chapter.entries().stream().filter(this::includes).toList();
                if (!entries.isEmpty()) {
                    chapters.add(new QuestLogSnapshot.Chapter(chapter.id(), chapter.groupId(), chapter.title(), entries));
                }
            }
            if (!chapters.isEmpty()) groups.add(new QuestLogSnapshot.Group(group.id(), group.title(), group.fileOrder(), chapters));
        }
        return List.copyOf(groups);
    }
}
