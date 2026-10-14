package com.hamish.emireorganised.editor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** The key that opens the Tab Editor (K by default, also works from inventory screens). */
public final class EditorKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.emi_reorganised.open_editor", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.emi_reorganised");

    private EditorKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.level != null && mc.screen == null) {
                mc.setScreen(new TabEditorScreen(null));
            }
        }
    }

    /** Called from screen key handling so the key also works while an inventory is open. */
    public static boolean handleScreenKey(int key, int scan) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && OPEN.matches(key, scan) && !(mc.screen instanceof TabEditorScreen)
                && !(mc.screen != null && mc.screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
            mc.setScreen(new TabEditorScreen(mc.screen));
            return true;
        }
        return false;
    }
}
