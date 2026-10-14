package com.hamish.emireorganised.editor;

import com.hamish.emireorganised.EmiReorganised;
import com.hamish.emireorganised.data.BannerDef;
import com.hamish.emireorganised.port.BannerImages;
import com.hamish.emireorganised.port.BannerView;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.Util;
import net.minecraft.util.FastColor;

/** Creating, deleting and showing a banner's background image under config/emi_reorganised/images. */
public final class BannerImageFiles {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 36;

    private BannerImageFiles() {
    }

    private static Path dir() {
        return com.hamish.emireorganised.data.TabStore.dir().resolve("images");
    }

    /** Writes a new gradient image for the banner (using its fallback colours) and returns the file name. */
    public static String create(BannerDef banner) throws IOException {
        Path dir = dir();
        Files.createDirectories(dir);
        String base = banner.name.replaceAll("[^A-Za-z0-9 _\\-]", "").trim();
        if (base.isEmpty()) {
            base = "banner";
        }
        String file = base + ".png";
        for (int i = 2; Files.exists(dir.resolve(file)); i++) {
            file = base + " (" + i + ").png";
        }
        int left = banner.colors.left() != null ? banner.colors.left() : BannerView.DEFAULT_FILL;
        int right = banner.colors.right() != null ? banner.colors.right() : BannerView.DEFAULT_FILL;
        try (NativeImage image = new NativeImage(WIDTH, HEIGHT, false)) {
            for (int x = 0; x < WIDTH; x++) {
                float t = x / (float) (WIDTH - 1);
                int argb = FastColor.ARGB32.lerp(t, left, right) | 0xFF000000;
                for (int y = 0; y < HEIGHT; y++) {
                    image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(argb));
                }
            }
            image.writeToFile(dir.resolve(file));
        }
        BannerImages.bump();
        return file;
    }

    /**
     * Writes {@code source} (with its animation {@code mcmeta}, if any) as a new image named after the banner,
     * multiplied towards {@code tint} by {@code strength} (0 = untouched, 1 = fully tinted). Returns the file name.
     */
    public static String bake(BannerDef banner, NativeImage source, @org.jetbrains.annotations.Nullable byte[] mcmeta,
                              int tint, float strength) throws IOException {
        Path dir = dir();
        Files.createDirectories(dir);
        String file = freeName(dir, banner.name);
        float r = 1 + (FastColor.ARGB32.red(tint) / 255F - 1) * strength;
        float g = 1 + (FastColor.ARGB32.green(tint) / 255F - 1) * strength;
        float b = 1 + (FastColor.ARGB32.blue(tint) / 255F - 1) * strength;
        try (NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false)) {
            image.copyFrom(source);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int a = abgr >>> 24;
                    int bb = Math.round(((abgr >> 16) & 0xFF) * b);
                    int gg = Math.round(((abgr >> 8) & 0xFF) * g);
                    int rr = Math.round((abgr & 0xFF) * r);
                    image.setPixelRGBA(x, y, a << 24 | bb << 16 | gg << 8 | rr);
                }
            }
            image.writeToFile(dir.resolve(file));
        }
        if (mcmeta != null) {
            Files.write(dir.resolve(file + ".mcmeta"), mcmeta);
        }
        BannerImages.bump();
        return file;
    }

    /** Writes a gradient from the banner's fallback colours, without writing it to disk. */
    public static NativeImage gradient(BannerDef banner) {
        int left = banner.colors.left() != null ? banner.colors.left() : BannerView.DEFAULT_FILL;
        int right = banner.colors.right() != null ? banner.colors.right() : BannerView.DEFAULT_FILL;
        NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
        for (int x = 0; x < WIDTH; x++) {
            float t = x / (float) (WIDTH - 1);
            int argb = FastColor.ARGB32.lerp(t, left, right) | 0xFF000000;
            for (int y = 0; y < HEIGHT; y++) {
                image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(argb));
            }
        }
        return image;
    }

    private static String freeName(Path dir, String name) {
        String base = name.replaceAll("[^A-Za-z0-9 _\\-]", "").trim();
        if (base.isEmpty()) {
            base = "banner";
        }
        String file = base + ".png";
        for (int i = 2; Files.exists(dir.resolve(file)); i++) {
            file = base + " (" + i + ").png";
        }
        return file;
    }

    public static Path images() {
        return dir();
    }

    public static void delete(String file) throws IOException {
        Files.deleteIfExists(dir().resolve(file));
        Files.deleteIfExists(dir().resolve(file + ".mcmeta"));
        BannerImages.bump();
    }

    /** Opens the file explorer with the image selected (or its folder where selecting isn't supported). */
    public static void show(String file) {
        Path path = dir().resolve(file).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            Util.getPlatform().openFile(dir().toFile());
            return;
        }
        try {
            switch (Util.getPlatform()) {
                // Separate "/select," argument: Java quotes args with spaces, which explorer won't parse as "/select,<path>".
                case WINDOWS -> new ProcessBuilder("explorer.exe", "/select,", path.toString()).start();
                case OSX -> new ProcessBuilder("open", "-R", path.toString()).start();
                default -> Util.getPlatform().openFile(path.getParent().toFile());
            }
        } catch (Exception e) {
            EmiReorganised.LOGGER.warn("Failed to open {}", path, e);
        }
    }
}
