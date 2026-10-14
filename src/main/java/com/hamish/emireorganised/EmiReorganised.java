package com.hamish.emireorganised;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = EmiReorganised.MOD_ID, dist = Dist.CLIENT)
public final class EmiReorganised {
    public static final String MOD_ID = "emi_reorganised";
    public static final Logger LOGGER = LoggerFactory.getLogger("EMI Reorganised");

    public EmiReorganised(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modBus.addListener(com.hamish.emireorganised.editor.EditorKeys::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.hamish.emireorganised.editor.EditorKeys::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) -> {
            if (com.hamish.emireorganised.editor.EditorKeys.handleScreenKey(event.getKeyCode(), event.getScanCode())) {
                event.setCanceled(true);
            }
        });
    }
}
