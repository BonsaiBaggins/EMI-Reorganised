package com.bonsaibaggins.emireorganised.config;

import com.bonsaibaggins.emireorganised.EmiReorganised;
import com.bonsaibaggins.emireorganised.data.TabStore;
import dev.emi.emi.config.ButtonVisibility;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarTheme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Applies our preferred EMI settings once; afterwards EMI's own config screen has the final say. */
public final class EmiDefaults {
    private static final String MARKER = ".emi_defaults";
    private static boolean checked;

    private EmiDefaults() {
    }

    public static void applyOnce() {
        if (checked) {
            return;
        }
        checked = true;
        Path marker = TabStore.dir().resolve(MARKER);
        if (Files.exists(marker)) {
            return;
        }
        try {
            EmiConfig.leftSidebarTheme = SidebarTheme.VANILLA;
            EmiConfig.rightSidebarTheme = SidebarTheme.VANILLA;
            EmiConfig.leftSidebarSize.values.set(0, 9);
            EmiConfig.rightSidebarSize.values.set(0, 9);
            EmiConfig.emiConfigButtonVisibility = ButtonVisibility.HIDDEN;
            EmiConfig.recipeTreeButtonVisibility = ButtonVisibility.HIDDEN;
            EmiConfig.writeConfig();
        } catch (RuntimeException e) {
            EmiReorganised.LOGGER.warn("Couldn't apply EMI defaults", e);
            return;
        }
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "Delete this file to re-apply EMI Reorganised's EMI defaults (vanilla theme, 9 columns, hidden corner buttons).\n");
        } catch (IOException e) {
            EmiReorganised.LOGGER.warn("Couldn't write {}", marker, e);
        }
    }
}
