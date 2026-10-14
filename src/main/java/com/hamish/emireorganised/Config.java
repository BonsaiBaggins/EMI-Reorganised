package com.hamish.emireorganised;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/emi_reorganised-client.toml). */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("The EMI item index.").push("index");
    }

    public static final ModConfigSpec.BooleanValue SHOW_BANNERS_WHILE_SEARCHING = BUILDER
            .comment("Keep banners visible while a search is typed.")
            .define("showBannersWhileSearching", false);
    public static final ModConfigSpec.EnumValue<EdgeMode> EDGE_MODE = BUILDER
            .comment("How banners wider than their artwork fill the sides.")
            .defineEnum("edgeMode", EdgeMode.STRETCH);
    public static final ModConfigSpec.BooleanValue SHOW_UNSORTED = BUILDER
            .comment("List items that aren't in any tab in an automatic 'Unsorted' tab.")
            .define("showUnsortedTab", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public enum EdgeMode {
        STRETCH,
        AVERAGE
    }

    private Config() {
    }

    /** Value, or its default before the config has loaded. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
