package com.hamish.emireorganised.editor;

import com.hamish.emireorganised.data.BannerDef;
import com.hamish.emireorganised.port.BannerRenderer;
import com.hamish.emireorganised.port.BannerView;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Picks a banner's two title colours and its two fallback background gradient colours. */
public class BannerColorScreen extends EditorPopup {
    private static final String[] KEYS = {"title", "secondary", "background", "fill_left", "mid1", "mid2", "fill_right"};
    private static final int W = 260;
    private static final int H = 222;
    private static final int SV_W = 120;
    private static final int SV_H = 60;

    private final BannerView base;
    private final Consumer<BannerDef.Colors> onDone;
    private final Integer[] values = new Integer[KEYS.length];
    private final int[] defaults;
    private int active;
    private float hue;
    private float sat;
    private float val;
    private boolean draggingSv;
    private boolean draggingHue;
    private boolean updatingHex;
    private EditBox hex;

    /** {@code base} is the banner drawn with default colours. */
    public BannerColorScreen(Screen parent, BannerView base, BannerDef.Colors current, Consumer<BannerDef.Colors> onDone) {
        super(parent, Component.translatable("emi_reorganised.colors"));
        this.onDone = onDone;
        this.base = base;
        this.defaults = new int[] {base.color(), base.secondaryColor(), base.titleBackground(), base.fillLeft(), 0, 0, base.fillRight()};
        values[0] = current.title();
        values[1] = current.outline();
        values[2] = current.titleBackground();
        values[3] = current.left();
        values[4] = current.mid1();
        values[5] = current.mid2();
        values[6] = current.right();
    }

    @Override
    protected int panelX() {
        return left();
    }

    @Override
    protected int panelY() {
        return top();
    }

    @Override
    protected int panelW() {
        return W;
    }

