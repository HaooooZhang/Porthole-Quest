package ink.myumoon.portholequest.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Finite, stable left-to-right layout for the read-only quest dependency preview. */
public final class QuestFlowchartLayout {
    public static final String REVISION = "r33-lane-packing-3";
    public static final int VIEWPORT_WIDTH = 300;
    public static final int VIEWPORT_HEIGHT = 174;
    public static final int NODE_WIDTH = 32;
    public static final int NODE_HEIGHT = 32;
    private static final int MARGIN = 12;
    private static final int COLUMN_GAP = 40;
    private static final int ROW_GAP = 14;

    private QuestFlowchartLayout() {}

    public static Graph forChapter(QuestLogSnapshot snapshot, long chapterId) {
        return create(snapshot, chapterId, null);
    }

    public static Graph forQuest(QuestLogSnapshot snapshot, long chapterId, long questId) {
        return create(snapshot, chapterId, questId);
    }

    private static Graph create(QuestLogSnapshot snapshot, long chapterId, Long focusQuestId) {
        Objects.requireNonNull(snapshot);
        Map<Long, QuestLogSnapshot.GraphQuest> available = new LinkedHashMap<>();
        snapshot.graphQuests().stream()
                .sorted(Comparator.comparingInt(QuestLogSnapshot.GraphQuest::stableOrder))
                .forEach(quest -> available.putIfAbsent(quest.questId(), quest));

        var chapterQuestIds = new LinkedHashSet<Long>();
        for (var group : snapshot.groups()) for (var chapter : group.chapters()) {
            if (chapter.id() == chapterId) {
                chapter.entries().forEach(entry -> chapterQuestIds.add(entry.questId()));
            }
        }

        var selected = new LinkedHashSet<Long>();
        for (long questId : chapterQuestIds) if (available.containsKey(questId)) selected.add(questId);
        for (long questId : List.copyOf(selected)) {
            QuestLogSnapshot.GraphQuest quest = available.get(questId);
            for (long dependency : quest.dependencyQuestIds()) {
                if (available.containsKey(dependency)) selected.add(dependency);
            }
            for (long dependant : quest.dependantQuestIds()) {
                if (available.containsKey(dependant)) selected.add(dependant);
            }
        }

        var orderedIds = selected.stream().sorted(Comparator
                .comparingInt((Long id) -> available.get(id).stableOrder())
                .thenComparingLong(Long::longValue)).toList();
        var edges = new LinkedHashSet<Edge>();
        for (long questId : orderedIds) {
            QuestLogSnapshot.GraphQuest quest = available.get(questId);
            for (long dependency : quest.dependencyQuestIds()) {
                if (selected.contains(dependency)) edges.add(new Edge(dependency, questId));
            }
            for (long dependant : quest.dependantQuestIds()) {
                if (selected.contains(dependant)) edges.add(new Edge(questId, dependant));
            }
        }

        var outgoing = new HashMap<Long, List<Long>>();
        var indegree = new HashMap<Long, Integer>();
        var layer = new HashMap<Long, Integer>();
        var stableOrder = new HashMap<Long, Integer>();
        var topoOrder = new ArrayList<Long>();
        for (int index = 0; index < orderedIds.size(); index++) {
            long id = orderedIds.get(index);
            indegree.put(id, 0);
            stableOrder.put(id, index);
        }
        edges = new LinkedHashSet<>(edges.stream().sorted(Comparator
                .comparingInt((Edge edge) -> stableOrder.get(edge.fromQuestId()))
                .thenComparingInt(edge -> stableOrder.get(edge.toQuestId()))).toList());
        for (Edge edge : edges) {
            outgoing.computeIfAbsent(edge.fromQuestId(), ignored -> new ArrayList<>()).add(edge.toQuestId());
            indegree.compute(edge.toQuestId(), (ignored, count) -> count + 1);
        }
        // FTBQ supplies both dependency and dependant lists; their traversal order must not
        // decide which equal-length path becomes the trunk. Use the declared task order.
        outgoing.values().forEach(children -> children.sort(Comparator.comparingInt(stableOrder::get)));

        var ready = new ArrayDeque<Long>();
        for (long id : orderedIds) {
            if (indegree.get(id) == 0) {
                layer.put(id, 0);
                ready.addLast(id);
            }
        }
        while (!ready.isEmpty()) {
            long parent = ready.removeFirst();
            topoOrder.add(parent);
            for (long child : outgoing.getOrDefault(parent, List.of())) {
                layer.merge(child, layer.get(parent) + 1, Math::max);
                int remaining = indegree.compute(child, (ignored, count) -> count - 1);
                if (remaining == 0) ready.addLast(child);
            }
        }

        int fallbackLayer = layer.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        var cyclicIds = new LinkedHashSet<Long>();
        for (long id : orderedIds) {
            if (!layer.containsKey(id)) {
                layer.put(id, fallbackLayer);
                cyclicIds.add(id);
            }
        }
        var mainTrunk = findMainTrunk(orderedIds, topoOrder, outgoing, cyclicIds);
        var incoming = new HashMap<Long, List<Long>>();
        for (Edge edge : edges) incoming.computeIfAbsent(edge.toQuestId(), ignored -> new ArrayList<>()).add(edge.fromQuestId());
        incoming.values().forEach(parents -> parents.sort(Comparator.comparingInt(stableOrder::get)));
        var rowById = assignRows(orderedIds, topoOrder, incoming, outgoing, mainTrunk,
                stableOrder, edges, layer);
        var columns = new HashMap<Integer, List<Long>>();
        for (long id : orderedIds) columns.computeIfAbsent(layer.get(id), ignored -> new ArrayList<>()).add(id);

        var nodes = new ArrayList<Node>();
        int canvasWidth = VIEWPORT_WIDTH;
        int canvasHeight = VIEWPORT_HEIGHT;
        int maxLayer = columns.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int stepY = NODE_HEIGHT + ROW_GAP;
        int maxSideCount = rowById.values().stream().mapToInt(row -> Math.abs(row)).max().orElse(0);
        canvasHeight = Math.max(canvasHeight, 2 * (MARGIN + NODE_HEIGHT / 2 + maxSideCount * stepY));
        for (int column = 0; column <= maxLayer; column++) {
            List<Long> rows = columns.getOrDefault(column, List.of());
            int x = MARGIN + column * (NODE_WIDTH + COLUMN_GAP);
            int centerY = canvasHeight / 2 - NODE_HEIGHT / 2;
            for (long id : rows) {
                int row = rowById.get(id);
                int y = centerY + row * stepY;
                nodes.add(new Node(available.get(id), x, y, layer.get(id), row));
            }
            canvasWidth = Math.max(canvasWidth, x + NODE_WIDTH + MARGIN);
        }

        var nodeById = new HashMap<Long, Node>();
        nodes.forEach(node -> nodeById.put(node.quest().questId(), node));
        var graphEdges = edges.stream()
                .map(edge -> new PositionedEdge(nodeById.get(edge.fromQuestId()), nodeById.get(edge.toQuestId())))
                .filter(edge -> edge.from() != null && edge.to() != null)
                .toList();

        Long chapterAnchor = orderedIds.stream().filter(id -> layer.get(id) == 0).findFirst()
                .orElse(orderedIds.isEmpty() ? null : orderedIds.getFirst());
        Long horizontalAnchor = focusQuestId != null && nodeById.containsKey(focusQuestId)
                ? focusQuestId : chapterAnchor;
        Long verticalAnchor = focusQuestId != null && nodeById.containsKey(focusQuestId) ? focusQuestId : null;
        return new Graph(nodes, graphEdges, canvasWidth, canvasHeight, horizontalAnchor, verticalAnchor);
    }

