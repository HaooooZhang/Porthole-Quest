package ink.myumoon.portholequest.simulator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ink.myumoon.portholequest.model.QuestFlowchartLayout;
import ink.myumoon.portholequest.model.QuestFlowchartCase;
import ink.myumoon.portholequest.model.QuestFlowchartRouting;
import ink.myumoon.portholequest.model.QuestLogSnapshot;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Loopback-only, dependency-free development server. No game state or files are modified. */
public final class QuestFlowchartSimulator {
    private static final int MAX_BODY = 100_000;

    public static void main(String[] args) throws IOException {
        Path html = Path.of(args.length > 0 ? args[0] : "src/simulator/web/index.html").toAbsolutePath();
        if (!Files.isRegularFile(html)) throw new IllegalArgumentException("Missing simulator page: " + html);
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 18333;
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                if (method.equals("GET") && path.equals("/")) {
                    respond(exchange, 200, "text/html; charset=utf-8", Files.readString(html));
                } else if (method.equals("GET") && path.equals("/api/scenarios")) {
                    respond(exchange, 200, "application/json; charset=utf-8", json(scenarios()));
                } else if (method.equals("POST") && path.equals("/api/layout")) {
                    // Only plain text from this tool's own origin; reject cross-origin form requests.
                    String origin = exchange.getRequestHeaders().getFirst("Origin");
                    String ownOrigin = "http://127.0.0.1:" + server.getAddress().getPort();
                    String host = exchange.getRequestHeaders().getFirst("Host");
                    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                    if (!("127.0.0.1:" + server.getAddress().getPort()).equals(host)
                            || origin != null && !origin.equals(ownOrigin)
                            || contentType == null || !contentType.startsWith("text/plain")) {
                        respond(exchange, 403, "text/plain", "Origin rejected");
                        return;
                    }
                    byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                    if (body.length > MAX_BODY) throw new IllegalArgumentException("输入不能超过 100 KB");
                    respond(exchange, 200, "application/json; charset=utf-8",
                            json(layout(new String(body, StandardCharsets.UTF_8))));
                } else {
                    respond(exchange, 404, "text/plain", "Not found");
                }
            } catch (IllegalArgumentException exception) {
                respond(exchange, 400, "application/json; charset=utf-8", json(Map.of("error", exception.getMessage())));
            } catch (Exception exception) {
                exception.printStackTrace();
                respond(exchange, 500, "application/json; charset=utf-8", json(Map.of("error", "布局失败，请查看终端日志")));
            } finally {
                exchange.close();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("Quest flowchart simulator: http://127.0.0.1:" + server.getAddress().getPort());
        System.out.println("Production QuestFlowchartLayout + QuestFlowchartRouting. Stop with Ctrl+C.");
    }

