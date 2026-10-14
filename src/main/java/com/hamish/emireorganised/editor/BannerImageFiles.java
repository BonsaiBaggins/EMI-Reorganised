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
import net.minecraft.resources.ResourceLocation;
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
                              int tint, float strength, float saturation) throws IOException {
        Path dir = dir();
        Files.createDirectories(dir);
        String file = freeName(dir, banner.name);
        try (NativeImage image = process(source, tint, strength, saturation)) {
            image.writeToFile(dir.resolve(file));
        }
        if (mcmeta != null) {
            Files.write(dir.resolve(file + ".mcmeta"), mcmeta);
        }
        BannerImages.bump();
        return file;
    }

    /**
     * A new image: {@code source} with its saturation scaled ({@code 1} = unchanged, {@code 0} = grey), then multiplied
     * towards {@code tint} by {@code strength} ({@code 0} = untouched, {@code 1} = fully tinted).
     */
    public static NativeImage process(NativeImage source, int tint, float strength, float saturation) {
        float tr = 1 + (FastColor.ARGB32.red(tint) / 255F - 1) * strength;
        float tg = 1 + (FastColor.ARGB32.green(tint) / 255F - 1) * strength;
        float tb = 1 + (FastColor.ARGB32.blue(tint) / 255F - 1) * strength;
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
        image.copyFrom(source);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                float r = abgr & 0xFF;
                float g = (abgr >> 8) & 0xFF;
                float b = (abgr >> 16) & 0xFF;
                float grey = 0.299F * r + 0.587F * g + 0.114F * b;
                r = (grey + (r - grey) * saturation) * tr;
                g = (grey + (g - grey) * saturation) * tg;
                b = (grey + (b - grey) * saturation) * tb;
                image.setPixelRGBA(x, y, (abgr & 0xFF000000) | clamp(b) << 16 | clamp(g) << 8 | clamp(r));
            }
        }
        return image;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    /** Where a GUI sprite's texture (and its animation {@code .mcmeta}) lives. */
    public static ResourceLocation spriteTexture(ResourceLocation sprite) {
        return sprite.withPath("textures/gui/sprites/" + sprite.getPath() + ".png");
    }

    @org.jetbrains.annotations.Nullable
    public static byte[] readMeta(ResourceLocation texture) {
        return net.minecraft.client.Minecraft.getInstance().getResourceManager()
                .getResource(texture.withPath(texture.getPath() + ".mcmeta")).map(r -> {
                    try (java.io.InputStream in = r.open()) {
                        return in.readAllBytes();
                    } catch (IOException e) {
                        return null;
                    }
                }).orElse(null);
    }

    /**
     * Our own copy of a banner linked to an addon section: the section's title and colours, and its artwork written to
     * an image file. Falls back to an unlinked copy if the section or its art can't be read.
     */
    public static BannerDef fromSection(BannerDef linked) {
        BannerDef own = linked.copy(linked.id);
        own.section = null;
        var section = linked.section != null ? com.hamish.emireorganised.compat.SimulatedCompat.section(linked.section) : null;
        if (section == null) {
            return own;
        }
        if (own.name == null || own.name.isEmpty()) {
            own.name = section.title().getString();
        }
        if (own.colors.equals(BannerDef.Colors.NONE)) {
            own.colors = new BannerDef.Colors(section.color(), section.secondaryColor(), section.background(), null, null);
        }
        if (own.image == null) {
            ResourceLocation texture = spriteTexture(section.sprite());
            var resource = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (java.io.InputStream in = resource.get().open(); NativeImage art = NativeImage.read(in)) {
                    own.image = bake(own, art, readMeta(texture), 0xFFFFFF, 0, 1);
                } catch (IOException e) {
                    EmiReorganised.LOGGER.warn("Couldn't copy banner art {}", texture, e);
                }
            }
        }
        return own;
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
