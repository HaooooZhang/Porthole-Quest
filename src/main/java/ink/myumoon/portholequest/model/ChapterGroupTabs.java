package ink.myumoon.portholequest.model;

import java.util.ArrayList;
import java.util.List;

/** Integer slots relative to the centered 336x208 body. No renderer types. */
public final class ChapterGroupTabs {
    private ChapterGroupTabs() {}

    public enum Edge { LEFT, BOTTOM, TOP, RIGHT }

    public record Slot(GroupSelection selection, String title, int color, Edge edge,
                       int x, int y, int width, int height) {
        public boolean contains(double localX, double localY) {
            return localX >= x && localX < x + width && localY >= y && localY < y + height;
        }
    }

    public static List<Slot> layout(List<QuestLogSnapshot.Group> groups, String allTitle,
                                    int guiWidth, int guiHeight, double scale) {
        int left = (int) Math.floor((guiWidth - 336 * scale) / 2);
        int top = (int) Math.floor((guiHeight - 208 * scale) / 2);
        var result = new ArrayList<Slot>();
        int index = 0;
        for (Edge edge : Edge.values()) {
            boolean side = edge == Edge.LEFT || edge == Edge.RIGHT;
            int length = side ? 208 : 336;
            for (int offset = 16; offset + 24 <= length && index <= groups.size(); offset += 24) {
                int x = switch (edge) { case LEFT -> -24; case RIGHT -> 328; default -> offset; };
                int y = switch (edge) { case TOP -> -24; case BOTTOM -> 200; default -> offset; };
                int w = edge == Edge.LEFT || edge == Edge.RIGHT ? 32 : 24;
                int h = edge == Edge.LEFT || edge == Edge.RIGHT ? 24 : 32;
                // Side tabs keep their slots even when the window clips their outer pixels.
                if (!side && (left + x * scale < 0 || top + y * scale < 0
                        || left + (x + w) * scale > guiWidth || top + (y + h) * scale > guiHeight)) continue;
                var selection = index == 0 ? GroupSelection.ALL : GroupSelection.group(groups.get(index - 1).id());
                String title = index == 0 ? allTitle : groups.get(index - 1).title();
                int color = index == 0 ? -1 : Math.floorMod(groups.get(index - 1).fileOrder(), 6);
                result.add(new Slot(selection, title, color, edge, x, y, w, h));
                index++;
            }
        }
        return List.copyOf(result);
    }

    public static GroupSelection resolve(GroupSelection requested, List<Slot> slots) {
        return slots.stream().anyMatch(slot -> slot.selection().equals(requested)) ? requested : GroupSelection.ALL;
    }

    public static List<QuestLogSnapshot.Group> select(List<QuestLogSnapshot.Group> groups, GroupSelection selection) {
        return selection.all() ? groups : groups.stream().filter(group -> group.id() == selection.groupId()).toList();
    }
}
