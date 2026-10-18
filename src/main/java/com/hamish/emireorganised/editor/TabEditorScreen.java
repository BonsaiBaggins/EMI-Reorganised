package com.hamish.emireorganised.editor;

import com.hamish.emireorganised.data.BannerDef;
import com.hamish.emireorganised.data.ItemGroup;
import com.hamish.emireorganised.data.Tab;
import com.hamish.emireorganised.data.TabEntry;
import com.hamish.emireorganised.port.BannerImages;
import com.hamish.emireorganised.port.BannerRenderer;
import com.hamish.emireorganised.port.BannerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** One screen for editing our tabs: their items, item groups and banners. */
public class TabEditorScreen extends Screen {
    private static final int SLOT = 18;
    private static final int TOP = 30;
    private static final int LEFT_W = 112;
    private static final int TAB_ROW = 20;
    private static final int BOTTOM_BAR = 24;
    private static final int SELECT_ITEM = 0x804C8CFF;
    private static final int SELECT_GROUP = 0x8040C040;
    private static final int SELECT_ADD = 0x90A040FF;
    private static final int MEMBER_TINT = 0x305C9DFF;
    private static final int GROUP_FRAME = 0xFF5E5E5E;

    enum Kind { ITEM, BANNER, GROUP, MEMBER }

