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
    }
}
