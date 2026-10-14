package com.hamish.emireorganised.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.hamish.emireorganised.EmiReorganised;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/** Loads and saves our tabs ({@code config/emi_reorganised/tabs}), creating them from the creative tabs on first use. */
public final class TabStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static List<Tab> tabs = new ArrayList<>();
    private static boolean loaded;
    private static int version;

    private TabStore() {
    }

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(EmiReorganised.MOD_ID);
    }

    private static Path tabsDir() {
        return dir().resolve("tabs");
    }

    public static List<Tab> tabs() {
        return tabs;
    }

    /** Bumped whenever the tabs change. */
    public static int version() {
        return version;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    @Nullable
    private static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    /** Loads the tabs once a world (and so its registries) is available. */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        HolderLookup.Provider registries = registries();
        if (registries == null) {
            return;
        }
        loaded = true;
        tabs = load(registries);
        if (tabs.isEmpty()) {
            tabs = snapshot(registries);
            save();
        }
        if (Migration.run(tabs)) {
            save();
        }
        version++;
    }

    public static void reload() {
        loaded = false;
        ensureLoaded();
    }

    /** Replaces every tab and saves. */
    public static void setTabs(List<Tab> newTabs) {
        tabs = new ArrayList<>(newTabs);
        save();
        version++;
    }

    // ---- creative tab snapshot ----

    /** Copies the visible creative tabs as they are now. */
    public static List<Tab> snapshot(HolderLookup.Provider registries) {
        rebuildCreativeTabs(registries);
        List<Tab> out = new ArrayList<>();
        for (CreativeModeTab creative : CreativeModeTabs.tabs()) {
            if (creative.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            Tab tab = fromCreative(creative);
            if (tab != null && !tab.entries.isEmpty()) {
                out.add(tab);
            }
        }
        return out;
    }

    /** A fresh copy of one creative tab (used by Reset), or null if it no longer exists. */
    @Nullable
    public static Tab snapshotOne(ResourceLocation source) {
        HolderLookup.Provider registries = registries();
        CreativeModeTab creative = BuiltInRegistries.CREATIVE_MODE_TAB.get(source);
        if (registries == null || creative == null) {
            return null;
        }
        rebuildCreativeTabs(registries);
        return fromCreative(creative);
    }

    private static void rebuildCreativeTabs(HolderLookup.Provider registries) {
        Minecraft mc = Minecraft.getInstance();
        FeatureFlagSet flags = mc.player != null ? mc.player.connection.enabledFeatures() : FeatureFlags.DEFAULT_FLAGS;
        boolean op = mc.player != null && mc.options.operatorItemsTab().get() && mc.player.canUseGameMasterBlocks();
        CreativeModeTabs.tryRebuildTabContents(flags, op, registries);
    }

    @Nullable
    private static Tab fromCreative(CreativeModeTab creative) {
        ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(creative);
        if (key == null) {
            return null;
        }
        Tab tab = new Tab(idFor(key), creative.getDisplayName().getString());
        tab.icon = creative.getIconItem().copyWithCount(1);
        tab.source = key;
        for (ItemStack stack : creative.getDisplayItems()) {
            if (!stack.isEmpty()) {
                tab.entries.add(new TabEntry.Item(stack.copyWithCount(1)));
            }
        }
        return tab;
    }

    public static String idFor(ResourceLocation key) {
        return key.getNamespace() + "_" + key.getPath().replace('/', '_');
    }

    public static String sanitize(String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return id.isEmpty() ? "tab" : id;
    }

    // ---- JSON ----

    private static List<Tab> load(HolderLookup.Provider registries) {
        List<Tab> out = new ArrayList<>();
        Path dir = tabsDir();
        Path orderFile = dir.resolve("_order.json");
        if (!Files.isRegularFile(orderFile)) {
            return out;
        }
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        try {
            for (JsonElement id : JsonParser.parseString(Files.readString(orderFile)).getAsJsonArray()) {
                Path file = dir.resolve(id.getAsString() + ".json");
                if (Files.isRegularFile(file)) {
                    try {
                        out.add(readTab(JsonParser.parseString(Files.readString(file)).getAsJsonObject(), ops));
                    } catch (Exception e) {
                        EmiReorganised.LOGGER.error("Couldn't read tab {}", file, e);
                    }
                }
            }
        } catch (Exception e) {
            EmiReorganised.LOGGER.error("Couldn't read tab order", e);
        }
        return out;
    }

    public static void save() {
        HolderLookup.Provider registries = registries();
        if (registries == null) {
            return;
        }
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        Path dir = tabsDir();
        try {
            Files.createDirectories(dir);
            JsonArray order = new JsonArray();
            List<String> keep = new ArrayList<>();
            for (Tab tab : tabs) {
                order.add(tab.id);
                keep.add(tab.id + ".json");
                Files.writeString(dir.resolve(tab.id + ".json"), GSON.toJson(writeTab(tab, ops)));
            }
            Files.writeString(dir.resolve("_order.json"), GSON.toJson(order));
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".json") && !name.equals("_order.json") && !keep.contains(name)) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        } catch (IOException e) {
            EmiReorganised.LOGGER.error("Couldn't save tabs", e);
        }
    }

    private static JsonObject writeTab(Tab tab, RegistryOps<JsonElement> ops) {
        JsonObject json = new JsonObject();
        json.addProperty("id", tab.id);
        json.addProperty("name", tab.name);
        json.add("icon", writeStack(tab.icon, ops));
        if (tab.hidden) {
            json.addProperty("hidden", true);
        }
        if (tab.source != null) {
            json.addProperty("source", tab.source.toString());
        }
        JsonArray entries = new JsonArray();
        for (TabEntry entry : tab.entries) {
            JsonObject e = new JsonObject();
            switch (entry) {
                case TabEntry.Item item -> e.add("item", writeStack(item.stack(), ops));
                case TabEntry.Banner banner -> e.add("banner", writeBanner(banner.banner()));
                case TabEntry.Group group -> e.add("group", writeGroup(group.group(), ops));
            }
            entries.add(e.has("item") ? e.get("item") : e);
        }
        json.add("entries", entries);
        return json;
    }

    private static Tab readTab(JsonObject json, RegistryOps<JsonElement> ops) {
        Tab tab = new Tab(json.get("id").getAsString(), json.has("name") ? json.get("name").getAsString() : json.get("id").getAsString());
        tab.icon = json.has("icon") ? readStack(json.get("icon"), ops) : ItemStack.EMPTY;
        tab.hidden = json.has("hidden") && json.get("hidden").getAsBoolean();
        tab.source = json.has("source") ? ResourceLocation.tryParse(json.get("source").getAsString()) : null;
        if (json.has("entries")) {
            for (JsonElement element : json.getAsJsonArray("entries")) {
                if (element.isJsonObject() && element.getAsJsonObject().has("banner")) {
                    tab.entries.add(new TabEntry.Banner(readBanner(element.getAsJsonObject().getAsJsonObject("banner"))));
                } else if (element.isJsonObject() && element.getAsJsonObject().has("group")) {
                    tab.entries.add(new TabEntry.Group(readGroup(element.getAsJsonObject().getAsJsonObject("group"), ops)));
                } else {
                    ItemStack stack = readStack(element.isJsonObject() && element.getAsJsonObject().has("item")
                            ? element.getAsJsonObject().get("item") : element, ops);
                    if (!stack.isEmpty()) {
                        tab.entries.add(new TabEntry.Item(stack));
                    }
                }
            }
        }
        return tab;
    }

    private static JsonObject writeBanner(BannerDef banner) {
        JsonObject json = new JsonObject();
        json.addProperty("id", banner.id);
        json.addProperty("name", banner.name);
        if (banner.section != null) {
            json.addProperty("section", banner.section.toString());
        }
        if (banner.image != null) {
            json.addProperty("image", banner.image);
        }
        BannerDef.Colors c = banner.colors;
        putColor(json, "titleColor", c.title());
        putColor(json, "outlineColor", c.outline());
        putColor(json, "titleBackground", c.titleBackground());
        putColor(json, "fillLeft", c.left());
        putColor(json, "fillRight", c.right());
        return json;
    }

    private static BannerDef readBanner(JsonObject json) {
        BannerDef banner = new BannerDef(json.has("id") ? json.get("id").getAsString() : java.util.UUID.randomUUID().toString(),
                json.has("name") ? json.get("name").getAsString() : "");
        banner.section = json.has("section") ? ResourceLocation.tryParse(json.get("section").getAsString()) : null;
        banner.image = json.has("image") ? json.get("image").getAsString() : null;
        banner.colors = new BannerDef.Colors(color(json, "titleColor"), color(json, "outlineColor"), color(json, "titleBackground"),
                color(json, "fillLeft"), color(json, "fillRight"));
        return banner;
    }

    private static JsonObject writeGroup(ItemGroup group, RegistryOps<JsonElement> ops) {
        JsonObject json = new JsonObject();
        json.addProperty("id", group.id);
        json.addProperty("name", group.name);
        if (!group.icon.isEmpty()) {
            json.add("icon", writeStack(group.icon, ops));
        }
        if (group.regex != null) {
            json.addProperty("regex", group.regex);
        }
        if (group.tag != null) {
            json.addProperty("tag", group.tag);
        }
        JsonArray items = new JsonArray();
        group.items.forEach(stack -> items.add(writeStack(stack, ops)));
        json.add("items", items);
        return json;
    }

    private static ItemGroup readGroup(JsonObject json, RegistryOps<JsonElement> ops) {
        ItemGroup group = new ItemGroup(json.get("id").getAsString(), json.has("name") ? json.get("name").getAsString() : "");
        group.icon = json.has("icon") ? readStack(json.get("icon"), ops) : ItemStack.EMPTY;
        group.regex = json.has("regex") ? json.get("regex").getAsString() : null;
        group.tag = json.has("tag") ? json.get("tag").getAsString() : null;
        if (json.has("items")) {
            for (JsonElement element : json.getAsJsonArray("items")) {
                ItemStack stack = readStack(element, ops);
                if (!stack.isEmpty()) {
                    group.items.add(stack);
                }
            }
        }
        return group;
    }

    private static void putColor(JsonObject json, String key, @Nullable Integer color) {
        if (color != null) {
            json.addProperty(key, String.format("%08X", color));
        }
    }

    @Nullable
    private static Integer color(JsonObject json, String key) {
        try {
            return json.has(key) ? (int) Long.parseLong(json.get(key).getAsString(), 16) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Plain items are saved as their ID; items with components use the item stack codec. */
    public static JsonElement writeStack(ItemStack stack, RegistryOps<JsonElement> ops) {
        if (stack.isEmpty()) {
            return new JsonPrimitive("minecraft:air");
        }
        if (stack.getComponentsPatch().isEmpty()) {
            return new JsonPrimitive(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1)).result()
                .orElse(new JsonPrimitive(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }

    public static ItemStack readStack(JsonElement json, RegistryOps<JsonElement> ops) {
        if (json.isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.tryParse(json.getAsString());
            return id != null && BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
        }
        return ItemStack.CODEC.parse(ops, json).result().orElse(ItemStack.EMPTY);
    }
}
