package com.hamish.emireorganised;

import com.hamish.emireorganised.editor.OpenEditorButton;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
public final class EmiReorganisedPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea(OpenEditorButton::exclusion);
    }
}
