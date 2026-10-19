package com.bonsaibaggins.emireorganised.editor;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A small popup asking for one line of text. */
public class TextPromptScreen extends EditorPopup {
    private final String initial;
    private final Consumer<String> onDone;
    private EditBox box;

    public TextPromptScreen(Screen parent, Component title, String initial, Consumer<String> onDone) {
        super(parent, title);
        this.initial = initial;
        this.onDone = onDone;
    }

    @Override
    protected int panelX() {
        return width / 2 - 110;
    }

    @Override
    protected int panelY() {
        return height / 2 - 46;
    }

    @Override
    protected int panelW() {
        return 220;
    }

    @Override
    protected int panelH() {
        return 84;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 20;
        box = new EditBox(font, x, y, 200, 20, title);
        box.setMaxLength(128);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done()).bounds(x, y + 26, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 102, y + 26, 98, 20).build());
    }

    private void done() {
        String value = box.getValue().trim();
        minecraft.setScreen(parent);
        if (!value.isEmpty()) {
            onDone.accept(value);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 36, 0xFFFFFF);
    }
}
