package com.bonsaibaggins.emireorganised.mixin;

import com.bonsaibaggins.emireorganised.skin.ReorganisedSkin;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;

/** A skinned favourites sidebar has no header; the skin's search bar sits in its frame instead. */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class FavouriteHeaderMixin {
    @Redirect(method = "createScreenSpace", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Ldev/emi/emi/screen/EmiScreenManager$SidebarPanel;header:Z"))
    private static void emireorganised$noFavouritesHeader(EmiScreenManager.SidebarPanel panel, boolean header) {
        panel.header = header && !(ReorganisedSkin.applies(panel) && panel.supportsType(SidebarType.FAVORITES)
                && !panel.supportsType(SidebarType.INDEX));
    }
}
