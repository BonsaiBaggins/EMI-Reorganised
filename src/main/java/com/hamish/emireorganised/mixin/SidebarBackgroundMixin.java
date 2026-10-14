package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.sidebar.IndexScroll;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Widens the vanilla-theme frame so a wide scroll bar fits inside it. */
@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class SidebarBackgroundMixin {
    @Shadow public EmiScreenManager.ScreenSpace space;

    @ModifyArg(method = "drawBackground", at = @At(value = "INVOKE", target = "Ldev/emi/emi/EmiRenderHelper;drawNinePatch(Ldev/emi/emi/runtime/EmiDrawContext;Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"), index = 4)
    private int emireorganised$widenForScrollbar(int width) {
        return space == null ? width : width + IndexScroll.extraWidth(space.getType());
    }
}
