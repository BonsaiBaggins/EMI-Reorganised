package com.bonsaibaggins.emireorganised.port;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/** Rolls EMI's left and right sidebars in (shown) and out (hidden) by sliding their layout bounds off screen. */
public final class SidebarRollout {
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HOVERED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    private static final boolean[] shown = {true, true};
    private static final float[] progress = {1, 1};
    @Nullable
    private static final Bounds[] lastShownBounds = new Bounds[2];
    private static boolean initialized;
    private static long lastFrame;

    private SidebarRollout() {
    }

    private static boolean enabled() {
        return com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.ROLLOUT_BUTTONS);
    }

    private static int index(SidebarSide side) {
        return side == SidebarSide.LEFT ? LEFT : side == SidebarSide.RIGHT ? RIGHT : -1;
    }

    /** Called when EMI lays out a newly opened screen, before anything is drawn, so a roll-in starts hidden. */
    public static boolean onScreenInit() {
        long now = Util.getMillis();
        if (now - lastFrame <= 250) {
            return false;
        }
        lastFrame = now;
        return opened();
    }

    private static boolean opened() {
        if (!enabled()) {
            return false;
        }
        boolean changed = false;
        if (!initialized) {
            initialized = true;
            shown[LEFT] = com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.LEFT_SIDEBAR_INITIAL) == com.bonsaibaggins.emireorganised.Config.SidebarState.IN;
            shown[RIGHT] = com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.RIGHT_SIDEBAR_INITIAL) == com.bonsaibaggins.emireorganised.Config.SidebarState.IN;
            for (int i = 0; i < 2; i++) {
                progress[i] = shown[i] ? 1 : 0;
            }
            changed = true;
        }
        if (com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.ANIMATE_ON_OPEN)) {
            for (int i = 0; i < 2; i++) {
                if (shown[i] && progress[i] != 0) {
                    progress[i] = 0;
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Advances the animation once per frame; returns true when EMI's layout has to be recalculated. */
    public static boolean update() {
        long now = Util.getMillis();
        boolean fresh = now - lastFrame > 250;
        long dt = fresh ? 0 : now - lastFrame;
        lastFrame = now;
        boolean changed = false;
        // Opening an inventory is detected in onScreenInit only; a slow frame must never restart the roll-in.
        if (!initialized) {
            changed = opened();
        }
        if (!enabled()) {
            for (int i = 0; i < 2; i++) {
                if (progress[i] != 1) {
                    progress[i] = 1;
                    changed = true;
                }
            }
            return changed;
        }
        int duration = com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.ROLLOUT_DURATION);
        float step = duration <= 0 ? 1 : dt / (float) duration;
        for (int i = 0; i < 2; i++) {
            float target = shown[i] ? 1 : 0;
            if (progress[i] != target) {
                progress[i] = target > progress[i] ? Math.min(target, progress[i] + step) : Math.max(target, progress[i] - step);
                changed = true;
            }
        }
        return changed;
    }

    private static float eased(int i) {
        float t = progress[i];
        return t * t * (3 - 2 * t);
    }

    /** Shifts a left/right sidebar's layout bounds outwards by the hidden fraction of their width. */
    public static Bounds shift(SidebarSide side, Bounds bounds) {
        int i = index(side);
        if (i < 0 || progress[i] >= 1) {
            return bounds;
        }
        int full = bounds.width() + 4 + com.bonsaibaggins.emireorganised.port.SidebarExtras.tabBarWidth() + 40;
        // Fully rolled out: push everything (including REMI's tab bar) well off screen.
        int offset = progress[i] <= 0 ? full + 4000 : Math.round((1 - eased(i)) * full);
        return new Bounds(i == LEFT ? bounds.x() - offset : bounds.x() + offset, bounds.y(), bounds.width(), bounds.height());
    }

    /** {x, y, w, h} of a side's roll button, or null when it has none. */
    private static int @Nullable [] buttonRect(int i, EmiScreenBase base) {
        if (!enabled() || EmiScreenManager.isDisabled()) {
            return null;
        }
        EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(i == LEFT ? SidebarSide.LEFT : SidebarSide.RIGHT);
        Bounds bounds = panel != null && panel.space != null && panel.isVisible() ? panel.getBounds() : null;
        if (bounds != null) {
            // The frame grows to the right for a scroll bar; EMI's bounds don't include that.
            bounds = new Bounds(bounds.x(), bounds.y(), bounds.width() + com.bonsaibaggins.emireorganised.sidebar.IndexScroll.extraWidth(panel.getType()), bounds.height());
            bounds = com.bonsaibaggins.emireorganised.port.SidebarExtras.withTabBar(panel, bounds);
        }
        if (bounds != null && bounds.height() > 0 && progress[i] >= 1) {
            lastShownBounds[i] = bounds;
        }
        if (bounds == null && shown[i]) {
            return null;
        }
        int sw = base.screen().width;
        int sh = base.screen().height;
        Bounds container = base.bounds();
        int bw = com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.ROLLOUT_BUTTON_WIDTH);
        int bh = com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.ROLLOUT_BUTTON_HEIGHT);
        int pad = com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.ROLLOUT_BUTTON_PADDING);
        int x;
        if (i == LEFT) {
            x = bounds != null ? bounds.right() + pad : pad;
            x = Math.max(pad, Math.min(x, container.left() - bw));
        } else {
            x = bounds != null ? bounds.left() - pad - bw : sw - bw - pad;
            x = Math.min(sw - bw - pad, Math.max(x, container.right()));
        }
        Bounds vertical = lastShownBounds[i] != null ? lastShownBounds[i] : new Bounds(0, 0, sw, sh);
        int y = switch (com.bonsaibaggins.emireorganised.Config.get(com.bonsaibaggins.emireorganised.Config.ROLLOUT_BUTTON_POSITION)) {
            case TOP -> vertical.top() + pad;
            case MIDDLE -> vertical.top() + (vertical.height() - bh) / 2;
            case BOTTOM -> vertical.bottom() - bh - pad;
        };
        return new int[] {x, Math.max(0, Math.min(y, sh - bh)), bw, bh};
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        EmiScreenBase base = EmiScreenBase.getCurrent();
        if (base.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        int tooltipSide = -1;
        for (int i = 0; i < 2; i++) {
            int[] r = buttonRect(i, base);
            if (r == null) {
                continue;
            }
            boolean hovered = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
            if (hovered) {
                tooltipSide = i;
            }
            graphics.blitSprite(hovered ? BUTTON_HOVERED : BUTTON, r[0], r[1], r[2], r[3]);
            // Points to the screen centre to roll the sidebar in, to the edge to roll it out.
            boolean pointRight = (i == LEFT) != shown[i];
            int size = Math.max(2, Math.min((r[3] - 4) / 2, r[2] / 2));
            int ax = r[0] + (r[2] - size) / 2;
            int cy = r[1] + r[3] / 2;
            int color = hovered ? 0xFFFFFFA0 : 0xFFFFFFFF;
            for (int c = 0; c < size; c++) {
                int half = pointRight ? size - 1 - c : c;
                graphics.fill(ax + c, cy - half - 1, ax + c + 1, cy + half, color);
            }
        }
        graphics.pose().popPose();
        if (tooltipSide >= 0) {
            renderTooltip(graphics, tooltipSide, mouseX, mouseY);
        }
    }

    private static void renderTooltip(GuiGraphics graphics, int i, int mouseX, int mouseY) {
        EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(i == LEFT ? SidebarSide.LEFT : SidebarSide.RIGHT);
        if (panel == null || panel.pages.pages.isEmpty()) {
            return;
        }
        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        dev.emi.emi.config.SidebarType current = panel.getType();
        for (var page : panel.pages.pages) {
            lines.add(page.type.getText().copy().withStyle(page.type == current && panel.pages.pages.size() > 1
                    ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.GRAY));
        }
        // EMI's own tooltip renderer: vanilla's draws an empty box at this point of EMI's render.
        dev.emi.emi.EmiRenderHelper.drawTooltip(Minecraft.getInstance().screen, dev.emi.emi.runtime.EmiDrawContext.wrap(graphics),
                lines.stream().map(l -> net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(l.getVisualOrderText())).toList(),
                mouseX, mouseY);
    }

    public static boolean click(double mouseX, double mouseY, int button) {
        EmiScreenBase base = EmiScreenBase.getCurrent();
        if (button != 0 || base.isEmpty()) {
            return false;
        }
        for (int i = 0; i < 2; i++) {
            int[] r = buttonRect(i, base);
            if (r != null && mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                shown[i] = !shown[i];
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }
}
