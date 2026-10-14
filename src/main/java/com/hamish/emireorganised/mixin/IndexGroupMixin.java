package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.index.GroupIngredient;
import com.hamish.emireorganised.index.GroupState;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Left-clicking an item group's icon in EMI's index opens or closes it. */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class IndexGroupMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void emireorganised$toggleGroup(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) {
            return;
        }
        EmiStackInteraction hovered = EmiScreenManager.getHoveredStack((int) mouseX, (int) mouseY, false);
        if (hovered.getStack() instanceof GroupIngredient group) {
            GroupState.toggle(group.key);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            cir.setReturnValue(true);
        }
    }
}
