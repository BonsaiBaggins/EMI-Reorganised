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

    @Unique
    private int emireorganised$offset;

    @WrapMethod(method = "getStacks")
    private List<? extends EmiIngredient> emireorganised$stacks(Operation<List<? extends EmiIngredient>> operation) {
        List<? extends EmiIngredient> full = emireorganised$fullStacks(operation);
        EmiScreenManager.ScreenSpace space = (EmiScreenManager.ScreenSpace) (Object) this;
        int offset = 0;
        if (full != null && com.hamish.emireorganised.sidebar.IndexScroll.enabled(space.getType())) {
            offset = Math.min(full.size(), com.hamish.emireorganised.sidebar.IndexScroll.row(space, space.getType(), full.size()) * space.tw);
        }
        if (offset != emireorganised$offset) {
            emireorganised$offset = offset;
            batcher.repopulate();
        }
        return offset == 0 || full == null ? full : full.subList(offset, full.size());
    }

    @Unique
    private List<? extends EmiIngredient> emireorganised$favSource;
    @Unique
    private int emireorganised$favVersion = -1;
    @Unique
    private int emireorganised$favSize = -1;
    @Unique
    private List<? extends EmiIngredient> emireorganised$favFiltered;

    @Unique
    private List<? extends EmiIngredient> emireorganised$filterFavourites(List<? extends EmiIngredient> original) {
        if (com.hamish.emireorganised.sidebar.FavouriteSearch.query().isEmpty()) {
            emireorganised$favSource = null;
            return original;
        }
        int version = com.hamish.emireorganised.sidebar.FavouriteSearch.version();
        if (original != emireorganised$favSource || version != emireorganised$favVersion || original.size() != emireorganised$favSize) {
            emireorganised$favSource = original;
            emireorganised$favVersion = version;
            emireorganised$favSize = original.size();
            emireorganised$favFiltered = com.hamish.emireorganised.sidebar.FavouriteSearch.filter(original);
            batcher.repopulate();
        }
        return emireorganised$favFiltered;
    }

    @Unique
    private List<? extends EmiIngredient> emireorganised$fullStacks(Operation<List<? extends EmiIngredient>> operation) {
        List<? extends EmiIngredient> original = operation.call();
        EmiScreenManager.ScreenSpace space = (EmiScreenManager.ScreenSpace) (Object) this;
        if (original != null && space.getType() == dev.emi.emi.config.SidebarType.FAVORITES) {
            return emireorganised$filterFavourites(original);
        }
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
        int index = startIndex + emireorganised$offset + getRawOffsetFromMouse(mouseX, mouseY);
        return index < 0 || index >= layout.stacks.size() || !layout.stacks.get(index).isEmpty();
    }

    /** Open groups get one continuous grey band behind their icon and items. */
    @Inject(method = "render", at = @At("HEAD"))
    private void emireorganised$renderGroupBands(EmiDrawContext context, int mouseX, int mouseY, float delta, int startIndex,
                                                 CallbackInfo ci) {
        EmiScreenManager.ScreenSpace space = (EmiScreenManager.ScreenSpace) (Object) this;
        startIndex += emireorganised$offset;
        List<? extends EmiIngredient> stacks = emireorganised$layout.stacks;
        for (int offset = 0; offset < space.pageSize && startIndex + offset < stacks.size(); offset++) {
            EmiIngredient stack = stacks.get(startIndex + offset);
            if (IndexBuilder.isGroupMember(stack)) {
                int x = space.getRawX(offset);
                int y = space.getRawY(offset);
                context.raw().fill(x, y, x + 18, y + 18, com.hamish.emireorganised.index.GroupIngredient.FRAME);
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void emireorganised$renderBanners(EmiDrawContext context, int mouseX, int mouseY, float delta, int startIndex,
                                              CallbackInfo ci) {
        EmiScreenManager.ScreenSpace space = (EmiScreenManager.ScreenSpace) (Object) this;
        if (com.hamish.emireorganised.sidebar.IndexScroll.enabled(space.getType())) {
            com.hamish.emireorganised.sidebar.IndexScroll.renderBar(context.raw(), space, space.getType());
        }
        startIndex += emireorganised$offset;
        BannerRenderer.render((EmiScreenManager.ScreenSpace) (Object) this, emireorganised$layout, context.raw(),
                mouseX, mouseY, startIndex);
    }
}
