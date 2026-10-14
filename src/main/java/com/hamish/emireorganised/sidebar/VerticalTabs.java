package com.hamish.emireorganised.sidebar;

import com.hamish.emireorganised.Config;
import com.hamish.emireorganised.data.Tab;
import com.hamish.emireorganised.data.TabState;
import com.hamish.emireorganised.data.TabStore;
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
 * A column of vanilla-style tab buttons on the outer edge of EMI's index sidebar. The first tab ("Search All") shows
 * the full index; the others show one of our tabs.
 */
public final class VerticalTabs {
    /** How far the tabs sit over (and below the top of) the index, so the selected tab joins the vanilla frame. */
    private static final int OVERLAP = 3;
    private static final ResourceLocation TAB = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2");
    private static final ResourceLocation TAB_SELECTED = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_2");
    private static final ResourceLocation SCROLL_UP = ResourceLocation.withDefaultNamespace("transferable_list/move_up");
    private static final ResourceLocation SCROLL_DOWN = ResourceLocation.withDefaultNamespace("transferable_list/move_down");

    private static int scroll;
    /** Bounds of the tab column, in screen space; null while not shown. */
    @Nullable private static Bounds area;
    private static SidebarSide side = SidebarSide.RIGHT;
    private static int slideOffset;

    private VerticalTabs() {
    }

    public static boolean enabled() {
        if (!Config.get(Config.VERTICAL_TABS)) {
            return false;
        }
        TabStore.ensureLoaded();
        return TabStore.isLoaded();
    }

    public static int size() {
        return Config.get(Config.TAB_SIZE);
    }

    public static int spacing() {
        return Config.get(Config.TAB_SPACING);
    }

    /** Width the tab column takes from the index sidebar (0 when hidden). */
    public static int width() {
        return enabled() ? size() + 6 : 0;
    }

    /** Called while EMI builds the index sidebar: takes the tab column's room from the outer edge. */
    public static Bounds reserve(EmiScreenManager.SidebarPanel panel, Bounds bounds) {
        bounds = com.hamish.emireorganised.editor.OpenEditorButton.avoid(panel, bounds);
        int extra = IndexScroll.extraWidth(panel.getType());
        if (extra > 0 && bounds.width() > extra) {
            bounds = new Bounds(bounds.x(), bounds.y(), bounds.width() - extra, bounds.height());
        }
        if (!enabled() || !panel.supportsType(SidebarType.INDEX) || bounds.width() <= width()) {
            return bounds;
        }
        int w = width();
        side = panel.side;
        if (leftOfIndex()) {
            return new Bounds(bounds.x() + w, bounds.y(), bounds.width() - w, bounds.height());
        }
        return new Bounds(bounds.x(), bounds.y(), bounds.width() - w, bounds.height());
    }

    private static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(null, Component.translatable("emi_reorganised.tab.all"), new ItemStack(Items.COMPASS)));
        for (Tab tab : TabStore.tabs()) {
            if (!tab.hidden) {
                out.add(new Entry(tab.id, Component.literal(tab.name), tab.icon.isEmpty() ? new ItemStack(Items.BARRIER) : tab.icon));
            }
        }
        if (Config.get(Config.SHOW_UNSORTED)) {
            out.add(new Entry(TabState.UNSORTED, Component.translatable("emi_reorganised.tab.unsorted"), new ItemStack(Items.CHEST)));
        }
        return out;
    }

    private record Entry(@Nullable String id, Component name, ItemStack icon) {
    }

    @Nullable
    private static EmiScreenManager.SidebarPanel indexPanel() {
        EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(SidebarType.INDEX);
        return panel != null && panel.isVisible() && (panel.side == SidebarSide.LEFT || panel.side == SidebarSide.RIGHT) ? panel : null;
    }

    /** Horizontal shift applied by the rollout animation. */
    public static void setSlideOffset(int offset) {
        slideOffset = offset;
    }

    /** How far a tab sticks out from the index; selected and hovered tabs grow by 2 more. */
    private static int depth() {
        return size() + 4;
    }

    private static boolean leftOfIndex() {
        return Config.get(Config.TAB_SIDE) == Config.TabSide.LEFT;
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        area = null;
        EmiScreenManager.SidebarPanel panel = enabled() ? indexPanel() : null;
        if (panel == null || panel.getType() != SidebarType.INDEX) {
            return;
        }
        Bounds bounds = panel.getBounds();
        int s = size();
        int w = width();
        boolean left = leftOfIndex();
        int edgeLeft = bounds.x();
        int edgeRight = bounds.right();
        if (panel.theme == dev.emi.emi.config.SidebarTheme.VANILLA && panel.space != null) {
            edgeLeft = panel.space.tx - 9;
            edgeRight = panel.space.tx + panel.space.tw * 18 + 9 + IndexScroll.extraWidth(panel.getType());
        }
        int x = (left ? edgeLeft - w + OVERLAP : edgeRight - OVERLAP) + slideOffset;
        int top = bounds.y() + OVERLAP;
        int step = s + spacing();
        List<Entry> entries = entries();
        int visible = Math.max(1, (bounds.height() - (entries.size() * step > bounds.height() ? 20 : 0)) / step);
        scroll = Math.max(0, Math.min(scroll, entries.size() - visible));
        boolean scrolling = entries.size() > visible;
        int y0 = top + (scrolling ? 10 : 0);
        area = new Bounds(x, top, w, bounds.height());
        Entry hovered = null;
        for (int i = scroll; i < Math.min(entries.size(), scroll + visible); i++) {
            Entry entry = entries.get(i);
            int y = y0 + (i - scroll) * step;
            boolean selected = java.util.Objects.equals(entry.id(), TabState.selected());
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + s;
            int d = depth() + (selected || hover ? 2 : 0);
            int tx = left ? x + w - d : x;
            if (!selected) {
                // Unselected tabs sit behind the index frame: clip off the part that would overlap it.
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
            graphics.renderItem(entry.icon(), ix, iy);
            if (!selected) {
                graphics.disableScissor();
            }
            if (hover) {
                hovered = entry;
            }
        }
        if (scrolling) {
            graphics.blitSprite(SCROLL_UP, x + w / 2 - 16, top - 6, 32, 32);
            graphics.blitSprite(SCROLL_DOWN, x + w / 2 - 16, top + bounds.height() - 26, 32, 32);
        }
        if (hovered != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, hovered.name(), mouseX, mouseY);
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        Bounds a = area;
        if (a == null || button != 0 || !a.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        EmiScreenManager.SidebarPanel panel = indexPanel();
        if (panel == null) {
            return false;
        }
        Bounds bounds = panel.getBounds();
        int s = size();
        int step = s + spacing();
        List<Entry> entries = entries();
        int visible = Math.max(1, (bounds.height() - (entries.size() * step > bounds.height() ? 20 : 0)) / step);
        boolean scrolling = entries.size() > visible;
        int y0 = a.y() + (scrolling ? 10 : 0);
        if (scrolling && mouseY < y0) {
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        int i = scroll + (int) ((mouseY - y0) / step);
        if (scrolling && i >= scroll + visible) {
            scroll++;
            return true;
        }
        if (i >= 0 && i < entries.size() && (mouseY - y0) % step < s) {
            TabState.select(entries.get(i).id());
            panel.page = 0;
            IndexScroll.reset(SidebarType.INDEX);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return true;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        Bounds a = area;
        if (a == null || !a.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        scroll = Math.max(0, scroll - (int) Math.signum(amount));
        return true;
    }

    public static SidebarSide side() {
        return side;
    }
}
