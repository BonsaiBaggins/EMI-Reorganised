package com.hamish.emireorganised.index;

import com.hamish.emireorganised.data.ItemGroup;
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
    private static final int FRAME = 0xFF7FA7D9;

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
            graphics.renderItem(icon, x, y);
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
}
