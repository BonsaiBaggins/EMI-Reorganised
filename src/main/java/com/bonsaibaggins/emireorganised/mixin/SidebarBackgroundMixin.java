package com.bonsaibaggins.emireorganised.mixin;

import com.bonsaibaggins.emireorganised.skin.ReorganisedSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Reorganised Vanilla skin (frame, panels and tabs) behind skinned sidebars, and clips their items to the skin's panel. */
@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class SidebarBackgroundMixin {
    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void emireorganised$skin(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ReorganisedSkin.drawBackground(context, (EmiScreenManager.SidebarPanel) (Object) this, mouseX, mouseY);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/screen/EmiScreenManager$ScreenSpace;render(Ldev/emi/emi/runtime/EmiDrawContext;IIFI)V"))
    private void emireorganised$clip(EmiScreenManager.ScreenSpace space, EmiDrawContext context, int mouseX, int mouseY,
                                     float delta, int start, Operation<Void> original) {
        boolean clipped = ReorganisedSkin.beginClip(context.raw(), space);
        int scrolled = clipped ? com.bonsaibaggins.emireorganised.sidebar.IndexScroll.pixel(space.getType()) : 0;
        context.raw().pose().pushPose();
        context.raw().pose().translate(0, -scrolled, 0);
        try {
            original.call(space, context, mouseX, mouseY, delta, start);
        } finally {
            context.raw().pose().popPose();
            if (clipped) {
                context.raw().disableScissor();
            }
        }
    }
}
