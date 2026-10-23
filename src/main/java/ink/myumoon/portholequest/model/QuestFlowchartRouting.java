package ink.myumoon.portholequest.model;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plans single-elbow, center-aligned routes once, shared by layout, game and simulator. */
public final class QuestFlowchartRouting {
    private QuestFlowchartRouting() {}

    public static Map<QuestFlowchartLayout.PositionedEdge, List<Point>> plan(
            List<QuestFlowchartLayout.Node> nodes, List<QuestFlowchartLayout.PositionedEdge> edges) {
        return plan(nodes, edges, Integer.MAX_VALUE);
    }

    /** Reject losing layout candidates before optimizing their elbow crossings. */
    static Map<QuestFlowchartLayout.PositionedEdge, List<Point>> plan(
            List<QuestFlowchartLayout.Node> nodes, List<QuestFlowchartLayout.PositionedEdge> edges, int maxNodeHits) {
        var indices = new IdentityHashMap<QuestFlowchartLayout.Node, Integer>();
        for (int index = 0; index < nodes.size(); index++) indices.put(nodes.get(index), index);
        int[] from = new int[edges.size()], to = new int[edges.size()];
        for (int index = 0; index < edges.size(); index++) {
            from[index] = indices.get(edges.get(index).from());
            to[index] = indices.get(edges.get(index).to());
        }
        var workspace = new Workspace(nodes.size(), from, to);
        for (int index = 0; index < nodes.size(); index++) {
            workspace.x[index] = nodes.get(index).x();
            workspace.y[index] = nodes.get(index).y();
            workspace.active[index] = true;
        }
        if (!workspace.evaluate(maxNodeHits)) return null;
        var result = new LinkedHashMap<QuestFlowchartLayout.PositionedEdge, List<Point>>();
        for (int index = 0; index < edges.size(); index++) result.put(edges.get(index), workspace.points(index));
        return java.util.Collections.unmodifiableMap(result);
    }

    /** Per-layout scratch space. Candidate scoring creates no Point lists or route maps. */
    static final class Workspace {
        final int[] x, y;
        final boolean[] active;
        private final int[] from, to, coordinates, sizes, hitCounts, selected;
        private final int[] horizontalMin, horizontalMax, horizontalY, verticalMin, verticalMax, verticalX;
        private final boolean[] horizontal, vertical;
        private final boolean[] present, diagonal;
        int nodeHits, crossingCount;

        Workspace(int nodeCount, int[] from, int[] to) {
            x = new int[nodeCount];
            y = new int[nodeCount];
            active = new boolean[nodeCount];
            this.from = from;
            this.to = to;
            coordinates = new int[from.length * 12];
            sizes = new int[from.length * 2];
            hitCounts = new int[from.length * 2];
            selected = new int[from.length];
            present = new boolean[from.length];
            diagonal = new boolean[from.length];
            horizontalMin = new int[from.length * 2];
            horizontalMax = new int[from.length * 2];
            horizontalY = new int[from.length * 2];
            verticalMin = new int[from.length * 2];
            verticalMax = new int[from.length * 2];
            verticalX = new int[from.length * 2];
            horizontal = new boolean[from.length * 2];
            vertical = new boolean[from.length * 2];
        }

        boolean evaluate(int maxNodeHits) {
            nodeHits = crossingCount = 0;
            for (int edge = 0; edge < from.length; edge++) {
                present[edge] = active[from[edge]] && active[to[edge]];
                if (!present[edge]) continue;
                int sx = x[from[edge]] + QuestFlowchartLayout.NODE_WIDTH / 2;
                int sy = y[from[edge]] + QuestFlowchartLayout.NODE_HEIGHT / 2;
                int ex = x[to[edge]] + QuestFlowchartLayout.NODE_WIDTH / 2;
                int ey = y[to[edge]] + QuestFlowchartLayout.NODE_HEIGHT / 2;
                int first = edge * 2;
                diagonal[edge] = sx != ex && sy != ey;
                route(first, sx, sy, ex, ey, true, diagonal[edge]);
                hitCounts[first] = hits(first, edge);
                selected[edge] = first;
                if (diagonal[edge]) {
                    route(first + 1, sx, sy, ex, ey, false, true);
                    hitCounts[first + 1] = hits(first + 1, edge);
                    if (hitCounts[first + 1] < hitCounts[first]) selected[edge]++;
                }
                nodeHits += hitCounts[selected[edge]];
                if (nodeHits > maxNodeHits) return false;
            }
            // Preserve the original forward/backward passes and strict tie handling.
            for (int pass = 0; pass < 2; pass++) for (int step = 0; step < from.length; step++) {
                int edge = pass == 0 ? step : from.length - step - 1;
                if (!present[edge] || !diagonal[edge] || hitCounts[edge * 2] != hitCounts[edge * 2 + 1]) continue;
                int current = selected[edge], alternate = current ^ 1;
                int currentCrosses = 0, alternateCrosses = 0;
                for (int other = 0; other < from.length; other++) {
                    if (other == edge || !present[other]) continue;
                    currentCrosses += crosses(current, selected[other]);
                    alternateCrosses += crosses(alternate, selected[other]);
                }
                if (alternateCrosses < currentCrosses) selected[edge] = alternate;
            }
            for (int edge = 0; edge < from.length; edge++) if (present[edge]) {
                for (int other = 0; other < edge; other++) if (present[other]) {
                    crossingCount += crosses(selected[edge], selected[other]);
                }
            }
            return true;
        }

