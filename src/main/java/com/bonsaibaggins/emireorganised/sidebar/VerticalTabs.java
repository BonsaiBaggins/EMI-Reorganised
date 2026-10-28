package com.bonsaibaggins.emireorganised.sidebar;

import com.bonsaibaggins.emireorganised.skin.ReorganisedSkin;
import com.bonsaibaggins.emireorganised.Config;
import com.bonsaibaggins.emireorganised.data.Tab;
import com.bonsaibaggins.emireorganised.data.TabState;
import com.bonsaibaggins.emireorganised.data.TabStore;
import com.bonsaibaggins.emireorganised.data.FavouriteTabs;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Part of the Reorganised Vanilla skin: columns of tab buttons on the inventory-facing edge of EMI's sidebars: one beside the index (our tabs, with
 * "Search All" first) and one beside the favourites (favourite lists, with a "+" tab to add one).
 */
public final class VerticalTabs {
    private static final int THICKNESS = 26;
    /** Room the tab column takes from its sidebar. */
    private static final int LENGTH = 32;

    private record Entry(Component name, ItemStack icon, boolean plus) {
    }

    private abstract static class Column {
        int scroll;
        @Nullable Bounds area;
        int y0;
        int step = 1;
        int visibleCount;
        /** Skin only: smooth scroll position in pixels, the tab strip's visible span and the arrows. */
        float offset;
        int top;
        int bottom;
        @Nullable Bounds upArrow;
        @Nullable Bounds downArrow;

        abstract boolean enabled();

        abstract SidebarType type();

        abstract boolean leftOfPanel();

        abstract List<Entry> entries();

        abstract int selected();

        abstract void select(int i);

        void rightClick(int i, int x, int y) {
        }

        boolean owns(EmiScreenManager.SidebarPanel panel) {
            return panel.supportsType(type());
        }

        @Nullable
        EmiScreenManager.SidebarPanel panel() {
            EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(type());
            return panel != null && panel.isVisible() && (panel.side == SidebarSide.LEFT || panel.side == SidebarSide.RIGHT)
                    && owns(panel) ? panel : null;
        }

        int width() {
            return enabled() ? LENGTH : 0;
        }
    }

    private static final Column INDEX = new Column() {
        private final List<String> ids = new ArrayList<>();

        @Override
        boolean enabled() {
            if (!ReorganisedSkin.enabled() || !Config.get(Config.VERTICAL_TABS)) {
                return false;
            }
            TabStore.ensureLoaded();
            return TabStore.isLoaded();
        }

        @Override
        SidebarType type() {
            return SidebarType.INDEX;
        }

        @Override
        boolean leftOfPanel() {
            return Config.get(Config.TAB_SIDE) == Config.TabSide.LEFT;
        }

        @Override
        List<Entry> entries() {
            List<Entry> out = new ArrayList<>();
            ids.clear();
            out.add(new Entry(Component.translatable("emi_reorganised.tab.all"), new ItemStack(Items.COMPASS), false));
            ids.add(null);
            for (Tab tab : TabStore.tabs()) {
                if (!tab.hidden) {
                    out.add(new Entry(Component.literal(tab.name), tab.icon.isEmpty() ? new ItemStack(Items.BARRIER) : tab.icon, false));
                    ids.add(tab.id);
                }
            }
            if (Config.get(Config.SHOW_UNSORTED)) {
                out.add(new Entry(Component.translatable("emi_reorganised.tab.unsorted"), new ItemStack(Items.CHEST), false));
                ids.add(TabState.UNSORTED);
            }
            return out;
        }

        @Override
        int selected() {
            return ids.indexOf(TabState.selected());
        }

        @Override
        void select(int i) {
            TabState.select(ids.get(i));
            EmiScreenManager.SidebarPanel panel = panel();
            if (panel != null) {
                panel.page = 0;
            }
            IndexScroll.reset(SidebarType.INDEX);
        }
    };

