package ink.myumoon.portholequest;

import ink.myumoon.portholequest.model.ListFilter;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client defaults only. Live GUI state belongs to the current-world session cache. */
public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<ListFilter> DEFAULT_LIST_FILTER;
    public static final ModConfigSpec.BooleanValue DEFAULT_CHAPTER_EXPANDED;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("journal");
        DEFAULT_LIST_FILTER = builder.comment("Initial list filter when this world session has no cached selection.",
                        "ALL: visible quests; COMPLETED: executable or completed; ACTIVE: unfinished executable or claimable rewards.")
                .defineEnum("defaultListFilter", ListFilter.ACTIVE);
        DEFAULT_CHAPTER_EXPANDED = builder.comment("Initial expansion for chapters without a cached state.")
                .define("defaultChapterExpanded", true);
        builder.pop();
        SPEC = builder.build();
    }

    private Config() {}
}