        private void route(int route, int sx, int sy, int ex, int ey, boolean horizontalFirst, boolean elbow) {
            int base = route * 6;
            sizes[route] = elbow ? 3 : 2;
            coordinates[base] = sx;
            coordinates[base + 1] = sy;
            if (elbow) {
                coordinates[base + 2] = horizontalFirst ? ex : sx;
                coordinates[base + 3] = horizontalFirst ? sy : ey;
            }
            int end = base + (sizes[route] - 1) * 2;
            coordinates[end] = ex;
            coordinates[end + 1] = ey;
            // Clip center-to-center geometry to each endpoint's border center.
            if (sx != ex || sy != ey) {
                int endTowardX = coordinates[end - 2], endTowardY = coordinates[end - 1];
                clip(base, coordinates[base + 2], coordinates[base + 3]);
                clip(end, endTowardX, endTowardY);
            }
            horizontal[route] = vertical[route] = false;
            for (int segment = 1; segment < sizes[route]; segment++) {
                int p = base + (segment - 1) * 2, q = p + 2;
                if (coordinates[p + 1] == coordinates[q + 1]) {
                    int min = Math.min(coordinates[p], coordinates[q]), max = Math.max(coordinates[p], coordinates[q]);
                    horizontalMin[route] = horizontal[route] ? Math.min(horizontalMin[route], min) : min;
                    horizontalMax[route] = horizontal[route] ? Math.max(horizontalMax[route], max) : max;
                    horizontal[route] = true;
                    horizontalY[route] = coordinates[p + 1];
                } else {
                    vertical[route] = true;
                    verticalMin[route] = Math.min(coordinates[p + 1], coordinates[q + 1]);
                    verticalMax[route] = Math.max(coordinates[p + 1], coordinates[q + 1]);
                    verticalX[route] = coordinates[p];
                }
            }
        }

        private void clip(int endpoint, int towardX, int towardY) {
            if (coordinates[endpoint] != towardX) {
                coordinates[endpoint] += Integer.signum(towardX - coordinates[endpoint]) * QuestFlowchartLayout.NODE_WIDTH / 2;
            } else {
                coordinates[endpoint + 1] += Integer.signum(towardY - coordinates[endpoint + 1]) * QuestFlowchartLayout.NODE_HEIGHT / 2;
            }
        }

        private int hits(int route, int edge) {
            int count = 0;
            for (int node = 0; node < x.length; node++) {
                if (!active[node] || node == from[edge] || node == to[edge]) continue;
                if (horizontal[route] && horizontalY[route] > y[node]
                        && horizontalY[route] < y[node] + QuestFlowchartLayout.NODE_HEIGHT
                        && horizontalMax[route] > x[node]
                        && horizontalMin[route] < x[node] + QuestFlowchartLayout.NODE_WIDTH
                        || vertical[route] && verticalX[route] > x[node]
                        && verticalX[route] < x[node] + QuestFlowchartLayout.NODE_WIDTH
                        && verticalMax[route] > y[node]
                        && verticalMin[route] < y[node] + QuestFlowchartLayout.NODE_HEIGHT) count++;
            }
            return count;
        }

        private int crosses(int a, int b) {
            int count = 0;
            if (vertical[a] && horizontal[b] && verticalX[a] > horizontalMin[b] && verticalX[a] < horizontalMax[b]
                    && horizontalY[b] > verticalMin[a] && horizontalY[b] < verticalMax[a]) count++;
            if (horizontal[a] && vertical[b] && verticalX[b] > horizontalMin[a] && verticalX[b] < horizontalMax[a]
                    && horizontalY[a] > verticalMin[b] && horizontalY[a] < verticalMax[b]) count++;
            return count;
        }

        private List<Point> points(int edge) {
            int route = selected[edge], base = route * 6;
            var first = new Point(coordinates[base], coordinates[base + 1]);
            var second = new Point(coordinates[base + 2], coordinates[base + 3]);
            return sizes[route] == 2 ? List.of(first, second)
                    : List.of(first, second, new Point(coordinates[base + 4], coordinates[base + 5]));
        }
    }

    public record Point(int x, int y) {}
}