    private static void respond(HttpExchange exchange, int status, String type, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    static Map<String, Object> layout(String source) {
        var ids = new LinkedHashMap<String, Long>();
        var relations = new LinkedHashSet<Relation>();
        var external = new LinkedHashSet<String>();
        String focus = null;
        String[] lines = source.split("\\R");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].split("#", 2)[0].trim();
            if (line.isEmpty()) continue;
            try {
                if (line.startsWith("external:")) {
                    for (String name : names(line.substring(9))) { add(ids, name); external.add(name); }
                } else if (line.startsWith("focus:")) {
                    var names = names(line.substring(6));
                    if (names.size() != 1) throw new IllegalArgumentException("focus 只能指定一个任务");
                    focus = names.getFirst();
                } else {
                    String[] chain = line.split("->", -1);
                    List<String> previous = null;
                    for (String part : chain) {
                        var current = names(part);
                        for (String name : current) add(ids, name);
                        if (previous != null) for (String from : previous) for (String to : current) {
                            relations.add(new Relation(from, to));
                        }
                        previous = current;
                    }
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("第 " + (lineIndex + 1) + " 行：" + exception.getMessage());
            }
            if (ids.size() > 200 || relations.size() > 800) throw new IllegalArgumentException("最多支持 200 个节点、800 条依赖");
        }
        if (focus != null && !ids.containsKey(focus)) throw new IllegalArgumentException("聚焦任务不存在：" + focus);
        var entries = ids.entrySet().stream().filter(entry -> !external.contains(entry.getKey())).map(entry ->
                new QuestLogSnapshot.Entry(entry.getValue(), entry.getValue(), 100, entry.getKey(),
                        false, false, false, true, 0, false, false)).toList();
        var quests = ids.entrySet().stream().map(entry -> {
            String name = entry.getKey();
            return new QuestLogSnapshot.GraphQuest(entry.getValue(), external.contains(name) ? 200 : 100,
                    10, name, "{}", true, false, false, true, 0, false, false, entry.getValue().intValue(),
                    relations.stream().filter(edge -> edge.to.equals(name)).map(edge -> ids.get(edge.from)).toList(),
                    relations.stream().filter(edge -> edge.from.equals(name)).map(edge -> ids.get(edge.to)).toList());
        }).toList();
        var snapshot = new QuestLogSnapshot(1, new UUID(0, 1), false,
                List.of(new QuestLogSnapshot.Group(10, "模拟章节", 0,
                        List.of(new QuestLogSnapshot.Chapter(100, 10, "模拟章节", entries)))),
                Map.of(), Map.of(), Map.of(), "Simulator", "{}", quests);
        long start = System.nanoTime();
        var graph = focus == null ? QuestFlowchartLayout.forChapter(snapshot, 100)
                : QuestFlowchartLayout.forQuest(snapshot, 100, ids.get(focus));
        double elapsed = (System.nanoTime() - start) / 1_000_000d;
        var geometry = QuestFlowchartCase.geometry(graph, QuestLogSnapshot.GraphQuest::title, external);
        @SuppressWarnings("unchecked")
        var nodes = (List<Map<String, Object>>) geometry.get("nodes");
        var edges = graph.edges().stream().map(edge -> new Route(edge.from().quest().title(), edge.to().quest().title(),
                graph.route(edge))).toList();
        var result = new LinkedHashMap<String, Object>(geometry);
        result.put("nodeWidth", QuestFlowchartLayout.NODE_WIDTH);
        result.put("nodeHeight", QuestFlowchartLayout.NODE_HEIGHT);
        // Match the dialog's actual clip and content-bound clamping (layout anchor uses 300 units).
        int contentWidth = graph.nodes().stream().mapToInt(QuestFlowchartLayout.Node::right).max().orElse(280) + 12;
        int contentHeight = graph.nodes().stream().mapToInt(QuestFlowchartLayout.Node::bottom).max().orElse(162) + 12;
        result.put("viewport", Map.of("x", Math.min(graph.initialScrollX(), Math.max(0, contentWidth - 292)),
                "y", Math.min(graph.initialScrollY(), Math.max(0, contentHeight - QuestFlowchartLayout.VIEWPORT_HEIGHT)),
                "width", 292, "height", QuestFlowchartLayout.VIEWPORT_HEIGHT));
        result.put("elapsedMs", elapsed);
        result.put("issues", diagnose(edges, nodes));
        result.put("inputNodes", ids.size());
        result.put("inputEdges", relations.size());
        return result;
    }

    private static void add(Map<String, Long> ids, String name) {
        ids.computeIfAbsent(name, ignored -> (long) ids.size() + 1);
    }

    private static List<String> names(String part) {
        String value = part.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("缺少任务名称");
        var names = List.of(value.split("[\\s,，]+"));
        for (String name : names) {
            if (!name.matches("[\\p{L}\\p{N}_./-]{1,48}")) {
                throw new IllegalArgumentException("任务名称限 1–48 个字母、数字、中文或 _ . / -");
            }
        }
        return names;
    }

