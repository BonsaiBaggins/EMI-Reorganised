package com.hamish.emireorganised.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hamish.emireorganised.EmiReorganised;
import com.hamish.emireorganised.compat.SimulatedCompat;
import com.hamish.emireorganised.data.BannerDef;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Picks a banner background: a gradient, the artwork of every loaded Simulated section (Simulated's own and those of
 * addons such as Aeronautics), or an image the user favourited. The tint and saturation are baked into a new image file.
 */
public class BannerPickerScreen extends EditorPopup {
    private static final int W = 320;
    private static final int H = 306;
    private static final int ROW = 34;
    private static final int ROWS = 4;
    private static final int SV_W = 120;
    private static final int SV_H = 44;

    private record Candidate(String name, ResourceLocation texture, DynamicTexture dynamic, int width, int frameHeight,
                             int frames, int frametime, @Nullable byte[] mcmeta) {
        int frame() {
            return frames <= 1 ? 0 : (int) (Util.getMillis() / 50 / Math.max(1, frametime) % frames);
        }
    }

    private final BannerDef banner;
    private final Consumer<String> onDone;
    private final List<Candidate> candidates = new ArrayList<>();
    private int selected = -1;
    private int scroll;
    private float hue;
    private float sat;
    private float val = 1;
    private double strength;
    private double saturation = 1;
    private boolean draggingSv;
    private boolean draggingHue;
    private EditBox hex;
    private Button doneButton;
    private boolean loaded;
    @Nullable private DynamicTexture preview;
    private final ResourceLocation previewId = ResourceLocation.fromNamespaceAndPath(EmiReorganised.MOD_ID, "picker/preview");
    private boolean previewDirty = true;

    public BannerPickerScreen(Screen parent, BannerDef banner, Consumer<String> onDone) {
        super(parent, Component.translatable("emi_reorganised.picker.title"));
        this.banner = banner;
        this.onDone = onDone;
    }

    @Override
    protected int panelX() {
        return (width - W) / 2;
    }

    @Override
    protected int panelY() {
        return (height - H) / 2;
    }

    @Override
    protected int panelW() {
        return W;
    }

    @Override
    protected int panelH() {
        return H;
    }

    private int listX() {
        return panelX() + 10;
    }

    private int listY() {
        return panelY() + 20;
    }

    private int listW() {
        return W - 20;
    }

    private int previewW() {
        return listW() - 8;
    }

    private int previewH(Candidate c) {
        return Math.min(ROW - 4, Math.max(1, previewW() * c.frameHeight / Math.max(1, c.width)));
    }

    private int previewY() {
        return listY() + ROWS * ROW + 16;
    }

    private int colourY() {
        return previewY() + 40;
    }

    private int svX() {
        return panelX() + 10;
    }

    private int hueY() {
        return colourY() + SV_H + 4;
    }

    private int tint() {
        return Mth.hsvToRgb(Mth.clamp(hue, 0, 0.999F), sat, val);
    }

    @Override
    protected void init() {
        if (!loaded) {
            loaded = true;
            load();
        }
        int x = svX() + SV_W + 10;
        int sw = W - (x - panelX()) - 10;
        addRenderableWidget(new AbstractSliderButton(x, colourY(), sw, 20, Component.empty(), strength) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("emi_reorganised.picker.strength", Math.round(value * 100)));
            }

