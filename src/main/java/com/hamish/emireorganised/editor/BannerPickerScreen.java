package com.hamish.emireorganised.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hamish.emireorganised.EmiReorganised;
import com.hamish.emireorganised.data.BannerDef;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * Picks a banner background from every loaded banner texture ({@code textures/gui/banner/} in any namespace, e.g.
 * Simulated/Aeronautics), the images already in the config folder, or a gradient. The chosen tint is baked into a new
 * image file for the banner.
 */
public class BannerPickerScreen extends EditorPopup {
    private static final int W = 320;
    private static final int H = 288;
    private static final int ROW = 34;
    private static final int ROWS = 4;
    private static final int SWATCH = 11;

    private record Candidate(String name, ResourceLocation texture, DynamicTexture dynamic, int width, int frameHeight,
                             @Nullable byte[] mcmeta) {
    }

    private final BannerDef banner;
    private final Consumer<String> onDone;
    private final List<Candidate> candidates = new ArrayList<>();
    private int selected = -1;
    private int scroll;
    private int tint = 0xFFFFFF;
    private double strength = 0.5;
    private EditBox hex;
    private Button doneButton;
    private boolean loaded;

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

    private int tintY() {
        return previewY() + 38;
    }

    @Override
    protected void init() {
        if (!loaded) {
            loaded = true;
            load();
        }
        int x = panelX() + 10;
        hex = addRenderableWidget(new EditBox(font, x + 52, tintY() + 1, 56, 14, Component.empty()));
        hex.setMaxLength(6);
        hex.setValue(String.format(Locale.ROOT, "%06X", tint & 0xFFFFFF));
        hex.setResponder(v -> {
            try {
                if (v.length() == 6) {
                    tint = Integer.parseInt(v, 16);
                }
            } catch (NumberFormatException ignored) {
            }
        });
        hex.setTooltip(Tooltip.create(Component.translatable("emi_reorganised.picker.tint_tip")));
        addRenderableWidget(new AbstractSliderButton(x, tintY() + 20, W - 20, 20, Component.empty(), strength) {
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
            }
        });
        int by = panelY() + H - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(panelX() + W / 2 - 104, by, 100, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done())
                .bounds(panelX() + W / 2 + 4, by, 100, 20).build());
    }

    private void load() {
        NativeImage gradient = BannerImageFiles.gradient(banner);
        add(Component.translatable("emi_reorganised.picker.gradient").getString(), gradient, null);
        Map<ResourceLocation, Resource> found = minecraft.getResourceManager()
                .listResources("textures/gui/banner", loc -> loc.getPath().endsWith(".png"));
        found.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            ResourceLocation loc = e.getKey();
            try (InputStream in = e.getValue().open()) {
                NativeImage image = NativeImage.read(in);
                byte[] meta = minecraft.getResourceManager().getResource(loc.withPath(loc.getPath() + ".mcmeta")).map(r -> {
                    try (InputStream m = r.open()) {
                        return m.readAllBytes();
                    } catch (Exception ex) {
                        return null;
                    }
                }).orElse(null);
                String file = loc.getPath().substring(loc.getPath().lastIndexOf('/') + 1).replace(".png", "");
                add(loc.getNamespace() + ": " + file, image, meta);
            } catch (Exception ex) {
                EmiReorganised.LOGGER.warn("Couldn't read banner {}", loc, ex);
            }
        });
        Path dir = BannerImageFiles.images();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().forEach(p -> {
                    try (InputStream in = Files.newInputStream(p)) {
                        NativeImage image = NativeImage.read(in);
                        Path metaPath = p.resolveSibling(p.getFileName() + ".mcmeta");
                        byte[] meta = Files.exists(metaPath) ? Files.readAllBytes(metaPath) : null;
                        add(p.getFileName().toString(), image, meta);
                    } catch (Exception ex) {
                        EmiReorganised.LOGGER.warn("Couldn't read banner image {}", p, ex);
                    }
                });
            } catch (Exception ex) {
                EmiReorganised.LOGGER.warn("Couldn't list banner images", ex);
            }
        }
    }

    private void add(String name, NativeImage image, @Nullable byte[] mcmeta) {
        int frameHeight = image.getHeight();
        if (mcmeta != null) {
            try {
                JsonObject animation = JsonParser.parseString(new String(mcmeta, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animation");
                if (animation != null) {
                    frameHeight = Math.min(image.getHeight(),
                            animation.has("height") ? animation.get("height").getAsInt() : image.getWidth());
                }
            } catch (Exception ignored) {
            }
        }
        DynamicTexture dynamic = new DynamicTexture(image);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EmiReorganised.MOD_ID, "picker/" + candidates.size());
        minecraft.getTextureManager().register(id, dynamic);
        candidates.add(new Candidate(name, id, dynamic, image.getWidth(), frameHeight, mcmeta));
    }

    private void done() {
        if (selected < 0) {
            return;
        }
        Candidate c = candidates.get(selected);
        try {
            String file = BannerImageFiles.bake(banner, c.dynamic.getPixels(), c.mcmeta, tint, (float) strength);
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
        loaded = false;
    }

    private void drawPreview(GuiGraphics graphics, Candidate c, int x, int y, int w, int h) {
        graphics.blit(c.texture, x, y, w, h, 0, 0, c.width, c.frameHeight, c.width, c.dynamic.getPixels().getHeight());
    }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < listY() || my >= listY() + ROWS * ROW) {
            return -1;
        }
        int i = scroll + (int) ((my - listY()) / ROW);
        return i < candidates.size() ? i : -1;
    }

    private int swatchAt(double mx, double my) {
        int sx = panelX() + 10 + 116;
        if (my < tintY() + 2 || my >= tintY() + 2 + SWATCH || mx < sx) {
            return -1;
        }
        int i = (int) ((mx - sx) / SWATCH);
        return i < DyeColor.values().length ? i : -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        doneButton.active = selected >= 0;
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
            int py = y + (ROW - ph) / 2;
            drawPreview(graphics, c, lx + 4, py, previewW(), ph);
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
        if (selected >= 0) {
            Candidate c = candidates.get(selected);
            float s = (float) strength;
            RenderSystem.setShaderColor(1 + (FastColor.ARGB32.red(tint) / 255F - 1) * s,
                    1 + (FastColor.ARGB32.green(tint) / 255F - 1) * s, 1 + (FastColor.ARGB32.blue(tint) / 255F - 1) * s, 1);
            drawPreview(graphics, c, lx + 4, previewY(), previewW(), previewH(c));
            graphics.flush();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.drawString(font, banner.name, lx + 8, previewY() + previewH(c) / 2 - 4, 0xFFFFFF, true);
        } else if (candidates.size() <= 1) {
            graphics.drawString(font, Component.translatable("emi_reorganised.picker.empty").withStyle(ChatFormatting.GRAY),
                    lx + 4, previewY() + 4, 0x808080);
        }
        int ty = tintY();
        graphics.drawString(font, Component.translatable("emi_reorganised.picker.tint"), l + 10, ty + 4, 0xFFFFFF);
        graphics.fill(l + 32, ty, l + 48, ty + 16, 0xFF000000);
        graphics.fill(l + 33, ty + 1, l + 47, ty + 15, 0xFF000000 | tint);
        int sx = l + 10 + 116;
        DyeColor[] dyes = DyeColor.values();
        for (int i = 0; i < dyes.length; i++) {
            int x = sx + i * SWATCH;
            int color = dyes[i].getTextureDiffuseColor() | 0xFF000000;
            graphics.fill(x, ty + 2, x + SWATCH - 1, ty + 2 + SWATCH, 0xFF000000);
            graphics.fill(x + 1, ty + 3, x + SWATCH - 2, ty + 1 + SWATCH, color);
            if (swatchAt(mouseX, mouseY) == i) {
                graphics.renderOutline(x - 1, ty + 1, SWATCH + 1, SWATCH + 2, 0xFFFFFFFF);
            }
        }
        int hovered = rowAt(mouseX, mouseY);
        if (hovered >= 0) {
            graphics.renderTooltip(font, Component.literal(candidates.get(hovered).name), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int row = rowAt(mx, my);
        if (row >= 0 && button == 0) {
            selected = row;
            return true;
        }
        int swatch = swatchAt(mx, my);
        if (swatch >= 0 && button == 0) {
            tint = DyeColor.values()[swatch].getTextureDiffuseColor() & 0xFFFFFF;
            hex.setValue(String.format(Locale.ROOT, "%06X", tint));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy));
        return true;
    }
}
