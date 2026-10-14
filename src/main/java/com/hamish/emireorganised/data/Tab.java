package com.hamish.emireorganised.data;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** One of our tabs. {@code source} is the creative tab it was copied from (used by Reset). */
public final class Tab {
    public String id;
    public String name;
    public ItemStack icon = ItemStack.EMPTY;
    public boolean hidden;
    @Nullable public ResourceLocation source;
    public final List<TabEntry> entries = new ArrayList<>();

    public Tab(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Tab copy() {
        Tab copy = new Tab(id, name);
        copy.icon = icon.copy();
        copy.hidden = hidden;
        copy.source = source;
        for (TabEntry entry : entries) {
            copy.entries.add(switch (entry) {
                case TabEntry.Group g -> new TabEntry.Group(g.group().copy(g.group().id));
                case TabEntry.Banner b -> new TabEntry.Banner(b.banner().copy(b.banner().id));
                default -> entry.copy();
            });
        }
        return copy;
    }
}
