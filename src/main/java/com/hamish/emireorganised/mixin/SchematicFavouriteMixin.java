package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.data.FavouriteTabs;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Held/required counters and a tick on favourites in a schematic material list tab. */
@Mixin(value = EmiFavorite.class, remap = false)
public abstract class SchematicFavouriteMixin {
    @Inject(method = "isUnbatchable", at = @At("HEAD"), cancellable = true)
    private void emireorganised$unbatched(CallbackInfoReturnable<Boolean> cir) {
        if (FavouriteTabs.selectedSchematic() != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void emireorganised$counter(GuiGraphics graphics, int x, int y, float delta, int flags, CallbackInfo ci) {
        FavouriteTabs.FavTab tab = FavouriteTabs.selectedSchematic();
        if (tab == null || !net.neoforged.fml.ModList.get().isLoaded("create")) {
            return;
        }
        Item item = null;
        for (EmiStack stack : ((EmiFavorite) (Object) this).getEmiStacks()) {
            ItemStack is = stack.getItemStack();
            if (!is.isEmpty()) {
                item = is.getItem();
                break;
            }
        }
        Integer required = item != null ? tab.required.get(item) : null;
        if (required == null) {
            return;
        }
        int held = com.hamish.emireorganised.compat.CreateSchematics.held(item);
        var font = Minecraft.getInstance().font;
        String text = held + "/" + required;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 17, y + 12, 300);
        graphics.pose().scale(0.5F, 0.5F, 1);
        graphics.drawString(font, text, -font.width(text), 0, held >= required ? 0x55FF55 : 0xFFFFFF, true);
        graphics.pose().popPose();
        if (held >= required) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            // A small green tick in the top right corner.
            int c = 0xFF2DD12D;
            graphics.fill(x + 10, y + 3, x + 11, y + 4, c);
            graphics.fill(x + 11, y + 4, x + 12, y + 5, c);
            graphics.fill(x + 12, y + 3, x + 13, y + 4, c);
            graphics.fill(x + 13, y + 2, x + 14, y + 3, c);
            graphics.fill(x + 14, y + 1, x + 15, y + 2, c);
            graphics.fill(x + 15, y, x + 16, y + 1, c);
            graphics.fill(x + 10, y + 2, x + 11, y + 3, c);
            graphics.fill(x + 11, y + 3, x + 12, y + 4, c);
            graphics.fill(x + 12, y + 2, x + 13, y + 3, c);
            graphics.fill(x + 13, y + 1, x + 14, y + 2, c);
            graphics.fill(x + 14, y, x + 15, y + 1, c);
            graphics.pose().popPose();
        }
    }
}
