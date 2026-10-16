package ink.myumoon.portholequest.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Definition-only chapter ordering, computed before visibility and state filters. */
public final class ChapterEntryOrder {
    private ChapterEntryOrder() {}

    public record Definition(long entryId, long questId, List<Long> dependencyQuestIds) {
        public Definition {
            dependencyQuestIds = List.copyOf(dependencyQuestIds);
        }
    }

    /** Input order is quests followed by links, each in its FTBQ file order. */
    public static List<Definition> sort(List<Definition> entries) {
        // Multiple entries for a target share a level but retain their own identity.
        Map<Long, Set<Long>> parents = new HashMap<>();
        for (var entry : entries) parents.computeIfAbsent(entry.questId(), ignored -> new HashSet<>());
        for (var entry : entries) {
            for (long dependency : entry.dependencyQuestIds()) {
                if (parents.containsKey(dependency)) parents.get(entry.questId()).add(dependency);
            }
        }
        Map<Long, List<Long>> children = new HashMap<>();
        Map<Long, Integer> remaining = new HashMap<>();
        Map<Long, Integer> levels = new HashMap<>();
        var ready = new ArrayDeque<Long>();
        parents.forEach((quest, dependencies) -> {
            remaining.put(quest, dependencies.size());
            if (dependencies.isEmpty()) {
                levels.put(quest, 0);
                ready.add(quest);
            }
            for (long dependency : dependencies) {
                children.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(quest);
            }
        });
        Map<Long, Integer> pendingLevels = new HashMap<>();
        while (!ready.isEmpty()) {
            long quest = ready.remove();
            for (long child : children.getOrDefault(quest, List.of())) {
                pendingLevels.merge(child, levels.get(quest) + 1, Math::max);
                int count = remaining.compute(child, (ignored, previous) -> previous - 1);
                if (count == 0) {
                    levels.put(child, pendingLevels.get(child));
                    ready.add(child);
                }
            }
        }
        var sorted = new ArrayList<>(entries);
        // Stable sort preserves file order within a level. Cycles and their blocked
        // descendants remain at the end in original order; no recursive traversal.
        sorted.sort(Comparator.comparingInt(entry -> levels.getOrDefault(entry.questId(), Integer.MAX_VALUE)));
        return List.copyOf(sorted);
    }
}
