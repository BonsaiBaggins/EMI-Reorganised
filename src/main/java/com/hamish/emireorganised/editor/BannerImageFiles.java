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

    public static void delete(String file) throws IOException {
        Files.deleteIfExists(dir().resolve(file));
        Files.deleteIfExists(dir().resolve(file + ".mcmeta"));
        BannerImages.bump();
    }

    /** Opens the file explorer with the image selected (or its folder where selecting isn't supported). */
    public static void show(String file) {
        Path path = dir().resolve(file).toAbsolutePath().normalize();
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
