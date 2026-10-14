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
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
    private static final int GROUP_FRAME = 0xFF7FA7D9;

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
    private boolean pressWasSelected;

    @Nullable private List<MenuItem> menu;
    private int menuX;
    private int menuY;
    /** Items waiting to be added to a group (purple); null when not in add-to-group mode. */
    @Nullable private List<ItemStack> adding;
    /** Right panel lists groups instead of items. */
    private boolean groupMode;
    private List<GroupRef> groupList = List.of();
    @Nullable private String groupQuery;
    @Nullable private GroupRef pressGroup;
    @Nullable private EditorModel.Ref scrollTo;

    private record GroupRef(int tab, int entry, ItemGroup group) {
    }

    public TabEditorScreen(@Nullable Screen parent) {
        super(Component.translatable("emi_reorganised.editor.title"));
        this.parent = parent;
    }

    // ---- layout ----

    private int paletteW() {
        int cols = Math.max(4, Math.min(9, (width - LEFT_W - 200) / SLOT / 2));
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
        return TOP + 24;
    }

    private int paletteCols() {
        return (paletteW() - 8) / SLOT;
    }

    private int paletteRows() {
        return Math.max(1, (height - paletteY() - 6) / SLOT);
    }

    private int tabListBottom() {
        return height - 4 - 2 * 22;
    }

    @Override
    protected void init() {
        int right = width - 4;
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.close"), b -> onClose())
                .bounds(right - 60, 4, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.save"), b -> model.save())
                .bounds(right - 124, 4, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.discard"), b -> {
            model.reload();
            refreshName();
        }).bounds(right - 188, 4, 60, 20).build());
        int bx = gridX();
        int bottomY = height - 22;
        for (Button button : List.of(
                Button.builder(Component.translatable("emi_reorganised.editor.add_banner"), b -> addBanner()).build(),
                Button.builder(Component.translatable("emi_reorganised.editor.groups_toggle"), b -> model.setAllOpen(!model.anyOpen())).build())) {
            int w = font.width(button.getMessage()) + 12;
            button.setRectangle(w, 20, bx, bottomY);
            addRenderableWidget(button);
            bx += w + 4;
        }

        int bw = (LEFT_W - 8) / 3;
        int by = height - 4 - 44;
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.tab_new"), b -> minecraft.setScreen(
                new IconPickerScreen(this, Component.translatable("emi_reorganised.editor.tab_new_title"), "", ItemStack.EMPTY, (name, icon) -> {
                    model.newTab(name);
                    Tab t = model.current();
                    if (t != null && !icon.isEmpty()) {
                        t.icon = icon.copy();
                        model.markDirty();
                    }
                    refreshName();
                }))).bounds(4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u25B2"), b -> model.moveTab(-1)).bounds(6 + bw, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u25BC"), b -> model.moveTab(1)).bounds(8 + 2 * bw, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.editor.tab_delete"), b -> {
            Tab t = model.current();
            if (t != null) {
                minecraft.setScreen(new ConfirmScreen(yes -> {
                    if (yes) {
                        model.deleteTab();
                        refreshName();
                    }
                    minecraft.setScreen(this);
                }, Component.translatable("emi_reorganised.editor.tab_delete_title"), Component.literal(t.name)));
            }
        }).bounds(4, by + 22, LEFT_W - 4, 20).build());

        nameBox = new EditBox(font, gridX() + 22, TOP, gridCols() * SLOT - 22, 18, Component.empty());
        nameBox.setMaxLength(64);
        nameBox.setResponder(value -> {
            Tab t = model.current();
            if (t != null && !value.equals(t.name)) {
                t.name = value;
                model.markDirty();
            }
        });
        addRenderableWidget(nameBox);
        refreshName();

        String query = searchBox != null ? searchBox.getValue() : "";
        searchBox = new EditBox(font, paletteX(), TOP, paletteW() - 22, 18, Component.empty());
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
    }

    private void refreshName() {
        Tab t = model.current();
        if (nameBox != null) {
            nameBox.setValue(t != null ? t.name : "");
        }
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
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (!query.equals(paletteQuery)) {
            paletteQuery = query;
            paletteScroll = 0;
            List<ItemStack> out = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                ItemStack stack = item.getDefaultInstance();
                if (query.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                        || BuiltInRegistries.ITEM.getKey(item).toString().contains(query)) {
                    out.add(stack);
                }
            }
            palette = out;
        }
        return palette;
    }

    private List<Cell> buildCells() {
        List<Cell> out = new ArrayList<>();
        List<TabEntry> entries = model.entries();
        int cols = gridCols();
        int col = 0;
        int row = 0;
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
                    out.add(new Cell(EditorModel.Ref.of(i), gridX() + col * SLOT, rowY(row), SLOT, Kind.ITEM, item.stack(), entry));
                    if (++col >= cols) {
                        col = 0;
                        row++;
                    }
                }
                case TabEntry.Group group -> {
                    ItemGroup g = group.group();
                    out.add(new Cell(EditorModel.Ref.of(i), gridX() + col * SLOT, rowY(row), SLOT, Kind.GROUP, g.displayIcon(), entry));
                    if (++col >= cols) {
                        col = 0;
                        row++;
                    }
                    if (model.isOpen(g)) {
                        for (int m = 0; m < g.items.size(); m++) {
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
        int index = (paletteScroll + (int) ((my - py) / SLOT)) * paletteCols() + (int) ((mx - px) / SLOT);
        List<ItemStack> list = palette();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private int tabAt(double mx, double my) {
        if (mx < 4 || mx >= LEFT_W || my < TOP || my >= tabListBottom()) {
            return -1;
        }
        int index = tabScroll + (int) ((my - TOP) / TAB_ROW);
        return index < model.tabs.size() ? index : -1;
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
        graphics.fill(paletteX() - 2, paletteY() - 3, width - 2, height - 2, 0xC0101010);
        renderTabs(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);
        renderPalette(graphics, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int visible = (tabListBottom() - TOP) / TAB_ROW;
        tabScroll = Math.max(0, Math.min(tabScroll, model.tabs.size() - visible));
        for (int i = tabScroll; i < Math.min(model.tabs.size(), tabScroll + visible); i++) {
            Tab t = model.tabs.get(i);
            int y = TOP + (i - tabScroll) * TAB_ROW;
            if (i == model.tab) {
                graphics.fill(4, y, LEFT_W, y + TAB_ROW - 1, 0xFF3A5A8A);
            } else if (tabAt(mouseX, mouseY) == i) {
                graphics.fill(4, y, LEFT_W, y + TAB_ROW - 1, 0x40FFFFFF);
            }
            ItemStack icon = t.icon.isEmpty() ? firstItem(t) : t.icon;
            graphics.renderItem(icon, 6, y + 2);
            String name = font.plainSubstrByWidth(t.name, LEFT_W - 30);
            graphics.drawString(font, name, 26, y + 6, t.hidden ? 0xFF808080 : 0xFFFFFFFF);
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
            graphics.renderItem(cell.stack(), x + 1, y + 1);
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
            graphics.fill(bx, barY, bx + 2, barY + barH, 0xFFAAAAAA);
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
                graphics.renderItem(list.get(index), px + c * SLOT + 1, py + r * SLOT + 1);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Tab t = model.current();
        Component title = adding != null
                ? Component.translatable("emi_reorganised.editor.adding").withStyle(s -> s.withColor(0xC080FF))
                : Component.translatable("emi_reorganised.editor.title").append(model.dirty() ? " *" : "");
        int titleW = Math.max(40, width - 4 - 188 - 8 - gridX());
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
                    : pressPalette != null ? List.of(pressPalette) : model.selectedStacks();
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
                graphics.renderTooltip(font, Component.literal(((TabEntry.Banner) cell.entry()).banner().name), mouseX, mouseY);
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
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
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
        if (button == 0 && overTabIcon(mx, my)) {
            Tab tab = model.current();
            minecraft.setScreen(new IconPickerScreen(this, Component.translatable("emi_reorganised.editor.icon_title"), null, tab.icon, (name, icon) -> {
                tab.icon = icon.copy();
                model.markDirty();
            }));
            return true;
        }
        int tabIndex = tabAt(mx, my);
        if (tabIndex >= 0 && button == 0) {
            model.selectTab(tabIndex);
            gridScroll = 0;
            refreshName();
            return true;
        }
        Cell cell = cellAt(mx, my);
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
                pressPalette = pal;
                pressX = mx;
                pressY = my;
                return true;
            }
            if (cell == null) {
                if (inGrid(mx, my)) {
                    model.selection.clear();
                }
                return inGrid(mx, my);
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
        if ((pressRef != null || pressPalette != null || pressGroup != null) && !dragging && Math.abs(mx - pressX) + Math.abs(my - pressY) > 4) {
            dragging = true;
        }
        return dragging || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        try {
            if (dragging) {
                drop(mx, my);
                return true;
            }
            if (pressGroup != null && button == 0) {
                goToGroup(pressGroup);
                return true;
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
        }
    }

    private void drop(double mx, double my) {
        if (model.current() == null || !inGrid(mx, my)) {
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
            ItemStack stack = pressPalette.copy();
            if (member >= 0) {
                model.insertIntoGroup(entry, member, List.of(stack));
            } else {
                model.insert(entry, List.of(new TabEntry.Item(stack)));
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
        boolean typing = getFocused() instanceof EditBox;
        if (!typing && menu == null) {
            if (hasControlDown() && key == GLFW.GLFW_KEY_C) {
                model.copy();
                return true;
            }
            if (hasControlDown() && key == GLFW.GLFW_KEY_X) {
                model.cut();
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
        List<MenuItem> items = new ArrayList<>();
        boolean any = !model.selection.isEmpty();
        boolean itemsSelected = !model.selectedStacks().isEmpty();
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.cut"), model::cut, any));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.copy"), model::copy, any));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.paste"), model::paste, model.canPaste()));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.delete"), model::delete, any));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.new_group"), this::newGroup,
                itemsSelected && (cell == null || cell.kind() != Kind.GROUP)));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.add_to_group"), () -> adding = model.selectedStacks(), itemsSelected));
        items.add(new MenuItem(Component.translatable("emi_reorganised.menu.new_banner"), this::addBanner, model.current() != null));
        if (cell != null && cell.kind() == Kind.GROUP) {
            ItemGroup g = ((TabEntry.Group) cell.entry()).group();
            items.add(new MenuItem(Component.translatable("emi_reorganised.menu.edit_group"), () -> minecraft.setScreen(
                    new GroupEditScreen(this, g, model::markDirty)), true));
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
            items.add(new MenuItem(Component.translatable("emi_reorganised.colors.tip"), () -> {
                BannerDef plain = def.copy(def.id);
                plain.colors = BannerDef.Colors.NONE;
                minecraft.setScreen(new BannerColorScreen(this, BannerView.of(plain, BannerImages.path(def.image)), def.colors, colors -> {
                    def.colors = colors;
                    model.markDirty();
                }));
            }, true));
        }
        if (cell != null && cell.kind() == Kind.BANNER) {
            BannerDef def = ((TabEntry.Banner) cell.entry()).banner();
            if (def.image == null) {
                items.add(new MenuItem(Component.translatable("emi_reorganised.image.create"), () -> {
                    try {
                        def.image = BannerImageFiles.create(def);
                        model.markDirty();
                        BannerImageFiles.show(def.image);
                    } catch (java.io.IOException e) {
                        com.hamish.emireorganised.EmiReorganised.LOGGER.warn("Couldn't create banner image", e);
                    }
                }, true));
            } else {
                items.add(new MenuItem(Component.translatable("emi_reorganised.image.open"), () -> BannerImageFiles.show(def.image), true));
                items.add(new MenuItem(Component.translatable("emi_reorganised.image.remove"), () -> minecraft.setScreen(new ConfirmScreen(yes -> {
                    if (yes && def.image != null) {
                        try {
                            BannerImageFiles.delete(def.image);
                        } catch (java.io.IOException e) {
                            com.hamish.emireorganised.EmiReorganised.LOGGER.warn("Couldn't delete banner image", e);
                        }
                        def.image = null;
                        model.markDirty();
                    }
                    minecraft.setScreen(this);
                }, Component.translatable("emi_reorganised.image.confirm_title"),
                        Component.translatable("emi_reorganised.image.confirm_message", def.image))), true));
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
        String suggested = stacks.isEmpty() ? "" : stacks.getFirst().getHoverName().getString();
        minecraft.setScreen(new TextPromptScreen(this, Component.translatable("emi_reorganised.menu.new_group"), suggested, name -> {
            ItemGroup group = model.newGroup(model.uniqueGroupId(name), name);
            model.closed.remove(group.id);
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
        minecraft.setScreen(new ConfirmScreen(yes -> {
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
        minecraft.setScreen(new ConfirmScreen(yes -> {
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
