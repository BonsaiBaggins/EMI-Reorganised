package com.bonsaibaggins.emireorganised.data;

import net.minecraft.world.item.ItemStack;

/** One entry in a tab's list: an item, a banner or an item group. */
public sealed interface TabEntry {
    TabEntry copy();

    record Item(ItemStack stack) implements TabEntry {
        public TabEntry copy() {
            return new Item(stack.copy());
        }
    }

    record Banner(BannerDef banner) implements TabEntry {
        public TabEntry copy() {
            return new Banner(banner.copy(java.util.UUID.randomUUID().toString()));
        }
    }

    record Group(ItemGroup group) implements TabEntry {
        public TabEntry copy() {
            return new Group(group.copy(group.id + "_copy"));
        }
    }
}
