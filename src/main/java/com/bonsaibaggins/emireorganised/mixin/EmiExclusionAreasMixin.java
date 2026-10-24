package com.bonsaibaggins.emireorganised.mixin;

import com.bonsaibaggins.emireorganised.Config;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiExclusionAreas;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EmiExclusionAreas.class, remap = false)
public abstract class EmiExclusionAreasMixin {
    /** EMI always reserves the bottom-left corner for its config/tree buttons, even when both are hidden. */
    @WrapOperation(method = "getExclusion", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 1))
    private static boolean simemibanners$cornerButtons(List<Object> list, Object bounds, Operation<Boolean> original) {
        boolean visible = !EmiScreenManager.isDisabled();
        if (!EmiConfig.emiConfigButtonVisibility.resolve(visible) && !EmiConfig.recipeTreeButtonVisibility.resolve(visible)) {
            return false;
        }
        return original.call(list, bounds);
    }

    /** A search bar attached to the index panel must not push that panel away. */
    @WrapOperation(method = "getExclusion", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 2))
    private static boolean simemibanners$searchBar(List<Object> list, Object bounds, Operation<Boolean> original) {
        boolean skinned = com.bonsaibaggins.emireorganised.skin.ReorganisedSkin.enabled()
                && (EmiConfig.searchSidebar == dev.emi.emi.config.SidebarSide.LEFT || EmiConfig.searchSidebar == dev.emi.emi.config.SidebarSide.RIGHT);
        if (skinned || Config.get(Config.SEARCH_BAR_POSITION) != Config.SearchBarPosition.DEFAULT) {
            return false;
        }
        return original.call(list, bounds);
    }
}
