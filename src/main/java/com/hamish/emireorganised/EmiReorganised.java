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
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mc, parent) -> net.neoforged.fml.ModList.get().isLoaded("yet_another_config_lib_v3")
                        ? com.hamish.emireorganised.config.YaclConfigScreen.create(parent)
                        : new net.neoforged.neoforge.client.gui.ConfigurationScreen(container, parent));
        modBus.addListener(com.hamish.emireorganised.editor.EditorKeys::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.hamish.emireorganised.editor.EditorKeys::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) ->
                com.hamish.emireorganised.config.EmiDefaults.applyOnce());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.hamish.emireorganised.editor.OpenEditorButton::onInit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.hamish.emireorganised.sidebar.IndexScroll::onRenderPre);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.hamish.emireorganised.sidebar.IndexScroll::onMousePressed);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.hamish.emireorganised.sidebar.IndexScroll::onMouseDragged);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.hamish.emireorganised.sidebar.IndexScroll::onMouseReleased);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Opening event) -> {
            // Our sidebar widgets are drawn by EMI; stop the previous screen's ones taking clicks on the new screen.
            com.hamish.emireorganised.sidebar.FavouriteSearch.hide();
            com.hamish.emireorganised.sidebar.IndexScroll.clearBars();
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen && mc.player != null
                    && Config.get(Config.SURVIVAL_INVENTORY_IN_CREATIVE)) {
                event.setNewScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.MouseDragged.Pre event) -> {
            if (com.hamish.emireorganised.sidebar.VerticalTabs.mouseDragged(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonReleased.Pre event) -> {
            if (com.hamish.emireorganised.sidebar.VerticalTabs.mouseReleased(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) -> {
            if (event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_A && net.neoforged.fml.ModList.get().isLoaded("create")
                    && com.hamish.emireorganised.compat.CreateSchematics.tryAddHovered(event.getScreen())) {
                event.setCanceled(true);
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) -> {
            if (com.hamish.emireorganised.editor.EditorKeys.handleScreenKey(event.getKeyCode(), event.getScanCode())) {
                event.setCanceled(true);
            }
        });
    }
}
