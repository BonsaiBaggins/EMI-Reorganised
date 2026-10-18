package com.hamish.emireorganised.compat;

import com.hamish.emireorganised.EmiReorganised;
import com.hamish.emireorganised.data.FavouriteTabs;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.cannon.MaterialChecklist;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import dev.emi.emi.screen.EmiScreenManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Create schematic material lists as read-only favourites tabs. Only loaded when Create is installed. */
public final class CreateSchematics {
    private static long countedTick = -1;
    private static final Map<Item, Integer> HELD = new HashMap<>();

    private CreateSchematics() {
    }

    /** "A" over a filled schematic in an inventory adds a favourites tab with its material list. */
    public static boolean tryAddHovered(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || screen.getFocused() instanceof EditBox
                || (EmiScreenManager.search != null && EmiScreenManager.search.isFocused())) {
            return false;
        }
        Slot slot = container.getSlotUnderMouse();
        Level level = Minecraft.getInstance().level;
        if (slot == null || level == null || !(slot.getItem().getItem() instanceof SchematicItem)) {
            return false;
        }
        ItemStack stack = slot.getItem();
        Map<Item, Integer> required = materials(level, stack);
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (required.isEmpty() && server != null) {
            // Singleplayer: the uploaded copy the server keeps.
            required = materials(server.overworld(), stack);
        }
        if (required.isEmpty()) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "emi_reorganised.schematic.unreadable", stack.getHoverName()), true);
            }
            return true;
        }
        FavouriteTabs.addSchematic(stack.getHoverName().getString(), stack, required);
        return true;
    }

    private static Map<Item, Integer> materials(Level level, ItemStack stack) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        try {
            StructureTemplate template = SchematicItem.loadSchematic(level, stack);
            CompoundTag nbt = template.save(new CompoundTag());
            var blocks = level.holderLookup(Registries.BLOCK);
            ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
            if (palette.isEmpty() && nbt.contains("palettes", Tag.TAG_LIST)) {
                palette = nbt.getList("palettes", Tag.TAG_LIST).getList(0);
            }
            BlockState[] states = new BlockState[palette.size()];
            for (int i = 0; i < states.length; i++) {
                states[i] = NbtUtils.readBlockState(blocks, palette.getCompound(i));
            }
            MaterialChecklist checklist = new MaterialChecklist();
            for (Tag tag : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
                int index = ((CompoundTag) tag).getInt("state");
                if (index >= 0 && index < states.length && !states[index].isAir()) {
                    ItemRequirement requirement = ItemRequirement.of(states[index], null);
                    if (!requirement.isEmpty() && !requirement.isInvalid()) {
                        checklist.require(requirement);
                    }
                }
            }
            checklist.required.object2IntEntrySet().stream()
                    .sorted(Comparator.comparingInt((Object2IntMap.Entry<Item> e) -> e.getIntValue()).reversed())
                    .forEach(e -> out.put(e.getKey(), e.getIntValue()));
        } catch (Exception e) {
            EmiReorganised.LOGGER.warn("Couldn't read the schematic's material list", e);
        }
        return out;
    }

    /** How many of an item the player holds, including inside shulker boxes, bundles and similar containers. */
    public static int held(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        long tick = mc.level.getGameTime();
        if (tick != countedTick) {
            countedTick = tick;
            HELD.clear();
            var inventory = mc.player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                count(inventory.getItem(i), 0);
            }
        }
        return HELD.getOrDefault(item, 0);
    }

    private static void count(ItemStack stack, int depth) {
        if (stack.isEmpty() || depth > 4) {
            return;
        }
        HELD.merge(stack.getItem(), stack.getCount(), Integer::sum);
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.nonEmptyItems().forEach(inner -> count(inner, depth + 1));
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            bundle.items().forEach(inner -> count(inner, depth + 1));
        }
    }
}
