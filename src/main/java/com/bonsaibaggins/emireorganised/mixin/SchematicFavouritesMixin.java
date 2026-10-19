package com.bonsaibaggins.emireorganised.mixin;

import com.bonsaibaggins.emireorganised.data.FavouriteTabs;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiFavorites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Schematic material list tabs are read-only: nothing can be added to or removed from them. */
@Mixin(value = EmiFavorites.class, remap = false)
public abstract class SchematicFavouritesMixin {
    @Inject(method = "addFavorite(Ldev/emi/emi/api/stack/EmiIngredient;)V", at = @At("HEAD"), cancellable = true)
    private static void emireorganised$add(EmiIngredient stack, CallbackInfo ci) {
        if (FavouriteTabs.readOnly()) {
            ci.cancel();
        }
    }

    @Inject(method = "addFavorite(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/recipe/EmiRecipe;)V", at = @At("HEAD"), cancellable = true)
    private static void emireorganised$addRecipe(EmiIngredient stack, EmiRecipe recipe, CallbackInfo ci) {
        if (FavouriteTabs.readOnly()) {
            ci.cancel();
        }
    }

    @Inject(method = "addFavoriteAt", at = @At("HEAD"), cancellable = true)
    private static void emireorganised$addAt(EmiIngredient stack, int index, CallbackInfo ci) {
        if (FavouriteTabs.readOnly()) {
            ci.cancel();
        }
    }

    @Inject(method = "removeFavorite", at = @At("HEAD"), cancellable = true)
    private static void emireorganised$remove(EmiIngredient stack, CallbackInfoReturnable<Boolean> cir) {
        if (FavouriteTabs.readOnly()) {
            cir.setReturnValue(false);
        }
    }
}
