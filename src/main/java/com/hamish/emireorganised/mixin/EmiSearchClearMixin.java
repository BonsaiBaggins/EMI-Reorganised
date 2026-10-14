package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.Config;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.screen.widget.EmiSearchWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiSearchWidget.class, remap = false)
public abstract class EmiSearchClearMixin {
    @Unique
    private static final int simemibanners$SIZE = 7;

    @Unique
    private boolean simemibanners$showClear() {
        EditBox box = (EditBox) (Object) this;
        return EmiConfig.enabled && box.isVisible() && !box.getValue().isEmpty()
                && Config.get(Config.SEARCH_CLEAR_BUTTON);
    }

    @Unique
    private int simemibanners$clearX() {
        EditBox box = (EditBox) (Object) this;
        return box.getX() + box.getWidth() - simemibanners$SIZE - 3;
    }

    @Unique
    private int simemibanners$clearY() {
        EditBox box = (EditBox) (Object) this;
        return box.getY() + (box.getHeight() - simemibanners$SIZE) / 2;
    }

    @Unique
    private boolean simemibanners$overClear(double mouseX, double mouseY) {
        int x = simemibanners$clearX();
        int y = simemibanners$clearY();
        return mouseX >= x - 2 && mouseX < x + simemibanners$SIZE + 2 && mouseY >= y - 2 && mouseY < y + simemibanners$SIZE + 2;
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void simemibanners$renderClear(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!simemibanners$showClear()) {
            return;
        }
        int x = simemibanners$clearX();
        int y = simemibanners$clearY();
        int color = simemibanners$overClear(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFA0A0A0;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 50);
        for (int k = 0; k < simemibanners$SIZE; k++) {
            graphics.fill(x + k, y + k, x + k + 1, y + k + 1, color);
            graphics.fill(x + simemibanners$SIZE - 1 - k, y + k, x + simemibanners$SIZE - k, y + k + 1, color);
        }
        graphics.pose().popPose();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void simemibanners$clickClear(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && simemibanners$showClear() && simemibanners$overClear(mouseX, mouseY)) {
            EditBox box = (EditBox) (Object) this;
            box.setValue("");
            box.setFocused(true);
            cir.setReturnValue(true);
        }
    }
}
