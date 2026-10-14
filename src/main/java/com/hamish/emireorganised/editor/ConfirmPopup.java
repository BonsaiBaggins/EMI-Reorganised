package com.hamish.emireorganised.editor;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A yes/no question drawn over the editor. Clicking outside or pressing Esc goes back without answering. */
public class ConfirmPopup extends EditorPopup {
    private static final int W = 240;
    private final Component message;
    private final Component yes;
    private final Component no;
    private final BooleanConsumer callback;
    private List<FormattedCharSequence> lines = List.of();

    public ConfirmPopup(Screen parent, BooleanConsumer callback, Component title, Component message) {
        this(parent, callback, title, message, CommonComponents.GUI_YES, CommonComponents.GUI_NO);
    }

    public ConfirmPopup(Screen parent, BooleanConsumer callback, Component title, Component message, Component yes, Component no) {
        super(parent, title);
        this.callback = callback;
        this.message = message;
        this.yes = yes;
        this.no = no;
    }

    @Override
    protected int panelX() {
        return (width - W) / 2;
    }

    @Override
    protected int panelY() {
        return (height - panelH()) / 2;
    }

    @Override
    protected int panelW() {
        return W;
    }

    @Override
    protected int panelH() {
        return 50 + lines.size() * 10;
    }

    @Override
    protected void init() {
        lines = font.split(message, W - 20);
        int y = panelY() + panelH() - 26;
        addRenderableWidget(Button.builder(yes, b -> answer(true)).bounds(panelX() + 10, y, W / 2 - 14, 20).build());
        addRenderableWidget(Button.builder(no, b -> answer(false)).bounds(panelX() + W / 2 + 4, y, W / 2 - 14, 20).build());
    }

    private void answer(boolean value) {
        minecraft.setScreen(parent);
        callback.accept(value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, panelY() + 6, 0xFFFFFF);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawCenteredString(font, lines.get(i), width / 2, panelY() + 20 + i * 10, 0xC0C0C0);
        }
    }
}
