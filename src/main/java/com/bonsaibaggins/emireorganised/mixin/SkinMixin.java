package com.bonsaibaggins.emireorganised.mixin;

import com.bonsaibaggins.emireorganised.skin.ReorganisedSkin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.ScreenAlign;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lays out EMI's left and right sidebars for the Reorganised Vanilla skin in the room EMI found clear of exclusions. */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class SkinMixin {
    @Unique
    private static Bounds emireorganised$room = Bounds.EMPTY;

    @WrapOperation(method = "createScreenSpace", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/screen/EmiScreenManager;constrainBounds(Ljava/util/List;Ldev/emi/emi/api/widget/Bounds;Ldev/emi/emi/config/ScreenAlign;I)Ldev/emi/emi/api/widget/Bounds;"))
    private static Bounds emireorganised$room(List<Bounds> exclusion, Bounds bounds, ScreenAlign align, int headerOffset,
                                              Operation<Bounds> original) {
        Bounds room = original.call(exclusion, bounds, align, headerOffset);
        emireorganised$room = room;
        return room;
    }

    @Inject(method = "createScreenSpace", at = @At("TAIL"))
    private static void emireorganised$skinLayout(EmiScreenManager.SidebarPanel panel, Screen screen, List<Bounds> exclusion,
                                                 boolean rtl, Bounds bounds, SidebarSettings settings, CallbackInfo ci) {
        if (ReorganisedSkin.applies(panel)) {
            ReorganisedSkin.relayout(panel, emireorganised$room, settings.size().values.getInt(0), rtl);
        } else {
            ReorganisedSkin.forget(panel.side);
        }
    }
}
