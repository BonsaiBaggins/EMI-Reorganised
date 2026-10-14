package com.hamish.emireorganised.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Popup for a tab's name (optional) and icon, with a searchable item grid to pick the icon from. */
public class IconPickerScreen extends EditorPopup {
    private static final int SLOT = 18;
    private static final int COLS = 11;
    private static final int ROWS = 5;
    @Nullable private final String initialName;
    private final BiConsumer<String, ItemStack> onDone;
    private ItemStack icon;
    @Nullable private EditBox nameBox;
    private EditBox searchBox;
    private List<ItemStack> results = List.of();
    @Nullable private String query;
    private int scroll;

    /** {@code initialName} null hides the name field (icon only). */
    public IconPickerScreen(Screen parent, Component title, @Nullable String initialName, ItemStack icon,
                            BiConsumer<String, ItemStack> onDone) {
        super(parent, title);
        this.initialName = initialName;
        this.icon = icon.copy();
        this.onDone = onDone;
    }

    @Override
    protected int panelX() {
        return left() - 8;
    }

    @Override
    protected int panelY() {
        return top() - 8;
    }

    @Override
    protected int panelW() {
        return COLS * SLOT + 16;
    }

    @Override
    protected int panelH() {
        return gridY() + ROWS * SLOT + 34 - top();
    }

    private int left() {
        return width / 2 - COLS * SLOT / 2;
    }

    private int top() {
        return height / 2 - (initialName != null ? 90 : 70);
    }

    private int gridY() {
        return top() + (initialName != null ? 64 : 40);
    }

    @Override
    protected void init() {
        int x = left();
        int w = COLS * SLOT;
        int y = top() + 14;
        if (initialName != null) {
            nameBox = new EditBox(font, x, y, w - 22, 18, Component.translatable("emi_reorganised.editor.tab_name"));
            nameBox.setMaxLength(64);
            nameBox.setValue(initialName);
            nameBox.setHint(Component.translatable("emi_reorganised.editor.tab_name").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(nameBox);
            setInitialFocus(nameBox);
            y += 24;
        }
        searchBox = new EditBox(font, x, y, initialName != null ? w : w - 22, 18, Component.translatable("emi_reorganised.editor.search"));
        searchBox.setHint(Component.translatable("emi_reorganised.editor.search").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(searchBox);
        if (initialName == null) {
            setInitialFocus(searchBox);
        }
        int by = gridY() + ROWS * SLOT + 6;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done()).bounds(x, by, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + w / 2 + 2, by, w / 2 - 2, 20).build());
    }

    private List<ItemStack> results() {
        String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (!q.equals(query)) {
            query = q;
            scroll = 0;
            List<ItemStack> out = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                ItemStack stack = item.getDefaultInstance();
                if (q.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                        || BuiltInRegistries.ITEM.getKey(item).toString().contains(q)) {
                    out.add(stack);
                }
            }
            results = out;
        }
        return results;
    }

    @Nullable
    private ItemStack resultAt(double mx, double my) {
        int x = left();
        int y = gridY();
        if (mx < x || my < y || mx >= x + COLS * SLOT || my >= y + ROWS * SLOT) {
            return null;
        }
        int index = (scroll + (int) ((my - y) / SLOT)) * COLS + (int) ((mx - x) / SLOT);
        List<ItemStack> list = results();
        return index < list.size() ? list.get(index) : null;
    }

    private void done() {
        String name = nameBox != null ? nameBox.getValue().trim() : "";
        if (nameBox != null && name.isEmpty()) {
            setFocused(nameBox);
            return;
        }
        minecraft.setScreen(parent);
        onDone.accept(name, icon);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        ItemStack stack = resultAt(mx, my);
        if (stack != null && button == 0) {
            icon = stack.copy();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, (results().size() + COLS - 1) / COLS - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left();
        int w = COLS * SLOT;
        graphics.drawCenteredString(font, title, width / 2, top(), 0xFFFFFF);
        int iconY = top() + 14;
        int ix = x + w - 19;
        graphics.fill(ix, iconY, ix + 18, iconY + 18, 0xFF8B8B8B);
        graphics.fill(ix + 1, iconY + 1, ix + 17, iconY + 17, 0xFF373737);
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, ix + 1, iconY + 1);
        }
        int gy = gridY();
        graphics.fill(x - 2, gy - 2, x + w + 2, gy + ROWS * SLOT + 2, 0xC0101010);
        List<ItemStack> list = results();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int index = (scroll + r) * COLS + c;
                if (index >= list.size()) {
                    break;
                }
                ItemStack stack = list.get(index);
                int sx = x + c * SLOT;
                int sy = gy + r * SLOT;
                if (ItemStack.isSameItem(stack, icon)) {
                    graphics.fill(sx, sy, sx + SLOT, sy + SLOT, 0x804C8CFF);
                }
                graphics.renderItem(stack, sx + 1, sy + 1);
            }
        }
        ItemStack hovered = resultAt(mouseX, mouseY);
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        } else if (mouseX >= ix && mouseX < ix + 18 && mouseY >= iconY && mouseY < iconY + 18) {
            graphics.renderTooltip(font, Component.translatable("emi_reorganised.editor.icon"), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
