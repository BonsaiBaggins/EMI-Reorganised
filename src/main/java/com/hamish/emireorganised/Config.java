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
        BUILDER.comment("Buttons that roll EMI's left and right sidebars in and out.").push("sidebarButtons");
    }

    public static final ModConfigSpec.BooleanValue ROLLOUT_BUTTONS = BUILDER
            .comment("Show the roll in/out buttons on EMI's sidebars.").define("rolloutButtons", true);
    public static final ModConfigSpec.IntValue ROLLOUT_BUTTON_WIDTH = BUILDER
            .comment("Button width.").defineInRange("rolloutButtonWidth", 10, 6, 40);
    public static final ModConfigSpec.IntValue ROLLOUT_BUTTON_HEIGHT = BUILDER
            .comment("Button height.").defineInRange("rolloutButtonHeight", 20, 6, 80);
    public static final ModConfigSpec.IntValue ROLLOUT_BUTTON_PADDING = BUILDER
            .comment("Gap between the button and the sidebar or screen edge.").defineInRange("rolloutButtonPadding", 2, 0, 50);
    public static final ModConfigSpec.EnumValue<ButtonPosition> ROLLOUT_BUTTON_POSITION = BUILDER
            .comment("Vertical position of the buttons.").defineEnum("rolloutButtonPosition", ButtonPosition.MIDDLE);
    public static final ModConfigSpec.EnumValue<SidebarState> LEFT_SIDEBAR_INITIAL = BUILDER
            .comment("Left sidebar state when the game starts.").defineEnum("leftSidebarInitial", SidebarState.IN);
    public static final ModConfigSpec.EnumValue<SidebarState> RIGHT_SIDEBAR_INITIAL = BUILDER
            .comment("Right sidebar state when the game starts.").defineEnum("rightSidebarInitial", SidebarState.IN);
    public static final ModConfigSpec.BooleanValue ANIMATE_ON_OPEN = BUILDER
            .comment("Always animate a shown sidebar rolling in when an inventory opens.").define("animateOnOpen", false);
    public static final ModConfigSpec.IntValue ROLLOUT_DURATION = BUILDER
            .comment("Roll animation length in milliseconds.").defineInRange("rolloutDuration", 200, 0, 2000);

    static {
        BUILDER.pop();
        BUILDER.comment("EMI's search bar.").push("searchBar");
    }

    public static final ModConfigSpec.EnumValue<SearchBarPosition> SEARCH_BAR_POSITION = BUILDER
            .comment("Where EMI's search bar sits. ABOVE_INDEX and BELOW_INDEX attach it to the index and shrink the index to fit.")
            .defineEnum("searchBarPosition", SearchBarPosition.DEFAULT);
    public static final ModConfigSpec.BooleanValue SEARCH_CLEAR_BUTTON = BUILDER
            .comment("Show an 'x' inside the search bar that clears it while it has text.").define("clearButton", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public enum ButtonPosition {
        TOP,
        MIDDLE,
        BOTTOM
    }

    public enum SidebarState {
        IN,
        OUT
    }

    public enum SearchBarPosition {
        DEFAULT,
        ABOVE_INDEX,
        BELOW_INDEX
    }

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
