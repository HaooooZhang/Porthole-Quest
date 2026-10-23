package ink.myumoon.portholequest.simulator;

import ink.myumoon.portholequest.model.QuestFlowchartRouting.Point;
import ink.myumoon.portholequest.model.QuestFlowchartLayout;
import ink.myumoon.portholequest.model.QuestFlowchartCase;
import ink.myumoon.portholequest.model.QuestLogSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regression checks for the diagnostic tool, independent of Minecraft. */
public final class QuestFlowchartSimulatorCheck {
    public static void main(String[] args) {
        checkGameReplay();
        checkRoutePlanning();
        for (var scenario : QuestFlowchartSimulator.scenarios()) {
            var first = QuestFlowchartSimulator.layout(scenario.get("source"));
            var second = QuestFlowchartSimulator.layout(scenario.get("source"));
            require(first.get("nodes").equals(second.get("nodes")), "Stable nodes: " + scenario.get("id"));
            require(first.get("edges").equals(second.get("edges")), "Stable paths: " + scenario.get("id"));
            require(first.get("issues").equals(second.get("issues")), "Stable diagnostics: " + scenario.get("id"));
            var occupied = new java.util.HashSet<List<Object>>();
            var lanes = new java.util.HashSet<Integer>();
            for (Object value : (List<?>) first.get("nodes")) {
                var n = (Map<?, ?>) value;
                require(occupied.add(List.of(n.get("x"), n.get("y"))), "Distinct node positions: " + scenario.get("id"));
                lanes.add(((Number) n.get("row")).intValue());
            }
            for (int row : lanes) for (int inner = Integer.signum(row); Math.abs(inner) < Math.abs(row); inner += Integer.signum(row)) {
                require(lanes.contains(inner), "No empty lane separates a branch from the trunk: " + scenario.get("id"));
            }
            if (List.of("prologue", "simple-fork", "branches", "staggered", "compound", "diamond", "parents", "forest", "external", "wide", "layered")
                    .contains(scenario.get("id"))) {
                require(issueCount(first, "node") == 0 && issueCount(first, "cross") == 0,
                        "Common quest structures have clear routes: " + scenario.get("id") + " " + first.get("issues"));
            }
            if (scenario.get("id").equals("staggered")) {
                require(node(first, "U1").get("row").equals(node(first, "V1").get("row"))
                                && node(first, "W1").get("row").equals(node(first, "X1").get("row")),
                        "Non-overlapping branch intervals reuse lanes, including their connectors");
                require(((Number) first.get("height")).intValue() == QuestFlowchartLayout.VIEWPORT_HEIGHT,
                        "Staggered branches fit without extra vertical canvas space");
            }
            if (scenario.get("id").equals("prologue")) {
                require(((List<?>) first.get("nodes")).size() == 19
                                && ((List<?>) first.get("edges")).size() == 20,
                        "Prologue includes the complete chapter, not just the five checkmark nodes");
                require(issueCount(first, "cross") == 0 && issueCount(first, "node") == 0,
                        "Recorded prologue has no strict crossing or path through unrelated nodes");
                for (String id : List.of("A", "B", "C", "D", "E", "H", "M", "Q", "R", "S")) {
                    require(node(first, id).get("row").equals(0), "Prologue longest chain stays centered: " + id);
                }
                for (String id : List.of("F", "I", "J", "N", "O")) {
                    require(((Number) node(first, id).get("row")).intValue() < 0,
                            "Checkmark subtree stays above the full chapter trunk: " + id);
                }
                require(node(first, "F").get("row").equals(node(first, "I").get("row"))
                                && node(first, "I").get("row").equals(node(first, "N").get("row")),
                        "F -> I -> N stays straight instead of allocating a lane at each fork");
                require(node(first, "G").get("row").equals(node(first, "K").get("row"))
                                && node(first, "K").get("row").equals(node(first, "P").get("row")),
                        "G -> K -> P stays straight through the diamond");
                require(((Number) first.get("height")).intValue() <= 332,
                        "Simple prologue subtrees use three rows above and two below the trunk");
            }
            if (scenario.get("id").equals("simple-fork")) {
                require(((List<?>) first.get("nodes")).size() == 5
                                && ((List<?>) first.get("edges")).size() == 4,
                        "Simple fork matches A -> B, A -> C, B -> D, B -> E");
                require(issueCount(first, "cross") == 0 && issueCount(first, "node") == 0,
                        "Simple fork has no crossing or path through unrelated nodes");
                require(((Number) first.get("height")).intValue() == QuestFlowchartLayout.VIEWPORT_HEIGHT,
                        "A small fork does not reserve permanent lanes on one side");
                require(node(first, "A").get("row").equals(0)
                                && node(first, "B").get("row").equals(0)
                                && node(first, "D").get("row").equals(0),
                        "A -> B -> D remains centered as the main trunk");
            }
            if (scenario.get("id").equals("branches")) {
                require(issueCount(first, "cross") == 0,
                        "Long trunk and branch fixture has no path crossings");
            }
            if (scenario.get("id").equals("diamond")) {
                require(((Number) node(first, "U1").get("row")).intValue() < 0
                                && ((Number) node(first, "U2").get("row")).intValue() < 0
                                && ((Number) node(first, "L1").get("row")).intValue() > 0
                                && ((Number) node(first, "L2").get("row")).intValue() > 0,
                        "Reconverging L branch moves below trunk as a whole");
                require(issueCount(first, "cross") == 0 && issueCount(first, "node") == 0,
                        "Opposite branch sides avoid U2 -> D passing through L2");
            }
            if (scenario.get("id").equals("compound")) {
                Map<?, ?> u1 = node(first, "U1"), u2 = node(first, "U2"), u3 = node(first, "U3");
                int outermostRow = ((List<?>) first.get("nodes")).stream().mapToInt(value ->
                        ((Number) ((Map<?, ?>) value).get("row")).intValue()).min().orElseThrow();
                require(u1.get("y").equals(u2.get("y")) && u2.get("y").equals(u3.get("y")),
                        "Long U branch remains on one lane in the compound fixture");
                require(((Number) u1.get("row")).intValue() == outermostRow,
                        "Long U branch stays outside the complex branches");
                var kinds = ((List<?>) first.get("issues")).stream()
                        .map(value -> ((Map<?, ?>) value).get("kind").toString()).toList();
                require(kinds.stream().filter(kind -> kind.equals("cross")).count() == 0,
                        "Compound fixture has no path crossings; shared runs are allowed: " + kinds);
            }
            for (Object routeObject : (List<?>) first.get("edges")) {
                var route = (Map<?, ?>) routeObject;
                var points = (List<?>) route.get("points");
                require(points.size() <= 3, "Each dependency uses at most one elbow: " + route);
                int turns = 0;
                int previousDirection = 0;
                for (int index = 1; index < points.size(); index++) {
                    var from = (List<?>) points.get(index - 1);
                    var to = (List<?>) points.get(index);
                    int direction = ((Number) from.get(0)).intValue() == ((Number) to.get(0)).intValue() ? 2 : 1;
                    require(direction == 1 || ((Number) from.get(1)).intValue() != ((Number) to.get(1)).intValue(),
                            "Dependency segments are orthogonal: " + route);
                    if (previousDirection != 0 && direction != previousDirection) turns++;
                    previousDirection = direction;
                }
                require(turns <= 1, "Each dependency has at most one right-angle turn: " + route);
            }
            System.out.println(scenario.get("id") + ": " + ((List<?>) first.get("nodes")).size()
                    + " nodes, height=" + first.get("height") + ", crossings=" + issueCount(first, "cross")
                    + ", node hits=" + issueCount(first, "node"));
        }
        var scope = QuestFlowchartSimulator.layout("A -> B\nX -> A\nY -> X\nexternal: X,Y\n");
        require(((List<?>) scope.get("nodes")).size() == 3, "Only directly adjacent external nodes");
        var all = QuestFlowchartSimulator.layout("A,B -> C,D\nSolo\n");
        require(((List<?>) all.get("edges")).size() == 4, "Multi-parent Cartesian dependencies");
        var focused = QuestFlowchartSimulator.layout("A -> B -> C\nfocus: B");
        require(focused.get("nodes").equals(QuestFlowchartSimulator.layout("A -> B -> C").get("nodes")),
                "Entry focus does not change layout");
        require(((List<?>) QuestFlowchartSimulator.layout("# empty").get("nodes")).isEmpty(), "Empty input");
        for (String bad : List.of("A ->", "focus: Missing\nA", "external:", "A -> <script>", "A -> B ->")) {
            try {
                QuestFlowchartSimulator.layout(bad);
                throw new AssertionError("Should reject: " + bad);
            } catch (IllegalArgumentException expected) { /* Invalid input is actionable. */ }
        }
        var horizontal = route("A", "B", 0, 10, 100, 10);
        var vertical = route("C", "D", 50, 0, 50, 20);
        var crossings = QuestFlowchartSimulator.diagnose(List.of(horizontal, vertical), List.of());
        require(crossings.stream().anyMatch(issue -> issue.get("kind").equals("cross")
                && issue.get("x").equals(50) && issue.get("y").equals(10)), "Actual polyline crossing detected");
        var overlaps = QuestFlowchartSimulator.diagnose(List.of(horizontal, route("C", "D", 40, 10, 140, 10)), List.of());
        require(overlaps.stream().filter(issue -> issue.get("kind").equals("overlap")).count() == 1,
                "Shared runs reported once per edge pair");
        var shared = QuestFlowchartSimulator.diagnose(List.of(route("A", "B", 0, 0, 20, 0),
                route("A", "C", 0, 0, 0, 20)), List.of());
        require(shared.stream().noneMatch(issue -> issue.get("kind").equals("cross")), "Shared node port is not a crossing");
        var branchContact = QuestFlowchartSimulator.diagnose(List.of(route("A", "B", 0, 10, 100, 10),
                new QuestFlowchartSimulator.Route("C", "D", List.of(new Point(50, 0), new Point(50, 10), new Point(50, 30)))), List.of());
        require(branchContact.stream().anyMatch(issue -> issue.get("kind").equals("contact"))
                        && branchContact.stream().noneMatch(issue -> issue.get("kind").equals("cross")),
                "A T-junction is reported as endpoint contact, not a proper crossing");
        var node = new LinkedHashMap<String, Object>();
        node.put("id", "Other"); node.put("x", 40); node.put("y", 0);
        require(QuestFlowchartSimulator.diagnose(List.of(horizontal), List.of(node)).stream()
                .anyMatch(issue -> issue.get("kind").equals("node")), "Long edge through an unrelated node detected");
        require(QuestFlowchartSimulator.json(Map.of("test", "\"\\\n\t")).contains("\\n"), "JSON strings escaped");
        checkPerformanceReplay();
        if (java.util.Arrays.asList(args).contains("--stress")) {
            Map<String, Integer> sparse = QuestFlowchartSimulator.sweep(20261022, 40, 20, .05);
            Map<String, Integer> medium = QuestFlowchartSimulator.sweep(20261022, 40, 20, .10);
            Map<String, Integer> dense = QuestFlowchartSimulator.sweep(20261022, 40, 20, .18);
            for (var result : List.of(sparse, medium, dense)) System.out.println("40-node baseline " + result);
            for (int count : List.of(20, 40, 60)) for (double density : List.of(.05, .10, .18)) {
                var first = QuestFlowchartSimulator.sweep(20261022, count, 20, density);
                var second = QuestFlowchartSimulator.sweep(20261022, count, 20, density);
                require(first.equals(second), "Seed sweeps are reproducible: " + count + "/" + density);
                System.out.println("sweep " + first);
            }
        }
        System.out.println("Quest flowchart simulator checks PASS");
    }