    private static final Column FAVOURITES = new Column() {
        @Override
        boolean enabled() {
            return ReorganisedSkin.enabled() && Config.get(Config.FAVOURITE_TABS);
        }

        @Override
        SidebarType type() {
            return SidebarType.FAVORITES;
        }

        @Override
        boolean owns(EmiScreenManager.SidebarPanel panel) {
            return panel.supportsType(SidebarType.FAVORITES) && !panel.supportsType(SidebarType.INDEX);
        }

        @Override
        boolean leftOfPanel() {
            return Config.get(Config.FAVOURITE_TAB_SIDE) == Config.TabSide.LEFT;
        }

        @Override
        List<Entry> entries() {
            FavouriteTabs.tick();
            List<Entry> out = new ArrayList<>();
            for (int i = 0; i < FavouriteTabs.tabs().size(); i++) {
                String schematic = FavouriteTabs.tabs().get(i).schematic;
                out.add(new Entry(schematic != null ? Component.literal(schematic) : Component.translatable("emi_reorganised.fav.tab", i + 1),
                        FavouriteTabs.icon(i), false));
            }
            out.add(new Entry(Component.translatable("emi_reorganised.fav.new"), ItemStack.EMPTY, true));
            return out;
        }

        @Override
        int selected() {
            return FavouriteTabs.selected();
        }

        @Override
        void select(int i) {
            if (i >= FavouriteTabs.tabs().size()) {
                FavouriteTabs.add();
            } else {
                FavouriteTabs.select(i);
            }
        }

        @Override
        void rightClick(int i, int x, int y) {
            if (i < FavouriteTabs.tabs().size()) {
                menuTab = i;
                menuX = x;
                menuY = y;
            }
        }
    };

    private static final List<Column> COLUMNS = List.of(INDEX, FAVOURITES);

    private static int dragFrom = -1;
    private static boolean dragMoved;
    private static double dragStartY;
    private static double dragY;
    private static int menuTab = -1;
    private static int menuX;
    private static int menuY;

    private VerticalTabs() {
    }

    public static boolean enabled() {
        return INDEX.enabled();
    }

    public static int size() {
        return THICKNESS;
    }

    public static int spacing() {
        return Config.get(Config.TAB_SPACING);
    }

    /** Width the index tab column takes from the index sidebar (0 when hidden). */
    public static int width() {
        return INDEX.width();
    }

    public static int maxWidth() {
        return Math.max(INDEX.width(), FAVOURITES.width());
    }

    @Nullable
    private static Column columnFor(EmiScreenManager.SidebarPanel panel) {
        for (Column column : COLUMNS) {
            if (column.enabled() && column.owns(panel)) {
                return column;
            }
        }
        return null;
    }

    /** Called while EMI builds a sidebar: takes the scroll bar's and tab column's room from it. */
    public static Bounds reserve(EmiScreenManager.SidebarPanel panel, Bounds bounds) {
        bounds = com.bonsaibaggins.emireorganised.editor.OpenEditorButton.avoid(panel, bounds);
        int extra = IndexScroll.extraWidth(panel.getType());
        if (extra > 0 && bounds.width() > extra) {
            bounds = new Bounds(bounds.x(), bounds.y(), bounds.width() - extra, bounds.height());
        }
        Column column = columnFor(panel);
        int w = column != null ? column.width() : 0;
        if (column == null || bounds.width() <= w) {
            return bounds;
        }
        if (column.leftOfPanel()) {
            return new Bounds(bounds.x() + w, bounds.y(), bounds.width() - w, bounds.height());
        }
        return new Bounds(bounds.x(), bounds.y(), bounds.width() - w, bounds.height());
    }

    /** A sidebar's bounds widened to include its tab column. */
    public static Bounds withColumn(EmiScreenManager.SidebarPanel panel, Bounds bounds) {
        Column column = columnFor(panel);
        if (column == null || column.panel() != panel) {
            return bounds;
        }
        int w = column.width();
        return column.leftOfPanel() ? new Bounds(bounds.x() - w, bounds.y(), bounds.width() + w, bounds.height())
                : new Bounds(bounds.x(), bounds.y(), bounds.width() + w, bounds.height());
    }

    private static String lastQuery = "";
    @Nullable private static Component tooltip;
    private static int tooltipX;
    private static int tooltipY;

