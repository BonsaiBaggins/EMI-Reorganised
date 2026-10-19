package com.bonsaibaggins.emireorganised.mixin;

import com.bonsaibaggins.emireorganised.port.SidebarRollout;
import com.llamalad7.mixinextras.sugar.Local;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiRolloutMixin {
    @Shadow
    private static int lastWidth;

    @Inject(method = "render", at = @At("HEAD"))
    private static void simemibanners$animate(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (SidebarRollout.update()) {
            lastWidth = -1;
        }
    }

    @Inject(method = "addWidgets", at = @At("HEAD"))
    private static void simemibanners$screenOpened(net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {
        if (SidebarRollout.onScreenInit()) {
            lastWidth = -1;
        }
    }

    @Inject(method = "drawBackground", at = @At("HEAD"))
    private static void simemibanners$animateBeforeBackground(EmiDrawContext context, int mouseX, int mouseY, float delta,
                                                              CallbackInfo ci) {
        if (SidebarRollout.update()) {
            lastWidth = -1;
            EmiScreenManager.recalculate();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private static void simemibanners$renderButtons(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        SidebarRollout.render(context.raw(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void simemibanners$clickButtons(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (SidebarRollout.click(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @ModifyVariable(method = "createScreenSpace", at = @At("HEAD"), argsOnly = true)
    private static Bounds simemibanners$slide(Bounds bounds, @Local(argsOnly = true) EmiScreenManager.SidebarPanel panel) {
        return SidebarRollout.shift(panel.side, bounds);
    }
}
