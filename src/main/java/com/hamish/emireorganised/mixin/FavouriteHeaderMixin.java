package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.sidebar.FavouriteSearch;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The favourites sidebar shows our search bar where EMI draws its page header. */
@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class FavouriteHeaderMixin {
    @Shadow public EmiScreenManager.ScreenSpace space;

    @Shadow
    public abstract SidebarType getType();

    @Inject(method = "drawHeader", at = @At("HEAD"), cancellable = true)
    private void emireorganised$searchInHeader(EmiDrawContext context, int mouseX, int mouseY, float delta, int a, int b, CallbackInfo ci) {
        if (space != null && getType() == SidebarType.FAVORITES && FavouriteSearch.enabled()) {
            FavouriteSearch.render(context.raw(), space, mouseX, mouseY, delta);
            ci.cancel();
        }
    }
}
