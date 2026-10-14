package com.hamish.emireorganised.editor;

import com.hamish.emireorganised.data.ItemGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Edits a group's name, its optional regex/tag, and its item list (drag to reorder, right-click to remove). */
public class GroupEditScreen extends Screen {
    private static final int W = 300;
    private static final int H = 230;
    private static final int SLOT = 18;
    private static final int COLS = 15;
    private static final int ROWS = 5;

    private final Screen parent;
    private final ItemGroup target;
    private final Runnable onDone;
    private final List<ItemStack> items = new ArrayList<>();
    private EditBox name;
    private EditBox regex;
    private EditBox tag;
    private List<TagKey<Item>> tagMatches = List.of();
    private int scroll;
    private int dragFrom = -1;
    private boolean dragging;

    public GroupEditScreen(Screen parent, ItemGroup group, Runnable onDone) {
        super(Component.translatable("emi_reorganised.group.edit"));
        this.parent = parent;
        this.target = group;
        this.onDone = onDone;
        group.items.forEach(s -> items.add(s.copy()));
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int gridX() {
        return left() + (W - COLS * SLOT) / 2;
    }

    private int gridY() {
        return top() + 112;
    }

    @Override
    protected void init() {
        int x = left() + 60;
        int y = top() + 20;
        name = addRenderableWidget(new EditBox(font, x, y, W - 70, 16, Component.empty()));
        name.setMaxLength(64);
        name.setValue(target.name);
        regex = addRenderableWidget(new EditBox(font, x, y + 22, W - 120, 16, Component.empty()));
        regex.setMaxLength(256);
        regex.setValue(target.regex != null ? target.regex : "");
        regex.setHint(Component.literal("minecraft:.*_log").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.group.fill"), b -> fillRegex())
                .bounds(left() + W - 56, y + 21, 46, 18).build());
        tag = addRenderableWidget(new EditBox(font, x, y + 44, W - 120, 16, Component.empty()));
        tag.setMaxLength(256);
        tag.setValue(target.tag != null ? target.tag : "");
        tag.setHint(Component.literal("minecraft:logs").withStyle(ChatFormatting.DARK_GRAY));
        tag.setResponder(v -> updateTagMatches());
        addRenderableWidget(Button.builder(Component.translatable("emi_reorganised.group.fill"), b -> fillTag())
                .bounds(left() + W - 56, y + 43, 46, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left() + W / 2 - 104, top() + H - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done())
                .bounds(left() + W / 2 + 4, top() + H - 26, 100, 20).build());
        updateTagMatches();
    }

    private void updateTagMatches() {
        String q = tag.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || !tag.isFocused()) {
            tagMatches = List.of();
            return;
        }
        tagMatches = BuiltInRegistries.ITEM.getTagNames()
                .filter(t -> t.location().toString().contains(q))
                .sorted((a, b) -> a.location().toString().compareTo(b.location().toString()))
                .limit(6).toList();
    }

