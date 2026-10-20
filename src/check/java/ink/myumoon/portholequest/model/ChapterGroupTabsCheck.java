package ink.myumoon.portholequest.model;

import ink.myumoon.portholequest.client.session.GuiSessionCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChapterGroupTabsCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static QuestLogSnapshot.Group group(long id, boolean active) {
        return group(id, active, (int) id);
    }

    private static QuestLogSnapshot.Group group(long id, boolean active, int fileOrder) {
        var entry = new QuestLogSnapshot.Entry(id + 100, id + 100, id + 10, "Quest", false,
                false, !active, active, 0, false, false);
        return new QuestLogSnapshot.Group(id, "Group " + id, fileOrder,
                List.of(new QuestLogSnapshot.Chapter(id + 10, id, "Chapter", List.of(entry))));
    }

    public static void main(String[] args) {
        var groups = new ArrayList<QuestLogSnapshot.Group>();
        for (long id = 0; id < 60; id++) groups.add(group(id, true));
        var empty = ChapterGroupTabs.layout(List.of(), "All", 427, 240, 1);
        check(empty.size() == 1 && empty.getFirst().selection().all(), "Empty needs ALL");
        var small = ChapterGroupTabs.layout(groups, "All", 427, 240, 1);
        check(small.size() == 16, "Small window left/right capacity");
        check(small.get(7).edge() == ChapterGroupTabs.Edge.LEFT
                && small.get(8).edge() == ChapterGroupTabs.Edge.RIGHT, "Small window skip top/bottom");
        for (int[] window : List.of(new int[]{360, 208, 1}, new int[]{320, 180, 1}, new int[]{720, 416, 2})) {
            var clipped = ChapterGroupTabs.layout(groups, "All", window[0], window[1], window[2]);
            check(clipped.size() == 16 && clipped.getFirst().selection().all(), "Clipped window retains ALL and side capacity");
            for (int i = 0; i < clipped.size(); i++) {
                check(clipped.get(i).edge() == (i < 8 ? ChapterGroupTabs.Edge.LEFT : ChapterGroupTabs.Edge.RIGHT),
                        "Clipped window retains left/right order");
                check(clipped.get(i).selection().equals(small.get(i).selection()), "Side identities stable on resize");
            }
            check(ChapterGroupTabs.resolve(GroupSelection.group(14), clipped).equals(GroupSelection.group(14)),
                    "Clipped right tab keeps selection");
            check(ChapterGroupTabs.layout(List.of(group(0, true)), "All", window[0], window[1], window[2]).size() == 2,
                    "Clipped window retains default group tab");
        }
        var large = ChapterGroupTabs.layout(groups, "All", 480, 270, 1);
        check(large.size() == 42, "Four edge capacity");
        check(large.get(8).edge() == ChapterGroupTabs.Edge.BOTTOM
                && large.get(21).edge() == ChapterGroupTabs.Edge.TOP
                && large.get(34).edge() == ChapterGroupTabs.Edge.RIGHT, "Remaining edge order");
        check(large.get(0).color() == -1 && large.get(1).color() == 0 && large.get(7).color() == 0,
                "ALL excluded from color cycle");
        for (int i = 2; i < large.size(); i++) check(large.get(i - 1).color() != large.get(i).color(), "Adjacent colors");
        check(!large.get(1).selection().all() && large.get(1).selection().groupId() == 0, "Default ID 0 distinct");
        var colorSnapshot = new QuestLogSnapshot(1, UUID.randomUUID(), false,
                List.of(group(92, false, 2), group(53, true, 3), group(77, true, 9)));
        var colorAll = ChapterGroupTabs.layout(ListFilter.ALL.project(colorSnapshot), "All", 480, 270, 1);
        for (var mode : ListFilter.values()) {
            var colorFiltered = ChapterGroupTabs.layout(mode.project(colorSnapshot), "All", 480, 270, 1);
            for (var slot : colorFiltered) {
                var original = colorAll.stream().filter(s -> s.selection().equals(slot.selection())).findFirst().orElseThrow();
                check(slot.color() == original.color(), "Colors stay fixed across filters");
            }
        }
        var colorActive = ChapterGroupTabs.layout(ListFilter.ACTIVE.project(colorSnapshot), "All", 427, 240, 1);
        check(colorActive.get(1).color() == 3 && colorActive.get(2).color() == 3,
                "File order gaps preserved; neighboring same colors allowed");
        for (var slot : large) {
            check(slot.contains(slot.x(), slot.y()), "Top left hit");
            check(slot.contains(slot.x() + slot.width() - 0.01, slot.y() + slot.height() - 0.01), "Full slot hit");
            check(!slot.contains(slot.x() + slot.width(), slot.y()), "Right edge excluded");
            int frameLeft = 72, frameTop = 31;
            check(frameLeft + slot.x() >= 0 && frameTop + slot.y() >= 0
                    && frameLeft + slot.x() + slot.width() <= 480
                    && frameTop + slot.y() + slot.height() <= 270, "Window bounds");
        }
        var snapshot = new QuestLogSnapshot(1, UUID.randomUUID(), false, List.of(group(0, true), group(1, false)));
        var preferred = GroupSelection.group(1);
        var filtered = ListFilter.ACTIVE.project(snapshot);
        var activeSlots = ChapterGroupTabs.layout(filtered, "All", 427, 240, 1);
        check(ChapterGroupTabs.resolve(preferred, activeSlots).all(), "Filtered group fallback");
        check(preferred.retainValid(snapshot).equals(preferred), "Temporary exclusion retains preference");
        var allSlots = ChapterGroupTabs.layout(ListFilter.ALL.project(snapshot), "All", 427, 240, 1);
        check(ChapterGroupTabs.resolve(preferred, allSlots).equals(preferred), "Visibility restoration");
        check(ChapterGroupTabs.resolve(GroupSelection.ALL, allSlots).all(), "Explicit ALL stays ALL");
        check(ChapterGroupTabs.select(snapshot.groups(), GroupSelection.group(0)).size() == 1, "Default group selection");
        check(ChapterGroupTabs.select(snapshot.groups(), GroupSelection.ALL).size() == 2, "ALL includes all groups");
        var removed = new QuestLogSnapshot(2, snapshot.teamId(), false, List.of(group(0, true)));
        check(preferred.retainValid(removed).all(), "Removed group pruned");
        check(ChapterGroupTabs.resolve(GroupSelection.group(59), large).all(), "Unplaced selection fallback");
        var cache = new GuiSessionCache();
        cache.save(new GuiSessionCache.ListState(ListFilter.ALL, true, Map.of(10L, false), 42, preferred));
        var restored = cache.restore(ListFilter.ACTIVE, false);
        check(restored.group().equals(preferred) && restored.scrollTop() == 42, "Selection cache");
        cache.clear();
        check(cache.restore(ListFilter.ACTIVE, true).group().all(), "New world defaults ALL");
        try { large.clear(); throw new AssertionError("Mutable layout"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("ChapterGroupTab checks PASS: identity, colors, four edges, forced sides, window bounds, fallback/restore, cache");
    }
}
