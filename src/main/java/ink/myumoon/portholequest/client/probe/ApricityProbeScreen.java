package ink.myumoon.portholequest.client.probe;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.Locale;

public final class ApricityProbeScreen extends ApricityScreen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private Document boundDocument;
    private long boundGeneration = -1;
    private int clickCount;
    private int keyCount;
    private final String templatePath;
    private String lastScaleMetrics;
    private int scaleCheckTicks;
    private String lastFontMetrics;
    private static final int[] FONT_SIZES = {8, 9, 12, 18};
    private static final String FONT_SAMPLE = "日志 Ab1";
    private final ItemStack referenceDiamond = new ItemStack(Items.DIAMOND);

    public ApricityProbeScreen() {
        this("screens/probe.html");
    }

    public ApricityProbeScreen(String templatePath) {
        super(templatePath);
        this.templatePath = templatePath;
        setPauseGame(true);
    }

    @Override
    protected void init() {
        super.init();
        bindCurrentDocument("screen init");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (isFontProbe()) {
            renderNativeFontReference(graphics);
            return;
        }
        // Vanilla rendering is an independent reference; no AUI viewport transform is applied here.
        boolean fixedProbe = templatePath.equals("screens/probe_fixed.html");
        // The fixed page reserves this box beside the AUI samples, in the same GUI coordinate space.
        int x = fixedProbe ? 261 : width - 108;
        int y = fixedProbe ? 80 : height - 54;
        graphics.fill(x, y, x + 104, y + 50, 0xFF17282B);
        graphics.drawString(font, "MC native", x + 4, y + 4, 0xFFFFFF, false);
        graphics.renderItem(referenceDiamond, x + 6, y + 17);
        graphics.fill(x + 36, y + 17, x + 37, y + 29, 0xFFB6533D);
        graphics.fill(x + 42, y + 17, x + 44, y + 29, 0xFFB6533D);
        graphics.fill(x + 49, y + 17, x + 53, y + 29, 0xFFB6533D);
        graphics.fill(x + 58, y + 17, x + 66, y + 29, 0xFFB6533D);
        graphics.drawString(font, "16px  | 1/2/4/8px", x + 4, y + 38, 0xFFFFFF, false);
    }

    private boolean isFontProbe() {
        return templatePath.equals("screens/probe_font.html");
    }

    private void renderNativeFontReference(GuiGraphics graphics) {
        // Independent MC font rendering at the same size/9 scale as AUI's native backend.
        graphics.fill(221, 77, 365, 190, 0xFFE7D9B7);
        graphics.drawString(font, "MC native: 8/9/12/18", 221, 77, 0x213036, false);
        for (int i = 0; i < FONT_SIZES.length; i++) {
            float glyphScale = FONT_SIZES[i] / 9.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(221, 93 + i * 23, 0);
            graphics.pose().scale(glyphScale, glyphScale, 1);
            graphics.drawString(font, FONT_SAMPLE, 0, 0, 0x213036, false);
            graphics.pose().popPose();
        }
    }

    @Override
    public void tick() {
        super.tick();
        Document current = getLinkedDocument();
        if (current != boundDocument || current != null && current.getRefreshGeneration() != boundGeneration) {
            bindCurrentDocument("document generation changed");
        }
        // Recheck after resize settles: Screen.init can observe transitional window dimensions.
        if (current != null && ++scaleCheckTicks >= 10) {
            scaleCheckTicks = 0;
            updateScaleMetrics(current);
            if (isFontProbe()) updateFontMetrics(current);
        }
    }

    private void bindCurrentDocument(String reason) {
        Document document = getLinkedDocument();
        if (document == null) {
            boundDocument = null;
            boundGeneration = -1;
            LOGGER.warn("[PortholeQuest R0.1] Document unavailable after {}", reason);
            return;
        }

        long generation = document.getRefreshGeneration();
        boundDocument = document;
        boundGeneration = generation;
        lastScaleMetrics = null;
        lastFontMetrics = null;
        if (isFontProbe()) {
            updateScaleMetrics(document);
            LOGGER.info("[PortholeQuest R0.1] Bound path={} reason={} generation={} fontProbe=true",
                    templatePath, reason, generation);
            return;
        }

        Element clickButton = document.querySelector("#click-test");
        Element updateButton = document.querySelector("#update-test");
        Element refreshButton = document.querySelector("#refresh-test");
        Element textInput = document.querySelector("#key-test");
        Element status = document.querySelector("#java-status");
        Element bindStatus = document.querySelector("#bind-status");
        boolean complete = clickButton != null && updateButton != null && refreshButton != null
                && textInput != null && status != null && bindStatus != null;

        if (clickButton != null) {
            clickButton.addEventListener("click", event -> {
                if (!document.isCurrentGeneration(generation)) return;
                clickCount++;
                setText(document, "#click-status", "Mouse click callbacks: " + clickCount);
            });
        }
        if (updateButton != null) {
            updateButton.addEventListener("click", event -> {
                if (!document.isCurrentGeneration(generation)) return;
                setText(document, "#java-status", "Java updated this DOM at generation " + generation);
            });
        }
        if (refreshButton != null) {
            refreshButton.addEventListener("click", event -> {
                if (!document.isCurrentGeneration(generation)) return;
                LOGGER.info("[PortholeQuest R0.1] Refresh requested at generation {}", generation);
                document.refresh();
            });
        }
        if (textInput != null) {
            textInput.addEventListener("keydown", event -> {
                if (!document.isCurrentGeneration(generation)) return;
                keyCount++;
                setText(document, "#key-status", "Keyboard callbacks: " + keyCount + " (last: " + event.type + ")");
            });
        }

        if (status != null) {
            status.setTextContent("Java DOM binding ready; generation " + generation);
        }
        updateScaleMetrics(document);
        if (bindStatus != null) {
            bindStatus.setTextContent("Listeners rebound: " + (complete ? "yes" : "incomplete")
                    + " | generation " + generation);
        }
        LOGGER.info("[PortholeQuest R0.1] Bound path={} reason={} document={} generation={} elementsComplete={}",
                templatePath, reason, document, generation, complete);
    }

    private void updateFontMetrics(Document document) {
        StringBuilder metrics = new StringBuilder();
        for (int size : FONT_SIZES) {
            Element sample = document.querySelector("#font-sample-" + size);
            if (sample == null) continue;
            Text text = Text.of(sample);
            metrics.append(String.format(Locale.ROOT,
                    " size=%d family=%s resolvedSize=%.2f glyphScale=%.6f auiWidth=%.3f nativeWidth=%.3f;",
                    size, text.fontFamily, text.fontSize, text.defaultFontScale(),
                    Text.measureLine(text, FONT_SAMPLE), font.width(FONT_SAMPLE) * size / 9.0));
        }
        String value = metrics.toString();
        if (!value.equals(lastFontMetrics)) {
            lastFontMetrics = value;
            LOGGER.info("[PortholeQuest R0.1] Font metrics path={}{}", templatePath, value);
        }
    }

    private void updateScaleMetrics(Document document) {
        var window = Minecraft.getInstance().getWindow();
        Size viewportSize = document.getViewportSize();
        ApricityViewport viewport = document.getViewport();
        double framebufferScale = window.getGuiScale() * viewport.renderScale();
        String metrics = String.format(Locale.ROOT,
                "path=%s guiScale=%.2f gui=%dx%d window=%dx%d framebuffer=%dx%d auiViewport=%.0fx%.0f renderScale=%.6f viewportScale=%.6fx%.6f zoom=%.2f framebufferPxPerCssPx=%.6f icon16FramebufferPx=%.3f",
                templatePath, window.getGuiScale(), window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(),
                viewportSize.width(), viewportSize.height(), viewport.renderScale(),
                document.getViewportScaleX(), document.getViewportScaleY(), viewport.zoom(),
                framebufferScale, 16 * framebufferScale);
        if (metrics.equals(lastScaleMetrics)) return;
        lastScaleMetrics = metrics;
        setText(document, "#scale-status", String.format(Locale.ROOT,
                "MC GUI scale %.2f | MC GUI %dx%d | AUI CSS %.0fx%.0f | render %.3f | zoom %.2f | 1 CSS px = %.3f framebuffer px | 16 CSS px = %.1f framebuffer px",
                window.getGuiScale(), window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                viewportSize.width(), viewportSize.height(), viewport.renderScale(), viewport.zoom(),
                framebufferScale, 16 * framebufferScale));
        LOGGER.info("[PortholeQuest R0.1] Scale metrics {}", metrics);
    }

    private static void setText(Document document, String selector, String value) {
        Element element = document.querySelector(selector);
        if (element != null) {
            element.setTextContent(value);
        }
    }
}
