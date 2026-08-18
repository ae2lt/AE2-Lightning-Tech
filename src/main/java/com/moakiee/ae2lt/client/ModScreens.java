package com.moakiee.ae2lt.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import appeng.client.gui.style.StyleManager;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.menu.OverloadedPowerSupplyMenu;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Client event: binds MenuType to Screen.
 */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModScreens {

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(OverloadedPatternProviderMenu.TYPE, ModScreens::createOverloadedPatternProviderScreen);
            MenuScreens.register(OverloadPatternEncoderMenu.TYPE, OverloadPatternEncoderScreen::new);
            MenuScreens.register(OverloadedInterfaceMenu.TYPE, ModScreens::createOverloadedInterfaceScreen);
            if (ModBlocks.hasOverloadedPowerSupply()) {
                MenuScreens.register(OverloadedPowerSupplyMenu.TYPE, ModScreens::createOverloadedPowerSupplyScreen);
            }
            MenuScreens.register(LightningSimulationChamberMenu.TYPE, ModScreens::createLightningSimulationChamberScreen);
            MenuScreens.register(LightningAssemblyChamberMenu.TYPE, ModScreens::createLightningAssemblyChamberScreen);
            MenuScreens.register(LightningCollectorMenu.TYPE, ModScreens::createLightningCollectorScreen);
            MenuScreens.register(OverloadProcessingFactoryMenu.TYPE, ModScreens::createOverloadProcessingFactoryScreen);
            MenuScreens.register(TeslaCoilMenu.TYPE, ModScreens::createTeslaCoilScreen);
            MenuScreens.register(AtmosphericIonizerMenu.TYPE, ModScreens::createAtmosphericIonizerScreen);
            MenuScreens.register(FrequencyMenu.TYPE, FrequencyScreen::new);
            MenuScreens.register(CrystalCatalyzerMenu.TYPE, ModScreens::createCrystalCatalyzerScreen);
        });
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