    private static Set<Long> findMainTrunk(List<Long> orderedIds, List<Long> topoOrder,
                                            Map<Long, List<Long>> outgoing, Set<Long> cyclicIds) {
        var longestFrom = new HashMap<Long, Integer>();
        var next = new HashMap<Long, Long>();
        for (int index = topoOrder.size() - 1; index >= 0; index--) {
            long id = topoOrder.get(index);
            int longest = 1;
            for (long child : outgoing.getOrDefault(id, List.of())) {
                if (!longestFrom.containsKey(child)) continue;
                int candidate = longestFrom.getOrDefault(child, 0) + 1;
                if (candidate > longest) {
                    longest = candidate;
                    next.put(id, child);
                }
            }
            longestFrom.put(id, longest);
        }

        Long start = orderedIds.stream().filter(id -> !cyclicIds.contains(id))
                .filter(id -> longestFrom.containsKey(id))
                .max(Comparator.<Long>comparingInt(longestFrom::get)
                        .thenComparing((left, right) -> Integer.compare(
                                orderedIds.indexOf(right), orderedIds.indexOf(left))))
                .orElse(null);
        var trunk = new LinkedHashSet<Long>();
        while (start != null && trunk.add(start)) start = next.get(start);
        return trunk;
    }

    /** A branch owns a side; its longest continuations own reusable horizontal lanes. */
    private static Map<Long, Integer> assignRows(List<Long> ids, List<Long> topoOrder,
            Map<Long, List<Long>> incoming, Map<Long, List<Long>> outgoing, Set<Long> trunk,
            Map<Long, Integer> order, Set<Edge> edges, Map<Long, Integer> layers) {
        var branches = branchPaths(ids, topoOrder, incoming, outgoing, trunk, order, layers);
        var scoring = new ScoringContext(ids, edges, layers);
        var rows = placeBranches(branches, incoming, outgoing, trunk, scoring);
        var currentScore = scoring.score(rows, null);
        if (currentScore.nodeHits() == 0 && currentScore.crossings() == 0) return rows.toMap();
        // Two alternative insertion orders handle branches trapped on the wrong
        // sides. Ordinary trees take the first pass; this search stays bounded.
        var largestFirst = new ArrayList<>(branches);
        largestFirst.sort(Comparator.<List<List<Long>>>comparingInt(branch -> branch.stream().mapToInt(List::size).sum()).reversed());
        for (var insertionOrder : List.of(largestFirst, largestFirst.reversed())) {
            var candidate = placeBranches(insertionOrder, incoming, outgoing, trunk, scoring);
            var candidateScore = scoring.score(candidate, null);
            if (candidateScore.compareTo(currentScore) < 0) {
                rows = candidate;
                currentScore = candidateScore;
            }
        }
        return rows.toMap();
    }