    /** Centerline diagnostics. Expected shared ports are excluded, shared runs are reported separately. */
    static List<Map<String, Object>> diagnose(List<Route> routes, List<Map<String, Object>> nodes) {
        var issues = new ArrayList<Map<String, Object>>();
        for (int a = 0; a < routes.size(); a++) {
            Route first = routes.get(a);
            var points = first.points;
            if (points.getLast().x() <= points.getFirst().x()) {
                issues.add(issue("backward", first.label(), "", points.getFirst().x(), points.getFirst().y()));
            }
            for (var node : nodes) {
                if (node.get("id").equals(first.from) || node.get("id").equals(first.to)) continue;
                int x = (int) node.get("x"), y = (int) node.get("y");
                for (int p = 1; p < points.size(); p++) {
                    var u = points.get(p - 1); var v = points.get(p);
                    boolean hit = u.y() == v.y()
                            ? u.y() > y && u.y() < y + QuestFlowchartLayout.NODE_HEIGHT && Math.max(u.x(), v.x()) > x
                                    && Math.min(u.x(), v.x()) < x + QuestFlowchartLayout.NODE_WIDTH
                            : u.x() > x && u.x() < x + QuestFlowchartLayout.NODE_WIDTH && Math.max(u.y(), v.y()) > y
                                    && Math.min(u.y(), v.y()) < y + QuestFlowchartLayout.NODE_HEIGHT;
                    if (hit) {
                        issues.add(issue("node", first.label(), (String) node.get("id"), x + 16, y + 16));
                        break;
                    }
                }
            }
            for (int b = a + 1; b < routes.size(); b++) {
                Route second = routes.get(b);
                Set<String> seen = new LinkedHashSet<>();
                for (int p = 1; p < points.size(); p++) for (int q = 1; q < second.points.size(); q++) {
                    var u = points.get(p - 1); var v = points.get(p);
                    var w = second.points.get(q - 1); var z = second.points.get(q);
                    if (u.equals(v) || w.equals(z)) continue;
                    boolean h1 = u.y() == v.y(), h2 = w.y() == z.y();
                    if (h1 == h2) {
                        int lo = h1 ? Math.max(Math.min(u.x(), v.x()), Math.min(w.x(), z.x()))
                                : Math.max(Math.min(u.y(), v.y()), Math.min(w.y(), z.y()));
                        int hi = h1 ? Math.min(Math.max(u.x(), v.x()), Math.max(w.x(), z.x()))
                                : Math.min(Math.max(u.y(), v.y()), Math.max(w.y(), z.y()));
                        boolean sameLine = h1 ? u.y() == w.y() : u.x() == w.x();
                        if (sameLine && hi > lo && seen.add("overlap")) {
                            issues.add(issue("overlap", first.label(), second.label(),
                                    h1 ? (lo + hi) / 2 : u.x(), h1 ? u.y() : (lo + hi) / 2));
                        }
                    } else {
                        var hStart = h1 ? u : w; var hEnd = h1 ? v : z;
                        var vStart = h1 ? w : u; var vEnd = h1 ? z : v;
                        int x = vStart.x(), y = hStart.y();
                        var at = new QuestFlowchartRouting.Point(x, y);
                        boolean withinH = x >= Math.min(hStart.x(), hEnd.x()) && x <= Math.max(hStart.x(), hEnd.x());
                        boolean withinV = y >= Math.min(vStart.y(), vEnd.y()) && y <= Math.max(vStart.y(), vEnd.y());
                        if (withinH && withinV && !sharedPort(first, second, at)) {
                            boolean proper = x > Math.min(hStart.x(), hEnd.x()) && x < Math.max(hStart.x(), hEnd.x())
                                    && y > Math.min(vStart.y(), vEnd.y()) && y < Math.max(vStart.y(), vEnd.y());
                            String kind = proper ? "cross" : "contact";
                            if (seen.add(kind + ":" + x + ":" + y)) {
                                issues.add(issue(kind, first.label(), second.label(), x, y));
                            }
                        }
                    }
                }
            }
        }
        return issues;
    }

    private static boolean sharedPort(Route a, Route b, QuestFlowchartRouting.Point at) {
        return a.from.equals(b.from) && at.equals(a.points.getFirst())
                || a.to.equals(b.to) && at.equals(a.points.getLast());
    }

    private static Map<String, Object> issue(String kind, String a, String b, int x, int y) {
        return Map.of("kind", kind, "a", a, "b", b, "x", x, "y", y);
    }