    record Cell(EditorModel.Ref ref, int x, int y, int w, Kind kind, ItemStack stack, @Nullable TabEntry entry) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + SLOT;
        }
    }

    record MenuItem(Component label, Runnable action, boolean enabled) {
    }

    @Nullable private final Screen parent;
    private final EditorModel model = new EditorModel();
    private EditBox nameBox;
    private EditBox searchBox;
    private List<ItemStack> palette = List.of();
    private String paletteQuery = null;
    private int gridScroll;
    private int paletteScroll;
    private int tabScroll;
    private List<Cell> cells = List.of();

    // press / drag state
    @Nullable private EditorModel.Ref pressRef;
    @Nullable private Kind pressKind;
    @Nullable private ItemStack pressPalette;
    private double pressX;
    private double pressY;
    private boolean dragging;
    /** The open tab is also selected (blue), so Ctrl+C / Delete act on it. */
    private boolean tabSelected;
    private int draggingBar;
    private static final ResourceLocation[] BANNER_ICONS = {
            ResourceLocation.fromNamespaceAndPath("emi_reorganised", "textures/gui/image.png"),
            ResourceLocation.fromNamespaceAndPath("emi_reorganised", "textures/gui/palette.png"),
            ResourceLocation.fromNamespaceAndPath("emi_reorganised", "textures/gui/folder.png"),
            ResourceLocation.fromNamespaceAndPath("emi_reorganised", "textures/gui/crown.png")};
    private static final ResourceLocation CROWN_OFF = ResourceLocation.fromNamespaceAndPath("emi_reorganised", "textures/gui/crown_off.png");
    private static final String[] BANNER_TIPS = {"emi_reorganised.image.button", "emi_reorganised.colors.tip", "emi_reorganised.image.folder",
            "emi_reorganised.image.favourite"};
    /** The last thing copied was a tab rather than items. */
    private boolean clipboardTab;
    private int pressTab = -1;
    private boolean draggingTab;
    private boolean pressWasSelected;

    @Nullable private List<MenuItem> menu;
    private int menuX;
    private int menuY;
    /** Items waiting to be added to a group (purple); null when not in add-to-group mode. */
    @Nullable private List<ItemStack> adding;
    /** Right panel lists groups instead of items. */
    private boolean groupMode;
    private EditBox tabSearch;
    private final List<Button> editButtons = new ArrayList<>();
    private Button groupNew;
    private Button groupDelete;
    private List<GroupRef> groupList = List.of();
    @Nullable private String groupQuery;
    @Nullable private GroupRef pressGroup;
    @Nullable private EditorModel.Ref scrollTo;
    private Button undoButton;
    private Button redoButton;
    private Button groupsToggle;
    private Button filterButton;
    private EditBox tabFind;
    private enum PaletteFilter { ALL, UNSORTED, DUPLICATES }
    private PaletteFilter paletteFilter = PaletteFilter.ALL;
    private final java.util.TreeSet<Integer> paletteSelection = new java.util.TreeSet<>();
    private int paletteAnchor = -1;
    private int pressPaletteIndex = -1;
    /** Box selection: 0 = none, 1 = centre grid, 2 = item list. */
    private int boxMode;
    private double boxX0;
    private double boxY0;
    private double boxX1;
    private double boxY1;
    @Nullable private static List<ItemStack> allStacks;

    private record GroupRef(int tab, int entry, ItemGroup group) {
    }

    public TabEditorScreen(@Nullable Screen parent) {
        super(Component.translatable("emi_reorganised.editor.title"));
        this.parent = parent;
    }

    // ---- layout ----

    private int paletteW() {
        int cols = Math.max(5, Math.min(9, (width - LEFT_W - 200) / SLOT / 2));
        return cols * SLOT + 8;
    }

    private int gridX() {
        return LEFT_W + 8;
    }

    private int gridY() {
        return TOP + 24;
    }

    private int gridCols() {
        return Math.max(4, (width - LEFT_W - paletteW() - 24) / SLOT);
    }

    private int gridRows() {
        return Math.max(1, (height - gridY() - 6 - BOTTOM_BAR) / SLOT);
    }

    private int paletteX() {
        return width - paletteW() - 4;
    }

    private int paletteY() {
        return TOP + 46;
    }

    private int paletteCols() {
        return (paletteW() - 8) / SLOT;
    }

    private int paletteRows() {
        return Math.max(1, (height - paletteY() - 6 - (groupMode ? 24 : 0)) / SLOT);
    }

    private int tabListBottom() {
        return height - 4 - 2 * 22;
    }

    @Override
    protected void init() {
        editButtons.clear();
        int right = width - 4;
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.close"), b -> onClose())
                .bounds(right - 60, 4, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.save"), b -> model.save())
                .bounds(right - 124, 4, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.discard"), b -> {
            model.reload();
            refreshName();
        }).bounds(right - 188, 4, 60, 20).build());
        undoButton = addRenderableWidget(Button.builder(Component.literal("\u2190"), b -> undo()).bounds(right - 236, 4, 20, 20).build());
        undoButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("emi_reorganised.editor.undo")));
        redoButton = addRenderableWidget(Button.builder(Component.literal("\u2192"), b -> redo()).bounds(right - 212, 4, 20, 20).build());
        redoButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("emi_reorganised.editor.redo")));
        int bx = gridX();
        int bottomY = height - 22;
        for (Button button : List.of(
                Button.builder(Component.translatable("emi_reorganised.editor.add_banner"), b -> addBanner()).build(),
                (groupsToggle = Button.builder(Component.translatable("emi_reorganised.editor.groups_expand"),
                        b -> model.setAllOpen(!model.anyOpen())).build()))) {
            int w = Math.max(font.width(button.getMessage()), button == groupsToggle
                    ? font.width(Component.translatable("emi_reorganised.editor.groups_collapse")) : 0) + 12;
            button.setRectangle(w, 20, bx, bottomY);
            editButtons.add(addRenderableWidget(button));
            bx += w + 4;
        }

        int bw = (LEFT_W - 8) / 3;
        int by = height - 4 - 44;
        editButtons.add(addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.tab_new"), b -> minecraft.setScreen(
                new IconPickerScreen(this, Component.translatable("emi_reorganised.editor.tab_new_title"), "", ItemStack.EMPTY, (name, icon) -> {
                    model.newTab(name);
                    Tab t = model.current();
                    if (t != null && !icon.isEmpty()) {
                        t.icon = icon.copy();
                        model.markDirty();
                    }
                    refreshName();
                }))).bounds(4, by, bw, 20).build()));
        editButtons.add(addRenderableWidget(Button.builder(Component.literal("\u25B2"), b -> model.moveTab(-1)).bounds(6 + bw, by, bw, 20).build()));
        editButtons.add(addRenderableWidget(Button.builder(Component.literal("\u25BC"), b -> model.moveTab(1)).bounds(8 + 2 * bw, by, bw, 20).build()));
        editButtons.add(addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.tab_delete"), b -> {
            Tab t = model.current();
            if (t != null) {
                minecraft.setScreen(new ConfirmPopup(this, yes -> {
                    if (yes) {
                        model.deleteTab();
                        refreshName();
                    }
                    minecraft.setScreen(this);
                }, Component.translatable("emi_reorganised.editor.tab_delete_title"), Component.literal(t.name)));
            }
        }).bounds(4, by + 22, LEFT_W - 4, 20).build()));

        String tabQuery = tabSearch != null ? tabSearch.getValue() : "";
        tabSearch = new EditorSearch.ClearableBox(font, 4, TOP + 22, LEFT_W - 4, 18, Component.empty());
        tabSearch.setValue(tabQuery);
        tabSearch.setHint(Component.translatable("emi_reorganised.editor.search_tabs").withStyle(ChatFormatting.DARK_GRAY));
        tabSearch.setResponder(v -> tabScroll = 0);
        addRenderableWidget(tabSearch);
        Button tabSwap = Button.builder(Component.literal("\u21C4"), b -> {
            model.setCreativeMode(!model.creativeMode);
            tabScroll = 0;
            gridScroll = 0;
            refreshName();
        }).bounds(4, TOP - 1, 20, 20).build();
        tabSwap.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("emi_reorganised.editor.tab_swap")));
        addRenderableWidget(tabSwap);

        int gw = (width - 2 - paletteX() - 6) / 2;
        groupNew = addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.group_new"), b -> {
            newGroup();
        }).bounds(paletteX() + 2, height - 26, gw, 20).build());
        groupDelete = addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.group_delete"), b -> {
            GroupRef ref = selectedListGroup();
            if (ref != null) {
                confirmDeleteGroup(ref);
            }
        }).bounds(paletteX() + 4 + gw, height - 26, gw, 20).build());

        int nameW = (gridCols() * SLOT - 22) / 2;
        nameBox = new EditBox(font, gridX() + 22, TOP, nameW, 18, Component.empty());
        nameBox.setMaxLength(64);
        nameBox.setResponder(value -> {
            Tab t = model.current();
            if (t != null && !value.equals(t.name)) {
                t.name = value;
                model.markDirty("name:" + t.id);
            }
        });
        addRenderableWidget(nameBox);
        String find = tabFind != null ? tabFind.getValue() : "";
        int fx = gridX() + 22 + nameW + 4;
        tabFind = new EditorSearch.ClearableBox(font, fx, TOP, gridX() + gridCols() * SLOT - fx, 18, Component.empty());
        tabFind.setValue(find);
        tabFind.setHint(Component.translatable("emi_reorganised.editor.find").withStyle(ChatFormatting.DARK_GRAY));
        tabFind.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("emi_reorganised.group.regex_help")));
        tabFind.setResponder(v -> gridScroll = 0);
        addRenderableWidget(tabFind);
        refreshName();

        String query = searchBox != null ? searchBox.getValue() : "";
        searchBox = new EditorSearch.ClearableBox(font, paletteX(), TOP + 22, paletteW(), 18, Component.empty());
        searchBox.setValue(query);
        updateSearchHint();
        addRenderableWidget(searchBox);
        Button swap = Button.builder(Component.literal("\u21C4"), b -> {
            groupMode = !groupMode;
            paletteScroll = 0;
            updateSearchHint();
        }).bounds(paletteX() + paletteW() - 20, TOP - 1, 20, 20).build();
        swap.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("emi_reorganised.editor.swap")));
        addRenderableWidget(swap);
        filterButton = Button.builder(Component.literal("\u2261"), b -> showMenu(filterMenu(), b.getX(), b.getY() + 20))
                .bounds(paletteX() + paletteW() - 42, TOP - 1, 20, 20).build();
        filterButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("emi_reorganised.editor.filter")));
        addRenderableWidget(filterButton);
    }

    private void refreshName() {
        Tab t = model.current();
        if (nameBox != null) {
            nameBox.setValue(t != null ? t.name : "");
            nameBox.setEditable(!model.creativeMode);
        }
    }

    private int tabsTop() {
        return TOP + 44;
    }

    /** Indices into {@link EditorModel#shown()} that match the tab search. */
    private List<Integer> tabRows() {
        String query = tabSearch == null ? "" : tabSearch.getValue().trim().toLowerCase(Locale.ROOT);
        List<Integer> out = new ArrayList<>();
        List<Tab> list = model.shown();
        for (int i = 0; i < list.size(); i++) {
            if (query.isEmpty() || list.get(i).name.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(i);
            }
        }
        return out;
    }

    @Nullable
    private GroupRef selectedListGroup() {
        if (model.creativeMode) {
            return null;
        }
        for (GroupRef ref : groupList) {
            if (ref.tab() == model.tab && model.isSelected(EditorModel.Ref.of(ref.entry()))) {
                return ref;
            }
        }
        return null;
    }

    private void confirmDeleteGroup(GroupRef ref) {
        minecraft.setScreen(new ConfirmPopup(this, yes -> {
            if (yes) {
                model.deleteGroup(ref.tab(), ref.entry());
            }
            minecraft.setScreen(this);
        }, Component.translatable("emi_reorganised.editor.group_delete_title"), Component.literal(ref.group().name)));
    }

    private void showMenu(List<MenuItem> items, int x, int y) {
        menu = items;
        menuX = x;
        menuY = y;
    }

    private void updateSearchHint() {
        searchBox.setHint(Component.translatable(groupMode ? "emi_reorganised.editor.search_groups" : "emi_reorganised.editor.search")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private List<GroupRef> groups() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<GroupRef> out = new ArrayList<>();
        for (int t = 0; t < model.tabs.size(); t++) {
            List<TabEntry> entries = model.tabs.get(t).entries;
            for (int e = 0; e < entries.size(); e++) {
                if (entries.get(e) instanceof TabEntry.Group g && (query.isEmpty()
                        || g.group().name.toLowerCase(Locale.ROOT).contains(query) || g.group().id.contains(query))) {
                    out.add(new GroupRef(t, e, g.group()));
                }
            }
        }
        if (!query.equals(groupQuery)) {
            groupQuery = query;
            paletteScroll = 0;
        }
        groupList = out;
        return out;
    }

    @Nullable
    private GroupRef groupAt(double mx, double my) {
        if (!groupMode) {
            return null;
        }
        int py = paletteY();
        if (mx < paletteX() || mx >= width - 2 || my < py || my >= py + paletteRows() * SLOT) {
            return null;
        }
        int index = paletteScroll + (int) ((my - py) / SLOT);
        return index >= 0 && index < groupList.size() ? groupList.get(index) : null;
    }

    private void goToGroup(GroupRef ref) {
        if (model.creativeMode) {
            model.setCreativeMode(false);
            refreshName();
        }
        if (ref.tab() != model.tab) {
            model.selectTab(ref.tab());
            gridScroll = 0;
            refreshName();
        }
        EditorModel.Ref target = EditorModel.Ref.of(ref.entry());
        model.selection.clear();
        model.selection.add(target);
        scrollTo = target;
    }

    private boolean overTabIcon(double mx, double my) {
        return model.current() != null && mx >= gridX() && mx < gridX() + 18 && my >= TOP && my < TOP + 18;
    }

    private List<ItemStack> palette() {
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        String base = query + "\u0000" + paletteFilter + "\u0000";
        String key = base + (paletteFilter == PaletteFilter.ALL ? 0 : model.version);
        if (!key.equals(paletteQuery)) {
            if (paletteQuery == null || !paletteQuery.startsWith(base)) {
                paletteScroll = 0;
            }
            paletteQuery = key;
            paletteSelection.clear();
            paletteAnchor = -1;
            java.util.Map<dev.emi.emi.api.stack.EmiStack, Integer> counts = paletteFilter == PaletteFilter.ALL ? null : tabCounts();
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack stack : allStacks()) {
                if (counts != null) {
                    int n = counts.getOrDefault(dev.emi.emi.api.stack.EmiStack.of(stack), 0);
                    if (paletteFilter == PaletteFilter.UNSORTED ? n != 0 : n < 2) {
                        continue;
                    }
                }
                if (EditorSearch.matches(query, stack)) {
                    out.add(stack);
                }
            }
            palette = out;
        }
        return palette;
    }

    /** Every item EMI lists (including variants such as potions), then any other registered items. */
    private static List<ItemStack> allStacks() {
        if (allStacks == null || allStacks.isEmpty()) {
            List<ItemStack> out = new ArrayList<>();
            java.util.Set<Item> seen = new java.util.HashSet<>();
            List<dev.emi.emi.api.stack.EmiStack> emi = dev.emi.emi.registry.EmiStackList.stacks;
            if (emi != null) {
                for (dev.emi.emi.api.stack.EmiStack stack : emi) {
                    ItemStack item = stack.getItemStack();
                    if (!item.isEmpty() && stack.getKey() instanceof Item) {
                        out.add(item.copyWithCount(1));
                        seen.add(item.getItem());
                    }
                }
            }
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && seen.add(item)) {
                    out.add(item.getDefaultInstance());
                }
            }
            allStacks = out;
        }
        return allStacks;
    }

    /** In how many of the user's tabs each item appears. */
    private java.util.Map<dev.emi.emi.api.stack.EmiStack, Integer> tabCounts() {
        java.util.Map<dev.emi.emi.api.stack.EmiStack, Integer> counts = new java.util.HashMap<>();
        for (Tab t : model.tabs) {
            java.util.Set<dev.emi.emi.api.stack.EmiStack> inTab = new java.util.HashSet<>();
            for (TabEntry e : t.entries) {
                if (e instanceof TabEntry.Item item && !item.stack().isEmpty()) {
                    inTab.add(dev.emi.emi.api.stack.EmiStack.of(item.stack()));
                } else if (e instanceof TabEntry.Group g) {
                    for (ItemStack stack : g.group().items) {
                        if (!stack.isEmpty()) {
                            inTab.add(dev.emi.emi.api.stack.EmiStack.of(stack));
                        }
                    }
                }
            }
            inTab.forEach(st -> counts.merge(st, 1, Integer::sum));
        }
        return counts;
    }

    private List<MenuItem> filterMenu() {
        List<MenuItem> out = new ArrayList<>();
        for (PaletteFilter f : PaletteFilter.values()) {
            Component label = Component.literal(f == paletteFilter ? "\u2714 " : "    ")
                    .append(Component.translatable("emi_reorganised.editor.filter." + f.name().toLowerCase(Locale.ROOT)));
            out.add(new MenuItem(label, () -> paletteFilter = f, true));
        }
        return out;
    }

    private void undo() {
        model.undo();
        refreshName();
    }

    private void redo() {
        model.redo();
        refreshName();
    }

    private String findQuery() {
        return tabFind == null ? "" : tabFind.getValue().trim();
    }

    private boolean cellMatches(Cell cell, String query) {
        return switch (cell.kind()) {
            case ITEM, MEMBER -> EditorSearch.matches(query, cell.stack());
            case GROUP -> {
                ItemGroup g = ((TabEntry.Group) cell.entry()).group();
                yield EditorSearch.matches(query, g.name) || g.items.stream().anyMatch(st -> EditorSearch.matches(query, st));
            }
            case BANNER -> EditorSearch.matches(query, ((TabEntry.Banner) cell.entry()).banner().name);
        };
    }

    private static boolean onSlotBorder(double dx, double dy) {
        double x = ((dx % SLOT) + SLOT) % SLOT;
        double y = ((dy % SLOT) + SLOT) % SLOT;
        return x < 1 || x >= SLOT - 1 || y < 1 || y >= SLOT - 1;
    }

    private int paletteIndexAt(double mx, double my) {
        int px = paletteX() + 4;
        int py = paletteY();
        if (groupMode || mx < px || my < py || mx >= px + paletteCols() * SLOT || my >= py + paletteRows() * SLOT) {
            return -1;
        }
        if (onSlotBorder(mx - px, my - py)) {
            return -1;
        }
        int index = (paletteScroll + (int) ((my - py) / SLOT)) * paletteCols() + (int) ((mx - px) / SLOT);
        return index >= 0 && index < palette().size() ? index : -1;
    }

    private boolean inPalette(double mx, double my) {
        int px = paletteX() + 4;
        int py = paletteY();
        return !groupMode && mx >= px && my >= py && mx < px + paletteCols() * SLOT && my < py + paletteRows() * SLOT;
    }

    /** The palette items being dragged: the whole palette selection if the pressed item is part of it. */
    private List<ItemStack> draggedPalette() {
        if (pressPalette == null) {
            return List.of();
        }
        List<ItemStack> list = palette();
        if (paletteSelection.contains(pressPaletteIndex) && paletteSelection.size() > 1) {
            List<ItemStack> out = new ArrayList<>();
            for (int i : paletteSelection) {
                if (i < list.size()) {
                    out.add(list.get(i));
                }
            }
            return out;
        }
        return List.of(pressPalette);
    }

    /** The group's first few distinct items, for its stacked icon. */
    private static List<ItemStack> cascade(ItemGroup group) {
        List<ItemStack> layers = new ArrayList<>();
        ItemStack icon = group.displayIcon();
        if (!icon.isEmpty()) {
            layers.add(icon);
        }
        for (ItemStack stack : group.items) {
            if (layers.size() >= 3) {
                break;
            }
            if (!stack.isEmpty() && layers.stream().noneMatch(l -> ItemStack.isSameItemSameComponents(l, stack))) {
                layers.add(stack);
            }
        }
        return layers;
    }

    /** Names of the user's tabs listing the item, loose or in a group. */
    private List<String> tabsContaining(ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (Tab t : model.tabs) {
            boolean found = false;
            for (TabEntry e : t.entries) {
                if (e instanceof TabEntry.Item item && ItemStack.isSameItemSameComponents(item.stack(), stack)
                        || e instanceof TabEntry.Group g && g.group().items.stream().anyMatch(st -> ItemStack.isSameItemSameComponents(st, stack))) {
                    found = true;
                    break;
                }
            }
            if (found) {
                out.add(t.name);
            }
        }
        return out;
    }

    private void startBox(int mode, double mx, double my) {
        boxMode = mode;
        boxX0 = boxX1 = mx;
        boxY0 = boxY1 = my;
    }

    private void finishBox() {
        double x0 = Math.min(boxX0, boxX1);
        double x1 = Math.max(boxX0, boxX1);
        double y0 = Math.min(boxY0, boxY1);
        double y1 = Math.max(boxY0, boxY1);
        if (x1 - x0 < 3 && y1 - y0 < 3) {
            return;
        }
        if (boxMode == 1) {
            tabSelected = false;
            int top = gridY();
            int bottom = gridY() + gridRows() * SLOT;
            for (Cell cell : cells) {
                if (cell.y() + SLOT > top && cell.y() < bottom && cell.x() < x1 && cell.x() + cell.w() > x0
                        && cell.y() < y1 && cell.y() + SLOT > y0) {
                    model.selection.add(cell.ref());
                }
            }
        } else if (boxMode == 2) {
            int px = paletteX() + 4;
            int py = paletteY();
            int cols = paletteCols();
            int size = palette().size();
            for (int r = 0; r < paletteRows(); r++) {
                for (int c = 0; c < cols; c++) {
                    int index = (paletteScroll + r) * cols + c;
                    int sx = px + c * SLOT;
                    int sy = py + r * SLOT;
                    if (index < size && sx < x1 && sx + SLOT > x0 && sy < y1 && sy + SLOT > y0) {
                        paletteSelection.add(index);
                    }
                }
            }
        }
    }

    private void renderBox(GuiGraphics graphics) {
        if (boxMode == 0) {
            return;
        }
        int x0 = (int) Math.min(boxX0, boxX1);
        int y0 = (int) Math.min(boxY0, boxY1);
        int x1 = (int) Math.max(boxX0, boxX1);
        int y1 = (int) Math.max(boxY0, boxY1);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(x0, y0, x1, y1, 0x304C8CFF);
        graphics.renderOutline(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0), 0xFF4C8CFF);
        graphics.pose().popPose();
    }

    private List<Cell> buildCells() {
        List<Cell> out = new ArrayList<>();
        List<TabEntry> entries = model.entries();
        int cols = gridCols();
        int col = 0;
        int row = 0;
        String q = findQuery();
        for (int i = 0; i < entries.size(); i++) {
            TabEntry entry = entries.get(i);
            switch (entry) {
                case TabEntry.Banner banner -> {
                    if (col > 0) {
                        row++;
                        col = 0;
                    }
                    out.add(new Cell(EditorModel.Ref.of(i), gridX(), rowY(row), cols * SLOT, Kind.BANNER, ItemStack.EMPTY, entry));
                    row++;
                }
                case TabEntry.Item item -> {
                    if (!q.isEmpty() && !EditorSearch.matches(q, item.stack())) {
                        continue;
                    }
                    out.add(new Cell(EditorModel.Ref.of(i), gridX() + col * SLOT, rowY(row), SLOT, Kind.ITEM, item.stack(), entry));
                    if (++col >= cols) {
                        col = 0;
                        row++;
                    }
                }
                case TabEntry.Group group -> {
                    ItemGroup g = group.group();
                    boolean nameMatch = q.isEmpty() || EditorSearch.matches(q, g.name);
                    if (!nameMatch && g.items.stream().noneMatch(st -> EditorSearch.matches(q, st))) {
                        continue;
                    }
                    out.add(new Cell(EditorModel.Ref.of(i), gridX() + col * SLOT, rowY(row), SLOT, Kind.GROUP, g.displayIcon(), entry));
                    if (++col >= cols) {
                        col = 0;
                        row++;
                    }
                    if (model.isOpen(g)) {
                        for (int m = 0; m < g.items.size(); m++) {
                            if (!nameMatch && !EditorSearch.matches(q, g.items.get(m))) {
                                continue;
                            }
                            out.add(new Cell(new EditorModel.Ref(i, m), gridX() + col * SLOT, rowY(row), SLOT, Kind.MEMBER, g.items.get(m), entry));
                            if (++col >= cols) {
                                col = 0;
                                row++;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private int rowY(int row) {
        return gridY() + (row - gridScroll) * SLOT;
    }

    private boolean inGrid(double mx, double my) {
        return mx >= gridX() && mx < gridX() + gridCols() * SLOT && my >= gridY() && my < gridY() + gridRows() * SLOT;
    }

    @Nullable
    private Cell cellAt(double mx, double my) {
        if (!inGrid(mx, my)) {
            return null;
        }
        for (Cell cell : cells) {
            if (cell.contains(mx, my)) {
                return cell;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack paletteAt(double mx, double my) {
        int px = paletteX() + 4;
        int py = paletteY();
        if (groupMode || mx < px || my < py || mx >= px + paletteCols() * SLOT || my >= py + paletteRows() * SLOT) {
            return null;
        }
        if (onSlotBorder(mx - px, my - py)) {
            return null;
        }
        int index = (paletteScroll + (int) ((my - py) / SLOT)) * paletteCols() + (int) ((mx - px) / SLOT);
        List<ItemStack> list = palette();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private int tabAt(double mx, double my) {
        if (mx < 4 || mx >= LEFT_W || my < tabsTop() || my >= tabListBottom()) {
            return -1;
        }
        List<Integer> rows = tabRows();
        int index = tabScroll + (int) ((my - tabsTop()) / TAB_ROW);
        return index < rows.size() ? rows.get(index) : -1;
    }

    // ---- rendering ----

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        cells = buildCells();
        if (scrollTo != null) {
            for (Cell cell : cells) {
                if (cell.ref().equals(scrollTo)) {
                    int row = (cell.y() - gridY()) / SLOT + gridScroll;
                    if (row < gridScroll || row >= gridScroll + gridRows()) {
                        gridScroll = Math.max(0, row - 1);
                        cells = buildCells();
                    }
                    break;
                }
            }
            scrollTo = null;
        }
        graphics.fill(2, TOP - 2, LEFT_W + 2, height - 2, 0xC0101010);
        graphics.fill(gridX() - 3, gridY() - 3, gridX() + gridCols() * SLOT + 3, height - 2 - BOTTOM_BAR, 0xC0101010);
        graphics.fill(paletteX() - 2, TOP - 2, width - 2, height - 2, 0xC0101010);
        graphics.drawString(font, Component.translatable(model.creativeMode ? "emi_reorganised.editor.header.creative"
                : "emi_reorganised.editor.header.tabs"), 28, TOP + 5, 0xFFFFFF);
        graphics.drawString(font, Component.translatable(groupMode ? "emi_reorganised.editor.header.groups"
                : "emi_reorganised.editor.header.items"), paletteX() + 2, TOP + 5, 0xFFFFFF);
        renderTabs(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);
        renderPalette(graphics, mouseX, mouseY);
        renderBox(graphics);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int visible = (tabListBottom() - tabsTop()) / TAB_ROW;
        List<Integer> rows = tabRows();
        tabScroll = Math.max(0, Math.min(tabScroll, rows.size() - visible));
        for (int r = tabScroll; r < Math.min(rows.size(), tabScroll + visible); r++) {
            int i = rows.get(r);
            Tab t = model.shown().get(i);
            int y = tabsTop() + (r - tabScroll) * TAB_ROW;
            if (i == model.shownIndex()) {
                graphics.fill(4, y, LEFT_W, y + TAB_ROW - 1, tabSelected ? 0xFF3A5A8A : 0x70C0C0C0);
            } else if (tabAt(mouseX, mouseY) == i && !draggingTab) {
                graphics.fill(4, y, LEFT_W, y + TAB_ROW - 1, 0x40FFFFFF);
            }
            ItemStack icon = t.icon.isEmpty() ? firstItem(t) : t.icon;
            graphics.renderItem(icon, 6, y + 2);
            String name = font.plainSubstrByWidth(t.name, LEFT_W - 30);
            graphics.drawString(font, name, 26, y + 6, t.hidden ? 0xFF808080 : 0xFFFFFFFF);
        }
        if (draggingTab) {
            int ly = tabsTop() + (tabDropRow(mouseY) - tabScroll) * TAB_ROW - 1;
            graphics.fill(4, ly, LEFT_W, ly + 2, 0xFFFFFFFF);
        }
    }

    private static ItemStack firstItem(Tab t) {
        for (TabEntry e : t.entries) {
            if (e instanceof TabEntry.Item item) {
                return item.stack();
            }
            if (e instanceof TabEntry.Group g) {
                return g.group().displayIcon();
            }
        }
        return new ItemStack(Items.BARRIER);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = gridX();
        int y0 = gridY();
        graphics.enableScissor(x0, y0, x0 + gridCols() * SLOT, y0 + gridRows() * SLOT);
        for (Cell cell : cells) {
            if (cell.y() + SLOT < y0 || cell.y() > y0 + gridRows() * SLOT) {
                continue;
            }
            int x = cell.x();
            int y = cell.y();
            if (cell.kind() == Kind.BANNER) {
                BannerDef def = ((TabEntry.Banner) cell.entry()).banner();
                BannerRenderer.drawBanner(graphics, BannerView.of(def, BannerImages.path(def.image)), x, y, cell.w(), SLOT);
                BannerRenderer.restoreScissor(graphics);
                if (model.isSelected(cell.ref())) {
                    graphics.fill(x, y, x + cell.w(), y + SLOT, SELECT_ITEM);
                }
                if (!model.creativeMode) {
                    int hoveredButton = bannerButtonAt(cell, mouseX, mouseY);
                    for (int b = 0; b < (def.image != null ? 4 : 2); b++) {
                        int bx = bannerButtonX(cell, b);
                        ResourceLocation icon = b == 3 && !BannerFavourites.contains(def.image) ? CROWN_OFF : BANNER_ICONS[b];
                        graphics.blit(icon, bx, y + 1, 0, 0, 16, 16, 16, 16);
                        if (hoveredButton == b) {
                            graphics.fill(bx, y + 1, bx + 16, y + 17, 0x40FFFFFF);
                        }
                    }
                }
                continue;
            }
            graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF373737);
            graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF8B8B8B);
            if (cell.kind() == Kind.MEMBER) {
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, MEMBER_TINT);
            }
            boolean selected = model.isSelected(cell.ref());
            if (selected) {
                int colour = adding != null ? SELECT_ADD : cell.kind() == Kind.GROUP ? SELECT_GROUP : SELECT_ITEM;
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, colour);
            }
            if (cell.contains(mouseX, mouseY) && menu == null) {
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x60FFFFFF);
            }
            if (cell.kind() == Kind.GROUP) {
                com.hamish.emireorganised.index.GroupIngredient.drawCascade(graphics, cascade(((TabEntry.Group) cell.entry()).group()), x + 1, y + 1);
            } else {
                graphics.renderItem(cell.stack(), x + 1, y + 1);
            }
            if (cell.kind() == Kind.GROUP) {
                ItemGroup g = ((TabEntry.Group) cell.entry()).group();
                graphics.renderOutline(x, y, SLOT, SLOT, GROUP_FRAME);
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.drawString(font, model.isOpen(g) ? "-" : "+", x + 12, y, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
        }
        if (dragging) {
            renderDropMarker(graphics, mouseX, mouseY);
        }
        graphics.disableScissor();
        int rows = totalRows();
        if (rows > gridRows()) {
            int barH = Math.max(8, gridRows() * SLOT * gridRows() / rows);
            int barY = y0 + (gridRows() * SLOT - barH) * gridScroll / Math.max(1, rows - gridRows());
            int bx = x0 + gridCols() * SLOT + 1;
            graphics.fill(bx, y0, bx + 2, y0 + gridRows() * SLOT, 0x40000000);
            graphics.fill(bx, barY, bx + 2, barY + barH, draggingBar == 1 ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
    }

    private int totalRows() {
        int max = 0;
        for (Cell cell : cells) {
            max = Math.max(max, (cell.y() - gridY()) / SLOT + gridScroll + 1);
        }
        return max;
    }

    private void renderDropMarker(GuiGraphics graphics, int mouseX, int mouseY) {
        Cell cell = cellAt(mouseX, mouseY);
        if (cell == null) {
            return;
        }
        boolean after = cell.kind() != Kind.BANNER && mouseX >= cell.x() + SLOT / 2;
        int x = after ? cell.x() + cell.w() - 1 : cell.x();
        int colour = cell.kind() == Kind.MEMBER || cell.kind() == Kind.GROUP && after ? GROUP_FRAME : 0xFFFFFFFF;
        graphics.fill(x - 1, cell.y(), x + 1, cell.y() + SLOT, colour);
    }

    private void renderGroupList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<GroupRef> list = groups();
        int rows = paletteRows();
        paletteScroll = Math.max(0, Math.min(paletteScroll, Math.max(0, list.size() - rows)));
        int px = paletteX() + 4;
        int py = paletteY();
        int textW = width - 6 - (px + 20);
        for (int r = 0; r < rows && paletteScroll + r < list.size(); r++) {
            GroupRef ref = list.get(paletteScroll + r);
            int y = py + r * SLOT;
            boolean selected = ref.tab() == model.tab && model.isSelected(EditorModel.Ref.of(ref.entry()));
            if (selected) {
                graphics.fill(px - 2, y, width - 4, y + SLOT, adding != null ? SELECT_ADD : SELECT_GROUP);
            } else if (mouseX >= px - 2 && mouseX < width - 4 && mouseY >= y && mouseY < y + SLOT) {
                graphics.fill(px - 2, y, width - 4, y + SLOT, 0x30FFFFFF);
            }
            graphics.renderItem(ref.group().displayIcon(), px, y + 1);
            String label = ref.group().name;
            if (ref.tab() != model.tab) {
                label += " \u00A77(" + model.tabs.get(ref.tab()).name + ")";
            }
            graphics.drawString(font, font.plainSubstrByWidth(label, textW), px + 20, y + 5, 0xFFFFFF);
        }
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY) {
        if (groupMode) {
            renderGroupList(graphics, mouseX, mouseY);
            return;
        }
        List<ItemStack> list = palette();
        int cols = paletteCols();
        int rows = paletteRows();
        int maxScroll = Math.max(0, (list.size() + cols - 1) / cols - rows);
        paletteScroll = Math.max(0, Math.min(paletteScroll, maxScroll));
        int px = paletteX() + 4;
        int py = paletteY();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int index = (paletteScroll + r) * cols + c;
                if (index >= list.size()) {
                    return;
                }
                int sx = px + c * SLOT;
                int sy = py + r * SLOT;
                graphics.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF373737);
                graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0xFF8B8B8B);
                if (paletteSelection.contains(index)) {
                    graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, SELECT_ITEM);
                }
                graphics.renderItem(list.get(index), px + c * SLOT + 1, py + r * SLOT + 1);
            }
        }
        renderPaletteBar(graphics, list.size(), cols, rows);
    }

    private void renderPaletteBar(GuiGraphics graphics, int size, int cols, int rows) {
        int total = (size + cols - 1) / cols;
        if (total <= rows) {
            return;
        }
        int bx = width - 6;
        int top = paletteY();
        int h = rows * SLOT;
        int barH = Math.max(8, h * rows / total);
        int barY = top + (h - barH) * paletteScroll / Math.max(1, total - rows);
        graphics.fill(bx, top, bx + 2, top + h, 0x40000000);
        graphics.fill(bx, barY, bx + 2, barY + barH, draggingBar == 2 ? 0xFFFFFFFF : 0xFFAAAAAA);
    }

    /** 1 = central grid bar, 2 = item list bar; null-safe hit test for starting a drag. */
    private int barAt(double mx, double my) {
        int gx = gridX() + gridCols() * SLOT + 1;
        if (mx >= gx - 1 && mx < gx + 4 && my >= gridY() && my < gridY() + gridRows() * SLOT && totalRows() > gridRows()) {
            return 1;
        }
        if (!groupMode && mx >= width - 8 && mx < width - 2 && my >= paletteY() && my < paletteY() + paletteRows() * SLOT) {
            return 2;
        }
        return 0;
    }

    private void dragBar(double my) {
        if (draggingBar == 1) {
            int max = Math.max(0, totalRows() - gridRows());
            gridScroll = (int) Math.round(Math.max(0, Math.min(1, (my - gridY()) / (double) (gridRows() * SLOT))) * max);
        } else if (draggingBar == 2) {
            int cols = paletteCols();
            int max = Math.max(0, (palette().size() + cols - 1) / cols - paletteRows());
            paletteScroll = (int) Math.round(Math.max(0, Math.min(1, (my - paletteY()) / (double) (paletteRows() * SLOT))) * max);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        model.nextFrame();
        for (Button b : editButtons) {
            b.active = !model.creativeMode;
        }
        groupNew.visible = groupMode;
        groupDelete.visible = groupMode;
        groupNew.active = !model.creativeMode && model.current() != null;
        groupDelete.active = selectedListGroup() != null;
        undoButton.active = model.canUndo();
        redoButton.active = model.canRedo();
        filterButton.visible = !groupMode;
        groupsToggle.setMessage(Component.translatable(model.anyOpen() ? "emi_reorganised.editor.groups_collapse"
                : "emi_reorganised.editor.groups_expand"));
        super.render(graphics, mouseX, mouseY, partialTick);
        Tab t = model.current();
        Component title = adding != null
                ? Component.translatable("emi_reorganised.editor.adding").withStyle(s -> s.withColor(0xC080FF))
                : Component.translatable("emi_reorganised.editor.title").append(model.dirty() ? " *" : "");
        int titleW = Math.max(40, width - 4 - 236 - 8 - gridX());
        if (font.width(title) > titleW) {
            graphics.pose().pushPose();
            float scale = titleW / (float) font.width(title);
            graphics.pose().translate(gridX(), 10, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, title, 0, 0, 0xFFFFFF);
            graphics.pose().popPose();
        } else {
            graphics.drawString(font, title, gridX(), 10, 0xFFFFFF);
        }
        if (t != null) {
            int ix = gridX();
            graphics.fill(ix, TOP, ix + 18, TOP + 18, overTabIcon(mouseX, mouseY) ? 0xFFFFFFFF : 0xFF8B8B8B);
            graphics.fill(ix + 1, TOP + 1, ix + 17, TOP + 17, 0xFF373737);
            if (!t.icon.isEmpty()) {
                graphics.renderItem(t.icon, ix + 1, TOP + 1);
            } else {
                graphics.drawCenteredString(font, "?", ix + 9, TOP + 5, 0xFFAAAAAA);
            }
        }
        if (dragging) {
            List<ItemStack> ghost = pressGroup != null ? List.of(pressGroup.group().displayIcon())
                    : pressPalette != null ? draggedPalette() : model.selectedStacks();
            if (!ghost.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 300);
                graphics.renderItem(ghost.getFirst(), mouseX - 8, mouseY - 8);
                if (ghost.size() > 1) {
                    graphics.drawString(font, String.valueOf(ghost.size()), mouseX + 6, mouseY + 2, 0xFFFFFF, true);
                }
                graphics.pose().popPose();
            }
        }
        if (menu != null) {
            renderMenu(graphics, mouseX, mouseY);
            return;
        }
        if (!dragging) {
            Cell cell = cellAt(mouseX, mouseY);
            if (cell != null && cell.kind() == Kind.GROUP) {
                ItemGroup g = ((TabEntry.Group) cell.entry()).group();
                graphics.renderComponentTooltip(font, List.of(Component.literal(g.name),
                        Component.translatable("emi_reorganised.group.count", g.items.size()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            } else if (cell != null && cell.kind() == Kind.BANNER) {
                int b = model.creativeMode ? -1 : bannerButtonAt(cell, mouseX, mouseY);
                graphics.renderTooltip(font, b >= 0 ? Component.translatable(BANNER_TIPS[b])
                        : Component.literal(((TabEntry.Banner) cell.entry()).banner().name), mouseX, mouseY);
            } else if (cell != null) {
                graphics.renderTooltip(font, cell.stack(), mouseX, mouseY);
            } else if (overTabIcon(mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("emi_reorganised.editor.icon_tip"), mouseX, mouseY);
            } else if (groupAt(mouseX, mouseY) instanceof GroupRef ref) {
                graphics.renderComponentTooltip(font, List.of(Component.literal(ref.group().name),
                        Component.literal(model.tabs.get(ref.tab()).name).withStyle(ChatFormatting.GRAY),
                        Component.translatable("emi_reorganised.group.count", ref.group().items.size()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            } else {
                ItemStack stack = paletteAt(mouseX, mouseY);
                if (stack != null) {
                    List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, stack));
                    List<String> in = tabsContaining(stack);
                    lines.add(in.isEmpty() ? Component.translatable("emi_reorganised.editor.in_tabs.none").withStyle(ChatFormatting.GRAY)
                            : Component.translatable("emi_reorganised.editor.in_tabs", String.join(", ", in)).withStyle(ChatFormatting.GRAY));
                    graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
            }
        }
    }

    private void renderMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int w = 0;
        for (MenuItem item : menu) {
            w = Math.max(w, font.width(item.label()));
        }
        w += 12;
        int h = menu.size() * 12 + 4;
        menuX = Math.min(menuX, width - w - 2);
        menuY = Math.min(menuY, height - h - 2);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(menuX, menuY, menuX + w, menuY + h, 0xF0100010);
        graphics.renderOutline(menuX, menuY, w, h, 0xFF5000FF);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.get(i);
            int y = menuY + 2 + i * 12;
            boolean hover = mouseX >= menuX && mouseX < menuX + w && mouseY >= y && mouseY < y + 12;
            if (hover && item.enabled()) {
                graphics.fill(menuX + 1, y, menuX + w - 1, y + 12, 0x40FFFFFF);
            }
            graphics.drawString(font, item.label(), menuX + 6, y + 2, item.enabled() ? 0xFFFFFF : 0x707070);
        }
        graphics.pose().popPose();
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu != null) {
            clickMenu(mx, my);
            return true;
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        setFocused(null);
        if (button == 0 && barAt(mx, my) != 0) {
            draggingBar = barAt(mx, my);
            dragBar(my);
            return true;
        }
        if (button == 0 && overTabIcon(mx, my) && !model.creativeMode) {
            Tab tab = model.current();
            minecraft.setScreen(new IconPickerScreen(this, Component.translatable("emi_reorganised.editor.icon_title"), null, tab.icon, (name, icon) -> {
                tab.icon = icon.copy();
                model.markDirty();
            }));
            return true;
        }
        int tabIndex = tabAt(mx, my);
        if (tabIndex >= 0 && button == 0) {
            selectTabRow(tabIndex);
            pressTab = model.creativeMode || adding != null ? -1 : tabIndex;
            pressX = mx;
            pressY = my;
            return true;
        }
        tabSelected = false;
        Cell hit = cellAt(mx, my);
        // The dark border around a slot counts as empty space, so a box selection can start there.
        Cell cell = hit != null && button == 0 && adding == null && hit.kind() != Kind.BANNER
                && onSlotBorder(mx - hit.x(), my - hit.y()) ? null : hit;
        if (button == 0) {
            GroupRef groupRef = groupAt(mx, my);
            if (adding != null) {
                if (groupRef != null) {
                    confirmAdd(groupRef.group());
                    return true;
                }
                if (cell != null && (cell.kind() == Kind.GROUP || cell.kind() == Kind.MEMBER)) {
                    confirmAdd(((TabEntry.Group) cell.entry()).group());
                }
                return true;
            }
            if (groupRef != null) {
                pressGroup = groupRef;
                pressX = mx;
                pressY = my;
                return true;
            }
            ItemStack pal = paletteAt(mx, my);
            if (pal != null) {
                int pi = paletteIndexAt(mx, my);
                if (hasControlDown()) {
                    if (!paletteSelection.remove(pi)) {
                        paletteSelection.add(pi);
                    }
                    paletteAnchor = pi;
                } else if (hasShiftDown() && paletteAnchor >= 0) {
                    paletteSelection.clear();
                    for (int k = Math.min(paletteAnchor, pi); k <= Math.max(paletteAnchor, pi); k++) {
                        paletteSelection.add(k);
                    }
                } else if (!paletteSelection.contains(pi)) {
                    paletteSelection.clear();
                    paletteSelection.add(pi);
                    paletteAnchor = pi;
                }
                pressPaletteIndex = pi;
                pressPalette = pal;
                pressX = mx;
                pressY = my;
                return true;
            }
            if (cell == null) {
                if (inGrid(mx, my)) {
                    if (!hasShiftDown() && !hasControlDown()) {
                        model.selection.clear();
                    }
                    paletteSelection.clear();
                    startBox(1, mx, my);
                    return true;
                }
                if (inPalette(mx, my)) {
                    if (!hasShiftDown() && !hasControlDown()) {
                        paletteSelection.clear();
                    }
                    model.selection.clear();
                    startBox(2, mx, my);
                    return true;
                }
                return false;
            }
            if (!model.creativeMode && cell.kind() == Kind.BANNER) {
                int b = bannerButtonAt(cell, mx, my);
                if (b >= 0) {
                    BannerDef def = ((TabEntry.Banner) cell.entry()).banner();
                    switch (b) {
                        case 0 -> openBackground(def);
                        case 1 -> openBannerColors(def);
                        case 2 -> BannerImageFiles.show(def.image);
                        default -> BannerFavourites.toggle(def.image);
                    }
                    return true;
                }
            }
            pressRef = cell.ref();
            pressKind = cell.kind();
            pressX = mx;
            pressY = my;
            pressWasSelected = model.isSelected(cell.ref());
            if (hasControlDown()) {
                if (!model.selection.remove(cell.ref())) {
                    model.selection.add(cell.ref());
                }
            } else if (hasShiftDown() && !model.selection.isEmpty()) {
                selectRange(cell);
            } else if (!pressWasSelected) {
                model.selection.clear();
                model.selection.add(cell.ref());
            }
            return true;
        }
        if (button == 1 && adding == null) {
            int tabRow = tabAt(mx, my);
            if (tabRow >= 0) {
                selectTabRow(tabRow);
                Tab tab = model.shown().get(tabRow);
                showMenu(List.of(
                        new MenuItem(Component.translatable("emi_reorganised.menu.copy_tab"), () -> copyTab(tab), true),
                        new MenuItem(Component.translatable("emi_reorganised.menu.paste_tab"), this::pasteTab, model.canPasteTab()),
                        new MenuItem(Component.translatable("emi_reorganised.menu.delete"), this::confirmDeleteTab, !model.creativeMode)),
                        (int) mx, (int) my);
                return true;
            }
            GroupRef listRef = groupAt(mx, my);
            if (listRef != null) {
                goToGroup(listRef);
                showMenu(List.of(
                        new MenuItem(Component.translatable("emi_reorganised.menu.edit_group"), () -> minecraft.setScreen(
                                new GroupEditScreen(this, listRef.group(), () -> model.groupChanged(listRef.group()))), true),
                        new MenuItem(Component.translatable("emi_reorganised.menu.delete"), () -> confirmDeleteGroup(listRef), true)),
                        (int) mx, (int) my);
                return true;
            }
        }
        if (button == 1) {
            if (cell != null && !model.isSelected(cell.ref())) {
                if (adding == null) {
                    model.selection.clear();
                }
                model.selection.add(cell.ref());
            }
            if (cell != null || inGrid(mx, my)) {
                openMenu(cell, (int) mx, (int) my);
                return true;
            }
        }
        return false;
    }

    private int bannerButtonX(Cell cell, int button) {
        return cell.x() + cell.w() - 17 - 17 * button;
    }

    /** 0 = image, 1 = colours, 2 = folder, 3 = favourite (2 and 3 only with an image), or -1. */
    private int bannerButtonAt(Cell cell, double mx, double my) {
        if (my < cell.y() + 1 || my >= cell.y() + 17) {
            return -1;
        }
        BannerDef def = ((TabEntry.Banner) cell.entry()).banner();
        for (int b = 0; b < (def.image != null ? 4 : 2); b++) {
            int bx = bannerButtonX(cell, b);
            if (mx >= bx && mx < bx + 16) {
                return b;
            }
        }
        return -1;
    }

    private void openBannerColors(BannerDef def) {
        BannerDef plain = def.copy(def.id);
        plain.colors = BannerDef.Colors.NONE;
        minecraft.setScreen(new BannerColorScreen(this, BannerView.of(plain, BannerImages.path(def.image)), def.colors, colors -> {
            def.colors = colors;
            model.markDirty();
        }));
    }

    /** No image yet: straight to the picker. With an image: the Edit Background popup (replace or delete). */
    private void openBackground(BannerDef def) {
        if (def.image == null || BannerImages.path(def.image) == null) {
            openBannerPicker(def);
            return;
        }
        minecraft.setScreen(new EditBackgroundScreen(this, def, () -> openBannerPicker(def), () -> confirmDeleteImage(def)));
    }

    private void openBannerPicker(BannerDef def) {
        minecraft.setScreen(new BannerPickerScreen(this, def, file -> {
            def.image = file;
            model.markDirty();
        }));
    }

    private void confirmDeleteImage(BannerDef def) {
        String file = def.image;
        minecraft.setScreen(new ConfirmPopup(this, yes -> {
            if (yes && def.image != null) {
                try {
                    BannerImageFiles.delete(def.image);
                } catch (java.io.IOException e) {
                    com.hamish.emireorganised.EmiReorganised.LOGGER.warn("Couldn't delete banner image", e);
                }
                def.image = null;
                model.markDirty();
            }
        }, Component.translatable("emi_reorganised.image.confirm_title"),
                Component.translatable("emi_reorganised.image.confirm_message", file)));
    }

    private void selectTabRow(int index) {
        model.selectTab(index);
        gridScroll = 0;
        refreshName();
        if (adding == null) {
            model.selection.clear();
            tabSelected = true;
        }
    }

    private void copyTab(Tab tab) {
        model.copyTab(tab);
        clipboardTab = true;
    }

    private void pasteTab() {
        model.pasteTab();
        refreshName();
    }

    private void copyItems() {
        model.copy();
        clipboardTab = false;
    }

    private void cutItems() {
        model.cut();
        clipboardTab = false;
    }

    private void confirmDeleteTab() {
        Tab t = model.current();
        if (model.creativeMode || t == null) {
            return;
        }
        minecraft.setScreen(new ConfirmPopup(this, yes -> {
            if (yes) {
                model.deleteTab();
                tabSelected = false;
                refreshName();
            }
        }, Component.translatable("emi_reorganised.editor.tab_delete_title"), Component.literal(t.name)));
    }

    /** Row (in the filtered tab list) a dragged tab would be inserted before. */
    private int tabDropRow(double my) {
        int r = tabScroll + (int) Math.floor((my - tabsTop()) / TAB_ROW + 0.5);
        return Math.max(0, Math.min(r, tabRows().size()));
    }

    private void dropTab(double my) {
        List<Integer> rows = tabRows();
        int r = tabDropRow(my);
        int before = r < rows.size() ? rows.get(r) : rows.isEmpty() ? model.tabs.size() : rows.getLast() + 1;
        model.moveTabTo(pressTab, before);
        refreshName();
    }

    private void selectRange(Cell to) {
        EditorModel.Ref from = model.sortedSelection().getLast();
        int a = -1;
        int b = -1;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).ref().equals(from)) {
                a = i;
            }
            if (cells.get(i).ref().equals(to.ref())) {
                b = i;
            }
        }
        if (a < 0 || b < 0) {
            model.selection.add(to.ref());
            return;
        }
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
            model.selection.add(cells.get(i).ref());
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (boxMode != 0) {
            boxX1 = mx;
            boxY1 = my;
            return true;
        }
        if (draggingBar != 0) {
            dragBar(my);
            return true;
        }
        if (pressTab >= 0 && !draggingTab && Math.abs(my - pressY) > 4) {
            draggingTab = true;
        }
        if (draggingTab) {
            return true;
        }
        if (!model.creativeMode && (pressRef != null || pressPalette != null || pressGroup != null) && !dragging
                && Math.abs(mx - pressX) + Math.abs(my - pressY) > 4) {
            dragging = true;
        }
        return dragging || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        try {
            if (boxMode != 0) {
                finishBox();
                return true;
            }
            if (draggingBar != 0) {
                draggingBar = 0;
                return true;
            }
            if (draggingTab) {
                dropTab(my);
                return true;
            }
            if (dragging) {
                drop(mx, my);
                return true;
            }
            if (pressGroup != null && button == 0) {
                goToGroup(pressGroup);
                return true;
            }
            if (pressPalette != null && button == 0 && !hasControlDown() && !hasShiftDown()) {
                paletteSelection.clear();
                paletteSelection.add(pressPaletteIndex);
                paletteAnchor = pressPaletteIndex;
            }
            if (pressRef != null && button == 0 && !hasControlDown() && !hasShiftDown()) {
                if (pressKind == Kind.GROUP) {
                    TabEntry e = model.entries().get(pressRef.entry());
                    if (e instanceof TabEntry.Group g) {
                        model.toggleOpen(g.group());
                    }
                }
                if (pressWasSelected) {
                    model.selection.clear();
                    model.selection.add(pressRef);
                }
            }
            return super.mouseReleased(mx, my, button);
        } finally {
            pressRef = null;
            pressKind = null;
            pressPalette = null;
            pressGroup = null;
            dragging = false;
            pressTab = -1;
            draggingTab = false;
            boxMode = 0;
        }
    }

    private void drop(double mx, double my) {
        if (model.creativeMode || model.current() == null || !inGrid(mx, my)) {
            return;
        }
        Cell cell = cellAt(mx, my);
        int entry;
        int member = -1;
        if (cell == null) {
            entry = model.entries().size();
        } else {
            boolean after = cell.kind() != Kind.BANNER && mx >= cell.x() + SLOT / 2;
            entry = cell.ref().entry();
            if (cell.kind() == Kind.MEMBER) {
                member = cell.ref().member() + (after ? 1 : 0);
            } else if (cell.kind() == Kind.GROUP) {
                ItemGroup g = ((TabEntry.Group) cell.entry()).group();
                if (after && model.isOpen(g)) {
                    member = 0;
                } else if (after) {
                    entry++;
                }
            } else if (after) {
                entry++;
            }
        }
        if (pressGroup != null) {
            ItemGroup copy = pressGroup.group().copy(model.uniqueGroupId(pressGroup.group().name));
            model.insert(member >= 0 ? entry + 1 : entry, List.of(new TabEntry.Group(copy)));
            return;
        }
        if (pressPalette != null) {
            List<ItemStack> stacks = draggedPalette().stream().map(ItemStack::copy).toList();
            if (member >= 0) {
                model.insertIntoGroup(entry, member, stacks);
            } else {
                model.insert(entry, stacks.stream().map(st -> (TabEntry) new TabEntry.Item(st)).toList());
            }
            return;
        }
        if (cell != null && model.isSelected(cell.ref()) && pressRef != null && !cell.ref().equals(pressRef)
                && model.selection.contains(cell.ref())) {
            return;
        }
        model.moveSelection(entry, member);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int delta = (int) -Math.signum(sy);
        if (inGrid(mx, my)) {
            gridScroll = Math.max(0, Math.min(gridScroll + delta, Math.max(0, totalRows() - gridRows())));
            return true;
        }
        if (mx >= paletteX()) {
            paletteScroll = Math.max(0, paletteScroll + delta);
            return true;
        }
        if (mx < LEFT_W) {
            tabScroll = Math.max(0, tabScroll + delta);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) {
                menu = null;
                return true;
            }
            if (adding != null) {
                adding = null;
                return true;
            }
        }
        if (getFocused() == tabFind && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            tabFind.setFocused(false);
            setFocused(null);
            return true;
        }
        boolean typing = getFocused() instanceof EditBox;
        if (!typing && menu == null) {
            if (hasControlDown() && key == GLFW.GLFW_KEY_Z && !hasShiftDown()) {
                undo();
                return true;
            }
            if (hasControlDown() && (key == GLFW.GLFW_KEY_Y || key == GLFW.GLFW_KEY_Z)) {
                redo();
                return true;
            }
            if (tabSelected && hasControlDown() && key == GLFW.GLFW_KEY_C) {
                int i = model.shownIndex();
                if (i >= 0 && i < model.shown().size()) {
                    copyTab(model.shown().get(i));
                }
                return true;
            }
            if (clipboardTab && hasControlDown() && key == GLFW.GLFW_KEY_V) {
                pasteTab();
                return true;
            }
            if (tabSelected && (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE)) {
                confirmDeleteTab();
                return true;
            }
            if (hasControlDown() && key == GLFW.GLFW_KEY_C) {
                copyItems();
                return true;
            }
            if (model.creativeMode && (key == GLFW.GLFW_KEY_X || key == GLFW.GLFW_KEY_V
                    || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE)) {
                return true;
            }
            if (hasControlDown() && key == GLFW.GLFW_KEY_X) {
                cutItems();
                return true;
            }
            if (hasControlDown() && key == GLFW.GLFW_KEY_V) {
                model.paste();
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                model.delete();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ---- right-click menu ----

    private void openMenu(@Nullable Cell cell, int x, int y) {
        boolean any = !model.selection.isEmpty();
        if (model.creativeMode) {
            showMenu(List.of(
                    new MenuItem(Component.translatable("emi_reorganised.menu.cut"), () -> { }, false),
                    new MenuItem(Component.translatable("emi_reorganised.menu.copy"), this::copyItems, any),
                    new MenuItem(Component.translatable("emi_reorganised.menu.paste"), () -> { }, false),
                    new MenuItem(Component.translatable("emi_reorganised.menu.delete"), () -> { }, false)), x, y);
            return;
        }
        List<MenuItem> items = new ArrayList<>();
        boolean itemsSelected = !model.selectedStacks().isEmpty();
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.cut"), this::cutItems, any));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.copy"), this::copyItems, any));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.paste"), model::paste, model.canPaste()));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.delete"), model::delete, any));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.new_group"), this::newGroup,
                itemsSelected && (cell == null || cell.kind() != Kind.GROUP)));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.add_to_group"), () -> adding = model.selectedStacks(),
                itemsSelected && (cell == null || cell.kind() != Kind.GROUP)));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.new_banner"), this::addBanner, model.current() != null));
        if (cell != null && cell.kind() == Kind.GROUP) {
            ItemGroup g = ((TabEntry.Group) cell.entry()).group();
            items.add(new MenuItem(Component.translatable("emi_reorganised.menu.edit_group"), () -> minecraft.setScreen(
                    new GroupEditScreen(this, g, () -> model.groupChanged(g))), true));
            items.add(new MenuItem(Component.translatable("emi_reorganised.menu.ungroup"), () -> {
                List<TabEntry> entries = model.entries();
                int index = cell.ref().entry();
                entries.remove(index);
                entries.addAll(index, g.items.stream().map(s -> (TabEntry) new TabEntry.Item(s)).toList());
                model.selection.clear();
                model.markDirty();
            }, true));
        }
        if (cell != null && cell.kind() == Kind.BANNER) {
            BannerDef def = ((TabEntry.Banner) cell.entry()).banner();
            items.add(new MenuItem(Component.translatable("emi_reorganised.menu.rename"), () -> minecraft.setScreen(
                    new TextPromptScreen(this, Component.translatable("emi_reorganised.menu.rename"), def.name, name -> {
                        def.name = name;
                        model.markDirty();
                    })), true));
            items.add(new MenuItem(Component.translatable("emi_reorganised.colors.tip"), () -> openBannerColors(def), true));
        }
        if (cell != null && cell.kind() == Kind.BANNER) {
            BannerDef def = ((TabEntry.Banner) cell.entry()).banner();
            items.add(new MenuItem(Component.translatable("emi_reorganised.image.edit"), () -> openBackground(def), true));
            if (def.image != null) {
                items.add(new MenuItem(Component.translatable("emi_reorganised.image.open"), () -> BannerImageFiles.show(def.image), true));
            }
        }
        if (cell != null && (cell.kind() == Kind.ITEM || cell.kind() == Kind.MEMBER)) {
            ItemStack stack = cell.stack();
            items.add(new MenuItem(Component.translatable("emi_reorganised.menu.tab_icon"), () -> {
                Tab t = model.current();
                if (t != null) {
                    t.icon = stack.copyWithCount(1);
                    model.markDirty();
                }
            }, true));
        }
        menu = items;
        menuX = x;
        menuY = y;
    }

    private void clickMenu(double mx, double my) {
        List<MenuItem> items = menu;
        menu = null;
        if (items == null || mx < menuX) {
            return;
        }
        int i = (int) ((my - menuY - 2) / 12);
        if (my >= menuY + 2 && i >= 0 && i < items.size() && items.get(i).enabled()) {
            int w = 0;
            for (MenuItem item : items) {
                w = Math.max(w, font.width(item.label()));
            }
            if (mx < menuX + w + 12) {
                items.get(i).action().run();
            }
        }
    }

    private void newGroup() {
        List<ItemStack> stacks = model.selectedStacks();
        boolean fromSelection = !stacks.isEmpty();
        ItemGroup draft = new ItemGroup("", fromSelection ? stacks.getFirst().getHoverName().getString() : "");
        stacks.forEach(s -> draft.items.add(s.copy()));
        minecraft.setScreen(new GroupEditScreen(this, Component.translatable("emi_reorganised.group.new"), draft, () -> {
            String name = draft.name.isBlank() ? "Group" : draft.name;
            // Selected items taken out in the popup aren't grouped: they stay where they are in the tab.
            model.selection.removeIf(ref -> {
                ItemStack stack = model.stackAt(ref);
                return stack != null && draft.items.stream().noneMatch(d -> ItemStack.isSameItemSameComponents(d, stack));
            });
            ItemGroup group = fromSelection && !model.selection.isEmpty()
                    ? model.newGroup(model.uniqueGroupId(name), name) : model.newEmptyGroup(name);
            group.regex = draft.regex;
            group.tag = draft.tag;
            group.items.clear();
            draft.items.forEach(s -> group.items.add(s.copy()));
            model.closed.remove(group.id);
            model.groupChanged(group);
        }));
    }

    private void addBanner() {
        if (model.current() == null) {
            return;
        }
        minecraft.setScreen(new TextPromptScreen(this, Component.translatable("emi_reorganised.menu.new_banner"), "", model::newBanner));
    }

    private void confirmAdd(ItemGroup group) {
        List<ItemStack> stacks = adding;
        if (stacks == null) {
            return;
        }
        minecraft.setScreen(new ConfirmPopup(this, yes -> {
            if (yes) {
                model.addToGroup(group, stacks);
                adding = null;
            }
            minecraft.setScreen(this);
        }, Component.translatable("emi_reorganised.editor.add_title"),
                Component.translatable("emi_reorganised.editor.add_message", stacks.size(), group.name)));
    }

    // ---- closing ----

    @Override
    public void onClose() {
        if (!model.dirty()) {
            minecraft.setScreen(parent);
            return;
        }
        minecraft.setScreen(new ConfirmPopup(this, yes -> {
            if (yes) {
                model.save();
            }
            minecraft.setScreen(parent);
        }, Component.translatable("emi_reorganised.editor.unsaved_title"), Component.translatable("emi_reorganised.editor.unsaved_message"),
                Component.translatable("emi_reorganised.editor.save"), Component.translatable("emi_reorganised.editor.discard")));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
