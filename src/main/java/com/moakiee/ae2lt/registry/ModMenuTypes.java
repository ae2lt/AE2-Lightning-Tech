package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.AtmosphericIonizerMenu;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.menu.LightningAssemblyChamberMenu;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;
import com.moakiee.ae2lt.menu.LightningSimulationChamberMenu;
import com.moakiee.ae2lt.menu.MatrixControllerMenu;
import com.moakiee.ae2lt.menu.MatrixPortMenu;
import com.moakiee.ae2lt.menu.OverloadPatternEncoderMenu;
import com.moakiee.ae2lt.menu.OverloadProcessingFactoryMenu;
import com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.menu.OverloadedPowerSupplyMenu;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.menu.TianshuSupercomputerControllerMenu;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AE2LightningTech.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadedPatternProviderMenu>>
            OVERLOADED_PATTERN_PROVIDER = MENU_TYPES.register(
                    "overloaded_pattern_provider",
                    () -> OverloadedPatternProviderMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadPatternEncoderMenu>>
            OVERLOAD_PATTERN_ENCODER = MENU_TYPES.register(
                    "overload_pattern_encoder",
                    () -> OverloadPatternEncoderMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadedInterfaceMenu>>
            OVERLOADED_INTERFACE = MENU_TYPES.register(
                    "overloaded_interface",
                    () -> OverloadedInterfaceMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadedPowerSupplyMenu>>
            OVERLOADED_POWER_SUPPLY = ModBlocks.hasOverloadedPowerSupply()
                    ? MENU_TYPES.register(
                            "overloaded_power_supply",
                            () -> OverloadedPowerSupplyMenu.TYPE)
                    : null;

    public static final DeferredHolder<MenuType<?>, MenuType<LightningSimulationChamberMenu>>
            LIGHTNING_SIMULATION_CHAMBER = MENU_TYPES.register(
                    "lightning_simulation_room",
                    () -> LightningSimulationChamberMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<LightningAssemblyChamberMenu>>
            LIGHTNING_ASSEMBLY_CHAMBER = MENU_TYPES.register(
                    "lightning_assembly_chamber",
                    () -> LightningAssemblyChamberMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<LightningCollectorMenu>>
            LIGHTNING_COLLECTOR = MENU_TYPES.register(
                    "lightning_collector",
                    () -> LightningCollectorMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadProcessingFactoryMenu>>
            OVERLOAD_PROCESSING_FACTORY = MENU_TYPES.register(
                    "overload_processing_factory",
                    () -> OverloadProcessingFactoryMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<TeslaCoilMenu>>
            TESLA_COIL = MENU_TYPES.register(
                    "tesla_coil",
                    () -> TeslaCoilMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<AtmosphericIonizerMenu>>
            ATMOSPHERIC_IONIZER = MENU_TYPES.register(
                    "atmospheric_ionizer",
                    () -> AtmosphericIonizerMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<FrequencyMenu>>
            FREQUENCY_MENU = MENU_TYPES.register(
                    "frequency_menu",
                    () -> FrequencyMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<CrystalCatalyzerMenu>>
            CRYSTAL_CATALYZER = MENU_TYPES.register(
                    "crystal_catalyzer",
                    () -> CrystalCatalyzerMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<DeviceHubMenu>>
            DEVICE_HUB = MENU_TYPES.register(
                    "device_hub",
                    () -> DeviceHubMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<OverloadDeviceWorkbenchMenu>>
            OVERLOAD_DEVICE_WORKBENCH = MENU_TYPES.register(
                    "overload_device_workbench",
                    () -> OverloadDeviceWorkbenchMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<MatrixControllerMenu>>
            MATRIX_CONTROLLER = MENU_TYPES.register(
                    "matter_warping_matrix_controller",
                    () -> MatrixControllerMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<MatrixPortMenu>>
            MATRIX_PORT = MENU_TYPES.register(
                    "matter_warping_matrix_port",
                    () -> MatrixPortMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<TianshuSupercomputerControllerMenu>>
            TIANSHU_SUPERCOMPUTER_CONTROLLER = MENU_TYPES.register(
                    "tianshu_supercomputer_controller",
                    () -> TianshuSupercomputerControllerMenu.TYPE);

    private ModMenuTypes() {
    }
}