    private static RowAssignments placeBranches(List<List<List<Long>>> branches,
            Map<Long, List<Long>> incoming, Map<Long, List<Long>> outgoing, Set<Long> trunk,
            ScoringContext scoring) {
        var rows = new RowAssignments(scoring);
        for (long id : trunk) rows.put(id, 0);
        for (var branch : branches) {
            RowAssignments best = null;
            LayoutScore bestScore = null;
            int span = span(rows);
            for (int side : List.of(-1, 1)) for (int offset = 0; offset <= span; offset++) {
                var candidate = rows.copy();
                // The longest path is innermost; earlier nested forks go farther
                // out so their connectors do not cut through later descendants.
                for (int lane = 0; lane < branch.size(); lane++) {
                    int row = side * (offset + lane + 1);
                    branch.get(lane).forEach(id -> candidate.put(id, row));
                }
                var score = scoring.score(candidate, bestScore);
                if (bestScore == null || score.compareTo(bestScore) < 0) {
                    best = candidate;
                    bestScore = score;
                }
            }
            rows = best;
        }
        // Bounded local improvement: move or swap complete paths and flip whole
        // branches. Actual node/line geometry decides whether a lane can be reused.
        // No per-node crossing of the trunk, global rejection or permanent lane reservation.
        var paths = new ArrayList<List<Long>>();
        for (var branch : branches) for (var continuation : branch) {
            List<Long> path = null;
            Long previous = null;
            for (long id : continuation) {
                // Uninterrupted chains move together. At a real fork/merge the
                // continuation may change lanes if keeping it straight blocks a dependency.
                if (previous == null || outgoing.getOrDefault(previous, List.of()).size() != 1
                        || incoming.getOrDefault(id, List.of()).size() != 1
                        || incoming.getOrDefault(previous, List.of()).size() > 1
                        || outgoing.getOrDefault(id, List.of()).size() > 1) {
                    path = new ArrayList<>();
                    paths.add(path);
                }
                path.add(id);
                previous = id;
            }
        }
        var bestScore = scoring.score(rows, null);
        for (int pass = 0; pass < 4; pass++) {
            boolean improved = false;
            for (var branch : branches) {
                int side = Integer.signum(rows.get(branch.getFirst().getFirst()));
                var currentRows = rows;
                int inner = branch.stream().flatMap(List::stream).mapToInt(id -> Math.abs(currentRows.get(id))).min().orElse(1);
                int currentSpan = span(rows);
                for (int offset = 1 - inner; offset <= currentSpan; offset++) {
                    var candidate = rows.copy();
                    for (var path : branch) for (long id : path) candidate.put(id, -side * (Math.abs(rows.get(id)) + offset));
                    var candidateScore = scoring.score(candidate, bestScore);
                    if (candidateScore.compareTo(bestScore) < 0) {
                        rows = candidate;
                        bestScore = candidateScore;
                        improved = true;
                        break;
                    }
                }
            }
            for (var path : paths) {
                int current = rows.get(path.getFirst());
                int side = Integer.signum(current);
                int currentSpan = span(rows);
                for (int distance = 1; distance <= currentSpan + 1; distance++) {
                    int target = side * distance;
                    if (target == current) continue;
                    for (boolean insert : List.of(false, true)) {
                        var candidate = rows.copy();
                        if (insert) {
                            // Open a lane before moving into it. This allows two
                            // crowded fork tips to move together instead of getting
                            // stuck behind a temporarily worse one-path move.
                            for (var other : paths) {
                                int row = rows.get(other.getFirst());
                                if (other != path && Integer.signum(row) == side && Math.abs(row) >= distance) {
                                    other.forEach(id -> candidate.put(id, row + side));
                                }
                            }
                        }
                        path.forEach(id -> candidate.put(id, target));
                        var candidateScore = scoring.score(candidate, bestScore);
                        if (candidateScore.compareTo(bestScore) < 0) {
                            rows = candidate;
                            bestScore = candidateScore;
                            current = target;
                            improved = true;
                        }
                    }
                }
            }
            for (int a = 0; a < paths.size(); a++) for (int b = a + 1; b < paths.size(); b++) {
                var first = paths.get(a); var second = paths.get(b);
                int firstRow = rows.get(first.getFirst()), secondRow = rows.get(second.getFirst());
                if (firstRow == secondRow || Integer.signum(firstRow) != Integer.signum(secondRow)) continue;
                var candidate = rows.copy();
                first.forEach(id -> candidate.put(id, secondRow));
                second.forEach(id -> candidate.put(id, firstRow));
                var candidateScore = scoring.score(candidate, bestScore);
                if (candidateScore.compareTo(bestScore) < 0) {
                    rows = candidate;
                    bestScore = candidateScore;
                    improved = true;
                }
            }
            if (!improved) break;
        }
        compactRows(rows);
        return rows;
    }

