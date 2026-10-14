package com.hamish.emireorganised.port;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

/**
 * User banner images from the config folder, loaded as dynamic textures. Files are re-read when they change on disk,
 * so edits show up without a restart. Animation follows the vanilla {@code .mcmeta} format (frame height + frametime).
 */
public final class BannerImages {
    private static final long CHECK_INTERVAL_MS = 1000;
    private static final Map<Path, Image> IMAGES = new HashMap<>();
    private static int counter;

    private BannerImages() {
    }

    public static final class Image {
        public ResourceLocation texture;
        public int width;
        public int height;
        public int frameHeight;
        public int frames;
        public int frametime;
        public int[] edges;
        DynamicTexture dynamic;
        long modified;
        long lastCheck;
        boolean playing;
        int frame;
        long frameStart;

        /** Current animation frame; only advances while playing, pausing in place otherwise. */
        public int frame() {
            long now = System.currentTimeMillis();
            long frameMs = Math.max(1, frametime) * 50L;
            if (playing && frames > 1) {
                long elapsed = now - frameStart;
                if (elapsed >= frameMs) {
                    frame = (int) ((frame + elapsed / frameMs) % frames);
                    frameStart = now - elapsed % frameMs;
                }
            } else {
                frameStart = now;
            }
            return frame;
        }
    }

    private static int version;

    /** Bumped when images change on disk, so cached index layouts pick up new images. */
    public static int version() {
        return version;
    }

    public static void bump() {
        version++;
    }

    /** The image file for a banner, or null if unset or missing. */
    @org.jetbrains.annotations.Nullable
    public static Path path(@org.jetbrains.annotations.Nullable String image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        Path path = com.hamish.emireorganised.data.TabStore.dir().resolve("images").resolve(image);
        return java.nio.file.Files.isRegularFile(path) ? path : null;
    }

    public static void setPlaying(Path path, boolean playing) {
        Image image = IMAGES.get(path);
        if (image != null) {
            image.playing = playing;
        }
    }

    @Nullable
    public static Image get(Path path) {
        long now = System.currentTimeMillis();
        Image image = IMAGES.get(path);
        if (image != null && now - image.lastCheck < CHECK_INTERVAL_MS) {
            return image.texture == null ? null : image;
        }
        long modified;
        try {
            modified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1;
        } catch (Exception e) {
            modified = -1;
        }
        if (image != null && image.modified == modified) {
            image.lastCheck = now;
            return image.texture == null ? null : image;
        }
        if (image != null) {
            release(image);
        }
        image = new Image();
        image.lastCheck = now;
        image.modified = modified;
        IMAGES.put(path, image);
        if (modified >= 0) {
            load(path, image);
        }
        return image.texture == null ? null : image;
    }

    private static void load(Path path, Image image) {
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage pixels = NativeImage.read(in);
            image.width = pixels.getWidth();
            image.height = pixels.getHeight();
            image.frameHeight = image.height;
            image.frametime = 1;
            Path meta = path.resolveSibling(path.getFileName() + ".mcmeta");
            if (Files.exists(meta)) {
                try (Reader reader = Files.newBufferedReader(meta)) {
                    JsonObject animation = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animation");
                    if (animation != null) {
                        image.frameHeight = animation.has("height") ? animation.get("height").getAsInt() : image.width;
                        image.frametime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
                    }
                } catch (Exception e) {
                    com.hamish.emireorganised.EmiReorganised.LOGGER.warn("Invalid animation metadata {}", meta, e);
                }
            }
            image.frameHeight = Math.max(1, Math.min(image.frameHeight, image.height));
            image.frames = Math.max(1, image.height / image.frameHeight);
            image.edges = edges(pixels, image.frameHeight);
            image.dynamic = new DynamicTexture(pixels);
            image.texture = ResourceLocation.fromNamespaceAndPath(com.hamish.emireorganised.EmiReorganised.MOD_ID, "banner_image_" + counter++);
            Minecraft.getInstance().getTextureManager().register(image.texture, image.dynamic);
        } catch (Exception e) {
            com.hamish.emireorganised.EmiReorganised.LOGGER.warn("Failed to load banner image {}", path, e);
        }
    }

    private static void release(Image image) {
        if (image.texture != null) {
            Minecraft.getInstance().getTextureManager().release(image.texture);
        }
    }

    /** ARGB average of the outermost columns inside the 1px frame, from the first frame. */
    @Nullable
    static int[] edges(NativeImage image, int frameHeight) {
        int w = image.getWidth();
        int h = Math.min(frameHeight, image.getHeight());
        if (w < 3 || h < 3) {
            return null;
        }
        return new int[]{averageColumn(image, 1, h), averageColumn(image, w - 2, h)};
    }

    private static int averageColumn(NativeImage image, int column, int frameHeight) {
        long a = 0, r = 0, g = 0, b = 0;
        int n = 0;
        for (int row = 1; row < frameHeight - 1; row++) {
            int abgr = image.getPixelRGBA(column, row);
            int alpha = FastColor.ABGR32.alpha(abgr);
            a += alpha;
            r += (long) FastColor.ABGR32.red(abgr) * alpha;
            g += (long) FastColor.ABGR32.green(abgr) * alpha;
            b += (long) FastColor.ABGR32.blue(abgr) * alpha;
            n++;
        }
        if (n == 0 || a == 0) {
            return 0;
        }
        return FastColor.ARGB32.color((int) (a / n), (int) (r / a), (int) (g / a), (int) (b / a));
    }
}
