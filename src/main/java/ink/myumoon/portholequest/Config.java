package ink.myumoon.portholequest;

import ink.myumoon.portholequest.model.ListFilter;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client journal settings. Live GUI state belongs to the current-world session cache. */
public final class Config {
    public enum BlankDetailMode { IMAGE, BOOK, EMPTY }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<ListFilter> DEFAULT_LIST_FILTER;
    public static final ModConfigSpec.BooleanValue DEFAULT_CHAPTER_EXPANDED;
    public static final ModConfigSpec.BooleanValue FLOWCHART_ENABLED;
    public static final ModConfigSpec.IntValue MAX_DETAIL_HISTORY;
    public static final ModConfigSpec.IntValue DESCRIPTION_PREVIEW_LINES;
    public static final ModConfigSpec.IntValue DESCRIPTION_DIALOG_LINES;
    public static final ModConfigSpec.EnumValue<BlankDetailMode> BLANK_DETAIL_MODE;
    public static final ModConfigSpec.ConfigValue<String> BLANK_DETAIL_BOOK_TITLE_COLOR;
    public static final ModConfigSpec.ConfigValue<String> BLANK_DETAIL_IMAGE;
    public static final ModConfigSpec.IntValue BLANK_DETAIL_IMAGE_WIDTH;
    public static final ModConfigSpec.IntValue BLANK_DETAIL_IMAGE_HEIGHT;
    public static final ModConfigSpec.IntValue BLANK_DETAIL_TEXTURE_WIDTH;
    public static final ModConfigSpec.IntValue BLANK_DETAIL_TEXTURE_HEIGHT;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("journal");
        DEFAULT_LIST_FILTER = builder.comment("Initial list filter when this world session has no cached selection.",
                        "ALL: visible quests; COMPLETED: executable or completed; ACTIVE: unfinished executable or claimable rewards.")
                .defineEnum("defaultListFilter", ListFilter.ACTIVE);
        DEFAULT_CHAPTER_EXPANDED = builder.comment("Initial expansion for chapters without a cached state.")
                .define("defaultChapterExpanded", true);
        FLOWCHART_ENABLED = builder.comment("Allow the quest flowchart preview. Set false to hide its button and disable its shortcut.",
                        "Modpack authors can disable dependency previews to preserve discovery. This is a client setting, not a server-enforced lock.")
                .define("flowchartEnabled", true);
        MAX_DETAIL_HISTORY = builder.comment("Maximum number of detail navigation history entries kept while the journal is open.",
                        "When exceeded, the oldest entries are discarded first.")
                .defineInRange("maxDetailHistory", 32, 1, 256);
        DESCRIPTION_PREVIEW_LINES = builder.comment("Number of description lines shown before expanding the Detail preview.")
                .defineInRange("descriptionPreviewLines", 5, 1, 50);
        DESCRIPTION_DIALOG_LINES = builder.comment("Expanded descriptions at this line count or above open the reading dialog.",
                        "Keep this value greater than descriptionPreviewLines.")
                .defineInRange("descriptionDialogLines", 10, 2, 100);
        BLANK_DETAIL_MODE = builder.comment("Blank detail: IMAGE, BOOK, or EMPTY.")
                .defineEnum("blankDetailMode", BlankDetailMode.IMAGE);
        BLANK_DETAIL_BOOK_TITLE_COLOR = builder.comment("BOOK title color as #RRGGBB; default is dark beige.")
                .define("blankDetailBookTitleColor", "#9A8765",
                        value -> value instanceof String color && color.matches("#[0-9a-fA-F]{6}"));
        BLANK_DETAIL_IMAGE = builder.comment("Resource location for the blank detail image.")
                .define("blankDetailImage", "portholequest:textures/gui/detail/blank_detail_logo.png");
        BLANK_DETAIL_IMAGE_WIDTH = builder.comment("Blank image width in GUI pixels.")
                .defineInRange("blankDetailImageWidth", 160, 1, 192);
        BLANK_DETAIL_IMAGE_HEIGHT = builder.comment("Blank image height in GUI pixels.")
                .defineInRange("blankDetailImageHeight", 60, 1, 176);
        BLANK_DETAIL_TEXTURE_WIDTH = builder.comment("Source texture width in pixels.")
                .defineInRange("blankDetailTextureWidth", 160, 1, 4096);
        BLANK_DETAIL_TEXTURE_HEIGHT = builder.comment("Source texture height in pixels.")
                .defineInRange("blankDetailTextureHeight", 60, 1, 4096);
        builder.pop();
        SPEC = builder.build();
    }

    private Config() {}
}
