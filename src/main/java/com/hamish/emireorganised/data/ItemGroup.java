package com.hamish.emireorganised.data;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** An item group inside a tab: an ordered item list, optionally rebuilt from a regex or tag. */
public final class ItemGroup {
    public String id;
    public String name;
    public ItemStack icon = ItemStack.EMPTY;
    public final List<ItemStack> items = new ArrayList<>();
    @Nullable public String regex;
    @Nullable public String tag;

    public ItemGroup(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public ItemStack displayIcon() {
        return !icon.isEmpty() ? icon : items.isEmpty() ? ItemStack.EMPTY : items.getFirst();
    }

    public ItemGroup copy(String newId) {
        ItemGroup copy = new ItemGroup(newId, name);
        copy.icon = icon.copy();
        items.forEach(stack -> copy.items.add(stack.copy()));
        copy.regex = regex;
        copy.tag = tag;
        return copy;
    }
}
