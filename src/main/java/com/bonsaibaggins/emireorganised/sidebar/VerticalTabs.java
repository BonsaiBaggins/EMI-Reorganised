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
 * Columns of vanilla-style tab buttons on the outer edge of EMI's sidebars: one beside the index (our tabs, with
 * "Search All" first) and one beside the favourites (favourite lists, with a "+" tab to add one).
 */
public final class VerticalTabs {
    /** How far the tabs sit over (and below the top of) the sidebar, so the selected tab joins the vanilla frame. */
    private static final int OVERLAP = 3;
    private static final ResourceLocation SCROLL_UP = ResourceLocation.withDefaultNamespace("transferable_list/move_up");
    private static final ResourceLocation SCROLL_DOWN = ResourceLocation.withDefaultNamespace("transferable_list/move_down");
    /** Vanilla creative tab sprites, drawn transposed (rotated 90 degrees anticlockwise, then flipped vertically). */
    private static final ResourceLocation LEFT_UNSELECTED = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2");
    private static final ResourceLocation LEFT_SELECTED = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_2");
    private static final ResourceLocation RIGHT_UNSELECTED = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_2");
    private static final ResourceLocation RIGHT_SELECTED = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_2");
    /** Vanilla tab sprite size: 26 wide, 32 long. Transposed, it is 26 tall and sticks 32 out from the panel. */
    private static final int THICKNESS = 26;
    private static final int LENGTH = 32;
    /** How far tabs sit into the panel edge (under it, or over it when selected). */
    private static final int SHIFT = 4;

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
        boolean skinned;
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

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        tooltip = null;
        String query = EmiScreenManager.search != null ? EmiScreenManager.search.getValue() : "";
        if (!query.isEmpty() && lastQuery.isEmpty() && Config.get(Config.SEARCH_SHOWS_ALL) && TabState.selected() != null) {
            TabState.select(null);
            IndexScroll.reset(SidebarType.INDEX);
        }
        lastQuery = query;
        FavouriteSearch.beginFrame();
        EmiScreenManager.SidebarPanel favourites = EmiScreenManager.getPanelFor(SidebarType.FAVORITES);
        if (FavouriteSearch.enabled() && favourites != null && favourites.isVisible() && favourites.space != null
                && favourites.getType() == SidebarType.FAVORITES) {
            FavouriteSearch.render(graphics, favourites.space, favourites.getBounds().top() - 20, mouseX, mouseY, 0);
        }
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
        column.skinned = false;
        ReorganisedSkin.Layout skin = ReorganisedSkin.layout(panel);
        if (skin != null) {
            renderSkinned(column, skin, graphics, mouseX, mouseY);
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
        // Tabs grow outwards from the frame's outer edge (their root); x/w only describe the clickable column.
        int root = left ? edgeLeft : edgeRight;
        int x = left ? root - w : root;
        int top = bounds.y() + OVERLAP;
        int step = s + spacing();
        List<Entry> entries = column.entries();
        int visible = Math.max(1, (bounds.height() - (entries.size() * step > bounds.height() ? 20 : 0)) / step);
        column.scroll = Math.max(0, Math.min(column.scroll, entries.size() - visible));
        boolean scrolling = entries.size() > visible;
        int y0 = top + (scrolling ? 10 : 0);
        column.area = new Bounds(x, top, w, bounds.height());
        column.y0 = y0;
        column.step = step;
        column.visibleCount = visible;
        int selectedIndex = column.selected();
        Entry hovered = null;
        var font = Minecraft.getInstance().font;
        for (int i = column.scroll; i < Math.min(entries.size(), column.scroll + visible); i++) {
            Entry entry = entries.get(i);
            int y = y0 + (i - column.scroll) * step;
            boolean selected = i == selectedIndex;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + s;
            boolean raised = selected || hover;
            int shift = SHIFT;
            int sx = left ? root - LENGTH + shift : root - shift;
            if (!selected) {
                // Unselected and hovered tabs sit behind the sidebar frame: clip at the frame edge.
                if (left) {
                    graphics.enableScissor(sx, y, root, y + s);
                } else {
                    graphics.enableScissor(root, y, sx + LENGTH, y + s);
                }
            }
            ResourceLocation sprite = left ? (raised ? LEFT_SELECTED : LEFT_UNSELECTED) : (raised ? RIGHT_SELECTED : RIGHT_UNSELECTED);
            drawTransposed(graphics, sprite, sx, y, LENGTH, s);
            int ix = sx + (LENGTH - 16) / 2;
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
        if (column == FAVOURITES && dragFrom >= 0 && dragMoved) {
            int target = dropIndex(column, dragY, entries.size() - 1);
            int ly = y0 + (target - column.scroll) * step + (target > dragFrom ? s + spacing() / 2 : -1 - spacing() / 2);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.fill(x, ly, x + w, ly + 2, 0xFFFFFFFF);
            graphics.pose().popPose();
        }
        if (scrolling) {
            // The arrow sits in the right half of the 32px sprite: centre that half on the tabs.
            int ax = (left ? x + w - LENGTH / 2 : x + LENGTH / 2) - 24;
            graphics.blitSprite(SCROLL_UP, ax, top - 6, 32, 32);
            graphics.blitSprite(SCROLL_DOWN, ax, top + bounds.height() - 26, 32, 32);
        }
        if (hovered != null) {
            // Drawn once the whole screen has rendered (see renderTooltip): drawing it here, inside EMI's render,
            // shows an empty box.
            tooltip = hovered.name();
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
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
        column.skinned = true;
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
            if (!selected) {
                // Unselected and hovered tabs sit behind the frame.
                if (left) {
                    graphics.enableScissor(root - 40, y, root, y + SKIN_TAB);
                } else {
                    graphics.enableScissor(root, y, root + 40, y + SKIN_TAB);
                }
            }
            boolean cropped = y < top || y + SKIN_TAB > bottom;
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
            if (!selected) {
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

    /** Blits a GUI sprite with its axes swapped, so the sprite's length runs across the screen. */
    private static void drawTransposed(GuiGraphics graphics, ResourceLocation id, int x, int y, int w, int h) {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = Minecraft.getInstance().getGuiSprites().getSprite(id);
        graphics.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        org.joml.Matrix4f m = graphics.pose().last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder buf = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        // Screen x follows the sprite's v axis and screen y its u axis.
        buf.addVertex(m, x, y, 0).setUv(sprite.getU0(), sprite.getV0());
        buf.addVertex(m, x, y + h, 0).setUv(sprite.getU1(), sprite.getV0());
        buf.addVertex(m, x + w, y + h, 0).setUv(sprite.getU1(), sprite.getV1());
        buf.addVertex(m, x + w, y, 0).setUv(sprite.getU0(), sprite.getV1());
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buf.buildOrThrow());
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
        if (column.skinned) {
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
        } else {
            Bounds bounds = panel.getBounds();
            int s = size();
            int step = s + spacing();
            int visible = Math.max(1, (bounds.height() - (entries.size() * step > bounds.height() ? 20 : 0)) / step);
            boolean scrolling = entries.size() > visible;
            int y0 = a.y() + (scrolling ? 10 : 0);
            if (button == 0 && scrolling && mouseY < y0) {
                column.scroll = Math.max(0, column.scroll - 1);
                return true;
            }
            i = column.scroll + (int) ((mouseY - y0) / step);
            if (button == 0 && scrolling && i >= column.scroll + visible) {
                column.scroll++;
                return true;
            }
            onTab = (mouseY - y0) % step < s;
        }
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
