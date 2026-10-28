package com.bonsaibaggins.emireorganised.compat;

import com.bonsaibaggins.emireorganised.Config;
import com.bonsaibaggins.emireorganised.EmiReorganised;
import com.bonsaibaggins.emireorganised.data.TabStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.ModList;

/**
 * Authoring Mode opens the editor from its own UI (see {@link com.bonsaibaggins.emireorganised.api.EmiReorganisedApi}),
 * so the first time both mods load together our Open Editor button is turned off. The player can turn it back on.
 */
public final class AuthoringMode {
    private static final String MOD_ID = "authoringmode";
    private static final String MARKER = ".authoring_mode";
    private static boolean checked;

    private AuthoringMode() {
    }

    public static void applyOnce() {
        if (checked || !Config.SPEC.isLoaded()) {
            return;
        }
        checked = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        Path marker = TabStore.dir().resolve(MARKER);
        if (Files.exists(marker)) {
            return;
        }
        Config.EDITOR_BUTTON.set(Config.EditorButton.NEVER);
        Config.SPEC.save();
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "Authoring Mode was found, so the Open Editor button was turned off once. Delete this file to do it again.\n");
        } catch (IOException e) {
            EmiReorganised.LOGGER.warn("Couldn't write {}", marker, e);
        }
    }
}