    static List<Map<String, String>> scenarios() {
        return List.of(
                scenario("prologue", "序章：新生 · 完整章节", "来自 4682B7B6F07CB5FD.snbt，全部 19 项可见时的章节顺序。F/I/J/N/O 是截图中的五节点分叉；它们在全章中是侧支，不是主干。", """
                        # ChapterEntryOrder: dependency level, then saved file order.
                        A # 238F287437BC103B oak_log
                        B # 447F97CDDF3D1F02 crafting_table
                        C # 570D80E772EB563F quest_journal
                        D # 6A1DF77C318DECB2 stone_pickaxe
                        E # 57F61FC1F7518218 iron_ingot
                        F # 66FCBF298D164CC7 checkmark
                        G # 0BDF7C6F4E123A55 xp
                        H # 05EA917794A1370C armor
                        I # 3F50D756E84B54BE checkmark
                        J # 06333D5C695981D8 checkmark
                        K # 6103A8DF8E1311A6 armadillo
                        L # 611FD3C1A0F80E49 plains
                        M # 3CAFAC00E57B3FCC diamond
                        N # 553C88346FBB84E3 checkmark
                        O # 21BC63C23EF0AD54 checkmark
                        P # 7CABDA35CDDF9048 checkmark
                        Q # 599EDFD20841E963 diamond_pickaxe
                        R # 3AE301E26926C185 obsidian
                        S # 6F9725BD6E96713B nether
                        A -> B -> C -> D
                        D -> E,F,G
                        E -> H -> M -> Q -> R -> S
                        F -> I,J
                        I -> N,O
                        G -> K,L
                        K,L -> P -> Q
                        """),
                scenario("simple-fork", "主干双分支", "A → B → D 为主干，A → C 与 B → E 为分支。", """
                        A -> B
                        A -> C
                        B -> D
                        B -> E
                        """),
                scenario("branches", "长主干与支树", "观察同侧支树是否挤在一起、汇入其他支路。", """
                        A -> B -> C -> D -> E -> F
                        A -> U1 -> U2 -> U3
                        B -> V1 -> V2
                        B -> W1 -> W2
                        C -> X1 -> X2
                        D -> Y1
                        """),
                scenario("staggered", "错开区段复用车道", "前段分支结束后，后段分支可复用同一车道；连接线也要保持畅通。", """
                        A -> B -> C -> D -> E -> F -> G -> H
                        A -> U1 -> U2
                        E -> V1 -> V2
                        B -> W1
                        F -> X1
                        """),
                scenario("compound", "长链与复杂支树", "检查长单链是否保持在复杂分支外侧，以及分支汇合处的交叉。", """
                        A -> B -> C -> D -> E -> F
                        A -> U1 -> U2 -> U3
                        B -> V1 -> V2
                        B -> W1 -> W2
                        B -> X1 -> X2
                        V1,W1,X1 -> M1 -> M2 -> E
                        """),
                scenario("diamond", "分叉后汇合", "主干上下的分支，在后续任务重新汇合。", """
                        A -> B -> C -> D -> E
                        A -> U1 -> U2 -> D
                        B -> L1 -> L2 -> E
                        """),
                scenario("parents", "多前置与多后续", "同一层多对多依赖，观察交叉和重叠线段。", """
                        A -> B -> C -> D -> E
                        A -> U,V,W
                        U,V -> P
                        V,W -> Q
                        P,Q -> E
                        """),
                scenario("long", "跨层长边", "长边会绕过中间层，检查是否穿过任务节点。", """
                        A -> B -> C -> D -> E -> F
                        A -> E
                        B -> F
                        A -> U -> V
                        V -> F
                        """),
                scenario("forest", "多棵树与孤立节点", "互不相连的根节点与任务。", """
                        A -> B -> C -> D
                        P -> Q -> R
                        X -> Y
                        Solo1
                        Solo2
                        """),
                scenario("cycle", "环与环后任务", "异常依赖也应有限布局；检查反向和同层连线。", """
                        Root -> A
                        A -> B -> C -> A
                        C -> Tail
                        Independent
                        """),
                scenario("external", "跨章节一跳邻居", "EXT2 不直接相邻，应被章节范围筛掉；聚焦只改变起始视口。", """
                        A -> B -> C -> D
                        EXT2 -> EXT1 -> A
                        C -> EXT3 -> EXT4
                        external: EXT1,EXT2,EXT3,EXT4
                        focus: C
                        """),
                scenario("wide", "宽扇出与宽汇入", "单个任务连接多条分支，分支在远处汇合。", """
                        Root -> P1,P2,P3,P4,P5,P6
                        P1 -> A1
                        P2 -> A2
                        P3 -> A3
                        P4 -> A4
                        P5 -> A5
                        P6 -> A6
                        A1,A2,A3,A4,A5,A6 -> Join
                        Join -> Tail
                        """),
                scenario("layered", "重复层级与长旁路", "多个节点汇入同一层，侧向支路又有跨层依赖。", """
                        A -> B -> C -> D -> E -> F
                        A -> P1 -> P2 -> D
                        B -> Q1 -> Q2 -> E
                        P2,Q1 -> R1 -> F
                        A -> Bypass -> F
                        """),
                scenario("dense", "稠密 DAG · 固定种子", "可复现的 30 节点样例，不承诺无交叉。", randomCase(42, 30)));
    }

