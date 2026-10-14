package com.hamish.emireorganised.editor;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;

/** A popup drawn over the screen that opened it. Clicking outside the popup cancels it. */
public abstract class EditorPopup extends Screen {
    protected final Screen parent;

    protected EditorPopup(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected abstract int panelX();

    protected abstract int panelY();

    protected abstract int panelW();

    protected abstract int panelH();

    protected boolean insidePanel(double mx, double my) {
        return mx >= panelX() && mx < panelX() + panelW() && my >= panelY() && my < panelY() + panelH();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        parent.render(graphics, -1, -1, partialTick);
        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        graphics.fill(0, 0, width, height, 0x90000000);
        graphics.fill(panelX(), panelY(), panelX() + panelW(), panelY() + panelH(), 0xF0202020);
        graphics.renderOutline(panelX(), panelY(), panelW(), panelH(), 0xFF808080);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!insidePanel(mx, my)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        parent.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
