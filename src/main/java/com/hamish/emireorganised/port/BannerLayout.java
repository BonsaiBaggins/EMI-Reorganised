package com.hamish.emireorganised.port;

import com.hamish.emireorganised.port.compat.RemiCompat;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import dev.simulated_team.simulated.registrate.simulated_tab.SimulatedCreativeTab;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Inserts empty placeholder slots into an EMI stack list so that every banner (Simulated section or user banner) starts on a
 * fresh row preceded by a full-width banner row, mirroring Simulated's creative tab layout.
 */
public final class BannerLayout {
    public record Banner(int index, BannerView view) {
    }

    public record Key(List<?> source, int sourceSize, int tw, int th, int[] widths, boolean scrollMode,
                      boolean active, Object tab, int version) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.source == source && k.sourceSize == sourceSize && k.tw == tw && k.th == th
                    && Arrays.equals(k.widths, widths) && k.scrollMode == scrollMode && k.active == active && k.tab == tab && k.version == version;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(source), sourceSize, tw, th, Arrays.hashCode(widths), scrollMode, active);
        }
    }

    public static final BannerLayout NONE = new BannerLayout(null, List.of(), List.of());

    public final Key key;
    public final List<? extends EmiIngredient> stacks;
    public final List<Banner> banners;

    private BannerLayout(Key key, List<? extends EmiIngredient> stacks, List<Banner> banners) {
        this.key = key;
        this.stacks = stacks;
        this.banners = banners;
    }

    public static Key keyFor(EmiScreenManager.ScreenSpace space, List<? extends EmiIngredient> source) {
        boolean scroll = RemiCompat.isScrollMode();
        return new Key(source, source.size(), space.tw, space.th, space.widths, scroll, isActive(space), RemiCompat.currentTab(),
                BannerStore.version() * 31 + CreativeOrder.version());
    }

    private static boolean isActive(EmiScreenManager.ScreenSpace space) {
        if (!BannerConfig.get(BannerConfig.ENABLED) || space.getType() != SidebarType.INDEX) {
            return false;
        }
        if (!BannerConfig.get(BannerConfig.SHOW_WHILE_SEARCHING) && space.search
                && EmiScreenManager.search != null && !EmiScreenManager.search.getValue().isEmpty()) {
            return false;
        }
        return BannerConfig.get(BannerConfig.SHOW_IN_FULL_INDEX) || !RemiCompat.isFullIndex();
    }

    public static BannerLayout build(Key key, List<? extends EmiIngredient> source) {
        if (!key.active() || key.th() <= 0 || key.tw() <= 0 || Arrays.stream(key.widths()).sum() <= 0) {
            return new BannerLayout(key, source, List.of());
        }

        if (!RemiCompat.isLoaded() && RemiCompat.isFullIndex() && CreativeOrder.enabled()) {
            source = CreativeOrder.sort(source);
        }
        List<EmiIngredient> out = new ArrayList<>(source.size() + source.size() / 4);
        List<Banner> banners = new ArrayList<>();
        Rows rows = new Rows(key);
        Object[] sections = segmentsOf(source, key.tab(), RemiCompat.isFullIndex());
        boolean[] bannerAt = bannerPositions(sections);

        for (int i = 0; i < sections.length; i++) {
            if (bannerAt[i]) {
                rows.finishRow(out);
                for (BannerView view : ((Group) sections[i]).views) {
                    rows.skipEmptyRows();
                    banners.add(new Banner(out.size(), view));
                    rows.pad(out, rows.width());
                    rows.row++;
                }
            }

            rows.skipEmptyRows();
            out.add(source.get(i));
            if (++rows.col >= rows.width()) {
                rows.row++;
                rows.col = 0;
            }
        }

        if (banners.isEmpty()) {
            return new BannerLayout(key, source, List.of());
        }
        return new BannerLayout(key, List.copyOf(out), List.copyOf(banners));
    }

    /** Models EMI's row widths: rows repeat per page, or are uniformly {@code tw} wide in REMI scroll mode. */
    private static final class Rows {
        final Key key;
        int row;
        int col;

        Rows(Key key) {
            this.key = key;
        }

        int width() {
            return key.scrollMode() ? key.tw() : key.widths()[Math.floorMod(row, key.th())];
        }

        void skipEmptyRows() {
            if (col == 0) {
                while (width() == 0) {
                    row++;
                }
            }
        }

        void finishRow(List<EmiIngredient> out) {
            if (col > 0) {
                pad(out, width() - col);
                row++;
                col = 0;
            }
        }

        void pad(List<EmiIngredient> out, int count) {
            for (int i = 0; i < count; i++) {
                out.add(EmiStack.EMPTY);
            }
        }
    }

    private static final Object HIDDEN = new Object();

    /** Banners shown back to back before the same item. Compared by identity so each group is its own segment. */
    static final class Group {
        final List<BannerView> views;

        Group(List<BannerView> views) {
            this.views = views;
        }
    }

    /** Segment (banner) of each entry; tab-hidden items inherit the preceding entry's segment so they don't split runs. */
    private static Object[] segmentsOf(List<? extends EmiIngredient> source, Object currentTab, boolean fullIndex) {
        if (fullIndex && CreativeOrder.enabled()) {
            Object[] out = new Object[source.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = CreativeOrder.segmentOf(source.get(i));
            }
            return out;
        }
        Map<Item, Object> segments = segments(currentTab, fullIndex);
        Object[] out = new Object[source.size()];
        Object previous = null;
        for (int i = 0; i < out.length; i++) {
            Item item = itemOf(source.get(i));
            Object segment = item == null ? null : segments.get(item);
            if (segment == HIDDEN) {
                segment = previous;
            }
            out[i] = segment;
            previous = segment;
        }
        return out;
    }

    /**
     * One banner per segment, placed before its longest contiguous run. Stray entries elsewhere in the list (e.g.
     * Reliable EMI stack groups, which sit at their own position) don't get a duplicate banner.
     */
    private static boolean[] bannerPositions(Object[] segments) {
        Map<Object, int[]> best = new HashMap<>();
        int i = 0;
        while (i < segments.length) {
            Object segment = segments[i];
            int start = i;
            while (i < segments.length && segments[i] == segment) {
                i++;
            }
            if (segment instanceof Group) {
                int length = i - start;
                int[] current = best.get(segment);
                if (current == null || length > current[1]) {
                    best.put(segment, new int[]{start, length});
                }
            }
        }
        boolean[] positions = new boolean[segments.length];
        best.values().forEach(run -> positions[run[0]] = true);
        return positions;
    }

    /**
     * Maps items to the banner they sit under in their creative tab: the selected Reliable EMI tab, or every tab with
     * banners for the full index (first tab wins). Search-only items of a tab are {@link #HIDDEN}.
     */
    private static Map<Item, Object> segments(Object currentTab, boolean fullIndex) {
        Map<Item, Object> map = new HashMap<>();
        String simulatedTab = BannerStore.simulatedTabId();
        Map<String, List<BannerStore.Entry>> stored = BannerStore.tabs();
        List<CreativeModeTab> tabs = new ArrayList<>();
        if (!fullIndex && currentTab instanceof CreativeModeTab tab) {
            tabs.add(tab);
        } else {
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                if (id != null && (id.toString().equals(simulatedTab) || stored.containsKey(id.toString()))) {
                    tabs.add(tab);
                }
            }
        }
        for (CreativeModeTab tab : tabs) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id == null) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>(tab.getDisplayItems());
            List<ItemStack> search = new ArrayList<>(tab.getSearchTabDisplayItems());
            if (items.isEmpty() && id.toString().equals(simulatedTab)) {
                SimulatedCreativeTab.processItems(items::add, search::add);
            }
            List<BannerStore.Placed> placed = BannerStore.resolve(id.toString(), items, null);
            placed.removeIf(BannerStore.Placed::hidden);
            Set<Item> shown = new HashSet<>();
            Object segment = null;
            int p = 0;
            for (int i = 0; i < items.size(); i++) {
                if (p < placed.size() && placed.get(p).index() <= i) {
                    List<BannerView> views = new ArrayList<>();
                    while (p < placed.size() && placed.get(p).index() <= i) {
                        views.add(placed.get(p++).view());
                    }
                    segment = new Group(List.copyOf(views));
                }
                Item item = items.get(i).getItem();
                shown.add(item);
                if (segment != null) {
                    map.putIfAbsent(item, segment);
                }
            }
            if (!placed.isEmpty()) {
                for (ItemStack stack : search) {
                    if (!shown.contains(stack.getItem())) {
                        map.putIfAbsent(stack.getItem(), HIDDEN);
                    }
                }
            }
        }
        return map;
    }

    /** Splits a tab's stacks at its banners, so Reliable EMI stack groups can be built per section. */
    public static <T extends EmiIngredient> List<List<T>> splitForTab(Object currentTab, List<T> stacks) {
        if (!(currentTab instanceof CreativeModeTab)) {
            return List.of(stacks);
        }
        Map<Item, Object> segments = segments(currentTab, false);
        if (segments.isEmpty()) {
            return List.of(stacks);
        }
        List<List<T>> out = new ArrayList<>();
        List<T> chunk = new ArrayList<>();
        Object previous = null;
        for (T stack : stacks) {
            Item item = itemOf(stack);
            Object segment = item == null ? null : segments.get(item);
            if (segment == HIDDEN) {
                segment = previous;
            }
            if (segment != previous && !chunk.isEmpty()) {
                out.add(chunk);
                chunk = new ArrayList<>();
            }
            chunk.add(stack);
            previous = segment;
        }
        if (!chunk.isEmpty()) {
            out.add(chunk);
        }
        return out;
    }

    static Item itemOf(EmiIngredient ingredient) {
        try {
            List<EmiStack> stacks = RemiCompat.unwrap(ingredient).getEmiStacks();
            if (stacks.isEmpty()) {
                return null;
            }
            ItemStack stack = stacks.getFirst().getItemStack();
            return stack.isEmpty() ? null : stack.getItem();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