    private static List<List<List<Long>>> branchPaths(List<Long> ids, List<Long> topoOrder,
            Map<Long, List<Long>> incoming, Map<Long, List<Long>> outgoing, Set<Long> trunk,
            Map<Long, Integer> order, Map<Long, Integer> layers) {
        var visited = new LinkedHashSet<>(trunk);
        var branches = new ArrayList<List<List<Long>>>();
        for (long start : ids) {
            if (!visited.add(start)) continue;
            var members = new LinkedHashSet<Long>();
            var pending = new ArrayDeque<Long>();
            pending.add(start);
            while (!pending.isEmpty()) {
                long id = pending.removeFirst();
                members.add(id);
                for (var neighbors : List.of(incoming.getOrDefault(id, List.of()), outgoing.getOrDefault(id, List.of()))) {
                    for (long neighbor : neighbors) {
                        // Same-column cyclic leftovers get a finite placement,
                        // without influencing the normal DAG's branch grouping.
                        if (!layers.get(id).equals(layers.get(neighbor)) && visited.add(neighbor)) pending.add(neighbor);
                    }
                }
            }
            var longest = new HashMap<Long, Integer>();
            for (long id : topoOrder.reversed()) {
                if (!members.contains(id)) continue;
                longest.put(id, 1 + outgoing.getOrDefault(id, List.of()).stream().filter(members::contains)
                        .mapToInt(child -> longest.getOrDefault(child, 0)).max().orElse(0));
            }
            var starts = members.stream().sorted(Comparator.<Long>comparingInt(layers::get)
                    .thenComparing(Comparator.<Long>comparingInt(id -> longest.getOrDefault(id, 1)).reversed())
                    .thenComparingInt(order::get)).toList();
            var primaryParent = new HashMap<Long, Long>();
            for (long id : members) incoming.getOrDefault(id, List.of()).stream().filter(members::contains)
                    .min(Comparator.<Long>comparingInt(parent -> (int) outgoing.getOrDefault(parent, List.of())
                                    .stream().filter(members::contains).count()).thenComparingInt(order::get))
                    .ifPresent(parent -> primaryParent.put(id, parent));
            var assigned = new LinkedHashSet<Long>();
            var paths = new ArrayList<List<Long>>();
            for (long root : starts) {
                if (assigned.contains(root)) continue;
                var path = new ArrayList<Long>();
                Long current = root;
                while (current != null && assigned.add(current)) {
                    path.add(current);
                    final long parent = current;
                    current = outgoing.getOrDefault(current, List.of()).stream()
                            .filter(child -> members.contains(child) && !assigned.contains(child) && layers.get(child) > layers.get(parent)
                                    && primaryParent.getOrDefault(child, parent) == parent)
                            .min(Comparator.<Long>comparingInt(id -> longest.getOrDefault(id, 1)).reversed()
                                    .thenComparingInt(order::get)).orElse(null);
                }
                paths.add(path);
            }
            var backbone = new LinkedHashSet<>(trunk);
            var placedPaths = new ArrayList<List<Long>>();
            for (var path : paths) {
                var segment = new ArrayList<Long>();
                for (long id : path) {
                    segment.add(id);
                    if (outgoing.getOrDefault(id, List.of()).stream().anyMatch(backbone::contains)) {
                        placedPaths.add(segment);
                        segment = new ArrayList<>();
                    }
                }
                if (!segment.isEmpty()) placedPaths.add(segment);
                backbone.addAll(path);
            }
            paths = placedPaths;
            if (paths.size() > 1) paths.subList(1, paths.size()).sort(Comparator
                    .<List<Long>>comparingInt(path -> layers.get(path.getFirst())).reversed()
                    .thenComparingInt(path -> order.get(path.getFirst())));
            branches.add(paths);
        }
        return branches;
    }

