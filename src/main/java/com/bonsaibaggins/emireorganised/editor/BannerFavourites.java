package com.bonsaibaggins.emireorganised.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.bonsaibaggins.emireorganised.EmiReorganised;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Banner background images the user crowned, offered in the background picker. Stored as image file names. */
public final class BannerFavourites {
    private static final Set<String> FILES = new LinkedHashSet<>();
    private static boolean loaded;

    private BannerFavourites() {
    }

    private static Path file() {
        return BannerImageFiles.images().resolve("favourites.json");
    }

    public static Set<String> files() {
        load();
        FILES.removeIf(f -> !Files.isRegularFile(BannerImageFiles.images().resolve(f)));
        return FILES;
    }

    public static boolean contains(@Nullable String image) {
        load();
        return image != null && FILES.contains(image);
    }

    public static void toggle(@Nullable String image) {
        load();
        if (image == null) {
            return;
        }
        if (!FILES.remove(image)) {
            FILES.add(image);
        }
        save();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(file())) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file())) {
            JsonParser.parseReader(reader).getAsJsonArray().forEach(e -> FILES.add(e.getAsString()));
        } catch (Exception e) {
            EmiReorganised.LOGGER.warn("Couldn't read banner favourites", e);
        }
    }

    private static void save() {
        JsonArray json = new JsonArray();
        FILES.forEach(json::add);
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            EmiReorganised.LOGGER.warn("Couldn't save banner favourites", e);
        }
    }
}
