package com.hamish.emireorganised.editor;

import com.hamish.emireorganised.Config;
import dev.emi.emi.api.widget.Bounds;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** A small button beside the player inventory that opens the Tab Editor. */
public final class OpenEditorButton extends Button {
    private static final int SIZE = 20;
    private static final ItemStack ICON = new ItemStack(Items.WRITABLE_BOOK);
    private final AbstractContainerScreen<?> screen;

    private OpenEditorButton(AbstractContainerScreen<?> screen) {
        super(0, 0, SIZE, SIZE, Component.translatable("emi_reorganised.open_editor"),
                b -> Minecraft.getInstance().setScreen(new TabEditorScreen(screen)), DEFAULT_NARRATION);
        this.screen = screen;
        setTooltip(Tooltip.create(Component.translatable("emi_reorganised.open_editor")));
        place();
    }

    private static boolean applies(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
    }

    private static boolean shown() {
        return switch (Config.get(Config.EDITOR_BUTTON)) {
            case ALWAYS -> true;
            case NEVER -> false;
            case CREATIVE_ONLY -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
        };
    }

    private void place() {
        setX(screen.getGuiLeft() + screen.getXSize() + 2);
        setY(screen.getGuiTop());
        visible = shown();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        place();
        if (!visible) {
            return;
        }
        setMessage(Component.empty());
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        setMessage(Component.translatable("emi_reorganised.open_editor"));
        graphics.renderItem(ICON, getX() + 2, getY() + 2);
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> container && applies(container)) {
            event.addListener(new OpenEditorButton(container));
        }
    }

    /** Moves the right sidebar's left edge past the button when they would overlap. */
    public static Bounds avoid(dev.emi.emi.screen.EmiScreenManager.SidebarPanel panel, Bounds bounds) {
        Screen screen = Minecraft.getInstance().screen;
        if (panel.side != dev.emi.emi.config.SidebarSide.RIGHT || !(screen instanceof AbstractContainerScreen<?> container)
                || !applies(container) || !shown()) {
            return bounds;
        }
        int edge = container.getGuiLeft() + container.getXSize() + 2 + SIZE + 2;
        if (bounds.x() >= edge || bounds.right() <= edge) {
            return bounds;
        }
        return new Bounds(edge, bounds.y(), bounds.right() - edge, bounds.height());
    }

    /** Keeps EMI's sidebars clear of the button. */
    public static void exclusion(Screen screen, Consumer<Bounds> consumer) {
        if (screen instanceof AbstractContainerScreen<?> container && applies(container) && shown()) {
            consumer.accept(new Bounds(container.getGuiLeft() + container.getXSize() + 2, container.getGuiTop(), SIZE, SIZE));
        }
    }
}
