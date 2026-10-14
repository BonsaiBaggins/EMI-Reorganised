package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.sidebar.IndexScroll;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With scrolling on, the wheel scrolls rows without wrapping and the page buttons go away. */
@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class SidebarPanelScrollMixin {
    @Shadow @Final public SizedButtonWidget pageLeft;
    @Shadow @Final public SizedButtonWidget pageRight;
    @Shadow @Final public dev.emi.emi.screen.widget.SidebarButtonWidget cycle;
    @Shadow public EmiScreenManager.ScreenSpace space;
    @Shadow public int page;

    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
    private void emireorganised$scroll(int delta, CallbackInfo ci) {
        if (space != null && IndexScroll.enabled(space.getType())) {
            IndexScroll.scrollBy(space.getType(), delta);
            page = 0;
            ci.cancel();
        }
    }

    @Inject(method = "updateWidgetVisibility", at = @At("TAIL"))
    private void emireorganised$hidePageButtons(CallbackInfo ci) {
        if (space != null && IndexScroll.enabled(space.getType())) {
            pageLeft.visible = false;
            pageRight.visible = false;
        }
        if (space != null && space.getType() == dev.emi.emi.config.SidebarType.FAVORITES
                && com.hamish.emireorganised.sidebar.FavouriteSearch.enabled()) {
            // The search bar takes the header row.
            pageLeft.visible = false;
            pageRight.visible = false;
            cycle.visible = false;
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void emireorganised$firstPage(CallbackInfo ci) {
        if (space != null && IndexScroll.enabled(space.getType())) {
            page = 0;
        }
    }
}
