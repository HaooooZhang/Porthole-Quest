package ink.myumoon.portholequest.client.ui;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import dev.ftb.mods.ftblibrary.icon.Icon;
import ink.myumoon.portholequest.Config;
import ink.myumoon.portholequest.client.screen.ScreenViewState;
import ink.myumoon.portholequest.model.QuestLogSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Read-only Quest Detail body. Actions remain disabled until the later action slices. */
public final class QuestDetailView {
    private final List<IconPlacement> icons;
    private final Element content;
    private final List<TooltipTarget> tooltipTargets;
    private final List<TextPlacement> descriptionText;
    private final Element descriptionContainer;
    private final Element expandButton;
    private final TextPlacement descriptionPlacement;
    private final int previewRows;
    private boolean descriptionExpanded;
    private Style hoveredDescriptionStyle;

    private QuestDetailView(List<IconPlacement> icons, Element content, List<TooltipTarget> tooltipTargets,
                            List<TextPlacement> descriptionText, Element descriptionContainer,
                            Element expandButton, TextPlacement descriptionPlacement,
                            int previewRows, boolean descriptionExpanded) {
        this.icons = List.copyOf(icons);
        this.content = content;
        this.tooltipTargets = List.copyOf(tooltipTargets);
        this.descriptionText = List.copyOf(descriptionText);
        this.descriptionContainer = descriptionContainer;
        this.expandButton = expandButton;
        this.descriptionPlacement = descriptionPlacement;
        this.previewRows = previewRows;
        this.descriptionExpanded = descriptionExpanded;
    }