    /** ScreenEvent.Render.Post (lowest priority): the hovered tab's name. */
    public static void renderTooltip(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if (tooltip != null) {
            GuiGraphics graphics = event.getGuiGraphics();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 600);
            graphics.renderTooltip(Minecraft.getInstance().font, tooltip, tooltipX, tooltipY);
            graphics.pose().popPose();
            tooltip = null;
        }
    }

    /**
     * EmiScreenManager.drawBackground (head): forget last frame's tabs. Only a visible skinned sidebar draws (and so
     * makes clickable) its tab column again, so tabs come and go with the sidebars.
     */
    public static void beginFrame() {
        tooltip = null;
        clearAreas();
        FavouriteSearch.beginFrame();
        String query = EmiScreenManager.search != null ? EmiScreenManager.search.getValue() : "";
        if (!query.isEmpty() && lastQuery.isEmpty() && Config.get(Config.SEARCH_SHOWS_ALL) && TabState.selected() != null) {
            TabState.select(null);
            IndexScroll.reset(SidebarType.INDEX);
        }
        lastQuery = query;
    }

    /** Another screen is opening: drop the old screen's tab hitboxes, menu and drag. */
    public static void hide() {
        tooltip = null;
        clearAreas();
        menuTab = -1;
        dragFrom = -1;
        dragMoved = false;
    }

    private static void clearAreas() {
        for (Column column : COLUMNS) {
            column.area = null;
            column.upArrow = null;
            column.downArrow = null;
        }
    }

    /** Part of the skin's background: the tab column belonging to a visible skinned sidebar. */
    public static void render(EmiScreenManager.SidebarPanel panel, ReorganisedSkin.Layout skin, GuiGraphics graphics,
                              int mouseX, int mouseY) {
        for (Column column : COLUMNS) {
            if (column.enabled() && column.panel() == panel && panel.getType() == column.type()) {
                renderSkinned(column, skin, graphics, menuTab >= 0 ? -1 : mouseX, mouseY);
            }
        }
    }

    /** EmiScreenManager.drawBackground (end): the favourites search bar, drawn with the sidebars it belongs to. */
    public static void renderBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        EmiScreenManager.SidebarPanel favourites = EmiScreenManager.getPanelFor(SidebarType.FAVORITES);
        if (FavouriteSearch.enabled() && favourites != null && favourites.isVisible() && favourites.space != null
                && favourites.getType() == SidebarType.FAVORITES) {
            FavouriteSearch.render(graphics, favourites.space, mouseX, mouseY, 0);
        }
    }

    /** EmiScreenManager.render (end): the favourites tab menu, which opens over everything. */
    public static void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (FAVOURITES.area == null) {
            menuTab = -1;
        }
        renderMenu(graphics, mouseX, mouseY);
    }

    private static final int SKIN_TAB = 26;
    private static final int SKIN_TAB_SELECTED = 33;
    /** How far a selected tab reaches out of the frame; unselected tabs reach 2px less. */
    private static final int SKIN_REACH = 28;
    private static final int SKIN_ARROW_W = 12;
    private static final int SKIN_ARROW_H = 7;

    /**
     * Tabs in the Reorganised Vanilla skin: they run along the frame's inventory-facing edge between the top of the
     * search slot and the bottom of the item panel, scroll smoothly, and are cut off and dimmed where they cross it.
     */
    private static void renderSkinned(Column column, ReorganisedSkin.Layout skin, GuiGraphics graphics, int mouseX, int mouseY) {
        boolean left = column.leftOfPanel();
        Bounds frame = skin.frame();
        int root = left ? frame.x() : frame.right();
        int top = frame.y() + ReorganisedSkin.EDGE;
        int bottom = frame.bottom() - ReorganisedSkin.EDGE;
        int step = SKIN_TAB + spacing();
        List<Entry> entries = column.entries();
        int maxPx = Math.max(0, entries.size() * step - spacing() - (bottom - top));
        column.scroll = Math.max(0, Math.min(column.scroll, (maxPx + step - 1) / step));
        float target = Math.min(column.scroll * step, maxPx);
        column.offset += (target - column.offset) * 0.4F;
        if (Math.abs(target - column.offset) < 0.5F) {
            column.offset = target;
        }
        int y0 = top - Math.round(column.offset);
        int x = left ? root - SKIN_REACH : root;
        int ix = left ? root - SKIN_TAB + 5 : root + 5;
        column.top = top;
        column.bottom = bottom;
        column.step = step;
        column.y0 = y0 + column.scroll * step;
        column.visibleCount = Math.max(1, (bottom - top) / step);
        int ax = ix + 8 - SKIN_ARROW_W / 2;
        column.upArrow = maxPx > 0 ? new Bounds(ax, frame.y() - 4, SKIN_ARROW_W, SKIN_ARROW_H) : null;
        column.downArrow = maxPx > 0 ? new Bounds(ax, frame.bottom() - 3, SKIN_ARROW_W, SKIN_ARROW_H) : null;
        column.area = new Bounds(x, frame.y() - 4, SKIN_REACH, frame.height() + 8);
        int selectedIndex = column.selected();
        Entry hovered = null;
        var font = Minecraft.getInstance().font;
        graphics.enableScissor(left ? root - 40 : root - 8, top, left ? root + 8 : root + 40, bottom);
        for (int i = 0; i < entries.size(); i++) {
            int y = y0 + i * step;
            if (y + SKIN_TAB <= top || y >= bottom) {
                continue;
            }
            Entry entry = entries.get(i);
            boolean selected = i == selectedIndex;
            boolean hover = mouseX >= x && mouseX < x + SKIN_REACH && mouseY >= Math.max(y, top)
                    && mouseY < Math.min(y + SKIN_TAB, bottom);
            boolean raised = selected || hover;
            boolean cropped = y < top || y + SKIN_TAB > bottom;
            // Unselected, hovered and cut-off tabs sit behind the frame; only a fully visible selected tab joins it.
            boolean behind = !selected || cropped;
            if (behind) {
                if (left) {
                    graphics.enableScissor(root - 40, y, root, y + SKIN_TAB);
                } else {
                    graphics.enableScissor(root, y, root + 40, y + SKIN_TAB);
                }
            }
            if (cropped) {
                graphics.setColor(0.5F, 0.5F, 0.5F, 1.0F);
            }
            ResourceLocation sprite = left ? (raised ? ReorganisedSkin.TAB_RIGHT_SELECTED : ReorganisedSkin.TAB_RIGHT)
                    : (raised ? ReorganisedSkin.TAB_LEFT_SELECTED : ReorganisedSkin.TAB_LEFT);
            int width = raised ? SKIN_TAB_SELECTED : SKIN_TAB;
            int sx = left ? root - (raised ? SKIN_REACH : SKIN_TAB) : root - (raised ? SKIN_TAB_SELECTED - SKIN_REACH : 0);
            graphics.blitSprite(sprite, sx, y, width, SKIN_TAB);
            if (entry.plus()) {
                graphics.drawString(font, "+", ix + 8 - font.width("+") / 2, y + 9, 0xFF404040, false);
            } else {
                graphics.renderItem(entry.icon(), ix, y + 5);
            }
            if (cropped) {
                graphics.flush();
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            if (behind) {
                graphics.disableScissor();
            }
            if (hover) {
                hovered = entry;
            }
        }
        graphics.disableScissor();
        if (column == FAVOURITES && dragFrom >= 0 && dragMoved) {
            int target2 = dropIndex(column, dragY, entries.size() - 1);
            int ly = column.y0 + (target2 - column.scroll) * step + (target2 > dragFrom ? SKIN_TAB + spacing() / 2 : -1 - spacing() / 2);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.fill(x, ly, x + SKIN_REACH, ly + 2, 0xFFFFFFFF);
            graphics.pose().popPose();
        }
        if (column.upArrow != null) {
            ReorganisedSkin.blit(graphics, ReorganisedSkin.SCROLL_UP, column.upArrow);
            ReorganisedSkin.blit(graphics, ReorganisedSkin.SCROLL_DOWN, column.downArrow);
        }
        if (hovered != null) {
            tooltip = hovered.name();
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
    }

    private static final String[] MENU = {"emi_reorganised.fav.set_icon", "emi_reorganised.fav.delete"};

    private static int menuWidth() {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String key : MENU) {
            w = Math.max(w, font.width(Component.translatable(key)));
        }
        return w + 8;
    }

    private static int menuRow(double mouseX, double mouseY) {
        if (mouseX < menuX || mouseX >= menuX + menuWidth() || mouseY < menuY + 2 || mouseY >= menuY + 2 + MENU.length * 12) {
            return -1;
        }
        return (int) ((mouseY - menuY - 2) / 12);
    }

    private static void renderMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menuTab < 0) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int w = menuWidth();
        int h = MENU.length * 12 + 4;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        // The tooltip background reaches 4px past the box it is given.
        TooltipRenderUtil.renderTooltipBackground(graphics, menuX + 4, menuY + 4, w - 8, h - 8, 0);
        int hover = menuRow(mouseX, mouseY);
        for (int i = 0; i < MENU.length; i++) {
            int y = menuY + 2 + i * 12;
            if (i == hover) {
                graphics.fill(menuX + 2, y, menuX + w - 2, y + 12, 0x40FFFFFF);
            }
            graphics.drawString(font, Component.translatable(MENU[i]), menuX + 4, y + 2, 0xFFFFFF);
        }
        graphics.pose().popPose();
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menuTab < 0 && covered(mouseX, mouseY)) {
            return false;
        }
        if (menuTab >= 0) {
            int row = menuRow(mouseX, mouseY);
            int tab = menuTab;
            menuTab = -1;
            if (row == 0) {
                Minecraft mc = Minecraft.getInstance();
                net.minecraft.client.gui.screens.Screen screen = mc.screen;
                if (screen != null) {
                    mc.setScreen(new com.bonsaibaggins.emireorganised.editor.IconPickerScreen(screen,
                            Component.translatable("emi_reorganised.fav.icon_title"), null, FavouriteTabs.icon(tab),
                            (name, icon) -> FavouriteTabs.setIcon(tab, icon)));
                }
            } else if (row == 1) {
                FavouriteTabs.delete(tab);
            }
            return true;
        }
        for (Column column : COLUMNS) {
            if (mouseClicked(column, mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mouseClicked(Column column, double mouseX, double mouseY, int button) {
        Bounds a = column.area;
        if (a == null || (button != 0 && button != 1) || !a.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        EmiScreenManager.SidebarPanel panel = column.panel();
        if (panel == null) {
            return false;
        }
        List<Entry> entries = column.entries();
        int i;
        boolean onTab;
        if (column.upArrow != null && column.upArrow.contains((int) mouseX, (int) mouseY)) {
            column.scroll = Math.max(0, column.scroll - 1);
            return true;
        }
        if (column.downArrow != null && column.downArrow.contains((int) mouseX, (int) mouseY)) {
            column.scroll++;
            return true;
        }
        if (mouseY < column.top || mouseY >= column.bottom) {
            return false;
        }
        int rel = (int) Math.floor(mouseY - column.y0);
        i = column.scroll + Math.floorDiv(rel, column.step);
        onTab = Math.floorMod(rel, column.step) < SKIN_TAB;
        if (i >= 0 && i < entries.size() && onTab) {
            if (button == 1) {
                column.rightClick(i, (int) mouseX, (int) mouseY);
                return true;
            }
            column.select(i);
            if (column == FAVOURITES && i < FavouriteTabs.tabs().size()) {
                dragFrom = i;
                dragMoved = false;
                dragStartY = mouseY;
                dragY = mouseY;
            }
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }

    private static int dropIndex(Column column, double mouseY, int max) {
        int i = column.scroll + (int) Math.floor((mouseY - column.y0) / column.step);
        return Math.max(0, Math.min(max, i));
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (dragFrom < 0) {
            return false;
        }
        dragY = mouseY;
        if (Math.abs(mouseY - dragStartY) > 4) {
            dragMoved = true;
        }
        return dragMoved;
    }

    public static boolean mouseReleased(double mouseX, double mouseY) {
        if (dragFrom < 0) {
            return false;
        }
        boolean moved = dragMoved;
        if (moved) {
            FavouriteTabs.move(dragFrom, dropIndex(FAVOURITES, mouseY, FavouriteTabs.tabs().size() - 1));
        }
        dragFrom = -1;
        dragMoved = false;
        return moved;
    }

    /**
     * True where another mod's UI has told EMI to keep out (such as an LDLib screen drawn over the sidebars), so our
     * tabs and search bar leave those clicks alone.
     */
    public static boolean covered(double mouseX, double mouseY) {
        dev.emi.emi.screen.EmiScreenBase base = dev.emi.emi.screen.EmiScreenBase.getCurrent();
        if (base == null || base.isEmpty()) {
            return false;
        }
        for (Bounds b : dev.emi.emi.registry.EmiExclusionAreas.getExclusion(base)) {
            if (b.contains((int) mouseX, (int) mouseY) && !b.equals(base.bounds())) {
                return true;
            }
        }
        return false;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (covered(mouseX, mouseY)) {
            return false;
        }
        for (Column column : COLUMNS) {
            Bounds a = column.area;
            if (a != null && a.contains((int) mouseX, (int) mouseY)) {
                column.scroll = Math.max(0, column.scroll - (int) Math.signum(amount));
                return true;
            }
        }
        return false;
    }
}
