package com.hamish.emireorganised.mixin;

import com.hamish.emireorganised.index.IndexBuilder;
import com.hamish.emireorganised.port.BannerLayout;
import com.hamish.emireorganised.port.BannerRenderer;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.StackBatcher;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the index sidebar's list with our tabs' items and banner rows. */
@Mixin(value = EmiScreenManager.ScreenSpace.class, remap = false, priority = 1100)
public abstract class ScreenSpaceMixin {
    @Shadow
    @Final
    public StackBatcher batcher;

    @Shadow
    public abstract int getRawOffsetFromMouse(int mouseX, int mouseY);

    @Unique
    private BannerLayout emireorganised$layout = BannerLayout.NONE;

    @WrapMethod(method = "getStacks")
    private List<? extends EmiIngredient> emireorganised$stacks(Operation<List<? extends EmiIngredient>> operation) {
        List<? extends EmiIngredient> original = operation.call();
        EmiScreenManager.ScreenSpace space = (EmiScreenManager.ScreenSpace) (Object) this;
        if (original == null || original == emireorganised$layout.stacks) {
            return original;
        }
        List<Object> sequence = IndexBuilder.sequence(space.getType(), original);
        if (sequence == null) {
            emireorganised$layout = BannerLayout.NONE;
            return original;
        }
        BannerLayout.Key key = BannerLayout.keyFor(space, sequence);
        if (!key.equals(emireorganised$layout.key)) {
            emireorganised$layout = BannerLayout.build(key, sequence);
            batcher.repopulate();
        }
        return emireorganised$layout.stacks;
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/EmiRenderHelper;drawSlotHightlight(Ldev/emi/emi/runtime/EmiDrawContext;IIIII)V"))
    private boolean emireorganised$skipBannerHighlight(EmiDrawContext context, int x, int y, int w, int h, int z,
                                                       @Local(argsOnly = true, ordinal = 0) int mouseX,
                                                       @Local(argsOnly = true, ordinal = 1) int mouseY,
                                                       @Local(argsOnly = true, ordinal = 2) int startIndex) {
        BannerLayout layout = emireorganised$layout;
        if (layout.banners.isEmpty()) {
            return true;
        }
        int index = startIndex + getRawOffsetFromMouse(mouseX, mouseY);
        return index < 0 || index >= layout.stacks.size() || !layout.stacks.get(index).isEmpty();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void emireorganised$renderBanners(EmiDrawContext context, int mouseX, int mouseY, float delta, int startIndex,
                                              CallbackInfo ci) {
        EmiScreenManager.ScreenSpace space = (EmiScreenManager.ScreenSpace) (Object) this;
        List<? extends EmiIngredient> stacks = emireorganised$layout.stacks;
        for (int offset = 0; offset < space.pageSize && startIndex + offset < stacks.size(); offset++) {
            if (IndexBuilder.isGroupMember(stacks.get(startIndex + offset))) {
                int x = space.getRawX(offset);
                int y = space.getRawY(offset);
                context.raw().fill(x + 1, y + 1, x + 17, y + 17, 0x305C9DFF);
            }
        }
        BannerRenderer.render((EmiScreenManager.ScreenSpace) (Object) this, emireorganised$layout, context.raw(),
                mouseX, mouseY, startIndex);
    }
}
