package com.hamish.emireorganised.sidebar;

import com.hamish.emireorganised.Config;
import com.hamish.emireorganised.data.Tab;
import com.hamish.emireorganised.data.TabState;
import com.hamish.emireorganised.data.TabStore;
import com.hamish.emireorganised.data.FavouriteTabs;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Columns of vanilla-style tab buttons on the outer edge of EMI's sidebars: one beside the index (our tabs, with
 * "Search All" first) and one beside the favourites (favourite lists, with a "+" tab to add one).
 */
public final class VerticalTabs {
    /** How far the tabs sit over (and below the top of) the sidebar, so the selected tab joins the vanilla frame. */
    private static final int OVERLAP = 3;
    private static final ResourceLocation TAB = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2");
    private static final ResourceLocation TAB_SELECTED = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_2");
    private static final ResourceLocation SCROLL_UP = ResourceLocation.withDefaultNamespace("transferable_list/move_up");
    private static final ResourceLocation SCROLL_DOWN = ResourceLocation.withDefaultNamespace("transferable_list/move_down");

    private record Entry(Component name, ItemStack icon, boolean plus) {
    }

    private abstract static class Column {
        int scroll;
        @Nullable Bounds area;

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
            return enabled() ? size() + 6 : 0;
        }
    }

    private static final Column INDEX = new Column() {
        private final List<String> ids = new ArrayList<>();

        @Override
        boolean enabled() {
            if (!Config.get(Config.VERTICAL_TABS)) {
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
            return Config.get(Config.FAVOURITE_TABS);
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
                out.add(new Entry(Component.translatable("emi_reorganised.fav.tab", i + 1), FavouriteTabs.icon(i), false));
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

    private static int menuTab = -1;
    private static int menuX;
    private static int menuY;

    private VerticalTabs() {
    }

    public static boolean enabled() {
        return INDEX.enabled();
    }

    public static int size() {
        return Config.get(Config.TAB_SIZE);
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
        bounds = com.hamish.emireorganised.editor.OpenEditorButton.avoid(panel, bounds);
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

    /** How far a tab sticks out from the sidebar; selected and hovered tabs grow by 2 more. */
    private static int depth() {
        return size() + 4;
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Column column : COLUMNS) {
            render(column, graphics, menuTab >= 0 ? -1 : mouseX, mouseY);
        }
        renderMenu(graphics, mouseX, mouseY);
    }

    private static void render(Column column, GuiGraphics graphics, int mouseX, int mouseY) {
        column.area = null;
        EmiScreenManager.SidebarPanel panel = column.enabled() ? column.panel() : null;
        if (panel == null || panel.getType() != column.type()) {
            return;
        }
        Bounds bounds = panel.getBounds();
        int s = size();
        int w = column.width();
        boolean left = column.leftOfPanel();
        int edgeLeft = bounds.x();
        int edgeRight = bounds.right();
        if (panel.theme == dev.emi.emi.config.SidebarTheme.VANILLA && panel.space != null) {
            edgeLeft = panel.space.tx - 9;
            edgeRight = panel.space.tx + panel.space.tw * 18 + 9 + IndexScroll.extraWidth(panel.getType());
        }
        int x = (left ? edgeLeft - w + OVERLAP : edgeRight - OVERLAP) + 1;
        int top = bounds.y() + OVERLAP;
        int step = s + spacing();
        List<Entry> entries = column.entries();
        int visible = Math.max(1, (bounds.height() - (entries.size() * step > bounds.height() ? 20 : 0)) / step);
        column.scroll = Math.max(0, Math.min(column.scroll, entries.size() - visible));
        boolean scrolling = entries.size() > visible;
        int y0 = top + (scrolling ? 10 : 0);
        column.area = new Bounds(x, top, w, bounds.height());
        int selectedIndex = column.selected();
        Entry hovered = null;
        var font = Minecraft.getInstance().font;
        for (int i = column.scroll; i < Math.min(entries.size(), column.scroll + visible); i++) {
            Entry entry = entries.get(i);
            int y = y0 + (i - column.scroll) * step;
            boolean selected = i == selectedIndex;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + s;
            int d = depth() + (selected || hover ? 2 : 0);
            int tx = left ? x + w - d : x;
            if (!selected) {
                // Unselected tabs sit behind the sidebar frame: clip off the part that would overlap it.
                if (left) {
                    graphics.enableScissor(tx - 4, y - 4, edgeLeft, y + s + 4);
                } else {
                    graphics.enableScissor(edgeRight, y - 4, tx + d + 4, y + s + 4);
                }
            }
            var pose = graphics.pose();
            pose.pushPose();
            if (left) {
                // Rotated and mirrored so the sprite's highlight ends up on top, as on the right side.
                pose.translate(tx, y, 0);
                pose.scale(-1, 1, 1);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
                com.mojang.blaze3d.systems.RenderSystem.disableCull();
            } else {
                pose.translate(tx + d, y, 0);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
            }
            graphics.blitSprite(selected ? TAB_SELECTED : TAB, 0, 0, s, d);
            com.mojang.blaze3d.systems.RenderSystem.enableCull();
            pose.popPose();
            int ix = tx + (d - 16) / 2 + (left ? -1 : 1);
            int iy = y + (s - 16) / 2;
            if (entry.plus()) {
                graphics.drawString(font, "+", ix + 8 - font.width("+") / 2, iy + 4, 0xFF404040, false);
            } else {
                graphics.renderItem(entry.icon(), ix, iy);
            }
            if (!selected) {
                graphics.disableScissor();
            }
            if (hover) {
                hovered = entry;
            }
        }
        if (scrolling) {
            // The arrow sits in the right half of the 32px sprite: centre that half on the tabs.
            int ax = (left ? x + w - depth() / 2 : x + depth() / 2) - 24;
            graphics.blitSprite(SCROLL_UP, ax, top - 6, 32, 32);
            graphics.blitSprite(SCROLL_DOWN, ax, top + bounds.height() - 26, 32, 32);
        }
        if (hovered != null) {
            graphics.renderTooltip(font, hovered.name(), mouseX, mouseY);
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
        graphics.fill(menuX, menuY, menuX + w, menuY + h, 0xF0100010);
        graphics.renderOutline(menuX, menuY, w, h, 0xFF5000FF);
        int hover = menuRow(mouseX, mouseY);
        for (int i = 0; i < MENU.length; i++) {
            int y = menuY + 2 + i * 12;
            if (i == hover) {
                graphics.fill(menuX + 1, y, menuX + w - 1, y + 12, 0x40FFFFFF);
            }
            graphics.drawString(font, Component.translatable(MENU[i]), menuX + 4, y + 2, 0xFFFFFF);
        }
        graphics.pose().popPose();
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menuTab >= 0) {
            int row = menuRow(mouseX, mouseY);
            int tab = menuTab;
            menuTab = -1;
            if (row == 0) {
                Minecraft mc = Minecraft.getInstance();
                net.minecraft.client.gui.screens.Screen screen = mc.screen;
                if (screen != null) {
                    mc.setScreen(new com.hamish.emireorganised.editor.IconPickerScreen(screen,
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
        Bounds bounds = panel.getBounds();
        int s = size();
        int step = s + spacing();
        List<Entry> entries = column.entries();
        int visible = Math.max(1, (bounds.height() - (entries.size() * step > bounds.height() ? 20 : 0)) / step);
        boolean scrolling = entries.size() > visible;
        int y0 = a.y() + (scrolling ? 10 : 0);
        if (button == 0 && scrolling && mouseY < y0) {
            column.scroll = Math.max(0, column.scroll - 1);
            return true;
        }
        int i = column.scroll + (int) ((mouseY - y0) / step);
        if (button == 0 && scrolling && i >= column.scroll + visible) {
            column.scroll++;
            return true;
        }
        if (i >= 0 && i < entries.size() && (mouseY - y0) % step < s) {
            if (button == 1) {
                column.rightClick(i, (int) mouseX, (int) mouseY);
                return true;
            }
            column.select(i);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
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
