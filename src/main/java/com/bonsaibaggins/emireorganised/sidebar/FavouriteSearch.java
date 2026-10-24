package com.bonsaibaggins.emireorganised.sidebar;

import com.bonsaibaggins.emireorganised.skin.ReorganisedSkin;
import com.bonsaibaggins.emireorganised.Config;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** A search bar drawn in place of the favourites sidebar's header, filtering the favourites shown. */
public final class FavouriteSearch {
    @Nullable private static EditBox box;
    private static boolean shown;
    private static boolean registered;
    private static int version;

    private FavouriteSearch() {
    }

    public static boolean enabled() {
        return Config.get(Config.FAVOURITE_SEARCH);
    }

    public static String query() {
        return box == null || !enabled() ? "" : box.getValue().trim().toLowerCase(Locale.ROOT);
    }

    /** Bumped whenever the query changes. */
    public static int version() {
        return version;
    }

    public static List<? extends EmiIngredient> filter(List<? extends EmiIngredient> stacks) {
        String q = query();
        if (q.isEmpty()) {
            return stacks;
        }
        return stacks.stream().filter(ingredient -> ingredient.getEmiStacks().stream().anyMatch(s ->
                s.getName().getString().toLowerCase(Locale.ROOT).contains(q) || s.getId().toString().contains(q))).toList();
    }

    private static EditBox box() {
        if (box == null) {
            box = new EditBox(Minecraft.getInstance().font, 0, 0, 10, 16, Component.empty());
            box.setHint(Component.translatable("emi_reorganised.fav.search").withColor(0x7E7E7E));
            box.setResponder(v -> {
                version++;
                IndexScroll.reset(dev.emi.emi.config.SidebarType.FAVORITES);
            });
        }
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(FavouriteSearch::onMouse);
            NeoForge.EVENT_BUS.addListener(FavouriteSearch::onKey);
            NeoForge.EVENT_BUS.addListener(FavouriteSearch::onChar);
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing e) -> {
                if (box != null) {
                    box.setFocused(false);
                }
            });
        }
        return box;
    }

    /** Draws the bar above the favourites panel (whose header is turned off). */
    public static void render(GuiGraphics graphics, EmiScreenManager.ScreenSpace space, int y, int mouseX, int mouseY, float delta) {
        EditBox b = box();
        ReorganisedSkin.Layout skin = ReorganisedSkin.layoutAt(space);
        b.setBordered(skin == null);
        if (skin != null) {
            ReorganisedSkin.placeSearch(b, skin, 3);
        } else {
            b.setX(space.tx);
            b.setY(y);
            b.setWidth(Math.max(10, space.tw * 18));
            b.setHeight(18);
        }
        b.render(graphics, mouseX, mouseY, delta);
        shown = true;
    }

    /** A new screen is opening: the bar takes no input there until it's drawn on it. */
    public static void hide() {
        shown = false;
        if (box != null) {
            box.setFocused(false);
        }
    }

    /** Called once per frame before drawing: the bar only takes input while it was drawn. */
    public static void beginFrame() {
        shown = false;
    }

    private static boolean active() {
        return box != null && enabled() && Minecraft.getInstance().screen != null;
    }

    private static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!active()) {
            return;
        }
        boolean inside = shown && box.isMouseOver(event.getMouseX(), event.getMouseY());
        if (inside) {
            box.setFocused(true);
            if (event.getButton() == 1) {
                box.setValue("");
            } else {
                box.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
            }
            event.setCanceled(true);
        } else if (box.isFocused()) {
            box.setFocused(false);
        }
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!active() || !box.isFocused()) {
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE || event.getKeyCode() == GLFW.GLFW_KEY_ENTER) {
            box.setFocused(false);
        } else {
            box.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        }
        event.setCanceled(true);
    }

    private static void onChar(ScreenEvent.CharacterTyped.Pre event) {
        if (active() && box.isFocused()) {
            box.charTyped(event.getCodePoint(), event.getModifiers());
            event.setCanceled(true);
        }
    }
}
