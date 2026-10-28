package com.bonsaibaggins.emireorganised.skin;

import com.bonsaibaggins.emireorganised.Config;
import com.bonsaibaggins.emireorganised.sidebar.IndexScroll;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarTheme;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.api.widget.Bounds;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The "Reorganised Vanilla" look for EMI's left and right sidebars. Every piece is a GUI sprite under
 * {@code emi_reorganised:skin/}, so resource packs can restyle it; this class only works out where each piece goes.
 */
public final class ReorganisedSkin {
    public static final ResourceLocation FRAME = sprite("frame");
    public static final ResourceLocation SEARCH = sprite("search");
    public static final ResourceLocation PANEL = sprite("panel");
    public static final ResourceLocation TRACK = sprite("scroller_track");
    public static final ResourceLocation THUMB = sprite("scroller");
    public static final ResourceLocation TAB_LEFT = sprite("tab_left");
    public static final ResourceLocation TAB_LEFT_SELECTED = sprite("tab_left_selected");
    public static final ResourceLocation TAB_RIGHT = sprite("tab_right");
    public static final ResourceLocation TAB_RIGHT_SELECTED = sprite("tab_right_selected");
    public static final ResourceLocation SCROLL_UP = sprite("scroll_up");
    public static final ResourceLocation SCROLL_DOWN = sprite("scroll_down");
    public static final ResourceLocation ROLL_LEFT = sprite("roll_left");
    public static final ResourceLocation ROLL_RIGHT = sprite("roll_right");
    public static final ResourceLocation ROLL_LEFT_HIGHLIGHTED = sprite("roll_left_highlighted");
    public static final ResourceLocation ROLL_RIGHT_HIGHLIGHTED = sprite("roll_right_highlighted");
    public static final ResourceLocation EDIT = sprite("edit");
    public static final ResourceLocation EDIT_HIGHLIGHTED = sprite("edit_highlighted");
    /** Roll button size and its gap from the sidebar frame. */
    public static final int ROLL_W = 10;
    public static final int ROLL_H = 20;
    public static final int ROLL_GAP = 30;

    /** Gap between a sidebar and the screen's side edge, and its top and bottom edges. */
    public static final int SCREEN_GAP = 2;
    public static final int SCREEN_GAP_VERTICAL = 8;
    /** Frame thickness: outline, highlight/shadow and face. */
    public static final int EDGE = 5;
    /** The side facing the screen edge also holds the scroll bar, with 2px of face either side. */
    public static final int OUTER_EDGE = EDGE + 2 + 6;
    public static final int TRACK_W = 6;
    public static final int SEARCH_H = 18;
    public static final int GAP = 2;
    public static final int THUMB_H = 15;

    /** Where each part of a skinned sidebar sits on screen. */
    public record Layout(Bounds frame, Bounds search, Bounds panel, Bounds track, boolean left) {
        /** The dark area the items are drawn on (inside the panel's bevel). */
        public Bounds inner() {
            return new Bounds(panel.x() + 1, panel.y() + 1, panel.width() - 2, panel.height() - 2);
        }
    }

    private static final Map<SidebarSide, Layout> LAYOUTS = new EnumMap<>(SidebarSide.class);

    private ReorganisedSkin() {
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath("emi_reorganised", "skin/" + name);
    }

    public static boolean enabled() {
        return com.bonsaibaggins.emireorganised.EmiReorganised.active();
    }

    public static boolean applies(@Nullable EmiScreenManager.SidebarPanel panel) {
        return enabled() && panel != null && (panel.side == SidebarSide.LEFT || panel.side == SidebarSide.RIGHT)
                && panel.getType() != dev.emi.emi.config.SidebarType.CHESS;
    }

    /** The skin's layout of a visible skinned sidebar, or null. */
    @Nullable
    public static Layout layout(@Nullable EmiScreenManager.SidebarPanel panel) {
        if (!applies(panel) || panel.space == null || !panel.isVisible()) {
            return null;
        }
        return LAYOUTS.get(panel.side);
    }