    public static QuestDetailView bind(Document document, QuestLogSnapshot snapshot, ScreenViewState state,
                                       BooleanSupplier currentBinding,
                                       Consumer<QuestLogSnapshot.Entry> openQuest,
                                       Consumer<Long> openDescription,
                                       BiConsumer<Long, Boolean> setDescriptionExpanded,
                                       boolean descriptionExpanded) {
        Element panel = require(document, "#detail-panel");
        if (state.kind() != ScreenViewState.Kind.QUEST) return emptyView();
        Element content = document.createElement("div");
        content.setAttribute("id", "detail-content");
        content.setAttribute("aria-label", "quest-detail");
        int headerHeight = headerHeight(panel);
        content.setInlineStyleProperty("top", headerHeight + "px");
        content.setInlineStyleProperty("height", Math.max(0, 176 - headerHeight) + "px");
        content = panel.appendChild(content);

        QuestLogSnapshot.QuestDetail detail = snapshot.questDetails().get(state.questId());
        if (detail == null) {
            append(document, content, "detail-empty", Component.translatable("ui.portholequest.detail_unavailable").getString(), 0);
            return new QuestDetailView(List.of(), content, List.of(), List.of(), null, null, null, 0, false);
        }

        var icons = new ArrayList<IconPlacement>();
        var tooltipTargets = new ArrayList<TooltipTarget>();
        var descriptionText = new ArrayList<TextPlacement>();
        Element descriptionContainer = null;
        Element expandButton = null;
        TextPlacement descriptionPlacement = null;
        int descriptionRows = 0;
        if (detail.descriptionHidden()) {
            append(document, content, "detail-note", Component.translatable("ui.portholequest.description_hidden").getString(), 0);
        } else if (!detail.description().overviewParts().isEmpty() || detail.description().hasImages() || detail.paginated()) {
            append(document, content, "detail-section-label", Component.translatable("ui.portholequest.description").getString(), 0);
            Element description = append(document, content, "quest-description", "", 0);
            descriptionContainer = description;
            Font font = Minecraft.getInstance().font;
            int previewLimit = Config.DESCRIPTION_PREVIEW_LINES.get();
            Component component = DescriptionTextLayout.compose(detail.description().overviewParts());
            List<FormattedCharSequence> wrapped = DescriptionTextLayout.wrap(component, font, 178);
            descriptionRows = wrapped.size();
            int visible = descriptionExpanded ? descriptionRows : Math.min(descriptionRows, previewLimit);
            if (visible > 0) {
                Element anchor = append(document, description, "quest-description-line", "", 0);
                anchor.setInlineStyleProperty("height", (visible * 9) + "px");
                descriptionPlacement = new TextPlacement(component, wrapped, visible, anchor);
                descriptionText.add(descriptionPlacement);
            }
            int displayedRows = descriptionExpanded ? descriptionRows : Math.min(descriptionRows, previewLimit);
            description.setInlineStyleProperty("height", Math.max(3, displayedRows * 9 + 6) + "px");
            boolean mediaOrPages = detail.description().hasImages() || detail.paginated();
            int dialogLimit = Math.max(previewLimit + 1, Config.DESCRIPTION_DIALOG_LINES.get());
            boolean openDialog = QuestLogSnapshot.descriptionNeedsDialog(
                    detail.description().hasImages(), detail.paginated(), descriptionRows, dialogLimit);
            if (mediaOrPages || descriptionRows > previewLimit) {
                String action = openDialog ? "read_description"
                        : descriptionExpanded ? "collapse_description" : "expand_description";
                Element expand = append(document, content, "detail-expand-button",
                        Component.translatable("ui.portholequest." + action).getString(), state.questId());
                expandButton = expand;
                expand.setAttribute("aria-expanded", Boolean.toString(descriptionExpanded));
                expand.setAttribute("role", "button");
                expand.addEventListener("click", event -> {
                    if (!currentBinding.getAsBoolean() || !(event instanceof MouseEvent mouse) || mouse.button != 0) return;
                    if (openDialog) openDescription.accept(state.questId());
                    else setDescriptionExpanded.accept(state.questId(),
                            !Boolean.parseBoolean(expand.getAttribute("aria-expanded")));
                });
            }
        }

        append(document, content, "detail-section-label", Component.translatable("ui.portholequest.tasks").getString(), 0);
        if (detail.tasks().isEmpty()) {
            append(document, content, "detail-note", Component.translatable("ui.portholequest.no_tasks").getString(), 0);
        } else {
            for (var task : detail.tasks()) {
                Element row = append(document, content, "quest-task-row", "", task.id());
                row.setAttribute("data-task-id", Long.toUnsignedString(task.id(), 16));
                row.setAttribute("data-task-type", task.typeId());
                Element icon = append(document, row, "detail-inline-icon", "", 0);
                icons.add(new IconPlacement(task.iconJson(), icon));
                append(document, row, "quest-task-title", task.title(), 0);
                String progress = task.progressHidden()
                        ? (task.completed() ? Component.translatable("ui.portholequest.complete").getString()
                        : Component.translatable("ui.portholequest.incomplete").getString())
                        : task.progress() + "/" + task.maxProgress();
                append(document, row, "quest-task-progress", progress, 0);
                String action = task.automatic()
                        ? Component.translatable("ui.portholequest.task_automatic").getString()
                        : task.completed()
                        ? Component.translatable("ui.portholequest.complete").getString()
                        : Component.translatable("ui.portholequest.task_submit").getString();
                Element actionElement = append(document, row, "quest-task-action", action, 0);
                actionElement.setAttribute("aria-disabled", "true");
                actionElement.setAttribute("data-action", task.automatic() ? "automatic" : "submit");
                tooltipTargets.add(new TooltipTarget(row, List.of(
                        Component.literal(task.title()),
                        Component.literal(task.typeTitle()).withStyle(net.minecraft.ChatFormatting.GRAY),
                        Component.literal(progress).withStyle(net.minecraft.ChatFormatting.GRAY))));
            }
        }

        append(document, content, "detail-section-label", Component.translatable("ui.portholequest.rewards").getString(), 0);
        if (detail.rewards().isEmpty()) {
            append(document, content, "detail-note", Component.translatable("ui.portholequest.no_rewards").getString(), 0);
        } else {
            Element grid = append(document, content, "quest-reward-grid", "", 0);
            for (var reward : detail.rewards()) {
                Element card = append(document, grid, "quest-reward-card reward-" + reward.state().name().toLowerCase(), "", reward.id());
                card.setAttribute("data-reward-id", Long.toUnsignedString(reward.id(), 16));
                card.setAttribute("data-reward-type", reward.typeId());
                Element icon = append(document, card, "detail-inline-icon", "", 0);
                icons.add(new IconPlacement(reward.iconJson(), icon));
                append(document, card, "quest-reward-title", reward.title(), 0);
                String rewardState = switch (reward.state()) {
                    case CAN_CLAIM -> Component.translatable("ui.portholequest.reward_can_claim").getString();
                    case CLAIMED -> Component.translatable("ui.portholequest.reward_claimed").getString();
                    case CANT_CLAIM -> Component.translatable("ui.portholequest.reward_locked").getString();
                };
                append(document, card, "quest-reward-state", reward.automatic()
                        ? Component.translatable("ui.portholequest.reward_automatic").getString() : rewardState, 0);
                tooltipTargets.add(new TooltipTarget(card, List.of(
                        Component.literal(reward.title()),
                        Component.literal(reward.typeTitle()).withStyle(net.minecraft.ChatFormatting.GRAY),
                        Component.literal(reward.automatic()
                                ? Component.translatable("ui.portholequest.reward_automatic").getString() : rewardState)
                                .withStyle(net.minecraft.ChatFormatting.GRAY))));
            }
        }
        return new QuestDetailView(icons, content, tooltipTargets, descriptionText, descriptionContainer,
                expandButton, descriptionPlacement, Config.DESCRIPTION_PREVIEW_LINES.get(), descriptionExpanded);
    }

    private static QuestDetailView emptyView() {
        return new QuestDetailView(List.of(), null, List.of(), List.of(), null, null, null, 0, false);
    }

    public void setDescriptionExpanded(boolean expanded) {
        if (descriptionContainer == null || descriptionPlacement == null || expandButton == null
                || descriptionExpanded == expanded) return;
        descriptionExpanded = expanded;
        int rows = expanded ? descriptionPlacement.allLines().size()
                : Math.min(previewRows, descriptionPlacement.allLines().size());
        descriptionPlacement.setVisibleRows(rows);
        descriptionPlacement.anchor().setInlineStyleProperty("height", (rows * 9) + "px");
        descriptionContainer.setInlineStyleProperty("height", Math.max(3, rows * 9 + 6) + "px");
        expandButton.setTextContent(Component.translatable(expanded
                ? "ui.portholequest.collapse_description"
                : "ui.portholequest.expand_description").getString());
        expandButton.setAttribute("aria-expanded", Boolean.toString(expanded));
    }