    private static int span(RowAssignments rows) {
        int span = 0;
        for (int row : rows.values) if (row != RowAssignments.UNASSIGNED) span = Math.max(span, Math.abs(row));
        return span;
    }

    /** Close unused lanes without changing any node's side or vertical order. */
    private static void compactRows(RowAssignments rows) {
        var negative = Arrays.stream(rows.values).filter(row -> row != RowAssignments.UNASSIGNED && row < 0).boxed().distinct().sorted(Comparator.reverseOrder()).toList();
        var positive = Arrays.stream(rows.values).filter(row -> row > 0).boxed().distinct().sorted().toList();
        var packed = new HashMap<Integer, Integer>();
        packed.put(0, 0);
        for (int index = 0; index < negative.size(); index++) packed.put(negative.get(index), -index - 1);
        for (int index = 0; index < positive.size(); index++) packed.put(positive.get(index), index + 1);
        for (int index = 0; index < rows.values.length; index++) {
            if (rows.values[index] != RowAssignments.UNASSIGNED) rows.values[index] = packed.get(rows.values[index]);
        }
    }

    /** Only row numbers vary between candidates; topology and ID lookup are shared. */
    private static final class RowAssignments {
        static final int UNASSIGNED = Integer.MIN_VALUE;
        private final ScoringContext context;
        private final int[] values;

