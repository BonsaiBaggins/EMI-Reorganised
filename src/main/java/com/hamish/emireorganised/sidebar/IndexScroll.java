package com.hamish.emireorganised.sidebar;

import com.hamish.emireorganised.Config;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

/** Row-by-row scrolling (with a scroll bar) for the index and favourites sidebars, replacing EMI's pages. */
public final class IndexScroll {
    private static final int BAR_W = 3;
    /** Distance from the right edge of the item grid to the bar: the frame's bevel line plus a grey gap. */
    private static final int BAR_INSET = 4;
    private static final Map<SidebarType, Integer> ROWS = new EnumMap<>(SidebarType.class);
    private static final Map<SidebarType, Integer> MAX = new EnumMap<>(SidebarType.class);
    private static final Map<SidebarType, Bounds> BARS = new EnumMap<>(SidebarType.class);
    @Nullable private static SidebarType dragging;

    private IndexScroll() {
    }

    private static final net.minecraft.resources.ResourceLocation SCROLLER =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");

    private static Config.ScrollbarStyle style() {
        return Config.get(Config.SCROLLBAR_STYLE);
    }

    private static int trackWidth() {
        return switch (style()) {
            case NARROW -> BAR_W;
            case VANILLA_CREATIVE -> 14;
            case NARROW_CREATIVE -> 8;
        };
    }

    /** Width a scrolling panel needs beyond EMI's normal frame padding to fit the bar (and, for the creative styles, a frame margin around it). */
    public static int extraWidth(@Nullable SidebarType type) {
        if (!enabled(type)) {
            return 0;
        }
        return BAR_INSET - 1 + trackWidth();
    }

    public static boolean enabled(@Nullable SidebarType type) {
        if (type == SidebarType.INDEX) {
            return Config.get(Config.INDEX_SCROLLING);
        }
        if (type == SidebarType.FAVORITES) {
            return Config.get(Config.FAVOURITES_SCROLLING);
        }
        return false;
    }

    /** Last row a space with {@code size} stacks can scroll to while still filling its page. */
    public static int maxRow(EmiScreenManager.ScreenSpace space, int size) {
        if (space.tw <= 0 || size <= space.pageSize) {
            return 0;
        }
        return (size - space.pageSize + space.tw - 1) / space.tw;
    }

    /** Clamps and returns the scroll row for a space whose full list has {@code size} stacks. */
    public static int row(EmiScreenManager.ScreenSpace space, SidebarType type, int size) {
        int max = maxRow(space, size);
        MAX.put(type, max);
        int row = Math.max(0, Math.min(ROWS.getOrDefault(type, 0), max));
        ROWS.put(type, row);
        return row;
    }

    public static void scrollBy(SidebarType type, int delta) {
        set(type, ROWS.getOrDefault(type, 0) + delta);
    }

    public static void reset(SidebarType type) {
        ROWS.put(type, 0);
    }

    private static void set(SidebarType type, int row) {
        ROWS.put(type, Math.max(0, Math.min(row, MAX.getOrDefault(type, Integer.MAX_VALUE))));
    }

    public static void renderBar(GuiGraphics graphics, EmiScreenManager.ScreenSpace space, SidebarType type) {
        int max = MAX.getOrDefault(type, 0);
        if (max <= 0) {
            BARS.remove(type);
            return;
        }
        boolean narrow = style() == Config.ScrollbarStyle.NARROW;
        int tw = trackWidth();
        int x = space.tx + space.tw * 18 + BAR_INSET;
        int top = space.ty;
        int h = space.th * 18;
        BARS.put(type, new Bounds(x - 1, top, tw + 2, h));
        int thumb = thumbHeight(h, max, space.th);
        float frac = ROWS.getOrDefault(type, 0) / (float) max;
        if (!narrow) {
            graphics.fill(x, top, x + tw, top + h, 0xFF8B8B8B);
            graphics.fill(x, top, x + tw - 1, top + 1, 0xFF373737);
            graphics.fill(x, top, x + 1, top + h - 1, 0xFF373737);
            graphics.fill(x + 1, top + h - 1, x + tw, top + h, 0xFFFFFFFF);
            graphics.fill(x + tw - 1, top + 1, x + tw, top + h, 0xFFFFFFFF);
            graphics.blitSprite(SCROLLER, x + 1, top + 1 + Math.round((h - 2 - thumb) * frac), tw - 2, thumb);
            return;
        }
        int ty = top + Math.round((h - thumb) * frac);
        graphics.fill(x, top, x + BAR_W, top + h, 0x50000000);
        boolean active = dragging == type;
        graphics.fill(x, ty, x + BAR_W, ty + thumb, active ? 0xFFFFFFFF : 0xFFC6C6C6);
        graphics.fill(x + BAR_W - 1, ty + 1, x + BAR_W, ty + thumb, 0xFF808080);
    }

    private static int thumbHeight(int h, int max, int visibleRows) {
        if (style() != Config.ScrollbarStyle.NARROW) {
            return 15;
        }
        return Math.max(8, Math.min(h, h * visibleRows / Math.max(1, visibleRows + max)));
    }

    private static void dragTo(SidebarType type, double mouseY) {
        Bounds bar = BARS.get(type);
        int max = MAX.getOrDefault(type, 0);
        if (bar == null || max <= 0) {
            return;
        }
        int thumb = thumbHeight(bar.height(), max, Math.max(1, bar.height() / 18));
        double t = (mouseY - bar.y() - thumb / 2.0) / Math.max(1, bar.height() - thumb);
        set(type, (int) Math.round(Math.max(0, Math.min(1, t)) * max));
    }

    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        BARS.clear();
    }

    /** A new screen is opening: forget the old screen's scrollbars so they can't take its clicks. */
    public static void clearBars() {
        BARS.clear();
        dragging = null;
    }

    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        for (Map.Entry<SidebarType, Bounds> entry : BARS.entrySet()) {
            if (entry.getValue().contains((int) event.getMouseX(), (int) event.getMouseY())) {
                dragging = entry.getKey();
                dragTo(dragging, event.getMouseY());
                event.setCanceled(true);
                return;
            }
        }
    }

    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (dragging != null) {
            dragTo(dragging, event.getMouseY());
            event.setCanceled(true);
        }
    }

    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (dragging != null && event.getButton() == 0) {
            dragging = null;
            event.setCanceled(true);
        }
    }
}
