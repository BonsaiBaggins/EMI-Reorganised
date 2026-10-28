package com.bonsaibaggins.emireorganised.index;

import com.bonsaibaggins.emireorganised.Config;
import com.bonsaibaggins.emireorganised.data.ItemGroup;
import com.bonsaibaggins.emireorganised.data.Tab;
import com.bonsaibaggins.emireorganised.data.TabEntry;
import com.bonsaibaggins.emireorganised.data.TabState;
import com.bonsaibaggins.emireorganised.data.TabStore;
import com.bonsaibaggins.emireorganised.port.BannerImages;
import com.bonsaibaggins.emireorganised.port.BannerView;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
    /** Stacks shown as members of an open group (by identity), tinted in the index. */
    private static Set<EmiIngredient> members = Collections.newSetFromMap(new IdentityHashMap<>());

    private IndexBuilder() {
    }

    private static boolean searching() {
        return EmiScreenManager.search != null && !EmiScreenManager.search.getValue().isEmpty();
    }

    /** The sequence for a sidebar, or null to leave EMI's own list. */
    @Nullable
    public static List<Object> sequence(SidebarType type, List<? extends EmiIngredient> original) {
        if (type != SidebarType.INDEX || !com.bonsaibaggins.emireorganised.EmiReorganised.active()) {
            return null;
        }
        TabStore.ensureLoaded();
        if (!TabStore.isLoaded()) {
            return null;
        }
        boolean searching = searching();
        boolean banners = !searching || Config.get(Config.SHOW_BANNERS_WHILE_SEARCHING);
        List<Object> key = List.of(TabStore.version(), Objects.toString(selectedTab()), searching, banners,
                System.identityHashCode(original), original.size(), BannerImages.version(), GroupState.version());
        if (key.equals(cacheKey)) {
            return cache;
        }
        cacheKey = key;
        cache = build(original, searching, banners);
        return cache;
    }

    /** The tab the index shows; with the tab column hidden there's nothing to pick a tab with, so it shows them all. */
    @Nullable
    private static String selectedTab() {
        return com.bonsaibaggins.emireorganised.sidebar.VerticalTabs.enabled() ? TabState.selected() : null;
    }

    private static List<Object> build(List<? extends EmiIngredient> original, boolean searching, boolean banners) {
        // Only what EMI itself lists: its search results, or without a search every stack it doesn't hide.
        Set<EmiStack> allowed = new HashSet<>();
        for (EmiIngredient ingredient : original) {
            allowed.addAll(ingredient.getEmiStacks());
        }
        members = Collections.newSetFromMap(new IdentityHashMap<>());
        String selected = selectedTab();
        List<Object> out = new ArrayList<>();
        Set<EmiStack> seen = new HashSet<>();
        if (TabState.UNSORTED.equals(selected)) {
            Set<EmiStack> sorted = sortedStacks();
            for (EmiIngredient ingredient : original) {
                if (ingredient instanceof EmiStack stack && !sorted.contains(stack) && seen.add(stack)) {
                    out.add(stack);
                }
            }
            return out;
        }
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
                    case TabEntry.Group group -> addGroup(out, seen, allowed, searching, tab.id, group.group());
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

    /**
     * Adds a group as one icon, followed by its items if it's open. Outside search a group shows all its items, even
     * ones listed elsewhere; while searching each item only shows under the first place it appears.
     */
    private static void addGroup(List<Object> out, Set<EmiStack> seen, @Nullable Set<EmiStack> allowed, boolean searching,
                                 String tabId, ItemGroup group) {
        List<EmiStack> shown = new ArrayList<>();
        for (ItemStack stack : group.items) {
            if (stack.isEmpty()) {
                continue;
            }
            EmiStack emi = EmiStack.of(stack);
            if (allowed != null && !allowed.contains(emi)) {
                continue;
            }
            boolean first = seen.add(emi);
            if ((first || !searching) && !shown.contains(emi)) {
                shown.add(emi);
            }
        }
        if (shown.isEmpty()) {
            return;
        }
        if (shown.size() == 1) {
            out.add(shown.getFirst());
            return;
        }
        GroupIngredient icon = new GroupIngredient(GroupState.key(tabId, group.id), group, shown);
        out.add(icon);
        if (icon.expanded()) {
            out.addAll(shown);
            members.addAll(shown);
        }
    }

    /** Every stack listed in one of our visible tabs, loose or in a group. */
    private static Set<EmiStack> sortedStacks() {
        Set<EmiStack> out = new HashSet<>();
        for (Tab tab : TabStore.tabs()) {
            if (tab.hidden) {
                continue;
            }
            for (TabEntry entry : tab.entries) {
                if (entry instanceof TabEntry.Item item && !item.stack().isEmpty()) {
                    out.add(EmiStack.of(item.stack()));
                } else if (entry instanceof TabEntry.Group group) {
                    for (ItemStack stack : group.group().items) {
                        if (!stack.isEmpty()) {
                            out.add(EmiStack.of(stack));
                        }
                    }
                }
            }
        }
        return out;
    }

    public static boolean isGroupMember(EmiIngredient ingredient) {
        return members.contains(ingredient);
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