        RowAssignments(ScoringContext context) {
            this.context = context;
            values = new int[context.ids.size()];
            Arrays.fill(values, UNASSIGNED);
        }

        private RowAssignments(RowAssignments original) {
            context = original.context;
            values = original.values.clone();
        }

        RowAssignments copy() { return new RowAssignments(this); }
        int get(long id) { return values[context.indices.get(id)]; }
        void put(long id, int row) { values[context.indices.get(id)] = row; }

        Map<Long, Integer> toMap() {
            var result = new HashMap<Long, Integer>();
            for (int index = 0; index < values.length; index++) {
                if (values[index] != UNASSIGNED) result.put(context.ids.get(index), values[index]);
            }
            return result;
        }
    }

    /** Shared immutable topology and reusable geometry for one layout search. */
    private static final class ScoringContext {
        private final List<Long> ids;
        private final Map<Long, Integer> indices = new HashMap<>();
        private final int[] layers, from, to, parentCounts;
        private final long[] occupied;
        private final QuestFlowchartRouting.Workspace routing;

        ScoringContext(List<Long> ids, Set<Edge> edges, Map<Long, Integer> layers) {
            this.ids = ids;
            this.layers = new int[ids.size()];
            parentCounts = new int[ids.size()];
            occupied = new long[ids.size()];
            for (int index = 0; index < ids.size(); index++) {
                indices.put(ids.get(index), index);
                this.layers[index] = layers.get(ids.get(index));
            }
            from = new int[edges.size()];
            to = new int[edges.size()];
            int index = 0;
            for (var edge : edges) {
                from[index] = indices.get(edge.fromQuestId());
                to[index] = indices.get(edge.toQuestId());
                parentCounts[to[index]]++;
                index++;
            }
            routing = new QuestFlowchartRouting.Workspace(ids.size(), from, to);
            for (index = 0; index < ids.size(); index++) {
                routing.x[index] = this.layers[index] * (NODE_WIDTH + COLUMN_GAP);
            }
        }

