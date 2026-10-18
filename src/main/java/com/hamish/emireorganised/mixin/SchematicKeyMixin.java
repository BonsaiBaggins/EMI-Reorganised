package com.hamish.emireorganised.mixin;

import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "A" over a filled Create schematic makes a schematic list tab before EMI's favourite key sees it. */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class SchematicKeyMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private static void emireorganised$schematicList(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == GLFW.GLFW_KEY_A && net.neoforged.fml.ModList.get().isLoaded("create")
                && com.hamish.emireorganised.compat.CreateSchematics.tryAddHovered(Minecraft.getInstance().screen)) {
            cir.setReturnValue(true);
        }
    }
}
