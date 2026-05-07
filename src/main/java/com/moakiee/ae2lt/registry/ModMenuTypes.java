package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.menu.OverloadedPowerSupplyMenu;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AE2LightningTech.MODID);

    public static final RegistryObject<MenuType<OverloadedPatternProviderMenu>>
            OVERLOADED_PATTERN_PROVIDER = MENU_TYPES.register(
                    "overloaded_pattern_provider",
                    () -> OverloadedPatternProviderMenu.TYPE);

    public static final RegistryObject<MenuType<OverloadPatternEncoderMenu>>
            OVERLOAD_PATTERN_ENCODER = MENU_TYPES.register(
                    "overload_pattern_encoder",
                    () -> OverloadPatternEncoderMenu.TYPE);

    public static final RegistryObject<MenuType<OverloadedInterfaceMenu>>
            OVERLOADED_INTERFACE = MENU_TYPES.register(
                    "overloaded_interface",
                    () -> OverloadedInterfaceMenu.TYPE);

    public static final RegistryObject<MenuType<OverloadedPowerSupplyMenu>>
            OVERLOADED_POWER_SUPPLY = ModBlocks.hasOverloadedPowerSupply()
                    ? MENU_TYPES.register(
                            "overloaded_power_supply",
                            () -> OverloadedPowerSupplyMenu.TYPE)
                    : null;

    public static final RegistryObject<MenuType<LightningSimulationChamberMenu>>
            LIGHTNING_SIMULATION_CHAMBER = MENU_TYPES.register(
                    "lightning_simulation_room",
                    () -> LightningSimulationChamberMenu.TYPE);

    public static final RegistryObject<MenuType<LightningAssemblyChamberMenu>>
            LIGHTNING_ASSEMBLY_CHAMBER = MENU_TYPES.register(
                    "lightning_assembly_chamber",
                    () -> LightningAssemblyChamberMenu.TYPE);

    public static final RegistryObject<MenuType<LightningCollectorMenu>>
            LIGHTNING_COLLECTOR = MENU_TYPES.register(
                    "lightning_collector",
                    () -> LightningCollectorMenu.TYPE);

    public static final RegistryObject<MenuType<OverloadProcessingFactoryMenu>>
            OVERLOAD_PROCESSING_FACTORY = MENU_TYPES.register(
                    "overload_processing_factory",
                    () -> OverloadProcessingFactoryMenu.TYPE);

    public static final RegistryObject<MenuType<TeslaCoilMenu>>
            TESLA_COIL = MENU_TYPES.register(
                    "tesla_coil",
                    () -> TeslaCoilMenu.TYPE);

    public static final RegistryObject<MenuType<AtmosphericIonizerMenu>>
            ATMOSPHERIC_IONIZER = MENU_TYPES.register(
                    "atmospheric_ionizer",
                    () -> AtmosphericIonizerMenu.TYPE);

    public static final RegistryObject<MenuType<FrequencyMenu>>
            FREQUENCY_MENU = MENU_TYPES.register(
                    "frequency_menu",
                    () -> FrequencyMenu.TYPE);

    public static final RegistryObject<MenuType<CrystalCatalyzerMenu>>
            CRYSTAL_CATALYZER = MENU_TYPES.register(
                    "crystal_catalyzer",
                    () -> CrystalCatalyzerMenu.TYPE);

    private ModMenuTypes() {
    }
}

