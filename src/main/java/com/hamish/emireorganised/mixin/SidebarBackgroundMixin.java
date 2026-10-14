package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.sidebar.IndexScroll;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * With a scroll bar, the vanilla-style frame grows on the right and the extra room is painted as frame (light grey),
 * so the bar sits in the border beside the dark item area rather than inside it.
 */
@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class SidebarBackgroundMixin {
    @Shadow public EmiScreenManager.ScreenSpace space;

    @WrapOperation(method = "drawBackground", at = @At(value = "INVOKE", target = "Ldev/emi/emi/EmiRenderHelper;drawNinePatch(Ldev/emi/emi/runtime/EmiDrawContext;Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"))
    private void emireorganised$widenForScrollbar(EmiDrawContext context, ResourceLocation texture, int x, int y, int w, int h,
                                                  int u, int v, int corner, int center, Operation<Void> original) {
        int extra = space == null ? 0 : IndexScroll.extraWidth(space.getType());
        original.call(context, texture, x, y, w + extra, h, u, v, corner, center);
        if (extra <= 0 || corner != 8) {
            return;
        }
        GuiGraphics graphics = context.raw();
        // Nine-patch layout: the dark item area's right bevel is the first column of the right corner (x + w - 8),
        // followed by 4 light grey columns. Keep the bevel where it was and paint grey up to the moved corner's grey.
        int bevel = x + w - 8;
        int top = y + 7;
        int bottom = y + h - 8;
        graphics.fill(bevel + 1, top, x + w + extra - 3, bottom + 1, 0xFFC6C6C6);
        graphics.fill(bevel, top + 1, bevel + 1, bottom + 1, 0xFFFFFFFF);
        graphics.fill(bevel, top, bevel + 1, top + 1, 0xFF8B8B8B);
    }
}
