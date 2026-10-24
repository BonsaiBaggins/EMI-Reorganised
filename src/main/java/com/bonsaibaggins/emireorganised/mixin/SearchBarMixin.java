package com.bonsaibaggins.emireorganised.mixin;

import com.bonsaibaggins.emireorganised.skin.ReorganisedSkin;
import com.bonsaibaggins.emireorganised.Config;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attaches EMI's search bar above or below the index sidebar, shrinking the index to make room. */
@Mixin(value = EmiScreenManager.class, remap = false, priority = 1100)
public abstract class SearchBarMixin {
    @Unique
    private static final int simemibanners$SEARCH_SPACE = 22;

    @Unique
    private static Config.SearchBarPosition simemibanners$mode() {
        if (EmiConfig.searchSidebar != SidebarSide.LEFT && EmiConfig.searchSidebar != SidebarSide.RIGHT) {
            return Config.SearchBarPosition.DEFAULT;
        }
        return Config.get(Config.SEARCH_BAR_POSITION);
    }

    @WrapMethod(method = "createScreenSpace")
    private static void simemibanners$reserveSearch(EmiScreenManager.SidebarPanel panel, Screen screen, List<Bounds> exclusion,
                                                   boolean rtl, Bounds bounds, SidebarSettings settings, Operation<Void> original) {
        Config.SearchBarPosition mode = simemibanners$mode();
        if (ReorganisedSkin.applies(panel)) {
            // The skin has a search slot in every sidebar's frame; keep EMI from reserving its own search space.
            boolean center = EmiConfig.centerSearchBar;
            EmiConfig.centerSearchBar = true;
            try {
                original.call(panel, screen, exclusion, rtl, bounds, settings);
            } finally {
                EmiConfig.centerSearchBar = center;
            }
            return;
        }
        if (com.bonsaibaggins.emireorganised.sidebar.FavouriteSearch.enabled()
                && panel.supportsType(dev.emi.emi.config.SidebarType.FAVORITES)
                && !panel.supportsType(dev.emi.emi.config.SidebarType.INDEX)
                && panel.side != EmiConfig.searchSidebar) {
            // Room for the favourites search bar above the panel. The favourites have no 18px header row, so blocking
            // just the first slot row lines their frame up with the index (whose header row sits beside EMI's search bar).
            List<Bounds> withSearch = new ArrayList<>(exclusion);
            withSearch.add(new Bounds(bounds.x() - 2000, bounds.y(), bounds.width() + 4000, 4));
            original.call(panel, screen, withSearch, rtl, bounds, settings);
            return;
        }
        if (mode == Config.SearchBarPosition.DEFAULT || panel.side != EmiConfig.searchSidebar) {
            original.call(panel, screen, exclusion, rtl, bounds, settings);
            return;
        }
        List<Bounds> withSearch = new ArrayList<>(exclusion);
        // Wide enough to cover the panel even while it's sliding with the roll buttons.
        int y = mode == Config.SearchBarPosition.ABOVE_INDEX ? bounds.y() : bounds.bottom() - simemibanners$SEARCH_SPACE;
        withSearch.add(new Bounds(bounds.x() - 2000, y, bounds.width() + 4000, simemibanners$SEARCH_SPACE));
        // Stop EMI/REMI from also reserving their own search bar space at the bottom of this panel.
        boolean center = EmiConfig.centerSearchBar;
        EmiConfig.centerSearchBar = true;
        try {
            original.call(panel, screen, withSearch, rtl, bounds, settings);
        } finally {
            EmiConfig.centerSearchBar = center;
        }
    }

    @Inject(method = "recalculate", at = @At("RETURN"))
    private static void simemibanners$placeAfterRecalculate(CallbackInfo ci) {
        simemibanners$place();
    }

    @Inject(method = "addWidgets", at = @At("TAIL"))
    private static void simemibanners$placeAfterInit(Screen screen, CallbackInfo ci) {
        simemibanners$place();
    }

    @Unique
    private static void simemibanners$place() {
        if (EmiScreenManager.search == null) {
            return;
        }
        ReorganisedSkin.Layout skin = ReorganisedSkin.layout(EmiScreenManager.getPanelFor(EmiConfig.searchSidebar));
        EmiScreenManager.search.setBordered(skin == null);
        if (skin != null) {
            ReorganisedSkin.placeSearch(EmiScreenManager.search, skin, 4);
            return;
        }
        Config.SearchBarPosition mode = simemibanners$mode();
        if (mode == Config.SearchBarPosition.DEFAULT) {
            return;
        }
        EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(EmiConfig.searchSidebar);
        if (panel == null || panel.space == null || !panel.isVisible()) {
            return;
        }
        Bounds b = panel.getBounds();
        EmiScreenManager.search.setX(panel.space.tx);
        EmiScreenManager.search.setWidth(Math.max(1, panel.space.tw * 18));
        EmiScreenManager.search.setY(mode == Config.SearchBarPosition.ABOVE_INDEX ? b.top() - 20 : b.bottom() + 2);
    }
}
