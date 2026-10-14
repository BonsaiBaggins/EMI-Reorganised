package com.hamish.emireorganised.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hamish.emireorganised.EmiReorganised;
import com.hamish.emireorganised.compat.SimulatedCompat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * One-time import into our tabs: Simulated's section banners, the old Simulated EMI Banners {@code banners.json}
 * (and its images), and Reliable EMI stack group files. Runs once, guarded by a marker file.
 */
public final class Migration {
    private static final String MARKER = ".migrated";

    private Migration() {
    }

    /** Applies the import to {@code tabs} if it hasn't run before; returns true if anything may have changed. */
    public static boolean run(List<Tab> tabs) {
        Path marker = TabStore.dir().resolve(MARKER);
        if (Files.exists(marker)) {
            return false;
        }
        try {
            importSimulated(tabs);
        } catch (RuntimeException e) {
            EmiReorganised.LOGGER.warn("Simulated banner import failed", e);
        }
        try {
            migrateBanners(tabs);
        } catch (IOException | RuntimeException e) {
            EmiReorganised.LOGGER.warn("Banner migration failed", e);
        }
        try {
            migrateGroups(tabs);
        } catch (IOException | RuntimeException e) {
            EmiReorganised.LOGGER.warn("Stack group migration failed", e);
        }
        try {
            Files.createDirectories(TabStore.dir());
            Files.writeString(marker, "Delete this file to re-run the one-time import of Simulated banners, banners.json and Reliable EMI groups.\n");
        } catch (IOException e) {
            EmiReorganised.LOGGER.warn("Couldn't write {}", marker, e);
        }
        return true;
    }

    /** Adds a banner before the first item of each Simulated section in tabs copied from Simulated. */
    static void importSimulated(List<Tab> tabs) {
        if (!SimulatedCompat.isLoaded()) {
            return;
        }
        for (Tab tab : tabs) {
            if (tab.source != null && tab.source.getNamespace().equals("simulated")) {
                addSectionBanners(tab);
            }
        }
    }

    /** Adds a banner before the first item of each addon (Simulated) section in the tab that doesn't have one yet. */
    public static void addSectionBanners(Tab tab) {
        if (!SimulatedCompat.isLoaded()) {
            return;
        }
        {
            Set<ResourceLocation> seen = new HashSet<>();
            for (TabEntry entry : tab.entries) {
                if (entry instanceof TabEntry.Banner banner && banner.banner().section != null) {
                    seen.add(banner.banner().section);
                }
            }
            ResourceLocation previous = null;
            for (int i = 0; i < tab.entries.size(); i++) {
                if (!(tab.entries.get(i) instanceof TabEntry.Item item)) {
                    continue;
                }
                ResourceLocation section = SimulatedCompat.sectionOf(item.stack().getItem());
                if (section != null && !section.equals(previous) && seen.add(section)) {
                    BannerDef banner = new BannerDef("section_" + TabStore.idFor(section), "");
                    banner.section = section;
                    tab.entries.add(i++, new TabEntry.Banner(banner));
                }
                previous = section;
            }
        }
    }

    /** Old Simulated EMI Banners file: creative tab id -> banners placed before an item. */
    static void migrateBanners(List<Tab> tabs) throws IOException {
        Path oldDir = FMLPaths.CONFIGDIR.get().resolve("simulated_emi_banners");
        Path file = oldDir.resolve("banners.json");
        if (!Files.isRegularFile(file)) {
            return;
        }
        JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for (var tabEntry : root.entrySet()) {
            ResourceLocation key = ResourceLocation.tryParse(tabEntry.getKey());
            Tab tab = key == null ? null : tabs.stream().filter(t -> key.equals(t.source)).findFirst().orElse(null);
            if (tab == null || !tabEntry.getValue().isJsonArray()) {
                continue;
            }
            for (JsonElement element : tabEntry.getValue().getAsJsonArray()) {
                if (element.isJsonObject()) {
                    migrateBanner(tab, element.getAsJsonObject(), oldDir);
                }
            }
        }
    }

    private static void migrateBanner(Tab tab, JsonObject json, Path oldDir) {
        boolean hidden = json.has("hidden") && json.get("hidden").getAsBoolean();
        String sectionId = str(json, "section");
        ResourceLocation section = sectionId != null ? ResourceLocation.tryParse(sectionId) : null;
        int existing = -1;
        BannerDef banner;
        if (section != null) {
            for (int i = 0; i < tab.entries.size(); i++) {
                if (tab.entries.get(i) instanceof TabEntry.Banner b && section.equals(b.banner().section)) {
                    existing = i;
                }
            }
            if (existing < 0) {
                return;
            }
            if (hidden) {
                tab.entries.remove(existing);
                return;
            }
            banner = ((TabEntry.Banner) tab.entries.get(existing)).banner();
        } else {
            if (hidden) {
                return;
            }
            String id = str(json, "id");
            banner = new BannerDef(id != null ? id : UUID.randomUUID().toString(), "");
        }
        String name = str(json, "name");
        if (name != null) {
            banner.name = name;
        }
        String image = str(json, "image");
        banner.image = image != null ? copyImage(oldDir, image) : null;
        banner.colors = new BannerDef.Colors(integer(json, "titleColor"), integer(json, "titleSecondaryColor"),
                integer(json, "titleBackground"), integer(json, "fillLeft"), integer(json, "fillRight"), null, null);

        boolean end = json.has("end") && json.get("end").getAsBoolean();
        String before = json.has("before") && json.get("before").isJsonObject() ? str(json.getAsJsonObject("before"), "item") : null;
        if (section != null && !end && before == null) {
            return;
        }
        if (existing >= 0) {
            tab.entries.remove(existing);
        }
        int at = end || before == null ? -1 : indexOfItem(tab, before);
        if (at < 0) {
            at = existing >= 0 && !end ? Math.min(existing, tab.entries.size()) : tab.entries.size();
        }
        tab.entries.add(at, new TabEntry.Banner(banner));
    }

