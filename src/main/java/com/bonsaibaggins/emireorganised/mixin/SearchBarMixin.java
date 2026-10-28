package com.bonsaibaggins.emireorganised.mixin;

import com.bonsaibaggins.emireorganised.skin.ReorganisedSkin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sets EMI's search bar into the skin's search slot on the index sidebar. */
@Mixin(value = EmiScreenManager.class, remap = false, priority = 1100)
public abstract class SearchBarMixin {
    @WrapMethod(method = "createScreenSpace")
    private static void emireorganised$reserveSearch(EmiScreenManager.SidebarPanel panel, Screen screen, List<Bounds> exclusion,
                                                    boolean rtl, Bounds bounds, SidebarSettings settings, Operation<Void> original) {
        if (!ReorganisedSkin.applies(panel)) {
            original.call(panel, screen, exclusion, rtl, bounds, settings);
            return;
        }
        // The skin has a search slot in every sidebar's frame; keep EMI from reserving its own search space.
        boolean center = EmiConfig.centerSearchBar;
        EmiConfig.centerSearchBar = true;
        try {
            original.call(panel, screen, exclusion, rtl, bounds, settings);
        } finally {
            EmiConfig.centerSearchBar = center;
        }
    }

    @Inject(method = "recalculate", at = @At("RETURN"))
    private static void emireorganised$placeAfterRecalculate(CallbackInfo ci) {
        emireorganised$place();
    }

    @Inject(method = "addWidgets", at = @At("TAIL"))
    private static void emireorganised$placeAfterInit(Screen screen, CallbackInfo ci) {
        emireorganised$place();
    }

    @Unique
    private static void emireorganised$place() {
        if (EmiScreenManager.search == null) {
            return;
        }
        ReorganisedSkin.Layout skin = ReorganisedSkin.layout(EmiScreenManager.getPanelFor(EmiConfig.searchSidebar));
        EmiScreenManager.search.setBordered(skin == null);
        if (skin != null) {
            ReorganisedSkin.placeSearch(EmiScreenManager.search, skin, 4);
        }
    }
}
