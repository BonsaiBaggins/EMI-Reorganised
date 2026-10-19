package com.bonsaibaggins.emireorganised.editor;

import com.bonsaibaggins.emireorganised.data.BannerDef;
import com.bonsaibaggins.emireorganised.port.BannerImages;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shown when a banner already has a background image: replace it with another, or delete it. */
public class EditBackgroundScreen extends EditorPopup {
    private static final int W = 260;
    private static final int H = 112;
    private final BannerDef banner;
    private final Runnable replace;
    private final Runnable delete;

    public EditBackgroundScreen(Screen parent, BannerDef banner, Runnable replace, Runnable delete) {
        super(parent, Component.translatable("emi_reorganised.background.title"));
        this.banner = banner;
        this.replace = replace;
        this.delete = delete;
    }

    @Override
    protected int panelX() {
        return (width - W) / 2;
    }

    @Override
    protected int panelY() {
        return (height - H) / 2;
    }

    @Override
    protected int panelW() {
        return W;
    }

    @Override
    protected int panelH() {
        return H;
    }

    @Override
    protected void init() {
        int x = panelX() + 10;
        int y = panelY() + 60;
        int bw = (W - 24) / 2;
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.background.replace"), b -> {
            minecraft.setScreen(parent);
            replace.run();
        }).bounds(x, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.background.delete"), b -> {
            minecraft.setScreen(parent);
            delete.run();
        }).bounds(x + bw + 4, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(panelX() + W / 2 - 50, y + 24, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, panelY() + 6, 0xFFFFFF);
        int x = panelX() + 10;
        int y = panelY() + 20;
        int w = W - 20;
        Path path = BannerImages.path(banner.image);
        BannerImages.Image image = path != null ? BannerImages.get(path) : null;
        if (image != null) {
            int h = Math.min(32, Math.max(1, w * image.frameHeight / Math.max(1, image.width)));
            graphics.blit(image.texture, x, y, w, h, 0, 0, image.width, image.frameHeight, image.width, image.height);
            graphics.drawString(font, banner.name, x + 4, y + h / 2 - 4, 0xFFFFFF, true);
        }
        graphics.drawString(font, font.plainSubstrByWidth(String.valueOf(banner.image), w), x, panelY() + 48, 0x909090);
    }
}