    private static Map<String, String> scenario(String id, String title, String description, String source) {
        return Map.of("id", id, "title", title, "description", description, "source", source);
    }

    static String randomCase(long seed, int count) {
        return randomCase(seed, count, .10);
    }

    static String randomCase(long seed, int count, double probability) {
        var random = new Random(seed);
        var source = new StringBuilder("# seed=" + seed + "\n");
        for (int n = 1; n <= count; n++) source.append("Q").append(n).append('\n');
        for (int a = 1; a <= count; a++) for (int b = a + 1; b <= count; b++) {
            if (random.nextDouble() < probability) source.append("Q").append(a).append(" -> Q").append(b).append('\n');
        }
        return source.toString();
    }

    static Map<String, Integer> sweep(long baseSeed, int count, int cases, double probability) {
        long crosses = 0, contacts = 0, overlaps = 0, nodeHits = 0, backward = 0;
        int maxCrosses = 0, maxOverlaps = 0;
        for (int index = 0; index < cases; index++) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> issues = (List<Map<String, Object>>) layout(
                    randomCase(baseSeed + index * 104729L, count, probability)).get("issues");
            int localCrosses = 0, localOverlaps = 0;
            for (var issue : issues) switch ((String) issue.get("kind")) {
                case "cross" -> { crosses++; localCrosses++; }
                case "contact" -> contacts++;
                case "overlap" -> { overlaps++; localOverlaps++; }
                case "node" -> nodeHits++;
                case "backward" -> backward++;
            }
            maxCrosses = Math.max(maxCrosses, localCrosses);
            maxOverlaps = Math.max(maxOverlaps, localOverlaps);
        }
        return Map.of("cases", cases, "nodesPerCase", count,
                "meanEdgesPerCase", (int) Math.round(probability * count * (count - 1) / 2),
                "crosses", (int) crosses, "contacts", (int) contacts, "overlaps", (int) overlaps,
                "nodeHits", (int) nodeHits, "backward", (int) backward,
                "maxCrossesInOneCase", maxCrosses, "maxOverlapsInOneCase", maxOverlaps);
    }

    record Relation(String from, String to) {}
    record Route(String from, String to, List<QuestFlowchartRouting.Point> points) {
        String label() { return from + " → " + to; }
    }

    // Small JSON writer keeps the simulator independent of the game/Gson runtime.
    static String json(Object value) {
        if (value instanceof String string) {
            var out = new StringBuilder("\"");
            for (char c : string.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> { if (c < 32) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
                }
            }
            return out.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream()
                .map(entry -> json(entry.getKey().toString()) + ":" + json(entry.getValue())).toList()) + "}";
        if (value instanceof Iterable<?> values) {
            var parts = new ArrayList<String>();
            values.forEach(item -> parts.add(json(item)));
            return "[" + String.join(",", parts) + "]";
        }
        return String.valueOf(value);
    }
}