    private static void checkPerformanceReplay() {
        // Captured before the allocation optimization. Preserve every node and
        // route, including this deliberately difficult graph's existing defects.
        var result = QuestFlowchartSimulator.layout(QuestFlowchartSimulator.randomCase(42, 40));
        var geometry = new StringBuilder("size|").append(result.get("width")).append('|').append(result.get("height")).append('\n');
        for (Object value : (List<?>) result.get("nodes")) {
            var node = (Map<?, ?>) value;
            geometry.append("N");
            for (String key : List.of("id", "x", "y", "layer", "row")) geometry.append('|').append(node.get(key));
            geometry.append('\n');
        }
        for (Object value : (List<?>) result.get("edges")) {
            var edge = (Map<?, ?>) value;
            geometry.append("E|").append(edge.get("from")).append('|').append(edge.get("to"));
            for (Object point : (List<?>) edge.get("points")) {
                var xy = (List<?>) point;
                geometry.append('|').append(xy.get(0)).append(',').append(xy.get(1));
            }
            geometry.append('\n');
        }
        try {
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(geometry.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            require(digest.equals("98b8cb1d00c1503977eeb2f483d98003e36c2a0a6a68cdc4c70e87eb6519b545"),
                    "random-40 keeps its original node coordinates and every route point: " + digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
        System.out.println("random-40 pre-optimization geometry replay PASS");
    }

    private static void checkRoutePlanning() {
        var nodes = List.of(routeNode(1, "A", 0, 0), routeNode(2, "B", 144, 92),
                routeNode(3, "C", 72, 46), routeNode(4, "D", 216, -46));
        var edges = List.of(new QuestFlowchartLayout.PositionedEdge(nodes.get(0), nodes.get(1)),
                new QuestFlowchartLayout.PositionedEdge(nodes.get(2), nodes.get(3)));
        var plan = ink.myumoon.portholequest.model.QuestFlowchartRouting.plan(nodes, edges);
        var routes = edges.stream().map(edge -> new QuestFlowchartSimulator.Route(
                edge.from().quest().title(), edge.to().quest().title(), plan.get(edge))).toList();
        require(QuestFlowchartSimulator.diagnose(routes, List.of()).stream().noneMatch(issue -> issue.get("kind").equals("cross")),
                "Elbow selection avoids a crossing even when both elbows clear all nodes");
        var isolated = routeNode(5, "Isolated", 72, 0);
        var withIsolated = new java.util.ArrayList<>(nodes);
        withIsolated.add(isolated);
        var isolatedPlan = ink.myumoon.portholequest.model.QuestFlowchartRouting.plan(withIsolated, edges);
        var obstacle = Map.<String, Object>of("id", "Isolated", "x", isolated.x(), "y", isolated.y());
        var first = new QuestFlowchartSimulator.Route("A", "B", isolatedPlan.get(edges.getFirst()));
        require(QuestFlowchartSimulator.diagnose(List.of(first), List.of(obstacle)).isEmpty(),
                "Routing clears isolated nodes as well as edge endpoints");
    }

    private static QuestFlowchartLayout.Node routeNode(long id, String title, int x, int y) {
        var quest = new QuestLogSnapshot.GraphQuest(id, 100, 10, title, "{}", true, false, false,
                true, 0, false, false, (int) id, List.of(), List.of());
        return new QuestFlowchartLayout.Node(quest, x, y, 0, 0);
    }

    private static QuestFlowchartSimulator.Route route(String from, String to, int x1, int y1, int x2, int y2) {
        return new QuestFlowchartSimulator.Route(from, to, List.of(new Point(x1, y1), new Point(x2, y2)));
    }

    private static void checkGameReplay() {
        // FTBQ IDs, task order and dependant-list order differ from A/B/C text input.
        // Replaying must preserve all geometry, including equal-length trunk choices.
        List<Long> ids = List.of(901L, 42L, 781L, 9L, 55L);
        Map<Long, List<Long>> deps = Map.of(42L, List.of(901L), 781L, List.of(901L),
                9L, List.of(42L), 55L, List.of(42L));
        var entries = ids.stream().map(id -> new QuestLogSnapshot.Entry(id, id, 100, "任务 " + id,
                false, false, false, true, 0, false, false)).toList();
        var chapter = new QuestLogSnapshot.Chapter(100, 10, "Game fixture", entries);
        Map<?, ?> baseline = null;
        for (int mode = 0; mode < 3; mode++) {
            final int relationMode = mode;
            var quests = ids.stream().map(id -> new QuestLogSnapshot.GraphQuest(id, 100, 10, "任务 " + id,
                    "{}", true, false, false, true, 0, false, false, ids.indexOf(id),
                    relationMode == 1 ? List.of() : deps.getOrDefault(id, List.of()),
                    relationMode == 0 ? List.of() : ids.reversed().stream()
                            .filter(child -> deps.getOrDefault(child, List.of()).contains(id)).toList())).toList();
            var snapshot = new QuestLogSnapshot(1, new java.util.UUID(0, 1), false,
                    List.of(new QuestLogSnapshot.Group(10, "Group", 0, List.of(chapter))),
                    Map.of(), Map.of(), Map.of(), "Book", "{}", quests);
            for (boolean focused : List.of(false, true)) {
                var graph = focused ? QuestFlowchartLayout.forQuest(snapshot, 100, 42)
                        : QuestFlowchartLayout.forChapter(snapshot, 100);
                var replay = QuestFlowchartCase.capture(snapshot, 100, graph);
                var expected = (Map<?, ?>) replay.get("result");
                var actual = QuestFlowchartSimulator.layout(replay.get("source").toString());
                for (String key : List.of("nodes", "edges", "width", "height", "revision")) {
                    require(expected.get(key).equals(actual.get(key)),
                            "Game replay preserves " + key + " for relation mode=" + mode);
                }
                if (baseline == null) baseline = expected;
                require(baseline.equals(expected), "Dependency/dependant traversal order cannot change geometry");
            }
        }
        System.out.println("Game/simulator replay parity PASS: task IDs, stable order, relation order, both entry points");
    }

    private static Map<?, ?> node(Map<String, Object> result, String id) {
        return ((List<?>) result.get("nodes")).stream().map(value -> (Map<?, ?>) value)
                .filter(value -> value.get("id").equals(id)).findFirst().orElseThrow();
    }

    private static long issueCount(Map<String, Object> result, String kind) {
        return ((List<?>) result.get("issues")).stream().map(value -> (Map<?, ?>) value)
                .filter(value -> value.get("kind").equals(kind)).count();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
