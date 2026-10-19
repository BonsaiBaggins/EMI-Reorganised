package com.bonsaibaggins.emireorganised.editor;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Search helpers shared by the editor's search bars: regex matching and a clear button. */
public final class EditorSearch {
    @Nullable private static String lastQuery;
    @Nullable private static Pattern lastPattern;

    private EditorSearch() {
    }

    /** Case-insensitive regex search (falling back to plain text for an invalid regex) on an item's name and id. */
    public static boolean matches(String query, ItemStack stack) {
        query = query.trim();
        if (query.isEmpty()) {
            return true;
        }
        String name = stack.getHoverName().getString();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Pattern pattern = pattern(query);
        if (pattern == null) {
            String q = query.toLowerCase(Locale.ROOT);
            return name.toLowerCase(Locale.ROOT).contains(q) || id.contains(q);
        }
        return pattern.matcher(name).find() || pattern.matcher(id).find();
    }

    public static boolean matches(String query, String text) {
        query = query.trim();
        if (query.isEmpty()) {
            return true;
        }
        Pattern pattern = pattern(query);
        return pattern != null ? pattern.matcher(text).find() : text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private static Pattern pattern(String query) {
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            try {
                lastPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                lastPattern = null;
            }
        }
        return lastPattern;
    }

    /** An edit box with an "x" at its inside right that clears it, shown while it has text. */
    public static class ClearableBox extends EditBox {
        public ClearableBox(Font font, int x, int y, int w, int h, Component message) {
            super(font, x, y, w, h, message);
        }

        private boolean overClear(double mx, double my) {
            return !getValue().isEmpty() && mx >= getX() + getWidth() - 11 && mx < getX() + getWidth()
                    && my >= getY() && my < getY() + getHeight();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (!getValue().isEmpty()) {
                Font font = net.minecraft.client.Minecraft.getInstance().font;
                graphics.drawString(font, "x", getX() + getWidth() - 9, getY() + (getHeight() - 8) / 2,
                        overClear(mouseX, mouseY) ? 0xFFFFFF55 : 0xFFAAAAAA, false);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (visible && active && button == 0 && overClear(mx, my)) {
                setValue("");
                setFocused(true);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
    }
}
