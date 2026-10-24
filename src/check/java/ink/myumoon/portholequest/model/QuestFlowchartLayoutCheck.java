package ink.myumoon.portholequest.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestFlowchartLayoutCheck {
    public static void main(String[] args) {
        checkQuestStates();
        var chapter = new QuestLogSnapshot.Chapter(100, 10, "Chapter", List.of(
                entry(1, 100), linkedEntry(1, 100), entry(2, 100), entry(3, 100), entry(4, 100)));
        var group = new QuestLogSnapshot.Group(10, "Group", 0, List.of(chapter));
        var snapshot = new QuestLogSnapshot(1, UUID.randomUUID(), false, List.of(group),
                Map.of(), Map.of(), Map.of(), "Book", "{}", List.of(
                quest(1, 100, List.of(10L), List.of(), 1),
                quest(2, 100, List.of(1L), List.of(), 2),
                quest(3, 100, List.of(), List.of(20L), 3),
                quest(4, 100, List.of(), List.of(), 4),
                quest(10, 200, List.of(30L), List.of(1L), 5),
                quest(20, 300, List.of(3L), List.of(40L), 6),
                quest(30, 400, List.of(), List.of(10L), 7),
                quest(40, 500, List.of(20L), List.of(), 8)));

        var chapterGraph = QuestFlowchartLayout.forChapter(snapshot, 100);
        require(ids(chapterGraph).equals(List.of(1L, 2L, 3L, 4L, 10L, 20L)),
                "Chapter graph contains its quests and one-hop adjacent quests only: " + ids(chapterGraph));
        require(chapterGraph.nodes().stream().filter(node -> node.quest().questId() == 1).count() == 1,
                "Quest IDs are deduplicated across linked entries");
        require(chapterGraph.edges().stream().anyMatch(edge -> edge.from().quest().questId() == 10
                        && edge.to().quest().questId() == 1),
                "Dependencies point from prerequisite to dependant");
        require(chapterGraph.node(1).right() < chapterGraph.node(2).x()
                        && chapterGraph.node(2).x() - chapterGraph.node(1).right() >= 36,
                "Columns reserve clear space for dependency routing");
        require(chapterGraph.node(10).x() < chapterGraph.node(1).x()
                        && chapterGraph.node(1).x() < chapterGraph.node(2).x(),
                "Acyclic dependencies lay out from left to right");
        require(chapterGraph.anchorQuestId() == 3L,
                "Chapter entry focuses the leftmost prerequisite root");
        require(chapterGraph.verticalAnchorQuestId() == null
                        && chapterGraph.initialScrollY() + QuestFlowchartLayout.VIEWPORT_HEIGHT / 2d
                        == chapterGraph.height() / 2d,
                "Chapter entry keeps the main trunk centered vertically");
        for (long questId : List.of(10L, 1L, 2L)) {
            require(chapterGraph.node(questId).y() + QuestFlowchartLayout.NODE_HEIGHT / 2 == chapterGraph.height() / 2,
                    "Longest dependency path stays on the vertical canvas centerline");
        }
        require(chapterGraph.node(3).y() < chapterGraph.node(10).y()
                        && chapterGraph.node(4).y() > chapterGraph.node(10).y(),
                "Sibling branches occupy separate sides of the centered trunk");
        require(chapterGraph.node(3).row() < 0 && chapterGraph.node(20).row() < 0
                        && chapterGraph.node(4).row() > 0,
                "A branch keeps its side as it continues through later layers");
        var laneGraph = QuestFlowchartLayout.forChapter(snapshot, 100);
        require(laneGraph.node(3).row() == laneGraph.node(20).row(),
                "A simple branch remains on one horizontal lane through later layers");
        var laneFixture = laneFixture();
        var laneFixtureGraph = QuestFlowchartLayout.forChapter(laneFixture, 100);
        require(laneFixtureGraph.node(7).row() == laneFixtureGraph.node(8).row()
                        && laneFixtureGraph.node(8).row() == laneFixtureGraph.node(9).row(),
                "A -> U1 -> U2 -> U3 stays in one horizontal lane");
        require(laneFixtureGraph.node(7).row() < laneFixtureGraph.node(10).row(),
                "The longer U branch is raised above shorter branches on the upper side");
        for (var edge : laneFixtureGraph.edges()) {
            var route = laneFixtureGraph.route(edge);
            require(route.size() <= 3,
                    "Each dependency route has at most one elbow: " + edge.from().quest().questId()
                            + " -> " + edge.to().quest().questId());
            var visible = route;
            require(isBorderCenter(visible.getFirst(), edge.from())
                            && isBorderCenter(visible.getLast(), edge.to()),
                    "Rendered dependency meets each task at the center of its border");
        }
        require(chapterGraph.edges().stream().filter(edge -> edge.from().row() != 0 && edge.to().row() != 0)
                        .allMatch(edge -> Integer.signum(edge.from().row()) == Integer.signum(edge.to().row())),
                "Branch edges stay on one side of the main trunk");
        require(QuestFlowchartLayout.NODE_WIDTH == 32 && QuestFlowchartLayout.NODE_HEIGHT == 32,
                "Nodes reserve a compact icon and border footprint");

        var questGraph = QuestFlowchartLayout.forQuest(snapshot, 100, 2);
        require(questGraph.anchorQuestId() == 2L && questGraph.verticalAnchorQuestId() == 2L,
                "Quest entry focuses the requested quest on both axes");
        var repeated = QuestFlowchartLayout.forChapter(snapshot, 100);
        require(QuestFlowchartLayout.refresh(chapterGraph, snapshot, 100, null) == chapterGraph,
                "Unchanged graph data reuses the complete graph");
        var oldQuest = snapshot.graphQuests().getFirst();
        var completedQuest = new QuestLogSnapshot.GraphQuest(oldQuest.questId(), oldQuest.chapterId(), oldQuest.groupId(),
                oldQuest.title(), oldQuest.iconJson(), false, true, true, oldQuest.canStart(),
                100, true, true, oldQuest.stableOrder(), oldQuest.dependencyQuestIds(), oldQuest.dependantQuestIds());
        var changedQuests = new java.util.ArrayList<>(snapshot.graphQuests());
        changedQuests.set(0, completedQuest);
        var updated = new QuestLogSnapshot(snapshot.fileGeneration(), snapshot.teamId(), snapshot.pauseGame(),
                snapshot.groups(), snapshot.chapterHeaders(), snapshot.questHeaders(), snapshot.questDetails(),
                snapshot.bookTitle(), snapshot.bookIconJson(), changedQuests);
        var refreshed = QuestFlowchartLayout.refresh(chapterGraph, updated, 100, null);
        require(refreshed.node(1).quest().completed() && refreshed.node(1).quest().unclaimedRewards()
                        && !refreshed.node(1).quest().detailsAvailable(),
                "Refresh publishes progress, rewards and access restrictions together");
        require(refreshed.width() == chapterGraph.width() && refreshed.height() == chapterGraph.height(),
                "State-only refresh preserves canvas dimensions");
        for (var node : chapterGraph.nodes()) {
            var next = refreshed.node(node.quest().questId());
            require(next.x() == node.x() && next.y() == node.y(), "State refresh preserves every node position");
        }
        for (int i = 0; i < chapterGraph.edges().size(); i++) {
            require(chapterGraph.route(chapterGraph.edges().get(i)) == refreshed.route(refreshed.edges().get(i)),
                    "State refresh reuses planned route points without rerouting");
        }
        changedQuests.removeIf(quest -> quest.questId() == 10);
        var hidden = new QuestLogSnapshot(snapshot.fileGeneration(), snapshot.teamId(), snapshot.pauseGame(),
                snapshot.groups(), snapshot.chapterHeaders(), snapshot.questHeaders(), snapshot.questDetails(),
                snapshot.bookTitle(), snapshot.bookIconJson(), changedQuests);
        var reduced = QuestFlowchartLayout.refresh(refreshed, hidden, 100, null);
        require(reduced.node(10) == null && reduced.edges().stream().noneMatch(edge ->
                        edge.from().quest().questId() == 10 || edge.to().quest().questId() == 10),
                "Visibility changes rebuild scope without retaining hidden endpoints");
        require(chapterGraph.nodes().stream().map(node -> List.of(node.quest().questId(), node.x(), node.y())).toList()
                        .equals(repeated.nodes().stream()
                                .map(node -> List.of(node.quest().questId(), node.x(), node.y())).toList()),
                "Layout is deterministic");

        var cyclic = new QuestLogSnapshot.GraphQuest(1, 100, 10, "Cycle A", "{}", true,
                false, false, false, 0, false, false, 0, List.of(2L), List.of(2L));
        var cyclicOther = new QuestLogSnapshot.GraphQuest(2, 100, 10, "Cycle B", "{}", true,
                false, false, false, 0, false, false, 1, List.of(1L), List.of(1L));
        var cyclicSnapshot = new QuestLogSnapshot(2, UUID.randomUUID(), false,
                List.of(new QuestLogSnapshot.Group(10, "Cycle", 0, List.of(
                        new QuestLogSnapshot.Chapter(100, 10, "Cycle", List.of(entry(1, 100), entry(2, 100)))))),
                Map.of(), Map.of(), Map.of(), "Book", "{}", List.of(cyclic, cyclicOther));
        var cyclicGraph = QuestFlowchartLayout.forChapter(cyclicSnapshot, 100);
        require(cyclicGraph.nodes().size() == 2 && cyclicGraph.edges().size() == 2,
                "Cycles remain finite and preserve each dependency edge");
        require(cyclicGraph.height() == QuestFlowchartLayout.VIEWPORT_HEIGHT
                        && cyclicGraph.initialScrollY() == 0,
                "A compact graph does not expose unnecessary vertical scrolling");
        require(cyclicGraph.contentHeight() == QuestFlowchartLayout.VIEWPORT_HEIGHT
                        && cyclicGraph.contentWidth() == QuestFlowchartLayout.VIEWPORT_WIDTH,
                "Compact content uses the actual viewport without blank scroll ranges");

        var starIds = List.of(1L, 2L, 3L, 4L, 5L);
        var starQuests = starIds.stream().map(id -> quest(id, 100,
                id == 1 ? List.of() : List.of(1L), id == 1 ? starIds.subList(1, 5) : List.of(), id.intValue())).toList();
        var starSnapshot = new QuestLogSnapshot(1, UUID.randomUUID(), false,
                List.of(new QuestLogSnapshot.Group(10, "Star", 0, List.of(new QuestLogSnapshot.Chapter(
                        100, 10, "Star", starIds.stream().map(id -> entry(id, 100)).toList())))),
                Map.of(), Map.of(), Map.of(), "Book", "{}", starQuests);
        var star = QuestFlowchartLayout.forChapter(starSnapshot, 100);
        require(star.nodes().stream().mapToInt(QuestFlowchartLayout.Node::row).min().orElseThrow() == -2
                        && star.nodes().stream().mapToInt(QuestFlowchartLayout.Node::row).max().orElseThrow() == 1,
                "Regression fixture has more branches above the trunk than below");
        double trunkY = star.node(1).y() + QuestFlowchartLayout.NODE_HEIGHT / 2d;
        require(trunkY - star.initialScrollY() == QuestFlowchartLayout.VIEWPORT_HEIGHT / 2d
                        && star.initialScrollY() <= star.contentHeight() - QuestFlowchartLayout.VIEWPORT_HEIGHT,
                "Actual scroll bounds preserve trunk centering for an asymmetric tree");
        require(star.contentHeight() < star.height(),
                "Centering reserves only necessary trailing space, not the full symmetric canvas");

        var focus = new QuestFlowchartLayout.Node(starQuests.getFirst(), 400, 71, 0, 0);
        var far = new QuestFlowchartLayout.Node(starQuests.getLast(), 800, 71, 1, 0);
        var focused = new QuestFlowchartLayout.Graph(List.of(focus, far), List.of(), 844, 174, 1L, 1L);
        require(focus.x() + QuestFlowchartLayout.NODE_WIDTH / 2d - focused.initialScrollX()
                        == QuestFlowchartLayout.VIEWPORT_WIDTH / 2d,
                "Quest focus uses the same viewport width as the dialog");

        var crossingGraph = QuestFlowchartLayout.forChapter(crossingSnapshot(), 100);
        require(adjacentLayerCrossings(crossingGraph) == 0,
                "Median sweeps remove crossings in an ordered branch graph");

        System.out.println("Quest flowchart layout checks PASS: scope, deduplication, edge direction, anchors, one-elbow routes, stability, cycles");
    }

    private static void checkQuestStates() {
        var cases = List.of(
                new StateCase(false, false, false, false, QuestLogSnapshot.QuestState.LOCKED),
                new StateCase(false, false, true, false, QuestLogSnapshot.QuestState.AVAILABLE),
                new StateCase(true, false, true, false, QuestLogSnapshot.QuestState.STARTED),
                new StateCase(false, true, false, false, QuestLogSnapshot.QuestState.COMPLETED),
                new StateCase(false, true, false, true, QuestLogSnapshot.QuestState.REWARD),
                new StateCase(true, true, true, true, QuestLogSnapshot.QuestState.REWARD));
        for (var test : cases) {
            var entry = new QuestLogSnapshot.Entry(1, 1, 100, "State", false, test.started(), test.completed(),
                    test.canStart(), 0, test.unclaimedRewards(), false, test.expected());
            var graphQuest = new QuestLogSnapshot.GraphQuest(1, 100, 10, "State", "{}", true,
                    test.started(), test.completed(), test.canStart(), 0, test.unclaimedRewards(), false,
                    0, List.of(), List.of(), test.expected());
            require(entry.state() == test.expected() && graphQuest.state() == test.expected(),
                    "Entry and graph snapshots share canonical QuestState " + test.expected());
        }
        boolean mismatchRejected = false;
        try {
            new QuestLogSnapshot.Entry(1, 1, 100, "Mismatch", false, false, false,
                    true, 0, false, false, QuestLogSnapshot.QuestState.LOCKED);
        } catch (IllegalArgumentException expected) {
            mismatchRejected = true;
        }
        require(mismatchRejected, "Snapshots reject a QuestState inconsistent with authoritative facts");
    }

    private record StateCase(boolean started, boolean completed, boolean canStart,
                             boolean unclaimedRewards, QuestLogSnapshot.QuestState expected) {}

    private static QuestLogSnapshot.Entry entry(long questId, long chapterId) {
        return new QuestLogSnapshot.Entry(questId, questId, chapterId, "Quest " + questId, false,
                false, false, true, 0, false, false);
    }

    private static QuestLogSnapshot.Entry linkedEntry(long questId, long chapterId) {
        return new QuestLogSnapshot.Entry(questId + 1000, questId, chapterId, "Quest " + questId, true,
                false, false, true, 0, false, false);
    }

    private static QuestLogSnapshot.GraphQuest quest(long id, long chapterId,
                                                       List<Long> dependencies, List<Long> dependants,
                                                       int order) {
        return new QuestLogSnapshot.GraphQuest(id, chapterId, 10, "Quest " + id, "{}", true,
                false, false, true, 0, false, false, order, dependencies, dependants);
    }

    private static List<Long> ids(QuestFlowchartLayout.Graph graph) {
        return graph.nodes().stream().map(node -> node.quest().questId()).sorted().toList();
    }

    private static QuestLogSnapshot crossingSnapshot() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L);
        Map<Long, List<Long>> dependencies = Map.ofEntries(
                Map.entry(2L, List.of(1L)), Map.entry(3L, List.of(2L)),
                Map.entry(4L, List.of(3L)), Map.entry(5L, List.of(4L)),
                Map.entry(10L, List.of(1L)), Map.entry(11L, List.of(10L)),
                Map.entry(12L, List.of(2L)), Map.entry(13L, List.of(12L)),
                Map.entry(14L, List.of(3L)), Map.entry(15L, List.of(14L)),
                Map.entry(16L, List.of(2L)), Map.entry(17L, List.of(16L)));
        List<QuestLogSnapshot.Entry> entries = ids.stream().map(id -> entry(id, 100)).toList();
        List<QuestLogSnapshot.GraphQuest> quests = ids.stream().map(id -> new QuestLogSnapshot.GraphQuest(
                id, 100, 10, "Quest " + id, "{}", true, false, false, true, 0, false, false,
                ids.indexOf(id), dependencies.getOrDefault(id, List.of()),
                ids.stream().filter(target -> dependencies.getOrDefault(target, List.of()).contains(id)).toList())).toList();
        return new QuestLogSnapshot(3, UUID.randomUUID(), false,
                List.of(new QuestLogSnapshot.Group(10, "Graph", 0, List.of(
                        new QuestLogSnapshot.Chapter(100, 10, "Graph", entries)))),
                Map.of(), Map.of(), Map.of(), "Book", "{}", quests);
    }

    private static QuestLogSnapshot laneFixture() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L);
        Map<Long, List<Long>> dependencies = Map.ofEntries(
                Map.entry(2L, List.of(1L)), Map.entry(3L, List.of(2L)), Map.entry(4L, List.of(3L)),
                Map.entry(5L, List.of(4L)), Map.entry(6L, List.of(5L)),
                Map.entry(7L, List.of(1L)), Map.entry(8L, List.of(7L)), Map.entry(9L, List.of(8L)),
                Map.entry(10L, List.of(2L)), Map.entry(11L, List.of(10L)),
                Map.entry(12L, List.of(2L)), Map.entry(13L, List.of(12L)));
        var entries = ids.stream().map(id -> entry(id, 100)).toList();
        var quests = ids.stream().map(id -> new QuestLogSnapshot.GraphQuest(id, 100, 10,
                "Quest " + id, "{}", true, false, false, true, 0, false, false, ids.indexOf(id),
                dependencies.getOrDefault(id, List.of()), ids.stream()
                .filter(target -> dependencies.getOrDefault(target, List.of()).contains(id)).toList())).toList();
        return new QuestLogSnapshot(4, UUID.randomUUID(), false,
                List.of(new QuestLogSnapshot.Group(10, "Lanes", 0, List.of(
                        new QuestLogSnapshot.Chapter(100, 10, "Lanes", entries)))),
                Map.of(), Map.of(), Map.of(), "Book", "{}", quests);
    }

    private static long adjacentLayerCrossings(QuestFlowchartLayout.Graph graph) {
        long crossings = 0;
        for (int first = 0; first < graph.edges().size(); first++) {
            var a = graph.edges().get(first);
            for (int second = first + 1; second < graph.edges().size(); second++) {
                var b = graph.edges().get(second);
                if (a.from().layer() != b.from().layer() || a.to().layer() != b.to().layer()) continue;
                int fromOrder = Integer.compare(a.from().row(), b.from().row());
                int toOrder = Integer.compare(a.to().row(), b.to().row());
                if (fromOrder != 0 && toOrder != 0 && fromOrder != toOrder) crossings++;
            }
        }
        return crossings;
    }

    private static boolean isBorderCenter(QuestFlowchartRouting.Point point, QuestFlowchartLayout.Node node) {
        int centerX = node.x() + QuestFlowchartLayout.NODE_WIDTH / 2;
        int centerY = node.y() + QuestFlowchartLayout.NODE_HEIGHT / 2;
        return (point.x() == node.x() || point.x() == node.right()) && point.y() == centerY
                || (point.y() == node.y() || point.y() == node.bottom()) && point.x() == centerX;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