    public void draw(GuiGraphics graphics, int left, int top, float scale) {
        if (content == null || icons.isEmpty() && descriptionText.isEmpty()) return;
        var pose = graphics.pose();
        pose.pushPose();
        try {
            var clip = content.getBoundingClientRect();
            graphics.enableScissor((int) Math.floor(left + clip.left * scale),
                    (int) Math.floor(top + clip.top * scale),
                    (int) Math.ceil(left + clip.right * scale),
                    (int) Math.ceil(top + clip.bottom * scale));
            try {
                pose.translate(left, top, 300);
                pose.scale(scale, scale, 1);
                for (var placement : icons) {
                    var rect = placement.anchor().getBoundingClientRect();
                    pose.pushPose();
                    pose.translate((float) rect.x, (float) rect.y, 0);
                    placement.icon().draw(graphics, 0, 0, 16, 16);
                    pose.popPose();
                }
                for (var placement : descriptionText) {
                    var rect = placement.anchor().getBoundingClientRect();
                    for (int lineIndex = 0; lineIndex < placement.visibleRows(); lineIndex++) {
                        FormattedCharSequence line = placement.allLines().get(lineIndex);
                        ClickEvent hoveredClick = hoveredDescriptionStyle == null
                                ? null : hoveredDescriptionStyle.getClickEvent();
                        line = DescriptionTextLayout.highlightLink(line, hoveredClick);
                        pose.pushPose();
                        pose.translate((float) rect.x, (float) rect.y + lineIndex * 9, 0);
                        graphics.drawString(Minecraft.getInstance().font, line, 0, 0, 0xff423027, false);
                        pose.popPose();
                    }
                }
            } finally {
                graphics.disableScissor();
            }
            graphics.flush();
        } finally {
            pose.popPose();
        }
    }

    public List<Component> tooltipAt(double x, double y) {
        if (content == null || !contains(content.getBoundingClientRect(), x, y)) return List.of();
        for (var target : tooltipTargets) {
            if (contains(target.element().getBoundingClientRect(), x, y)) return target.lines();
        }
        return List.of();
    }

    public Style styleAt(double x, double y) {
        for (var placement : descriptionText) {
            var rect = placement.anchor().getBoundingClientRect();
            if (!contains(rect, x, y)) continue;
            int row = Math.max(0, (int) ((y - rect.top) / 9));
            if (row >= placement.visibleRows()) return null;
            return DescriptionTextLayout.styleAtWidth(placement.allLines().get(row),
                    Math.max(0, x - rect.left), Minecraft.getInstance().font);
        }
        return null;
    }

    public void setHoveredDescriptionStyle(Style style) {
        hoveredDescriptionStyle = style != null && style.getClickEvent() != null ? style : null;
    }

    public boolean isDescriptionAt(double x, double y) {
        for (var placement : descriptionText) {
            if (contains(placement.anchor().getBoundingClientRect(), x, y)) return true;
        }
        return false;
    }

    private static final class TextPlacement {
        private final Component component;
        private final List<FormattedCharSequence> allLines;
        private final Element anchor;
        private int visibleRows;

        private TextPlacement(Component component, List<FormattedCharSequence> allLines, int visibleRows, Element anchor) {
            this.component = component;
            this.allLines = List.copyOf(allLines);
            this.visibleRows = visibleRows;
            this.anchor = anchor;
        }

        private List<FormattedCharSequence> allLines() { return allLines; }
        private Element anchor() { return anchor; }
        private int visibleRows() { return visibleRows; }
        private void setVisibleRows(int visibleRows) { this.visibleRows = visibleRows; }
        private Component component() { return component; }
    }

    private record IconPlacement(Icon icon, Element anchor) {
        private IconPlacement(String json, Element anchor) {
            this(Icon.getIcon(com.google.gson.JsonParser.parseString(json)), anchor);
        }
    }

    private record TooltipTarget(Element element, List<Component> lines) {
        private TooltipTarget {
            lines = List.copyOf(lines);
        }
    }

    private static boolean contains(Element.DOMRect rect, double x, double y) {
        return x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom;
    }

    private static int headerHeight(Element panel) {
        try {
            return Integer.parseInt(panel.getAttribute("data-detail-header-height"));
        } catch (NumberFormatException ignored) {
            return 64;
        }
    }

    private static Element append(Document document, Element parent, String cssClass, String text, long id) {
        Element element = document.createElement("div");
        element.setAttribute("class", cssClass);
        element.setAttribute("data-entry-id", Long.toUnsignedString(id, 16));
        element.setTextContent(text);
        return parent.appendChild(element);
    }

    private static Element require(Document document, String selector) {
        Element element = document.querySelector(selector);
        if (element == null) throw new IllegalStateException("Missing journal element: " + selector);
        return element;
    }
}