            @Override
            protected void applyValue() {
                strength = value;
                previewDirty = true;
            }
        });
        addRenderableWidget(new AbstractSliderButton(x, colourY() + 22, sw, 20, Component.empty(), saturation / 2) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("emi_reorganised.picker.saturation", Math.round(value * 200)));
            }

            @Override
            protected void applyValue() {
                saturation = value * 2;
                previewDirty = true;
            }
        });
        hex = addRenderableWidget(new EditBox(font, x + 22, hueY() - 2, 56, 14, Component.empty()));
        hex.setMaxLength(6);
        hex.setValue(String.format(Locale.ROOT, "%06X", tint() & 0xFFFFFF));
        hex.setResponder(v -> {
            if (v.length() != 6 || !hex.isFocused()) {
                return;
            }
            try {
                int rgb = Integer.parseInt(v, 16);
                float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
                hue = hsb[0];
                sat = hsb[1];
                val = hsb[2];
                previewDirty = true;
            } catch (NumberFormatException ignored) {
            }
        });
        hex.setTooltip(Tooltip.create(Component.translatable("emi_reorganised.picker.tint_tip")));
        int by = panelY() + H - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(panelX() + W / 2 - 104, by, 100, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done())
                .bounds(panelX() + W / 2 + 4, by, 100, 20).build());
    }

    private void load() {
        add(Component.translatable("emi_reorganised.picker.gradient").getString(), BannerImageFiles.gradient(banner), null);
        for (SimulatedCompat.Section section : SimulatedCompat.sections()) {
            ResourceLocation texture = BannerImageFiles.spriteTexture(section.sprite());
            var resource = minecraft.getResourceManager().getResource(texture);
            if (resource.isEmpty()) {
                continue;
            }
            try (InputStream in = resource.get().open()) {
                add(section.title().getString(), NativeImage.read(in), BannerImageFiles.readMeta(texture));
            } catch (Exception e) {
                EmiReorganised.LOGGER.warn("Couldn't read banner art {}", texture, e);
            }
        }
        for (String file : BannerFavourites.files()) {
            Path p = BannerImageFiles.images().resolve(file);
            try (InputStream in = Files.newInputStream(p)) {
                Path metaPath = p.resolveSibling(file + ".mcmeta");
                add(file, NativeImage.read(in), Files.exists(metaPath) ? Files.readAllBytes(metaPath) : null);
            } catch (Exception e) {
                EmiReorganised.LOGGER.warn("Couldn't read banner image {}", p, e);
            }
        }
    }

    private void add(String name, NativeImage image, @Nullable byte[] mcmeta) {
        int frameHeight = image.getHeight();
        int frametime = 1;
        if (mcmeta != null) {
            try {
                JsonObject animation = JsonParser.parseString(new String(mcmeta, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animation");
                if (animation != null) {
                    frameHeight = Math.min(image.getHeight(),
                            animation.has("height") ? animation.get("height").getAsInt() : image.getWidth());
                    frametime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
                }
            } catch (Exception ignored) {
            }
        }
        DynamicTexture dynamic = new DynamicTexture(image);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EmiReorganised.MOD_ID, "picker/" + candidates.size());
        minecraft.getTextureManager().register(id, dynamic);
        candidates.add(new Candidate(name, id, dynamic, image.getWidth(), Math.max(1, frameHeight),
                Math.max(1, image.getHeight() / Math.max(1, frameHeight)), frametime, mcmeta));
    }

    private void done() {
        if (selected < 0) {
            return;
        }
        Candidate c = candidates.get(selected);
        try {
            String file = BannerImageFiles.bake(banner, c.dynamic.getPixels(), c.mcmeta, tint(), (float) strength, (float) saturation);
            minecraft.setScreen(parent);
            onDone.accept(file);
        } catch (Exception e) {
            EmiReorganised.LOGGER.warn("Couldn't write banner image", e);
        }
    }

    @Override
    public void removed() {
        for (Candidate c : candidates) {
            minecraft.getTextureManager().release(c.texture);
        }
        candidates.clear();
        if (preview != null) {
            minecraft.getTextureManager().release(previewId);
            preview = null;
        }
        previewDirty = true;
        loaded = false;
    }

    private void updatePreview() {
        if (!previewDirty || selected < 0) {
            return;
        }
        previewDirty = false;
        NativeImage processed = BannerImageFiles.process(candidates.get(selected).dynamic.getPixels(), tint(), (float) strength, (float) saturation);
        if (preview != null) {
            minecraft.getTextureManager().release(previewId);
        }
        preview = new DynamicTexture(processed);
        minecraft.getTextureManager().register(previewId, preview);
    }

    private void drawFrame(GuiGraphics graphics, ResourceLocation texture, Candidate c, int x, int y, int w, int h) {
        graphics.blit(texture, x, y, w, h, 0, c.frame() * c.frameHeight, c.width, c.frameHeight, c.width, c.dynamic.getPixels().getHeight());
    }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < listY() || my >= listY() + ROWS * ROW) {
            return -1;
        }
        int i = scroll + (int) ((my - listY()) / ROW);
        return i < candidates.size() ? i : -1;
    }

    private boolean overSv(double mx, double my) {
        return mx >= svX() && mx < svX() + SV_W && my >= colourY() && my < colourY() + SV_H;
    }

    private boolean overHue(double mx, double my) {
        return mx >= svX() && mx < svX() + SV_W && my >= hueY() - 2 && my < hueY() + 10;
    }

    private void pickSv(double mx, double my) {
        sat = Mth.clamp((float) (mx - svX()) / (SV_W - 1), 0, 1);
        val = 1 - Mth.clamp((float) (my - colourY()) / (SV_H - 1), 0, 1);
        colourChanged();
    }

    private void pickHue(double mx) {
        hue = Mth.clamp((float) (mx - svX()) / (SV_W - 1), 0, 1);
        colourChanged();
    }

    private void colourChanged() {
        previewDirty = true;
        hex.setValue(String.format(Locale.ROOT, "%06X", tint() & 0xFFFFFF));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        doneButton.active = selected >= 0;
        updatePreview();
        super.render(graphics, mouseX, mouseY, partialTick);
        int l = panelX();
        graphics.drawCenteredString(font, title, width / 2, panelY() + 6, 0xFFFFFF);
        int lx = listX();
        int ly = listY();
        graphics.fill(lx, ly, lx + listW(), ly + ROWS * ROW, 0xFF101010);
        scroll = Math.max(0, Math.min(scroll, candidates.size() - ROWS));
        graphics.enableScissor(lx, ly, lx + listW(), ly + ROWS * ROW);
        for (int r = 0; r < ROWS && scroll + r < candidates.size(); r++) {
            int i = scroll + r;
            Candidate c = candidates.get(i);
            int y = ly + r * ROW;
            int ph = previewH(c);
            drawFrame(graphics, c.texture, c, lx + 4, y + (ROW - ph) / 2, previewW(), ph);
            graphics.drawString(font, c.name, lx + 7, y + ROW - 12, 0xFFFFFF, true);
            if (i == selected) {
                graphics.renderOutline(lx + 2, y + 1, listW() - 4, ROW - 2, 0xFF5C9DFF);
            } else if (rowAt(mouseX, mouseY) == i) {
                graphics.renderOutline(lx + 2, y + 1, listW() - 4, ROW - 2, 0x80FFFFFF);
            }
        }
        graphics.disableScissor();
        if (candidates.size() > ROWS) {
            int track = ROWS * ROW;
            int thumb = Math.max(8, track * ROWS / candidates.size());
            int ty = ly + (track - thumb) * scroll / Math.max(1, candidates.size() - ROWS);
            graphics.fill(lx + listW() - 3, ty, lx + listW() - 1, ty + thumb, 0xFF808080);
        }
        graphics.drawString(font, Component.translatable("emi_reorganised.picker.preview"), l + 10, previewY() - 11, 0xC0C0C0);
        if (selected >= 0 && preview != null) {
            Candidate c = candidates.get(selected);
            drawFrame(graphics, previewId, c, lx + 4, previewY(), previewW(), previewH(c));
            graphics.drawString(font, banner.name, lx + 8, previewY() + previewH(c) / 2 - 4, 0xFFFFFF, true);
        } else if (candidates.size() <= 1) {
            graphics.drawString(font, Component.translatable("emi_reorganised.picker.empty").withStyle(ChatFormatting.GRAY),
                    lx + 4, previewY() + 4, 0x808080);
        }
        // Colour: saturation/value square for the current hue, with a hue bar below.
        int sx = svX();
        int sy = colourY();
        graphics.fill(sx - 1, sy - 1, sx + SV_W + 1, sy + SV_H + 1, 0xFF000000);
        for (int i = 0; i < SV_W; i++) {
            int top = 0xFF000000 | Mth.hsvToRgb(Mth.clamp(hue, 0, 0.999F), i / (float) (SV_W - 1), 1);
            graphics.fillGradient(sx + i, sy, sx + i + 1, sy + SV_H, top, 0xFF000000);
        }
        int mx = sx + Math.round(sat * (SV_W - 1));
        int my = sy + Math.round((1 - val) * (SV_H - 1));
        graphics.renderOutline(mx - 2, my - 2, 5, 5, val > 0.5F ? 0xFF000000 : 0xFFFFFFFF);
        int hy = hueY();
        graphics.fill(sx - 1, hy - 1, sx + SV_W + 1, hy + 9, 0xFF000000);
        for (int i = 0; i < SV_W; i++) {
            graphics.fill(sx + i, hy, sx + i + 1, hy + 8, 0xFF000000 | Mth.hsvToRgb(i / (float) SV_W, 1, 1));
        }
        int hx = sx + Math.round(Mth.clamp(hue, 0, 1) * (SV_W - 1));
        graphics.renderOutline(hx - 1, hy - 2, 3, 12, 0xFFFFFFFF);
        int bx = svX() + SV_W + 10;
        graphics.fill(bx, hueY() - 2, bx + 16, hueY() + 12, 0xFF000000);
        graphics.fill(bx + 1, hueY() - 1, bx + 15, hueY() + 11, 0xFF000000 | tint());
        int hovered = rowAt(mouseX, mouseY);
        if (hovered >= 0) {
            graphics.renderTooltip(font, Component.literal(candidates.get(hovered).name), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int row = rowAt(mx, my);
            if (row >= 0) {
                selected = row;
                previewDirty = true;
                return true;
            }
            if (overSv(mx, my)) {
                draggingSv = true;
                pickSv(mx, my);
                return true;
            }
            if (overHue(mx, my)) {
                draggingHue = true;
                pickHue(mx);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSv) {
            pickSv(mx, my);
            return true;
        }
        if (draggingHue) {
            pickHue(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingSv = false;
        draggingHue = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy));
        return true;
    }
}
