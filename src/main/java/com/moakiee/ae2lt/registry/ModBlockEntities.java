package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
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
                            collectFumoBlocks())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverloadDeviceWorkbenchBlockEntity>>
            OVERLOAD_DEVICE_WORKBENCH = BLOCK_ENTITY_TYPES.register(
                    "overload_device_workbench",
                    () -> BlockEntityType.Builder.of(
                            OverloadDeviceWorkbenchBlockEntity::new,
                            ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get())
                            .build(null));

    private static Block[] collectFumoBlocks() {
        List<Block> blocks = new ArrayList<>(3);
        if (ModFumos.MOAKIEE_FUMO != null) {
            blocks.add(ModFumos.MOAKIEE_FUMO.get());
        }
        if (ModFumos.CYSTRYSU_FUMO != null) {
            blocks.add(ModFumos.CYSTRYSU_FUMO.get());
        }
        if (ModFumos.PIGMEE_FUMO != null) {
            blocks.add(ModFumos.PIGMEE_FUMO.get());
        }
        return blocks.toArray(new Block[0]);
    }

    private ModBlockEntities() {
    }
}