    /**
     * Replaces the space EMI just built for a left/right sidebar with one laid out by the skin, inside the room EMI
     * worked out for it ({@code bounds}, already clear of exclusion areas, the tab column and the roll animation).
     */
    public static void relayout(EmiScreenManager.SidebarPanel panel, Bounds bounds, int maxColumns, boolean rtl) {
        boolean left = panel.side == SidebarSide.LEFT;
        int room = bounds.width() - SCREEN_GAP - EDGE - OUTER_EDGE - 4;
        int tw = Math.max(0, Math.min(maxColumns, room / 18));
        int frameW = tw * 18 + 4 + EDGE + OUTER_EDGE;
        int frameX = left ? bounds.x() + SCREEN_GAP : bounds.right() - SCREEN_GAP - frameW;
        int frameY = bounds.y() + SCREEN_GAP_VERTICAL;
        int frameH = bounds.height() - SCREEN_GAP_VERTICAL * 2;
        int panelX = frameX + (left ? OUTER_EDGE : EDGE);
        int panelW = tw * 18 + 4;
        Bounds search = new Bounds(panelX, frameY + EDGE, panelW, SEARCH_H);
        int panelY = search.bottom() + GAP;
        Bounds panelBounds = new Bounds(panelX, panelY, panelW, frameY + frameH - EDGE - panelY);
        int trackX = left ? frameX + EDGE + GAP : panelBounds.right() + GAP;
        Bounds track = new Bounds(trackX, panelY, TRACK_W, panelBounds.height());
        int tx = panelX + 2;
        int ty = panelY + 2;
        int th = Math.max(0, (panelBounds.bottom() - 1 - ty + 17) / 18);
        if (IndexScroll.enabled(panel.getType())) {
            th++; // A row scrolled partly out of the top lets one more in at the bottom.
        }
        if (tw <= 0 || panelBounds.height() < 20) {
            tw = 0;
            th = 0;
        }
        panel.header = false;
        panel.theme = SidebarTheme.TRANSPARENT;
        EmiScreenManager.ScreenSpace space = new EmiScreenManager.ScreenSpace(tx, ty, tw, th, rtl, List.of(),
                panel::getType, panel.isSearch());
        panel.setSpaces(space, List.of());
        LAYOUTS.put(panel.side, new Layout(new Bounds(frameX, frameY, frameW, frameH), search, panelBounds, track, left));
    }

    /** Frame, search bar slot and item panel, drawn behind the items. */
    public static void drawBackground(EmiDrawContext context, EmiScreenManager.SidebarPanel panel, int mouseX, int mouseY) {
        Layout layout = layout(panel);
        if (layout == null) {
            return;
        }
        GuiGraphics graphics = context.raw();
        blit(graphics, FRAME, layout.frame());
        blit(graphics, SEARCH, layout.search());
        blit(graphics, PANEL, layout.panel());
        float scroll = IndexScroll.position(panel.getType());
        if (scroll >= 0) {
            drawScrollbar(graphics, layout, scroll);
        }
        com.bonsaibaggins.emireorganised.sidebar.VerticalTabs.render(panel, layout, graphics, mouseX, mouseY);
    }

    /** The scroll bar set into the frame; {@code frac} is the scroll position from 0 to 1. */
    public static void drawScrollbar(GuiGraphics graphics, Layout layout, float frac) {
        Bounds track = layout.track();
        blit(graphics, TRACK, track);
        int travel = track.height() - 2 - THUMB_H;
        SkinSprites.draw(graphics, THUMB, track.x() + 1, track.y() + 1 + Math.round(travel * frac), TRACK_W - 2, THUMB_H);
    }

    /**
     * Puts a borderless text box inside the skin's search slot. A borderless box draws its text at its top edge, so
     * the box starts where the text should; {@code inset} is the text's distance from the slot's left edge.
     */
    public static void placeSearch(net.minecraft.client.gui.components.EditBox box, Layout layout, int inset) {
        Bounds search = layout.search();
        box.setX(search.x() + inset);
        box.setY(search.y() + 5);
        box.setWidth(Math.max(1, search.width() - inset * 2));
        box.setHeight(search.height() - 6);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation sprite, Bounds b) {
        SkinSprites.draw(graphics, sprite, b.x(), b.y(), b.width(), b.height());
    }

    /** The layout whose panel holds {@code space}, or null when it isn't a skinned sidebar. */
    @Nullable
    public static Layout layoutAt(EmiScreenManager.ScreenSpace space) {
        if (!enabled()) {
            return null;
        }
        for (Layout layout : LAYOUTS.values()) {
            Bounds inner = layout.inner();
            if (space.tx >= inner.x() && space.tx < inner.right() && space.ty >= inner.y() && space.ty < inner.bottom()) {
                return layout;
            }
        }
        return null;
    }

    /** Clips item drawing to the panel so rows that run past it are cut off at its edge. */
    public static boolean beginClip(GuiGraphics graphics, EmiScreenManager.ScreenSpace space) {
        Layout layout = layoutAt(space);
        if (layout == null) {
            return false;
        }
        Bounds inner = layout.inner();
        graphics.enableScissor(inner.x(), inner.y(), inner.right(), inner.bottom());
        return true;
    }

    public static void forget(SidebarSide side) {
        LAYOUTS.remove(side);
    }
}
