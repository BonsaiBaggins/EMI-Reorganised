package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.sidebar.FavouriteSearch;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;

/** With the favourites search bar on, the favourites sidebar has no header (the bar sits above the panel instead). */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class FavouriteHeaderMixin {
    @Redirect(method = "createScreenSpace", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Ldev/emi/emi/screen/EmiScreenManager$SidebarPanel;header:Z"))
    private static void emireorganised$noFavouritesHeader(EmiScreenManager.SidebarPanel panel, boolean header) {
        panel.header = header && !(FavouriteSearch.enabled() && panel.supportsType(SidebarType.FAVORITES)
                && !panel.supportsType(SidebarType.INDEX));
    }
}
