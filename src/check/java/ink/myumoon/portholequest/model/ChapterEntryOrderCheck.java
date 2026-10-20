package ink.myumoon.portholequest.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Dependency-free contract checks, run by checkChapterEntryOrder. */
public final class ChapterEntryOrderCheck {
    private static ChapterEntryOrder.Definition quest(long id, Long... parents) {
        return new ChapterEntryOrder.Definition(id, id, Arrays.asList(parents));
    }

    private static List<Long> ids(List<ChapterEntryOrder.Definition> entries) {
        return entries.stream().map(ChapterEntryOrder.Definition::entryId).toList();
    }

    private static void expect(String scenario, List<ChapterEntryOrder.Definition> input, Long... expected) {
        var actual = ids(ChapterEntryOrder.sort(input));
        if (!actual.equals(Arrays.asList(expected))) throw new AssertionError(scenario + ": " + actual);
    }

    public static void main(String[] args) {
        expect("reverse file chain", List.of(quest(3, 2L), quest(2, 1L), quest(1)), 1L, 2L, 3L);
        expect("stable branch peers", List.of(quest(4, 2L, 3L), quest(3, 1L), quest(2, 1L), quest(1)),
                1L, 3L, 2L, 4L);
        expect("longest branch, not first available topological order",
                List.of(quest(5, 2L, 4L), quest(4, 3L), quest(3, 1L), quest(2, 1L), quest(1), quest(6)),
                1L, 6L, 3L, 2L, 4L, 5L);
        expect("external dependency has no local level", List.of(quest(2, 1L), quest(1, 99L)), 1L, 2L);
        var link = new ChapterEntryOrder.Definition(100, 99, List.of());
        expect("external dependency represented by link", List.of(quest(1, 99L), link), 100L, 1L);
        var duplicateLink = new ChapterEntryOrder.Definition(101, 1, List.of());
        expect("duplicate target entries retained and dependency deduplicated",
                List.of(quest(2, 1L, 1L), quest(1), duplicateLink), 1L, 101L, 2L);
        expect("same level normal quests then links", List.of(quest(2), quest(1), link), 2L, 1L, 100L);
        expect("cycle and blocked descendant preserve remainder file order",
                List.of(quest(3, 2L), quest(2, 1L), quest(1, 2L), quest(4), quest(5, 4L)),
                4L, 5L, 3L, 2L, 1L);
        expect("self cycle", List.of(quest(1, 1L), quest(2)), 2L, 1L);
        expect("empty", List.of());

        // Hidden 1 -> 2 must still put 2 after independent 3; never re-sort a filtered set.
        var sorted = ChapterEntryOrder.sort(List.of(quest(2, 1L), quest(3), quest(1)));
        var visible = sorted.stream().filter(entry -> entry.entryId() != 1).toList();
        if (!ids(visible).equals(List.of(3L, 2L))) throw new AssertionError("Hidden predecessor lost level");
        var mutable = new ArrayList<>(List.of(quest(2, 1L), quest(1)));
        var detached = ChapterEntryOrder.sort(mutable);
        if (!ids(mutable).equals(List.of(2L, 1L))) throw new AssertionError("Source mutated");
        mutable.clear();
        if (detached.size() != 2) throw new AssertionError("Output not detached");
        try {
            detached.clear();
            throw new AssertionError("Mutable output");
        } catch (UnsupportedOperationException expected) { }
        // Long reversed chains must not recurse or overflow the stack.
        var chain = new ArrayList<ChapterEntryOrder.Definition>();
        for (long id = 10000; id > 0; id--) chain.add(id == 1 ? quest(id) : quest(id, id - 1));
        var ordered = ChapterEntryOrder.sort(chain);
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).entryId() != i + 1) throw new AssertionError("Long chain order");
        }
        System.out.println("Chapter entry order checks PASS: layers, stable peers, links, external/hidden dependencies, cycles, long chain");
    }
}
