package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.sidebar.VerticalTabs;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reserves room for, draws and handles clicks on our vertical tab column. */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class VerticalTabsMixin {
    @ModifyVariable(method = "createScreenSpace", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Bounds emireorganised$reserveTabs(Bounds bounds, EmiScreenManager.SidebarPanel panel) {
        return VerticalTabs.reserve(panel, bounds);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private static void emireorganised$renderTabs(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        VerticalTabs.render(context.raw(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void emireorganised$clickTabs(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (VerticalTabs.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private static void emireorganised$scrollTabs(double mouseX, double mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
        if (VerticalTabs.mouseScrolled(mouseX, mouseY, amount)) {
            cir.setReturnValue(true);
        }
    }
}