    @Nullable
    private static String copyImage(Path oldDir, String image) {
        Path source = oldDir.resolve(image);
        if (!Files.isRegularFile(source)) {
            return null;
        }
        try {
            Path dir = TabStore.dir().resolve("images");
            Files.createDirectories(dir);
            if (!Files.exists(dir.resolve(image))) {
                Files.copy(source, dir.resolve(image));
            }
            Path meta = oldDir.resolve(image + ".mcmeta");
            if (Files.isRegularFile(meta) && !Files.exists(dir.resolve(image + ".mcmeta"))) {
                Files.copy(meta, dir.resolve(image + ".mcmeta"));
            }
            return image;
        } catch (IOException e) {
            EmiReorganised.LOGGER.warn("Couldn't copy banner image {}", source, e);
            return null;
        }
    }

    /**
     * Reliable EMI stack groups: each becomes a group in every tab holding at least two of its items, placed where
     * the first of them was.
     */
    static void migrateGroups(List<Tab> tabs) throws IOException {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("remi").resolve("stack_groups");
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.walk(dir)) {
            files = stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path path : files) {
            JsonObject json;
            try {
                json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                EmiReorganised.LOGGER.warn("Skipping unreadable stack group {}", path, e);
                continue;
            }
            migrateGroup(tabs, json, path.getFileName().toString().replaceFirst("\\.json$", ""));
        }
    }

    private static void migrateGroup(List<Tab> tabs, JsonObject json, String fileName) {
        String regex = str(json, "simulated_emi_banners:regex");
        if (regex == null) {
            regex = str(json, "regex");
        }
        String tag = str(json, "simulated_emi_banners:tag");
        String type = str(json, "type");
        if (tag == null && type != null && type.endsWith(":tag")) {
            tag = str(json, "tag");
        }
        boolean explicit = json.has("contents") && json.get("contents").isJsonArray();
        LinkedHashSet<Item> members = new LinkedHashSet<>();
        if (explicit) {
            for (JsonElement element : json.getAsJsonArray("contents")) {
                if (!element.isJsonPrimitive()) {
                    continue;
                }
                String raw = element.getAsString().replaceFirst("^item:", "");
                if (raw.startsWith("#")) {
                    addTag(members, raw.substring(1));
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id != null) {
                    BuiltInRegistries.ITEM.getOptional(id).ifPresent(members::add);
                }
            }
        } else if (tag != null) {
            addTag(members, tag.replaceFirst("^#", ""));
        } else if (regex != null) {
            Pattern pattern = Pattern.compile(regex);
            for (Item item : BuiltInRegistries.ITEM) {
                if (pattern.matcher(BuiltInRegistries.ITEM.getKey(item).toString()).matches()) {
                    members.add(item);
                }
            }
        }
        if (members.size() < 2) {
            return;
        }
        String id = str(json, "id");
        if (id == null) {
            id = fileName;
        }
        String name = str(json, "name");
        if (name == null) {
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
            name = path.isEmpty() ? id : path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
        }
        List<Item> order = new ArrayList<>(members);
        for (Tab tab : tabs) {
            List<Integer> found = new ArrayList<>();
            for (int i = 0; i < tab.entries.size(); i++) {
                if (tab.entries.get(i) instanceof TabEntry.Item item && members.contains(item.stack().getItem())) {
                    found.add(i);
                }
            }
            if (found.size() < 2) {
                continue;
            }
            ItemGroup group = new ItemGroup(TabStore.sanitize(id), name);
            group.regex = regex;
            group.tag = tag;
            List<ItemStack> stacks = new ArrayList<>();
            for (int i : found) {
                stacks.add(((TabEntry.Item) tab.entries.get(i)).stack().copy());
            }
            if (explicit) {
                stacks.sort(java.util.Comparator.comparingInt(s -> order.indexOf(s.getItem())));
            }
            group.items.addAll(stacks);
            for (int k = found.size() - 1; k >= 1; k--) {
                tab.entries.remove((int) found.get(k));
            }
            tab.entries.set(found.getFirst(), new TabEntry.Group(group));
        }
    }

    private static void addTag(Set<Item> into, String tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag);
        if (id != null) {
            BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id)).forEach(holder -> into.add(holder.value()));
        }
    }

    private static int indexOfItem(Tab tab, String itemId) {
        for (int i = 0; i < tab.entries.size(); i++) {
            if (tab.entries.get(i) instanceof TabEntry.Item item
                    && BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString().equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static String str(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    @Nullable
    private static Integer integer(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : null;
    }
}
