package com.hamish.emireorganised.editor;

import com.hamish.emireorganised.data.BannerDef;
import com.hamish.emireorganised.data.ItemGroup;
import com.hamish.emireorganised.data.Tab;
import com.hamish.emireorganised.data.TabEntry;
import com.hamish.emireorganised.data.TabStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Tab Editor's working copy of every tab, plus the selection and clipboard. Nothing is written to disk until
 * {@link #save()}.
 */
public final class EditorModel {
    /** A cell in the centre grid: a tab entry, or (member >= 0) one item inside a group entry. */
    public record Ref(int entry, int member) implements Comparable<Ref> {
        public static Ref of(int entry) {
            return new Ref(entry, -1);
        }

        public boolean isMember() {
            return member >= 0;
        }

        @Override
        public int compareTo(Ref o) {
            return entry != o.entry ? Integer.compare(entry, o.entry) : Integer.compare(member, o.member);
        }
    }

    public final List<Tab> tabs = new ArrayList<>();
    public int tab;
    /** Read-only copies of the vanilla creative tabs, shown instead of {@link #tabs} in creative mode. */
    public final List<Tab> creative = new ArrayList<>();
    public boolean creativeMode;
    public int creativeTab;
    @Nullable private Tab tabClipboard;
    public final Set<Ref> selection = new LinkedHashSet<>();
    /** Groups closed in the editor (by group id); groups start open. */
    public final Set<String> closed = new HashSet<>();
    private final List<TabEntry> clipboard = new ArrayList<>();
    private boolean dirty;

    public EditorModel() {
        reload();
    }

    public void reload() {
        tabs.clear();
        TabStore.ensureLoaded();
        for (Tab t : TabStore.tabs()) {
            tabs.add(t.copy());
        }
        tab = Math.min(tab, Math.max(0, tabs.size() - 1));
        selection.clear();
        dirty = false;
    }

    public void save() {
        TabStore.setTabs(tabs.stream().map(Tab::copy).toList());
        dirty = false;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    @Nullable
    public Tab current() {
        List<Tab> list = shown();
        int i = shownIndex();
        return i >= 0 && i < list.size() ? list.get(i) : null;
    }

    /** The tab list being shown: the user's tabs, or the creative tabs in creative mode. */
    public List<Tab> shown() {
        return creativeMode ? creative : tabs;
    }

    public int shownIndex() {
        return creativeMode ? creativeTab : tab;
    }

    public void setCreativeMode(boolean on) {
        if (on && creative.isEmpty()) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null) {
                creative.addAll(TabStore.snapshot(level.registryAccess()));
            }
        }
        creativeMode = on;
        selection.clear();
    }

    public void copyTab(Tab t) {
        tabClipboard = t.copy();
    }

    public boolean canPasteTab() {
        return tabClipboard != null && !creativeMode;
    }

    /** Pastes a copy of the copied tab after the current tab, with fresh tab and group ids. */
    public void pasteTab() {
        if (!canPasteTab()) {
            return;
        }
        Tab t = new Tab(uniqueTabId(tabClipboard.name), tabClipboard.name);
        t.icon = tabClipboard.icon.copy();
        for (TabEntry e : tabClipboard.entries) {
            t.entries.add(e instanceof TabEntry.Group g ? new TabEntry.Group(g.group().copy(uniqueGroupId(g.group().name))) : e.copy());
        }
        tabs.add(Math.min(tab + 1, tabs.size()), t);
        tab = tabs.indexOf(t);
        selection.clear();
        dirty = true;
    }

    public ItemGroup newEmptyGroup(String name) {
        ItemGroup group = new ItemGroup(uniqueGroupId(name), name);
        entries().add(new TabEntry.Group(group));
        selection.clear();
        selection.add(Ref.of(entries().size() - 1));
        dirty = true;
        return group;
    }

    public void deleteGroup(int tabIndex, int entry) {
        List<TabEntry> entries = tabs.get(tabIndex).entries;
        if (entry >= 0 && entry < entries.size() && entries.get(entry) instanceof TabEntry.Group) {
            entries.remove(entry);
            selection.clear();
            dirty = true;
        }
    }

    private static List<ItemStack> stacksOf(List<TabEntry> items) {
        List<ItemStack> out = new ArrayList<>();
        for (TabEntry e : items) {
            if (e instanceof TabEntry.Item item) {
                out.add(item.stack());
            } else if (e instanceof TabEntry.Group g) {
                out.addAll(g.group().items);
            }
        }
        return out;
    }

    /** An item may appear once per tab: removes every other copy of the {@code kept} stacks from the tab. */
    private static void enforceUnique(List<TabEntry> entries, List<ItemStack> kept) {
        Set<ItemStack> keep = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        keep.addAll(kept);
        java.util.function.Predicate<ItemStack> duplicate = s -> !keep.contains(s)
                && kept.stream().anyMatch(k -> ItemStack.isSameItemSameComponents(k, s));
        for (int i = entries.size() - 1; i >= 0; i--) {
            TabEntry e = entries.get(i);
            if (e instanceof TabEntry.Item item && duplicate.test(item.stack())) {
                entries.remove(i);
            } else if (e instanceof TabEntry.Group g && g.group().items.removeIf(duplicate) && g.group().items.isEmpty()) {
                entries.remove(i);
            }
        }
    }

    private static int indexOfIdentity(List<?> list, Object o) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == o) {
                return i;
            }
        }
        return -1;
    }

    public List<TabEntry> entries() {
        Tab t = current();
        return t != null ? t.entries : new ArrayList<>();
    }

    public void selectTab(int index) {
        if (creativeMode) {
            if (index != creativeTab) {
                creativeTab = index;
                selection.clear();
            }
            return;
        }
        if (index != tab) {
            tab = index;
            selection.clear();
        }
    }

    // ---- tabs ----

    public void newTab(String name) {
        Tab t = new Tab(uniqueTabId(name), name);
        tabs.add(Math.min(tab + 1, tabs.size()), t);
        tab = tabs.indexOf(t);
        selection.clear();
        dirty = true;
    }

    private String uniqueTabId(String name) {
        String base = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (base.isBlank() || base.equals("_")) {
            base = "tab";
        }
        String id = base;
        for (int i = 2; containsTab(id); i++) {
            id = base + "_" + i;
        }
        return id;
    }

    private boolean containsTab(String id) {
        return tabs.stream().anyMatch(t -> t.id.equals(id));
    }

    public void deleteTab() {
        if (current() != null) {
            tabs.remove(tab);
            tab = Math.max(0, Math.min(tab, tabs.size() - 1));
            selection.clear();
            dirty = true;
        }
    }

    public void moveTab(int delta) {
        int to = tab + delta;
        if (current() != null && to >= 0 && to < tabs.size()) {
            tabs.add(to, tabs.remove(tab));
            tab = to;
            dirty = true;
        }
    }

    /** Moves tab {@code from} so it sits before the tab currently at {@code before} (or last). */
    public void moveTabTo(int from, int before) {
        if (from < 0 || from >= tabs.size()) {
            return;
        }
        before = Math.max(0, Math.min(before, tabs.size()));
        if (before == from || before == from + 1) {
            return;
        }
        Tab open = current();
        Tab moved = tabs.remove(from);
        tabs.add(before > from ? before - 1 : before, moved);
        tab = Math.max(0, tabs.indexOf(open));
        dirty = true;
    }

    /** After a group's item list changed: removes other copies of its items from the tab that owns it. */
    public void groupChanged(ItemGroup group) {
        dirty = true;
        for (Tab t : tabs) {
            if (t.entries.stream().anyMatch(e -> e instanceof TabEntry.Group g && g.group() == group)) {
                enforceUnique(t.entries, group.items);
                return;
            }
        }
    }

    // ---- selection helpers ----

    public boolean isSelected(Ref ref) {
        return selection.contains(ref) || ref.isMember() && selection.contains(Ref.of(ref.entry()));
    }

    /** The selected items as stacks, in grid order (whole groups contribute all their items). */
    public List<ItemStack> selectedStacks() {
        List<ItemStack> out = new ArrayList<>();
        List<TabEntry> entries = entries();
        for (Ref ref : sortedSelection()) {
            if (ref.entry() >= entries.size()) {
                continue;
            }
            TabEntry e = entries.get(ref.entry());
            if (ref.isMember() && e instanceof TabEntry.Group g && ref.member() < g.group().items.size()) {
                out.add(g.group().items.get(ref.member()));
            } else if (!ref.isMember() && e instanceof TabEntry.Item item) {
                out.add(item.stack());
            } else if (!ref.isMember() && e instanceof TabEntry.Group g) {
                out.addAll(g.group().items);
            }
        }
        return out;
    }

    public List<Ref> sortedSelection() {
        List<Ref> refs = new ArrayList<>(selection);
        refs.sort(Comparator.naturalOrder());
        return refs;
    }

    @Nullable
    public ItemGroup selectedGroup() {
        if (selection.size() != 1) {
            return null;
        }
        Ref ref = selection.iterator().next();
        List<TabEntry> entries = entries();
        return !ref.isMember() && ref.entry() < entries.size() && entries.get(ref.entry()) instanceof TabEntry.Group g ? g.group() : null;
    }

    // ---- editing ----

    /** Removes the selected cells and returns them as standalone entries, in order. */
    public List<TabEntry> removeSelection() {
        List<TabEntry> entries = entries();
        List<TabEntry> removed = new ArrayList<>();
        List<Ref> refs = sortedSelection();
        for (Ref ref : refs) {
            if (ref.entry() >= entries.size()) {
                continue;
            }
            TabEntry e = entries.get(ref.entry());
            if (ref.isMember()) {
                if (e instanceof TabEntry.Group g && ref.member() < g.group().items.size() && !selection.contains(Ref.of(ref.entry()))) {
                    removed.add(new TabEntry.Item(g.group().items.get(ref.member()).copy()));
                }
            } else {
                removed.add(e);
            }
        }
        for (int i = refs.size() - 1; i >= 0; i--) {
            Ref ref = refs.get(i);
            if (ref.entry() >= entries.size()) {
                continue;
            }
            if (ref.isMember()) {
                if (!selection.contains(Ref.of(ref.entry())) && entries.get(ref.entry()) instanceof TabEntry.Group g
                        && ref.member() < g.group().items.size()) {
                    g.group().items.remove(ref.member());
                }
            }
        }
        for (int i = refs.size() - 1; i >= 0; i--) {
            Ref ref = refs.get(i);
            if (!ref.isMember() && ref.entry() < entries.size()) {
                entries.remove(ref.entry());
            }
        }
        entries.removeIf(e -> e instanceof TabEntry.Group g && g.group().items.isEmpty());
        selection.clear();
        if (!removed.isEmpty()) {
            dirty = true;
        }
        return removed;
    }

    public void delete() {
        removeSelection();
    }

    public void copy() {
        clipboard.clear();
        List<TabEntry> entries = entries();
        for (Ref ref : sortedSelection()) {
            if (ref.entry() >= entries.size()) {
                continue;
            }
            TabEntry e = entries.get(ref.entry());
            if (ref.isMember()) {
                if (!selection.contains(Ref.of(ref.entry())) && e instanceof TabEntry.Group g && ref.member() < g.group().items.size()) {
                    clipboard.add(new TabEntry.Item(g.group().items.get(ref.member()).copy()));
                }
            } else {
                clipboard.add(e.copy());
            }
        }
    }

    public void cut() {
        copy();
        removeSelection();
    }

    public boolean canPaste() {
        return !clipboard.isEmpty();
    }

    /** Pastes after the last selected entry, or at the end. */
    public void paste() {
        if (clipboard.isEmpty() || current() == null) {
            return;
        }
        List<TabEntry> entries = entries();
        int at = selection.isEmpty() ? entries.size() : sortedSelection().getLast().entry() + 1;
        List<TabEntry> copies = clipboard.stream().map(TabEntry::copy).toList();
        insert(at, copies);
    }

    /** Inserts entries at a top-level position and selects them. */
    public void insert(int at, List<TabEntry> items) {
        List<TabEntry> entries = entries();
        at = Math.max(0, Math.min(at, entries.size()));
        entries.addAll(at, items);
        enforceUnique(entries, stacksOf(items));
        selection.clear();
        for (TabEntry item : items) {
            int i = indexOfIdentity(entries, item);
            if (i >= 0) {
                selection.add(Ref.of(i));
            }
        }
        dirty = true;
    }

    /** Inserts loose items into a group at a member position. */
    public void insertIntoGroup(int entry, int member, List<ItemStack> stacks) {
        if (!(entries().get(entry) instanceof TabEntry.Group g)) {
            return;
        }
        List<ItemStack> items = g.group().items;
        member = Math.max(0, Math.min(member, items.size()));
        List<ItemStack> copies = stacks.stream().map(ItemStack::copy).toList();
        items.addAll(member, copies);
        List<TabEntry> entries = entries();
        TabEntry groupEntry = entries.get(entry);
        enforceUnique(entries, copies);
        selection.clear();
        int index = indexOfIdentity(entries, groupEntry);
        for (ItemStack copy : copies) {
            int m = indexOfIdentity(items, copy);
            if (index >= 0 && m >= 0) {
                selection.add(new Ref(index, m));
            }
        }
        dirty = true;
    }

    /**
     * Moves the selection to a drop position: {@code entry} is the top-level index to insert before, and if
     * {@code member >= 0} the drop goes inside that group. Whole groups and banners can't go inside a group.
     */
    public void moveSelection(int entry, int member) {
        List<TabEntry> entries = entries();
        if (selection.isEmpty()) {
            return;
        }
        TabEntry anchor = entry < entries.size() ? entries.get(entry) : null;
        int before = 0;
        for (Ref ref : selection) {
            if (!ref.isMember() && ref.entry() < entry) {
                before++;
            }
        }
        ItemGroup targetGroup = member >= 0 && anchor instanceof TabEntry.Group g ? g.group() : null;
        int memberShift = 0;
        if (targetGroup != null) {
            for (Ref ref : selection) {
                if (ref.isMember() && ref.entry() == entry && ref.member() < member) {
                    memberShift++;
                }
            }
        }
        List<TabEntry> moved = removeSelection();
        if (targetGroup != null && moved.stream().allMatch(e -> e instanceof TabEntry.Item)) {
            int index = entries.indexOf(anchor);
            if (index < 0) {
                index = entries.size();
                entries.add(new TabEntry.Group(targetGroup));
            }
            insertIntoGroup(index, member - memberShift, moved.stream().map(e -> ((TabEntry.Item) e).stack()).toList());
            return;
        }
        int at = anchor != null && entries.contains(anchor) ? entries.indexOf(anchor) : entry - before;
        insert(at, moved);
    }

    public ItemGroup newGroup(String id, String name) {
        List<TabEntry> entries = entries();
        int at = selection.isEmpty() ? entries.size() : sortedSelection().getFirst().entry();
        List<ItemStack> stacks = selectedStacks().stream().map(ItemStack::copy).toList();
        int before = (int) selection.stream().filter(r -> !r.isMember() && r.entry() < at).count();
        removeSelection();
        ItemGroup group = new ItemGroup(id, name);
        group.items.addAll(stacks);
        insert(Math.min(at - before, entries.size()), List.of(new TabEntry.Group(group)));
        return group;
    }

    public void addToGroup(ItemGroup group, List<ItemStack> stacks) {
        List<ItemStack> added = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (group.items.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
                ItemStack copy = stack.copy();
                group.items.add(copy);
                added.add(copy);
            }
        }
        for (Tab t : tabs) {
            if (t.entries.stream().anyMatch(e -> e instanceof TabEntry.Group g && g.group() == group)) {
                enforceUnique(t.entries, added);
                if (t == current()) {
                    selection.clear();
                }
            }
        }
        dirty = true;
    }

    public BannerDef newBanner(String name) {
        BannerDef banner = new BannerDef(UUID.randomUUID().toString(), name);
        List<TabEntry> entries = entries();
        int at = selection.isEmpty() ? 0 : sortedSelection().getFirst().entry();
        insert(at, List.of(new TabEntry.Banner(banner)));
        return banner;
    }

    public boolean isOpen(ItemGroup group) {
        return !closed.contains(group.id);
    }

    public void toggleOpen(ItemGroup group) {
        if (!closed.remove(group.id)) {
            closed.add(group.id);
        }
    }

    public boolean anyOpen() {
        return entries().stream().anyMatch(e -> e instanceof TabEntry.Group g && isOpen(g.group()));
    }

    public void setAllOpen(boolean open) {
        for (TabEntry e : entries()) {
            if (e instanceof TabEntry.Group g) {
                if (open) {
                    closed.remove(g.group().id);
                } else {
                    closed.add(g.group().id);
                }
            }
        }
    }

    public String uniqueGroupId(String name) {
        String base = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (base.isBlank() || base.equals("_")) {
            base = "group";
        }
        Set<String> used = new HashSet<>();
        for (Tab t : tabs) {
            for (TabEntry e : t.entries) {
                if (e instanceof TabEntry.Group g) {
                    used.add(g.group().id);
                }
            }
        }
        String id = base;
        for (int i = 2; used.contains(id); i++) {
            id = base + "_" + i;
        }
        return id;
    }
}
