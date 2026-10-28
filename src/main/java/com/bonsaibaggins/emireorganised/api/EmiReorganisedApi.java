package com.bonsaibaggins.emireorganised.api;

import com.bonsaibaggins.emireorganised.EmiReorganised;
import com.bonsaibaggins.emireorganised.editor.OpenEditorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Entry points for other mods. Check {@code ModList.get().isLoaded("emi_reorganised")} before touching this class.
 * Client side only; call from the render thread.
 */
public final class EmiReorganisedApi {
    private EmiReorganisedApi() {
    }

    /** False when the player has turned EMI Reorganised off in its config; the editor won't open then. */
    public static boolean isEnabled() {
        return EmiReorganised.active();
    }

    /** Opens the Reorganised editor over the current screen, which it returns to when closed. */
    public static boolean openEditor() {
        return openEditor(Minecraft.getInstance().screen);
    }

    /** Opens the Reorganised editor; closing it returns to {@code parent} (or to the game when null). */
    public static boolean openEditor(@Nullable Screen parent) {
        if (!isEnabled() || Minecraft.getInstance().level == null) {
            return false;
        }
        OpenEditorButton.open(parent);
        return true;
    }
}
