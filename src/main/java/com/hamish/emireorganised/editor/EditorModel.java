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
        return tab >= 0 && tab < tabs.size() ? tabs.get(tab) : null;
    }

    public List<TabEntry> entries() {
        Tab t = current();
        return t != null ? t.entries : new ArrayList<>();
    }

    public void selectTab(int index) {
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
        selection.clear();
        for (int i = 0; i < items.size(); i++) {
            selection.add(Ref.of(at + i));
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
        items.addAll(member, stacks.stream().map(ItemStack::copy).toList());
        selection.clear();
        for (int i = 0; i < stacks.size(); i++) {
            selection.add(new Ref(entry, member + i));
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
        for (ItemStack stack : stacks) {
            if (group.items.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
                group.items.add(stack.copy());
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
