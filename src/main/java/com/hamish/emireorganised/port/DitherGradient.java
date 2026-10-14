package com.hamish.emireorganised.port;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

/**
 * Banner gradients drawn as an ordered (Bayer) dither over four colours: the two chosen end colours and two
 * intermediates (automatic thirds unless set). The pattern is {@link #HEIGHT} px tall.
 */
public final class DitherGradient {
    public static final int HEIGHT = 16;
    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private DitherGradient() {
    }

    public static int[] colours(int left, @Nullable Integer mid1, @Nullable Integer mid2, int right) {
        return new int[] {left, mid1 != null ? mid1 : FastColor.ARGB32.lerp(1 / 3F, left, right),
                mid2 != null ? mid2 : FastColor.ARGB32.lerp(2 / 3F, left, right), right};
    }

    public static NativeImage image(int[] colours, int width, int height) {
        NativeImage image = new NativeImage(width, height, false);
        for (int x = 0; x < width; x++) {
            float t = width <= 1 ? 0 : x / (float) (width - 1) * 3;
            int i = Math.min(2, (int) t);
            float f = t - i;
            for (int y = 0; y < height; y++) {
                int argb = f > (BAYER[y & 3][x & 3] + 0.5F) / 16F ? colours[i + 1] : colours[i];
                image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(argb | 0xFF000000));
            }
        }
        return image;
    }

    /** Draws the dithered gradient over the area, one texture pixel per GUI pixel across, 16 px tall stretched to fit. */
    public static void draw(net.minecraft.client.gui.GuiGraphics graphics, int x0, int y0, int x1, int y1, int[] colours) {
        int w = x1 - x0;
        if (w <= 0 || y1 <= y0) {
            return;
        }
        String key = w + ":" + colours[0] + ":" + colours[1] + ":" + colours[2] + ":" + colours[3];
        ResourceLocation id = TEXTURES.get(key);
        if (id == null) {
            if (TEXTURES.size() > 64) {
                TEXTURES.values().forEach(Minecraft.getInstance().getTextureManager()::release);
                TEXTURES.clear();
            }
            id = ResourceLocation.fromNamespaceAndPath("emi_reorganised", "dither/" + Integer.toHexString(key.hashCode()) + "_" + TEXTURES.size());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image(colours, w, HEIGHT)));
            TEXTURES.put(key, id);
        }
        graphics.blit(id, x0, y0, w, y1 - y0, 0, 0, w, HEIGHT, w, HEIGHT);
    }
}