    private void fillRegex() {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex.getValue().trim());
        } catch (PatternSyntaxException e) {
            return;
        }
        List<ItemStack> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (pattern.matcher(BuiltInRegistries.ITEM.getKey(item).toString()).matches()) {
                out.add(item.getDefaultInstance());
            }
        }
        if (!out.isEmpty()) {
            items.clear();
            items.addAll(out);
            scroll = 0;
        }
    }

    private void fillTag() {
        ResourceLocation id = ResourceLocation.tryParse(tag.getValue().trim().replaceFirst("^#", ""));
        if (id == null) {
            return;
        }
        List<ItemStack> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(TagKey.create(BuiltInRegistries.ITEM.key(), id)).ifPresent(set -> {
            for (Holder<Item> holder : set) {
                out.add(holder.value().getDefaultInstance());
            }
        });
        if (!out.isEmpty()) {
            items.clear();
            items.addAll(out);
            scroll = 0;
        }
    }

    private void done() {
        if (items.isEmpty()) {
            if (!regex.getValue().isBlank()) {
                fillRegex();
            } else if (!tag.getValue().isBlank()) {
                fillTag();
            }
        }
        if (items.isEmpty()) {
            return;
        }
        String n = name.getValue().trim();
        if (!n.isEmpty()) {
            target.name = n;
        }
        target.regex = regex.getValue().isBlank() ? null : regex.getValue().trim();
        target.tag = tag.getValue().isBlank() ? null : tag.getValue().trim().replaceFirst("^#", "");
        target.items.clear();
        target.items.addAll(items);
        minecraft.setScreen(parent);
        onDone.run();
    }

    private int slotAt(double mx, double my) {
        if (mx < gridX() || my < gridY() || mx >= gridX() + COLS * SLOT || my >= gridY() + ROWS * SLOT) {
            return -1;
        }
        int i = (scroll + (int) ((my - gridY()) / SLOT)) * COLS + (int) ((mx - gridX()) / SLOT);
        return i < items.size() ? i : -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int l = left();
        int t = top();
        graphics.drawCenteredString(font, title, width / 2, t + 6, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("emi_reorganised.group.name"), l + 10, t + 24, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("emi_reorganised.group.regex"), l + 10, t + 46, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("emi_reorganised.group.tag"), l + 10, t + 68, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("emi_reorganised.group.items_hint", items.size())
                .withStyle(ChatFormatting.GRAY), l + 10, gridY() - 12, 0xFFFFFF);
        int gx = gridX();
        int gy = gridY();
        int maxScroll = Math.max(0, (items.size() + COLS - 1) / COLS - ROWS);
        scroll = Math.min(scroll, maxScroll);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = gx + c * SLOT;
                int y = gy + r * SLOT;
                graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF373737);
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF8B8B8B);
                int i = (scroll + r) * COLS + c;
                if (i < items.size()) {
                    if (dragging && i == dragFrom) {
                        graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x804C8CFF);
                    }
                    graphics.renderItem(items.get(i), x + 1, y + 1);
                }
            }
        }
        if (dragging && dragFrom >= 0) {
            int over = slotAt(mouseX, mouseY);
            if (over >= 0) {
                int r = over / COLS - scroll;
                int c = over % COLS;
                graphics.fill(gx + c * SLOT - 1, gy + r * SLOT, gx + c * SLOT + 1, gy + r * SLOT + SLOT, 0xFFFFFFFF);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.renderItem(items.get(dragFrom), mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        }
        if (!tagMatches.isEmpty()) {
            int x = tag.getX();
            int y = tag.getY() + 18;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.fill(x, y, x + tag.getWidth(), y + tagMatches.size() * 11 + 2, 0xF0000000);
            for (int i = 0; i < tagMatches.size(); i++) {
                int ry = y + 1 + i * 11;
                boolean hover = mouseX >= x && mouseX < x + tag.getWidth() && mouseY >= ry && mouseY < ry + 11;
                graphics.drawString(font, font.plainSubstrByWidth(tagMatches.get(i).location().toString(), tag.getWidth() - 4),
                        x + 2, ry + 1, hover ? 0xFFFF55 : 0xDDDDDD);
            }
            graphics.pose().popPose();
            return;
        }
        int hovered = slotAt(mouseX, mouseY);
        if (hovered >= 0 && !dragging) {
            graphics.renderTooltip(font, items.get(hovered), mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left(), top(), left() + W, top() + H, 0xF0202020);
        graphics.renderOutline(left(), top(), W, H, 0xFF808080);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!tagMatches.isEmpty()) {
            int x = tag.getX();
            int y = tag.getY() + 19;
            if (mx >= x && mx < x + tag.getWidth() && my >= y && my < y + tagMatches.size() * 11) {
                tag.setValue(tagMatches.get((int) ((my - y) / 11)).location().toString());
                tagMatches = List.of();
                return true;
            }
        }
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            if (button == 1) {
                items.remove(slot);
            } else if (button == 0) {
                dragFrom = slot;
            }
            return true;
        }
        boolean handled = super.mouseClicked(mx, my, button);
        updateTagMatches();
        return handled;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragFrom >= 0) {
            dragging = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && dragFrom >= 0) {
            int to = slotAt(mx, my);
            if (to < 0 && mx >= gridX() && my >= gridY() && mx < gridX() + COLS * SLOT && my < gridY() + ROWS * SLOT) {
                to = items.size() - 1;
            }
            if (to >= 0 && to != dragFrom) {
                items.add(to, items.remove(dragFrom));
            }
        }
        dragFrom = -1;
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean handled = super.keyPressed(key, scan, mods);
        updateTagMatches();
        return handled;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    ItemGroup target() {
        return target;
    }
}
