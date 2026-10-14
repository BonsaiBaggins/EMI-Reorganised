package com.hamish.emireorganised.index;

import com.hamish.emireorganised.Config;
import com.hamish.emireorganised.data.Tab;
import com.hamish.emireorganised.data.TabEntry;
import com.hamish.emireorganised.data.TabState;
import com.hamish.emireorganised.data.TabStore;
import com.hamish.emireorganised.port.BannerImages;
import com.hamish.emireorganised.port.BannerView;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Builds what EMI's index sidebar shows from our tabs: a sequence of {@link EmiIngredient}s and {@link BannerView}s.
 * The full index lists every tab in order (each item only under its first tab), then anything not in a tab.
 */
public final class IndexBuilder {
    private static Object cacheKey;
    private static List<Object> cache = List.of();

    private IndexBuilder() {
    }

    private static boolean searching() {
        return EmiScreenManager.search != null && !EmiScreenManager.search.getValue().isEmpty();
    }

    /** The sequence for a sidebar, or null to leave EMI's own list. */
    @Nullable
    public static List<Object> sequence(SidebarType type, List<? extends EmiIngredient> original) {
        if (type != SidebarType.INDEX) {
            return null;
        }
        TabStore.ensureLoaded();
        if (!TabStore.isLoaded()) {
            return null;
        }
        boolean searching = searching();
        boolean banners = !searching || Config.get(Config.SHOW_BANNERS_WHILE_SEARCHING);
        List<Object> key = List.of(TabStore.version(), Objects.toString(TabState.selected()), searching, banners,
                System.identityHashCode(original), original.size(), BannerImages.version());
        if (key.equals(cacheKey)) {
            return cache;
        }
        cacheKey = key;
        cache = build(original, searching, banners);
        return cache;
    }

    private static List<Object> build(List<? extends EmiIngredient> original, boolean searching, boolean banners) {
        Set<EmiStack> allowed = null;
        if (searching) {
            allowed = new HashSet<>();
            for (EmiIngredient ingredient : original) {
                allowed.addAll(ingredient.getEmiStacks());
            }
        }
        String selected = TabState.selected();
        List<Object> out = new ArrayList<>();
        Set<EmiStack> seen = new HashSet<>();
        for (Tab tab : TabStore.tabs()) {
            if (tab.hidden || selected != null && !selected.equals(tab.id)) {
                continue;
            }
            for (TabEntry entry : tab.entries) {
                switch (entry) {
                    case TabEntry.Banner b -> {
                        if (banners) {
                            out.add(BannerView.of(b.banner(), BannerImages.path(b.banner().image)));
                        }
                    }
                    case TabEntry.Item item -> add(out, seen, allowed, item.stack());
                    case TabEntry.Group group -> {
                        for (ItemStack stack : group.group().items) {
                            add(out, seen, allowed, stack);
                        }
                    }
                }
            }
        }
        if (selected == null || searching) {
            // Anything EMI lists that isn't in one of our tabs (fluids, new mods' items) goes at the end.
            for (EmiIngredient ingredient : original) {
                if (ingredient instanceof EmiStack stack && !seen.contains(stack) && (allowed == null || allowed.contains(stack))
                        && (selected == null || !searching)) {
                    seen.add(stack);
                    out.add(stack);
                }
            }
        }
        // Drop banners left with nothing after them (e.g. every item in their section filtered out by search).
        List<Object> trimmed = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            Object entry = out.get(i);
            if (entry instanceof BannerView && (i + 1 >= out.size() || out.get(i + 1) instanceof BannerView)) {
                continue;
            }
            trimmed.add(entry);
        }
        return trimmed;
    }

    private static void add(List<Object> out, Set<EmiStack> seen, @Nullable Set<EmiStack> allowed, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        EmiStack emi = EmiStack.of(stack);
        if (seen.add(emi) && (allowed == null || allowed.contains(emi))) {
            out.add(emi);
        }
    }
}
