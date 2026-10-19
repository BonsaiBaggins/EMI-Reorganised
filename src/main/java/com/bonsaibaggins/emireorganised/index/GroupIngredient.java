package com.bonsaibaggins.emireorganised.index;

import com.bonsaibaggins.emireorganised.data.ItemGroup;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** An item group's icon in EMI's index. Clicking it opens or closes the group; its stacks are the group's items. */
public final class GroupIngredient implements EmiIngredient {
    public static final int FRAME = 0xFF5E5E5E;

    public final String key;
    public final ItemGroup group;
    private final List<EmiStack> members;
    private final ItemStack icon;

    public GroupIngredient(String key, ItemGroup group, List<EmiStack> members) {
        this.key = key;
        this.group = group;
        this.members = List.copyOf(members);
        ItemStack display = group.displayIcon();
        this.icon = display.isEmpty() ? members.getFirst().getItemStack() : display;
    }

    public boolean expanded() {
        return GroupState.isExpanded(key);
    }

    @Override
    public List<EmiStack> getEmiStacks() {
        return members;
    }

    @Override
    public EmiIngredient copy() {
        return this;
    }

    @Override
    public long getAmount() {
        return 1;
    }

    @Override
    public EmiIngredient setAmount(long amount) {
        return this;
    }

    @Override
    public float getChance() {
        return 1;
    }

    @Override
    public EmiIngredient setChance(float chance) {
        return this;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
        graphics.fill(x - 1, y - 1, x + 17, y, FRAME);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, FRAME);
        graphics.fill(x - 1, y, x, y + 16, FRAME);
        graphics.fill(x + 16, y, x + 17, y + 16, FRAME);
        if ((flags & RENDER_ICON) != 0) {
            renderCascade(graphics, x, y);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.drawString(Minecraft.getInstance().font, expanded() ? "-" : "+", x + 11, y - 1, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<ClientTooltipComponent> out = new ArrayList<>();
        out.add(line(Component.literal(group.name).withStyle(ChatFormatting.WHITE)));
        out.add(line(Component.translatable("emi_reorganised.group.count", members.size()).withStyle(ChatFormatting.GRAY)));
        out.add(line(Component.translatable(expanded() ? "emi_reorganised.group.collapse" : "emi_reorganised.group.expand")
                .withStyle(ChatFormatting.DARK_GRAY)));
        return out;
    }

    private static ClientTooltipComponent line(Component text) {
        return ClientTooltipComponent.create(text.getVisualOrderText());
    }

    private static final float[] SHADES = {1.0F, 0.72F, 0.5F};

    /** Up to three of the group's items: the first in front at the top left, later ones towards the bottom right, behind and darker. */
    private void renderCascade(GuiGraphics graphics, int x, int y) {
        List<ItemStack> layers = new java.util.ArrayList<>();
        layers.add(icon);
        for (EmiStack member : members) {
            if (layers.size() >= 3) {
                break;
            }
            ItemStack stack = member.getItemStack();
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, icon)) {
                layers.add(stack);
            }
        }
        drawCascade(graphics, layers, x, y);
    }

    /** Draws up to three items as a stack: the first in front at the top left, the rest behind towards the bottom right. */
    public static void drawCascade(GuiGraphics graphics, List<ItemStack> layers, int x, int y) {
        if (layers.isEmpty()) {
            return;
        }
        if (layers.size() == 1) {
            graphics.renderItem(layers.getFirst(), x, y);
            return;
        }
        layers = layers.subList(0, Math.min(3, layers.size()));
        float scale = 0.75f;
        float step = (16 - 16 * scale) / (layers.size() - 1);
        var pose = graphics.pose();
        for (int i = layers.size() - 1; i >= 0; i--) {
            pose.pushPose();
            pose.translate(x + step * i, y + step * i, (layers.size() - 1 - i) * 40);
            pose.scale(scale, scale, scale);
            float shade = SHADES[i];
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(shade, shade, shade, 1.0F);
            graphics.renderItem(layers.get(i), 0, 0);
            graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
    }
}
