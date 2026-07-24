package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.ExtendedOverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuPatternStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSeedStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2LightningTech.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningCollectorBlockEntity>>
            LIGHTNING_COLLECTOR = BLOCK_ENTITY_TYPES.register(
                    "lightning_collector",
                    () -> BlockEntityType.Builder.of(
                            LightningCollectorBlockEntity::new,
                            ModBlocks.LIGHTNING_COLLECTOR.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FirmamentConversionCoreBlockEntity>>
            FIRMAMENT_CONVERSION_CORE = BLOCK_ENTITY_TYPES.register(
                    "firmament_conversion_core",
                    () -> BlockEntityType.Builder.of(
                            FirmamentConversionCoreBlockEntity::new,
                            ModBlocks.FIRMAMENT_CONVERSION_CORE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedControllerBlockEntity>>
            OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_controller",
                    () -> BlockEntityType.Builder.of(
                            OverloadedControllerBlockEntity::new,
                            ModBlocks.OVERLOADED_CONTROLLER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningSimulationChamberBlockEntity>>
            LIGHTNING_SIMULATION_CHAMBER = BLOCK_ENTITY_TYPES.register(
                    "lightning_simulation_room",
                    () -> BlockEntityType.Builder.of(
                            LightningSimulationChamberBlockEntity::new,
                            ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningAssemblyChamberBlockEntity>>
            LIGHTNING_ASSEMBLY_CHAMBER = BLOCK_ENTITY_TYPES.register(
                    "lightning_assembly_chamber",
                    () -> BlockEntityType.Builder.of(
                            LightningAssemblyChamberBlockEntity::new,
                            ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadProcessingFactoryBlockEntity>>
            OVERLOAD_PROCESSING_FACTORY = BLOCK_ENTITY_TYPES.register(
                    "overload_processing_factory",
                    () -> BlockEntityType.Builder.of(
                            OverloadProcessingFactoryBlockEntity::new,
                            ModBlocks.OVERLOAD_PROCESSING_FACTORY.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeslaCoilBlockEntity>>
            TESLA_COIL = BLOCK_ENTITY_TYPES.register(
                    "tesla_coil",
                    () -> BlockEntityType.Builder.of(
                            TeslaCoilBlockEntity::new,
                            ModBlocks.TESLA_COIL.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtmosphericIonizerBlockEntity>>
            ATMOSPHERIC_IONIZER = BLOCK_ENTITY_TYPES.register(
                    "atmospheric_ionizer",
                    () -> BlockEntityType.Builder.of(
                            AtmosphericIonizerBlockEntity::new,
                            ModBlocks.ATMOSPHERIC_IONIZER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalCatalyzerBlockEntity>>
            CRYSTAL_CATALYZER = BLOCK_ENTITY_TYPES.register(
                    "crystal_catalyzer",
                    () -> BlockEntityType.Builder.of(
                            CrystalCatalyzerBlockEntity::new,
                            ModBlocks.CRYSTAL_CATALYZER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedPatternProviderBlockEntity>>
            OVERLOADED_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "overloaded_pattern_provider",
                    () -> BlockEntityType.Builder.of(
                            OverloadedPatternProviderBlockEntity::new,
                            ModBlocks.OVERLOADED_PATTERN_PROVIDER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtendedOverloadedPatternProviderBlockEntity>>
            EXTENDED_OVERLOADED_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "extended_overloaded_pattern_provider",
                    () -> BlockEntityType.Builder.of(
                            ExtendedOverloadedPatternProviderBlockEntity::new,
                            ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedInterfaceBlockEntity>>
            OVERLOADED_INTERFACE = BLOCK_ENTITY_TYPES.register(
                    "overloaded_interface",
                    () -> BlockEntityType.Builder.of(
                            OverloadedInterfaceBlockEntity::new,
                            ModBlocks.OVERLOADED_INTERFACE.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadedPowerSupplyBlockEntity>>
            OVERLOADED_POWER_SUPPLY = ModBlocks.hasOverloadedPowerSupply()
                    ? BLOCK_ENTITY_TYPES.register(
                            "overloaded_power_supply",
                            () -> BlockEntityType.Builder.of(
                                    OverloadedPowerSupplyBlockEntity::new,
                                    ModBlocks.OVERLOADED_POWER_SUPPLY.get())
                                    .build(null))
                    : null;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessReceiverBlockEntity>>
            WIRELESS_RECEIVER = BLOCK_ENTITY_TYPES.register(
                    "wireless_receiver",
                    () -> BlockEntityType.Builder.of(
                            WirelessReceiverBlockEntity::new,
                            ModBlocks.WIRELESS_RECEIVER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessOverloadedControllerBlockEntity>>
            WIRELESS_OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "wireless_overloaded_controller",
                    () -> BlockEntityType.Builder.of(
                            WirelessOverloadedControllerBlockEntity::new,
                            ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdvancedWirelessOverloadedControllerBlockEntity>>
            ADVANCED_WIRELESS_OVERLOADED_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "advanced_wireless_overloaded_controller",
                    () -> BlockEntityType.Builder.of(
                            AdvancedWirelessOverloadedControllerBlockEntity::new,
                            ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PigmeeMentalmathUnitBlockEntity>>
            PIGMEE_MENTALMATH_UNIT = BLOCK_ENTITY_TYPES.register(
                    "pigmee_mentalmath_unit",
                    () -> BlockEntityType.Builder.of(
                            PigmeeMentalmathUnitBlockEntity::new,
                            ModBlocks.PIGMEE_MENTALMATH_UNIT.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PigmeePatternProviderBlockEntity>>
            PIGMEE_PATTERN_PROVIDER = BLOCK_ENTITY_TYPES.register(
                    "pigmee_pattern_provider",
                    () -> BlockEntityType.Builder.of(
                            PigmeePatternProviderBlockEntity::new,
                            ModBlocks.PIGMEE_PATTERN_PROVIDER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PigmeeMolecularAssemblerBlockEntity>>
            PIGMEE_MOLECULAR_ASSEMBLER = BLOCK_ENTITY_TYPES.register(
                    "pigmee_molecular_assembler",
                    () -> BlockEntityType.Builder.of(
                            PigmeeMolecularAssemblerBlockEntity::new,
                            ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TianshuSupercomputerControllerBlockEntity>>
            TIANSHU_SUPERCOMPUTER_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "tianshu_supercomputer_controller",
                    () -> BlockEntityType.Builder.of(
                            TianshuSupercomputerControllerBlockEntity::new,
                            ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TianshuSupercomputerPortBlockEntity>>
            TIANSHU_SUPERCOMPUTER_PORT = BLOCK_ENTITY_TYPES.register(
                    "tianshu_supercomputer_port",
                    () -> BlockEntityType.Builder.of(
                            TianshuSupercomputerPortBlockEntity::new,
                            ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TianshuSeedStorageBlockEntity>>
            TIANSHU_SEED_STORAGE = BLOCK_ENTITY_TYPES.register(
                    "closed_loop_seed_storage",
                    () -> BlockEntityType.Builder.of(
                            TianshuSeedStorageBlockEntity::new,
                            ModBlocks.CLOSED_LOOP_SEED_STORAGE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TianshuPatternStorageBlockEntity>>
            TIANSHU_PATTERN_STORAGE = BLOCK_ENTITY_TYPES.register(
                    "closed_loop_pattern_storage",
                    () -> BlockEntityType.Builder.of(
                            TianshuPatternStorageBlockEntity::new,
                            ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatrixControllerBlockEntity>>
            MATRIX_CONTROLLER = BLOCK_ENTITY_TYPES.register(
                    "matter_warping_matrix_controller",
                    () -> BlockEntityType.Builder.of(
                            MatrixControllerBlockEntity::new,
                            ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatrixPortBlockEntity>>
            MATRIX_PORT = BLOCK_ENTITY_TYPES.register(
                    "matter_warping_matrix_port",
                    () -> BlockEntityType.Builder.of(
                            MatrixPortBlockEntity::new,
                            ModBlocks.MATTER_WARPING_MATRIX_PORT.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatrixPatternStorageBlockEntity>>
            MATRIX_PATTERN_STORAGE = BLOCK_ENTITY_TYPES.register(
                    "matter_warping_matrix_pattern_storage",
                    () -> BlockEntityType.Builder.of(
                            MatrixPatternStorageBlockEntity::new,
                            ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1.get(),
                            ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T2.get())
                            .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GhostOutputBlockEntity>>
            GHOST_OUTPUT = BLOCK_ENTITY_TYPES.register(
                    "ghost_output",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new GhostOutputBlockEntity(pos),
                            Blocks.AIR)
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FumoBlockEntity>>
            FUMO = BLOCK_ENTITY_TYPES.register(
                    "fumo",
                    () -> BlockEntityType.Builder.of(
                            FumoBlockEntity::new,
                            ModFumos.MOAKIEE_FUMO.get(),
                            ModFumos.CYSTRYSU_FUMO.get(),
                            ModFumos.PIGMEE_FUMO.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadDeviceWorkbenchBlockEntity>>
            OVERLOAD_DEVICE_WORKBENCH = BLOCK_ENTITY_TYPES.register(
                    "overload_device_workbench",
                    () -> BlockEntityType.Builder.of(
                            OverloadDeviceWorkbenchBlockEntity::new,
                            ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get())
                            .build(null));

    private ModBlockEntities() {
    }
}
