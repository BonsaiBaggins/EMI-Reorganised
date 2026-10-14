package com.hamish.emireorganised.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hamish.emireorganised.EmiReorganised;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiPersistentData;
import dev.emi.emi.screen.EmiScreenManager;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tabs on the favourites sidebar. EMI keeps one favourites list; the selected tab's list is EMI's live list, and the
 * other tabs' lists are stored here (in EMI's own favourites JSON format) and swapped in when their tab is selected.
 */
public final class FavouriteTabs {
    public static final class FavTab {
        public ItemStack icon = ItemStack.EMPTY;
        ItemStack autoIcon = ItemStack.EMPTY;
        JsonArray favourites = new JsonArray();
    }

    private static final List<FavTab> TABS = new ArrayList<>();
    private static int selected = -1;
    private static boolean loaded;

    private FavouriteTabs() {
    }

    private static Path file() {
        return TabStore.dir().resolve("favourites.json");
    }

    public static List<FavTab> tabs() {
        ensureLoaded();
        return TABS;
    }

    public static int selected() {
        ensureLoaded();
        return selected;
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        TABS.clear();
        selected = 0;
        try {
            if (Files.exists(file())) {
                try (Reader reader = Files.newBufferedReader(file())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    for (var e : json.getAsJsonArray("tabs")) {
                        JsonObject t = e.getAsJsonObject();
                        FavTab tab = new FavTab();
                        tab.icon = item(t, "icon");
                        tab.autoIcon = item(t, "autoIcon");
                        tab.favourites = t.has("favourites") ? t.getAsJsonArray("favourites") : new JsonArray();
                        TABS.add(tab);
                    }
                    selected = json.has("selected") ? json.get("selected").getAsInt() : 0;
                }
            }
        } catch (Exception e) {
            EmiReorganised.LOGGER.warn("Couldn't read favourite tabs", e);
            TABS.clear();
        }
        if (TABS.isEmpty() && !Files.exists(file())) {
            TABS.add(new FavTab());
        }
        selected = TABS.isEmpty() ? -1 : Math.max(0, Math.min(selected, TABS.size() - 1));
        // EMI's live list is the truth for the selected tab.
        sync();
    }

    private static ItemStack item(JsonObject json, String key) {
        if (!json.has(key)) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(json.get(key).getAsString());
        return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    /** Called every frame the tabs show: with no tabs left, a new favourite creates a tab for it. */
    public static void tick() {
        ensureLoaded();
        if (TABS.isEmpty() && !EmiFavorites.favorites.isEmpty()) {
            TABS.add(new FavTab());
            selected = 0;
            changed();
        }
    }

    private static void sync() {
        if (selected >= 0 && selected < TABS.size()) {
            FavTab tab = TABS.get(selected);
            tab.favourites = EmiFavorites.save();
            ItemStack first = firstFavourite();
            if (!first.isEmpty()) {
                tab.autoIcon = first;
            }
        }
    }

    private static ItemStack firstFavourite() {
        for (EmiFavorite favorite : EmiFavorites.favorites) {
            for (var stack : favorite.getEmiStacks()) {
                ItemStack item = stack.getItemStack();
                if (!item.isEmpty()) {
                    return item;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack icon(int i) {
        FavTab tab = tabs().get(i);
        if (!tab.icon.isEmpty()) {
            return tab.icon;
        }
        ItemStack auto = i == selected ? firstFavourite() : tab.autoIcon;
        return auto.isEmpty() ? new ItemStack(Items.NETHER_STAR) : auto;
    }

    public static void select(int i) {
        ensureLoaded();
        if (i == selected || i < 0 || i >= TABS.size()) {
            return;
        }
        sync();
        selected = i;
        EmiFavorites.load(TABS.get(i).favourites.deepCopy());
        changed();
    }

    public static void add() {
        ensureLoaded();
        sync();
        TABS.add(new FavTab());
        selected = TABS.size() - 1;
        EmiFavorites.load(new JsonArray());
        changed();
    }

    public static void delete(int i) {
        ensureLoaded();
        if (i < 0 || i >= TABS.size()) {
            return;
        }
        sync();
        TABS.remove(i);
        if (TABS.isEmpty()) {
            selected = -1;
            EmiFavorites.load(new JsonArray());
        } else if (i == selected) {
            selected = Math.min(i, TABS.size() - 1);
            EmiFavorites.load(TABS.get(selected).favourites.deepCopy());
        } else if (i < selected) {
            selected--;
        }
        changed();
    }

    public static void setIcon(int i, ItemStack icon) {
        ensureLoaded();
        if (i >= 0 && i < TABS.size()) {
            TABS.get(i).icon = icon.copyWithCount(1);
            save();
        }
    }

    private static void changed() {
        save();
        EmiPersistentData.save();
        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
        com.hamish.emireorganised.sidebar.IndexScroll.reset(SidebarType.FAVORITES);
    }

    public static void save() {
        sync();
        JsonObject json = new JsonObject();
        json.addProperty("selected", selected);
        JsonArray tabs = new JsonArray();
        for (FavTab tab : TABS) {
            JsonObject t = new JsonObject();
            if (!tab.icon.isEmpty()) {
                t.addProperty("icon", BuiltInRegistries.ITEM.getKey(tab.icon.getItem()).toString());
            }
            if (!tab.autoIcon.isEmpty()) {
                t.addProperty("autoIcon", BuiltInRegistries.ITEM.getKey(tab.autoIcon.getItem()).toString());
            }
            t.add("favourites", tab.favourites);
            tabs.add(t);
        }
        json.add("tabs", tabs);
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (Exception e) {
            EmiReorganised.LOGGER.warn("Couldn't save favourite tabs", e);
        }
    }
}
