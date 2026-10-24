package com.bonsaibaggins.emireorganised;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/emi_reorganised-client.toml). */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("The EMI item index.").push("index");
    }

    public static final ModConfigSpec.BooleanValue REORGANISED_SKIN = BUILDER
            .comment("Draw EMI's left and right sidebars with the Reorganised Vanilla skin, whatever theme EMI is set to.",
                    "Its sprites live under assets/emi_reorganised/textures/gui/sprites/skin/ and can be replaced by resource packs.")
            .define("reorganisedVanillaSkin", true);

    public static final ModConfigSpec.BooleanValue SHOW_BANNERS_WHILE_SEARCHING = BUILDER
            .comment("Keep banners visible while a search is typed.")
            .define("showBannersWhileSearching", false);
    public static final ModConfigSpec.EnumValue<EdgeMode> EDGE_MODE = BUILDER
            .comment("How banners wider than their artwork fill the sides.")
            .defineEnum("edgeMode", EdgeMode.STRETCH);
    public static final ModConfigSpec.BooleanValue SHOW_UNSORTED = BUILDER
            .comment("List items that aren't in any tab in an automatic 'Unsorted' tab.")
            .define("showUnsortedTab", true);

    public static final ModConfigSpec.BooleanValue VERTICAL_TABS = BUILDER
            .comment("Show a column of tab buttons beside EMI's index.").define("verticalTabs", true);
    public static final ModConfigSpec.EnumValue<TabSide> TAB_SIDE = BUILDER
            .comment("Which side of the index the tabs sit on.").defineEnum("tabSide", TabSide.LEFT);
    public static final ModConfigSpec.IntValue TAB_SPACING = BUILDER
            .comment("Gap between vertical tab buttons.").defineInRange("tabSpacing", 1, 0, 10);
    public static final ModConfigSpec.BooleanValue INDEX_SCROLLING = BUILDER
            .comment("Scroll the index with a scroll bar and the mouse wheel instead of pages (no page buttons, no wrapping).")
            .define("indexScrolling", true);
    public static final ModConfigSpec.BooleanValue FAVOURITES_SCROLLING = BUILDER
            .comment("Scroll the favourites sidebar with a scroll bar and the mouse wheel instead of pages.")
            .define("favouritesScrolling", true);
    public static final ModConfigSpec.BooleanValue FAVOURITE_TABS = BUILDER
            .comment("Show tabs beside the favourites sidebar, each holding its own list of favourites.")
            .define("favouriteTabs", true);
    public static final ModConfigSpec.EnumValue<TabSide> FAVOURITE_TAB_SIDE = BUILDER
            .comment("Which side of the favourites sidebar its tabs sit on.").defineEnum("favouriteTabSide", TabSide.RIGHT);
    public static final ModConfigSpec.BooleanValue FAVOURITE_SEARCH = BUILDER
            .comment("Replace the favourites sidebar's header with a search bar that filters the favourites.")
            .define("favouriteSearch", true);
    public static final ModConfigSpec.EnumValue<ScrollbarStyle> SCROLLBAR_STYLE = BUILDER
            .comment("Scroll bar look: NARROW, VANILLA_CREATIVE, or NARROW_CREATIVE (a half-width creative bar).")
            .defineEnum("scrollbarStyle", ScrollbarStyle.NARROW);

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
            .comment("Where EMI's search bar sits. DEFAULT is EMI's own spot (screen centre). ABOVE_INDEX and BELOW_INDEX attach it to the index and shrink the index to fit.")
            .defineEnum("searchBarPosition", SearchBarPosition.ABOVE_INDEX);
    public static final ModConfigSpec.BooleanValue SEARCH_SHOWS_ALL = BUILDER
            .comment("Switch the index to the 'Search All' tab when you start typing a search.").define("searchShowsAll", true);
    public static final ModConfigSpec.BooleanValue SEARCH_CLEAR_BUTTON = BUILDER
            .comment("Show an 'x' inside the search bar that clears it while it has text.").define("clearButton", true);

    static {
        BUILDER.pop();
        BUILDER.comment("The Tab Editor.").push("editor");
    }

    public static final ModConfigSpec.EnumValue<EditorButton> EDITOR_BUTTON = BUILDER
            .comment("When to show the Open Editor button beside the inventory.").defineEnum("openEditorButton", EditorButton.CREATIVE_ONLY);

    static {
        BUILDER.pop();
        BUILDER.comment("The player inventory.").push("inventory");
    }

    public static final ModConfigSpec.BooleanValue SURVIVAL_INVENTORY_IN_CREATIVE = BUILDER
            .comment("In creative mode, open the survival inventory instead of the creative inventory.")
            .define("survivalInventoryInCreative", true);

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

    public enum ScrollbarStyle {
        NARROW,
        VANILLA_CREATIVE,
        NARROW_CREATIVE
    }

    public enum TabSide {
        LEFT,
        RIGHT
    }

    public enum EditorButton {
        ALWAYS,
        NEVER,
        CREATIVE_ONLY
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