    @Override
    protected int panelH() {
        return H;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int value(int i) {
        if (values[i] == null && (i == 4 || i == 5)) {
            // Automatic intermediates: thirds between the end colours.
            return net.minecraft.util.FastColor.ARGB32.lerp(i == 4 ? 1 / 3F : 2 / 3F, value(3), value(6));
        }
        return values[i] != null ? values[i] : defaults[i];
    }

    private BannerDef.Colors colors() {
        return new BannerDef.Colors(values[0], values[1], values[2], values[3], values[6], values[4], values[5]);
    }

    @Override
    protected void init() {
        int x = left();
        int y = top();
        hex = new EditBox(font, x + 140, y + 126, 60, 16, Component.literal("hex"));
        hex.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("emi_reorganised.colors.hex_tip")));
        hex.setMaxLength(8);
        hex.setFilter(s -> s.matches("[0-9a-fA-F]*"));
        hex.setResponder(s -> {
            if (!updatingHex && s.length() == 6) {
                values[active] = (value(active) & 0xFF000000) | Integer.parseInt(s, 16);
                loadHsv();
            } else if (!updatingHex && s.length() == 8) {
                values[active] = (int) Long.parseLong(s, 16);
                loadHsv();
            }
        });
        addRenderableWidget(hex);
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.colors.default"), b -> {
            values[active] = null;
            select(active);
        }).bounds(x + 10, y + H - 26, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + 92, y + H - 26, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            onDone.accept(colors());
            onClose();
        }).bounds(x + 174, y + H - 26, 76, 20).build());
        select(active);
    }

    private void select(int index) {
        active = index;
        loadHsv();
        updateHex();
    }

    private void loadHsv() {
        int rgb = value(active);
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        // Keep the hue/saturation the user picked when the colour is grey or black.
        if (hsb[2] > 0 && hsb[1] > 0) {
            hue = hsb[0];
        }
        if (hsb[2] > 0) {
            sat = hsb[1];
        }
        val = hsb[2];
    }

    private void updateHex() {
        if (hex != null) {
            updatingHex = true;
            int v = value(active);
            hex.setValue((v >>> 24) == 0xFF ? String.format(Locale.ROOT, "%06X", v & 0xFFFFFF)
                    : String.format(Locale.ROOT, "%08X", v));
            updatingHex = false;
        }
    }

    private void applyHsv() {
        int alpha = values[active] != null || (defaults[active] >>> 24) != 0 ? value(active) & 0xFF000000 : 0xFF000000;
        values[active] = alpha | Mth.hsvToRgb(Mth.clamp(hue, 0, 0.999F), sat, val);
        updateHex();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        parent.render(graphics, -1, -1, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(0, 0, width, height, 0x99000000);
        int x = left();
        int y = top();
        graphics.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF8B8B8B);
        graphics.fill(x, y, x + W, y + H, 0xFF202020);
        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        int x = left();
        int y = top();
        graphics.drawCenteredString(font, title, x + W / 2, y + 6, 0xFFFFFF);
        BannerRenderer.drawBanner(graphics, base.with(colors()), x + 10, y + 20, W - 20, BannerRenderer.ROW);

        for (int i = 0; i < KEYS.length; i++) {
            int ry = y + 46 + i * 20;
            boolean over = mouseX >= x + 10 && mouseX < x + 125 && mouseY >= ry && mouseY < ry + 20;
            if (i == active) {
                graphics.fill(x + 10, ry, x + 125, ry + 20, 0xFF3A6EA5);
            } else if (over) {
                graphics.fill(x + 10, ry, x + 125, ry + 20, 0xFF383838);
            }
            graphics.fill(x + 12, ry + 2, x + 28, ry + 18, 0xFF000000);
            graphics.fill(x + 13, ry + 3, x + 27, ry + 17, value(i));
            Component label = Component.translatable("emi_reorganised.colors." + KEYS[i]);
            graphics.drawString(font, label, x + 32, ry + 6, values[i] == null ? 0xAAAAAA : 0xFFFFFF);
        }

        int sx = x + 130;
        int sy = y + 46;
        graphics.fill(sx - 1, sy - 1, sx + SV_W + 1, sy + SV_H + 1, 0xFF000000);
        for (int i = 0; i < SV_W; i++) {
            int top = 0xFF000000 | Mth.hsvToRgb(Mth.clamp(hue, 0, 0.999F), i / (float) (SV_W - 1), 1);
            graphics.fillGradient(sx + i, sy, sx + i + 1, sy + SV_H, top, 0xFF000000);
        }
        int mx = sx + Math.round(sat * (SV_W - 1));
        int my = sy + Math.round((1 - val) * (SV_H - 1));
        graphics.renderOutline(mx - 2, my - 2, 5, 5, val > 0.5F ? 0xFF000000 : 0xFFFFFFFF);

        int hy = y + 112;
        graphics.fill(sx - 1, hy - 1, sx + SV_W + 1, hy + 9, 0xFF000000);
        for (int i = 0; i < SV_W; i++) {
            graphics.fill(sx + i, hy, sx + i + 1, hy + 8, 0xFF000000 | Mth.hsvToRgb(i / (float) SV_W, 1, 1));
        }
        int hx = sx + Math.round(Mth.clamp(hue, 0, 1) * (SV_W - 1));
        graphics.fill(hx - 1, hy - 2, hx + 2, hy + 10, 0xFFFFFFFF);
        graphics.fill(hx, hy - 1, hx + 1, hy + 9, 0xFF000000);

        graphics.drawString(font, "#", x + 131, y + 130, 0xFFFFFF);
        graphics.fill(x + 206, y + 126, x + 250, y + 142, 0xFF000000);
        graphics.fill(x + 207, y + 127, x + 249, y + 141, value(active));

        for (var child : renderables) {
            child.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left();
        int y = top();
        if (button == 0) {
            for (int i = 0; i < KEYS.length; i++) {
                int ry = y + 46 + i * 20;
                if (mouseX >= x + 10 && mouseX < x + 125 && mouseY >= ry && mouseY < ry + 20) {
                    select(i);
                    return true;
                }
            }
            if (overSv(mouseX, mouseY)) {
                draggingSv = true;
                dragSv(mouseX, mouseY);
                return true;
            }
            if (overHue(mouseX, mouseY)) {
                draggingHue = true;
                dragHue(mouseX);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSv) {
            dragSv(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            dragHue(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSv = false;
        draggingHue = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean overSv(double mouseX, double mouseY) {
        int sx = left() + 130;
        int sy = top() + 46;
        return mouseX >= sx && mouseX < sx + SV_W && mouseY >= sy && mouseY < sy + SV_H;
    }

    private boolean overHue(double mouseX, double mouseY) {
        int sx = left() + 130;
        int hy = top() + 112;
        return mouseX >= sx && mouseX < sx + SV_W && mouseY >= hy - 2 && mouseY < hy + 10;
    }

    private void dragSv(double mouseX, double mouseY) {
        sat = Mth.clamp((float) (mouseX - (left() + 130)) / (SV_W - 1), 0, 1);
        val = 1 - Mth.clamp((float) (mouseY - (top() + 46)) / (SV_H - 1), 0, 1);
        applyHsv();
    }

    private void dragHue(double mouseX) {
        hue = Mth.clamp((float) (mouseX - (left() + 130)) / (SV_W - 1), 0, 1);
        applyHsv();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
