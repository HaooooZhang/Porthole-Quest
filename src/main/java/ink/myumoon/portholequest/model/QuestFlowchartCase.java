package ink.myumoon.portholequest.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Reproducible diagnostic input and exact rendered geometry; never used to restore UI state. */
public final class QuestFlowchartCase {
    private QuestFlowchartCase() {}

    public static Map<String, Object> capture(QuestLogSnapshot snapshot, long chapterId,
                                              QuestFlowchartLayout.Graph graph) {
        var chapterIds = new LinkedHashSet<Long>();
        for (var group : snapshot.groups()) for (var chapter : group.chapters()) {
            if (chapter.id() == chapterId) chapter.entries().forEach(entry -> chapterIds.add(entry.questId()));
        }
        Function<QuestLogSnapshot.GraphQuest, String> name = quest -> "Q" + Long.toUnsignedString(quest.questId(), 16);
        var source = new StringBuilder("# layout " + QuestFlowchartLayout.REVISION + "\n");
        var external = new LinkedHashSet<String>();
        graph.nodes().stream().sorted(Comparator.comparingInt(node -> node.quest().stableOrder()))
                .forEach(node -> {
                    source.append(name.apply(node.quest())).append(" # ")
                            .append(node.quest().title().replace('\n', ' ').replace('\r', ' ')).append('\n');
                    if (!chapterIds.contains(node.quest().questId())) external.add(name.apply(node.quest()));
                });
        for (var edge : graph.edges()) source.append(name.apply(edge.from().quest())).append(" -> ")
                .append(name.apply(edge.to().quest())).append('\n');
        if (!external.isEmpty()) source.append("external: ").append(String.join(",", external)).append('\n');
        if (graph.verticalAnchorQuestId() != null) {
            source.append("focus: ").append(name.apply(graph.node(graph.verticalAnchorQuestId()).quest())).append('\n');
        }
        return Map.of("format", "portholequest-flowchart-v1", "title", "游戏流程图 · " + chapterId,
                "source", source.toString(), "result", geometry(graph, name, external));
    }

    public static Map<String, Object> geometry(QuestFlowchartLayout.Graph graph,
            Function<QuestLogSnapshot.GraphQuest, String> name, Set<String> external) {
        var result = new LinkedHashMap<String, Object>();
        result.put("revision", QuestFlowchartLayout.REVISION);
        result.put("nodes", graph.nodes().stream().map(node -> Map.<String, Object>of(
                "id", name.apply(node.quest()), "x", node.x(), "y", node.y(), "layer", node.layer(),
                "row", node.row(), "external", external.contains(name.apply(node.quest())))).toList());
        result.put("edges", graph.edges().stream().map(edge -> Map.of(
                "from", name.apply(edge.from().quest()), "to", name.apply(edge.to().quest()), "points",
                graph.route(edge).stream()
                        .map(point -> List.of(point.x(), point.y())).toList())).toList());
        result.put("width", graph.width());
        result.put("height", graph.height());
        return result;
    }
}
