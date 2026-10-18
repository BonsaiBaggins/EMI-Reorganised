package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.Config;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** With the survival-inventory option on, the survival inventory stops swapping itself for the creative one. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Redirect(method = {"init", "containerTick"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasInfiniteItems()Z"))
    private boolean emireorganised$stayInSurvivalInventory(MultiPlayerGameMode gameMode) {
        return !Config.get(Config.SURVIVAL_INVENTORY_IN_CREATIVE) && gameMode.hasInfiniteItems();
    }
}