        LayoutScore score(RowAssignments assignment, LayoutScore incumbent) {
            int[] rows = assignment.values;
            int span = 0, distance = 0, occupiedCount = 0;
            for (int index = 0; index < ids.size(); index++) {
                int row = assignment.values[index];
                routing.active[index] = row != RowAssignments.UNASSIGNED;
                if (!routing.active[index]) continue;
                routing.y[index] = row * (NODE_HEIGHT + ROW_GAP);
                span = Math.max(span, Math.abs(row));
                distance += Math.abs(row);
                occupied[occupiedCount++] = ((long) layers[index] << 32) | (row & 0xffffffffL);
            }
            int inward = 0, length = 0;
            for (int edge = 0; edge < from.length; edge++) {
                int a = from[edge], b = to[edge];
                if (!routing.active[a] || !routing.active[b]) continue;
                length += Math.abs(rows[a] - rows[b]);
                if (rows[b] != 0 && parentCounts[b] == 1) {
                    inward += Math.max(0, Math.abs(rows[a]) - Math.abs(rows[b]));
                }
            }
            var lowerBound = new LayoutScore(0, 0, 0, span, inward, distance, length);
            if (incumbent != null && lowerBound.compareTo(incumbent) >= 0) return lowerBound;
            Arrays.sort(occupied, 0, occupiedCount);
            int overlaps = 0;
            for (int index = 1; index < occupiedCount; index++) {
                if (occupied[index] == occupied[index - 1]) overlaps++;
            }
            if (overlaps > 0) return new LayoutScore(overlaps, 0, 0, 0, 0, 0, 0);
            int maxHits = incumbent == null || incumbent.overlaps() > 0 ? Integer.MAX_VALUE : incumbent.nodeHits();
            if (!routing.evaluate(maxHits)) return new LayoutScore(0, maxHits + 1, 0, span, inward, distance, length);
            return new LayoutScore(0, routing.nodeHits, routing.crossingCount, span, inward, distance, length);
        }
    }

    private record LayoutScore(int overlaps, int nodeHits, int crossings, int span, int inward, int distance, int length)
            implements Comparable<LayoutScore> {
        @Override public int compareTo(LayoutScore other) {
            int result = Integer.compare(overlaps, other.overlaps);
            if (result == 0) result = Integer.compare(nodeHits, other.nodeHits);
            if (result == 0) result = Integer.compare(crossings, other.crossings);
            if (result == 0) result = Integer.compare(span, other.span);
            if (result == 0) result = Integer.compare(inward, other.inward);
            if (result == 0) result = Integer.compare(distance, other.distance);
            if (result == 0) result = Integer.compare(length, other.length);
            return result;
        }
    }

    public record Node(QuestLogSnapshot.GraphQuest quest, int x, int y, int layer, int row) {
        public int right() { return x + NODE_WIDTH; }
        public int bottom() { return y + NODE_HEIGHT; }
    }

    public record PositionedEdge(Node from, Node to) {}

    private record Edge(long fromQuestId, long toQuestId) {}

    public record Graph(List<Node> nodes, List<PositionedEdge> edges, int width, int height,
                        Long anchorQuestId, Long verticalAnchorQuestId,
                        Map<PositionedEdge, List<QuestFlowchartRouting.Point>> routes) {
        public Graph(List<Node> nodes, List<PositionedEdge> edges, int width, int height,
                     Long anchorQuestId, Long verticalAnchorQuestId) {
            this(nodes, edges, width, height, anchorQuestId, verticalAnchorQuestId, QuestFlowchartRouting.plan(nodes, edges));
        }
        public Graph {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            routes = Map.copyOf(routes);
        }

        public List<QuestFlowchartRouting.Point> route(PositionedEdge edge) {
            return routes.get(edge);
        }

        public Node node(long questId) {
            return nodes.stream().filter(node -> node.quest().questId() == questId).findFirst().orElse(null);
        }

        public double initialScrollX() {
            Node anchor = anchorQuestId == null ? null : node(anchorQuestId);
            if (anchor == null) return 0;
            return clamp(anchor.x() + NODE_WIDTH / 2d - VIEWPORT_WIDTH / 2d, 0, width - VIEWPORT_WIDTH);
        }

        public double initialScrollY() {
            if (verticalAnchorQuestId == null) return clamp(height / 2d - VIEWPORT_HEIGHT / 2d, 0, height - VIEWPORT_HEIGHT);
            Node anchor = node(verticalAnchorQuestId);
            if (anchor == null) return 0;
            return clamp(anchor.y() + NODE_HEIGHT / 2d - VIEWPORT_HEIGHT / 2d, 0, height - VIEWPORT_HEIGHT);
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
