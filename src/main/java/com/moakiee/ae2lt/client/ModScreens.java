package com.moakiee.ae2lt.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.client.gui.style.StyleManager;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.client.hub.DeviceHubScreen;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.menu.OverloadedPowerSupplyMenu;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuSeedStorageMenu;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * Client event: binds MenuType to Screen.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModScreens {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(OverloadedPatternProviderMenu.TYPE, ModScreens::createOverloadedPatternProviderScreen);
        event.register(OverloadPatternEncoderMenu.TYPE, OverloadPatternEncoderScreen::new);
        event.register(OverloadDeviceWorkbenchMenu.TYPE, OverloadDeviceWorkbenchScreen::new);
        event.register(OverloadedInterfaceMenu.TYPE, ModScreens::createOverloadedInterfaceScreen);
        if (ModBlocks.hasOverloadedPowerSupply()) {
            event.register(OverloadedPowerSupplyMenu.TYPE, ModScreens::createOverloadedPowerSupplyScreen);
        }
        event.register(LightningSimulationChamberMenu.TYPE, ModScreens::createLightningSimulationChamberScreen);
        event.register(LightningAssemblyChamberMenu.TYPE, ModScreens::createLightningAssemblyChamberScreen);
        event.register(LightningCollectorMenu.TYPE, ModScreens::createLightningCollectorScreen);
        event.register(OverloadProcessingFactoryMenu.TYPE, ModScreens::createOverloadProcessingFactoryScreen);
        event.register(TeslaCoilMenu.TYPE, ModScreens::createTeslaCoilScreen);
        event.register(AtmosphericIonizerMenu.TYPE, ModScreens::createAtmosphericIonizerScreen);
        event.register(FrequencyMenu.TYPE, FrequencyScreen::new);
        event.register(CrystalCatalyzerMenu.TYPE, ModScreens::createCrystalCatalyzerScreen);
        event.register(DeviceHubMenu.TYPE, DeviceHubScreen::new);
        event.register(MatrixControllerMenu.TYPE, MatrixControllerScreen::new);
        event.register(TianshuSupercomputerControllerMenu.TYPE, TianshuSupercomputerControllerScreen::new);
        event.register(TianshuPatternEncodingTermMenu.TYPE, ModScreens::createTianshuPatternEncodingTermScreen);
        event.register(TianshuSeedStorageMenu.TYPE, ModScreens::createTianshuSeedStorageScreen);
        if (net.neoforged.fml.ModList.get().isLoaded("ae2wtlib")) {
            com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration.registerTerminalScreen(event);
        }
    }

    private static TianshuPatternEncodingTermScreen<TianshuPatternEncodingTermMenu> createTianshuPatternEncodingTermScreen(
            TianshuPatternEncodingTermMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/terminals/tianshu_pattern_encoding_terminal.json");
        return new TianshuPatternEncodingTermScreen(menu, inv, title, style);
    }

    private static TianshuSeedStorageScreen createTianshuSeedStorageScreen(
            TianshuSeedStorageMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/tianshu_seed_storage.json");
        return new TianshuSeedStorageScreen(menu, inv, title, style);
    }

    private static OverloadedPatternProviderScreen<OverloadedPatternProviderMenu> createOverloadedPatternProviderScreen(
            OverloadedPatternProviderMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overloaded_pattern_provider.json");
        return new OverloadedPatternProviderScreen(menu, inv, title, style);
    }

    private static OverloadedInterfaceScreen createOverloadedInterfaceScreen(
            OverloadedInterfaceMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overloaded_interface.json");
        return new OverloadedInterfaceScreen(menu, inv, title, style);
    }

    private static OverloadedPowerSupplyScreen createOverloadedPowerSupplyScreen(
            OverloadedPowerSupplyMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overloaded_power_supply.json");
        return new OverloadedPowerSupplyScreen(menu, inv, title, style);
    }

    private static LightningSimulationChamberScreen createLightningSimulationChamberScreen(
            LightningSimulationChamberMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/lightning_simulation_room.json");
        return new LightningSimulationChamberScreen(menu, inv, title, style);
    }

    private static LightningAssemblyChamberScreen createLightningAssemblyChamberScreen(
            LightningAssemblyChamberMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/lightning_assembly_chamber.json");
        return new LightningAssemblyChamberScreen(menu, inv, title, style);
    }

    private static LightningCollectorScreen createLightningCollectorScreen(
            LightningCollectorMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/lightning_collector.json");
        return new LightningCollectorScreen(menu, inv, title, style);
    }

    private static OverloadProcessingFactoryScreen createOverloadProcessingFactoryScreen(
            OverloadProcessingFactoryMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/overload_processing_factory.json");
        return new OverloadProcessingFactoryScreen(menu, inv, title, style);
    }

    private static TeslaCoilScreen createTeslaCoilScreen(
            TeslaCoilMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/tesla_coil.json");
        return new TeslaCoilScreen(menu, inv, title, style);
    }

    private static AtmosphericIonizerScreen createAtmosphericIonizerScreen(
            AtmosphericIonizerMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/atmospheric_ionizer.json");
        return new AtmosphericIonizerScreen(menu, inv, title, style);
    }

    private static CrystalCatalyzerScreen createCrystalCatalyzerScreen(
            CrystalCatalyzerMenu menu, Inventory inv, Component title) {
        var style = StyleManager.loadStyleDoc("/screens/crystal_catalyzer.json");
        return new CrystalCatalyzerScreen(menu, inv, title, style);
    }

}
