package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.AtmosphericIonizerBlock;
import com.moakiee.ae2lt.block.BuddingOverloadCrystalBlock;
import com.moakiee.ae2lt.block.CrystalCatalyzerBlock;
import com.moakiee.ae2lt.block.FirmamentConversionCoreBlock;
import com.moakiee.ae2lt.block.LightningAssemblyChamberBlock;
import com.moakiee.ae2lt.block.LightningCollectorBlock;
import com.moakiee.ae2lt.block.LightningSimulationChamberBlock;
import com.moakiee.ae2lt.block.MatrixCasingBlock;
import com.moakiee.ae2lt.block.MatrixControllerBlock;
import com.moakiee.ae2lt.block.MatrixFormedBlock;
import com.moakiee.ae2lt.block.MatrixGlassBlock;
import com.moakiee.ae2lt.block.MatrixPatternStorageBlock;
import com.moakiee.ae2lt.block.MatrixPortBlock;
import com.moakiee.ae2lt.block.OverloadProcessingFactoryBlock;
import com.moakiee.ae2lt.block.OverloadTntBlock;
import com.moakiee.ae2lt.block.OverloadCrystalClusterBlock;
import com.moakiee.ae2lt.block.OverloadDeviceWorkbenchBlock;
import com.moakiee.ae2lt.block.OverloadedControllerBlock;
import com.moakiee.ae2lt.block.OverloadedInterfaceBlock;
import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.block.OverloadedPowerSupplyBlock;
import com.moakiee.ae2lt.block.PigmeeMentalmathUnitBlock;
import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.block.TianshuSeedStorageBlock;
import com.moakiee.ae2lt.block.TianshuPatternStorageBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerGlassBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerStructureBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputingUnitBlock;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import com.moakiee.ae2lt.block.AdvancedWirelessOverloadedControllerBlock;
import com.moakiee.ae2lt.block.WirelessOverloadedControllerBlock;
import com.moakiee.ae2lt.block.WirelessReceiverBlock;
import com.moakiee.ae2lt.blockentity.ExtendedOverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final String APPFLUX_MODID = "appflux";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AE2LightningTech.MODID);

    private static final BlockBehaviour.Properties BUDDING_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F, 5.0F)
            .sound(SoundType.AMETHYST)
            .randomTicks()
            .requiresCorrectToolForDrops();

    private static final BlockBehaviour.Properties CLUSTER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(1.5F)
            .sound(SoundType.AMETHYST_CLUSTER)
            .forceSolidOn()
            .requiresCorrectToolForDrops();

    private static final BlockBehaviour.Properties OVERLOAD_CRYSTAL_BLOCK_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F, 5.0F)
            .sound(SoundType.STONE)
            .forceSolidOn()
            .requiresCorrectToolForDrops();

    private static final BlockBehaviour.Properties SILICON_BLOCK_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 6.0F)
            .sound(SoundType.METAL)
            .forceSolidOn()
            .requiresCorrectToolForDrops();

    private static final BlockBehaviour.Properties OVERLOAD_MACHINE_FRAME_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 6.0F)
            .sound(SoundType.METAL)
            .forceSolidOn()
            .requiresCorrectToolForDrops();

    private static final BlockBehaviour.Properties FIRMAMENT_CONVERSION_CORE_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(-1.0F, 3600000.0F)
            .sound(SoundType.METAL)
            .forceSolidOn()
            .pushReaction(PushReaction.BLOCK)
            .noLootTable();

    private static final BlockBehaviour.Properties MATRIX_MACHINE_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(5.0F, 6.0F)
            .sound(SoundType.METAL)
            .forceSolidOn()
            .requiresCorrectToolForDrops();

    private static final BlockBehaviour.Properties MATRIX_GLASS_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F, 6.0F)
            .sound(SoundType.GLASS)
            .noOcclusion()
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false)
            .requiresCorrectToolForDrops();

    public static final DeferredBlock<Block> OVERLOAD_CRYSTAL_BLOCK =
            registerBlock("overload_crystal_block", () -> new Block(OVERLOAD_CRYSTAL_BLOCK_PROPERTIES));

    public static final DeferredBlock<Block> SILICON_BLOCK =
            registerBlock("silicon_block", () -> new Block(SILICON_BLOCK_PROPERTIES));

    public static final DeferredBlock<Block> OVERLOAD_MACHINE_FRAME =
            registerBlock("overload_machine_frame", () -> new Block(OVERLOAD_MACHINE_FRAME_PROPERTIES));

    public static final DeferredBlock<FirmamentConversionCoreBlock> FIRMAMENT_CONVERSION_CORE =
            registerBlock(
                    "firmament_conversion_core",
                    () -> new FirmamentConversionCoreBlock(FIRMAMENT_CONVERSION_CORE_PROPERTIES));

    public static final DeferredBlock<OverloadTntBlock> OVERLOAD_TNT =
            registerBlock("overload_tnt", () -> new OverloadTntBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.TNT)));

    public static final DeferredBlock<LightningCollectorBlock> LIGHTNING_COLLECTOR =
            registerBlock("lightning_collector", LightningCollectorBlock::new);

    public static final DeferredBlock<LightningSimulationChamberBlock> LIGHTNING_SIMULATION_CHAMBER =
            registerBlock("lightning_simulation_room", LightningSimulationChamberBlock::new);

    public static final DeferredBlock<LightningAssemblyChamberBlock> LIGHTNING_ASSEMBLY_CHAMBER =
            registerBlock("lightning_assembly_chamber", LightningAssemblyChamberBlock::new);

    public static final DeferredBlock<OverloadProcessingFactoryBlock> OVERLOAD_PROCESSING_FACTORY =
            registerBlock("overload_processing_factory", OverloadProcessingFactoryBlock::new);

    public static final DeferredBlock<TeslaCoilBlock> TESLA_COIL =
            registerBlock("tesla_coil", TeslaCoilBlock::new);

    public static final DeferredBlock<AtmosphericIonizerBlock> ATMOSPHERIC_IONIZER =
            registerBlock("atmospheric_ionizer", AtmosphericIonizerBlock::new);

    public static final DeferredBlock<CrystalCatalyzerBlock> CRYSTAL_CATALYZER =
            registerBlock("crystal_catalyzer", CrystalCatalyzerBlock::new);

    public static final DeferredBlock<OverloadedControllerBlock> OVERLOADED_CONTROLLER =
            registerBlock("overloaded_controller", OverloadedControllerBlock::new);

    public static final DeferredBlock<BuddingOverloadCrystalBlock> FLAWLESS_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("flawless_budding_overload_crystal", () ->
                    new BuddingOverloadCrystalBlock(BUDDING_PROPERTIES));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> FLAWED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("flawed_budding_overload_crystal", () ->
                    new BuddingOverloadCrystalBlock(BUDDING_PROPERTIES));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> CRACKED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("cracked_budding_overload_crystal", () ->
                    new BuddingOverloadCrystalBlock(BUDDING_PROPERTIES));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> DAMAGED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("damaged_budding_overload_crystal", () ->
                    new BuddingOverloadCrystalBlock(BUDDING_PROPERTIES));

    public static final DeferredBlock<OverloadCrystalClusterBlock> SMALL_OVERLOAD_CRYSTAL_BUD =
            registerBlock("small_overload_crystal_bud", () ->
                    new OverloadCrystalClusterBlock(3, 4, CLUSTER_PROPERTIES.sound(SoundType.SMALL_AMETHYST_BUD).lightLevel(s -> 1)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> MEDIUM_OVERLOAD_CRYSTAL_BUD =
            registerBlock("medium_overload_crystal_bud", () ->
                    new OverloadCrystalClusterBlock(4, 3, CLUSTER_PROPERTIES.sound(SoundType.MEDIUM_AMETHYST_BUD).lightLevel(s -> 2)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> LARGE_OVERLOAD_CRYSTAL_BUD =
            registerBlock("large_overload_crystal_bud", () ->
                    new OverloadCrystalClusterBlock(5, 3, CLUSTER_PROPERTIES.sound(SoundType.LARGE_AMETHYST_BUD).lightLevel(s -> 4)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> OVERLOAD_CRYSTAL_CLUSTER =
            registerBlock("overload_crystal_cluster", () ->
                    new OverloadCrystalClusterBlock(7, 3, CLUSTER_PROPERTIES.sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 5)));

    public static final DeferredBlock<OverloadedPatternProviderBlock<OverloadedPatternProviderBlockEntity>>
            OVERLOADED_PATTERN_PROVIDER =
            registerBlock("overloaded_pattern_provider", OverloadedPatternProviderBlock::new);

    public static final DeferredBlock<OverloadedPatternProviderBlock<ExtendedOverloadedPatternProviderBlockEntity>>
            EXTENDED_OVERLOADED_PATTERN_PROVIDER =
            registerBlock("extended_overloaded_pattern_provider", OverloadedPatternProviderBlock::new);

    public static final DeferredBlock<OverloadedInterfaceBlock> OVERLOADED_INTERFACE =
            registerBlock("overloaded_interface", OverloadedInterfaceBlock::new);

    public static final DeferredBlock<OverloadedPowerSupplyBlock> OVERLOADED_POWER_SUPPLY =
            registerBlock(
                    "overloaded_power_supply",
                    OverloadedPowerSupplyBlock::new,
                    ModBlocks::isAppFluxLoaded);

    public static final DeferredBlock<WirelessReceiverBlock> WIRELESS_RECEIVER =
            registerBlock("wireless_receiver", WirelessReceiverBlock::new);

    public static final DeferredBlock<WirelessOverloadedControllerBlock> WIRELESS_OVERLOADED_CONTROLLER =
            registerBlock("wireless_overloaded_controller", WirelessOverloadedControllerBlock::new);

    public static final DeferredBlock<AdvancedWirelessOverloadedControllerBlock> ADVANCED_WIRELESS_OVERLOADED_CONTROLLER =
            registerBlock("advanced_wireless_overloaded_controller", AdvancedWirelessOverloadedControllerBlock::new);

    public static final DeferredBlock<OverloadDeviceWorkbenchBlock> OVERLOAD_DEVICE_WORKBENCH =
            registerBlock("overload_device_workbench", OverloadDeviceWorkbenchBlock::new);

    public static final DeferredBlock<PigmeeMentalmathUnitBlock> PIGMEE_MENTALMATH_UNIT =
            registerBlock("pigmee_mentalmath_unit", PigmeeMentalmathUnitBlock::new);

    public static final DeferredBlock<TianshuSupercomputerStructureBlock> TIANSHU_SUPERCOMPUTER_CASING =
            registerBlock("tianshu_supercomputer_casing", () -> new TianshuSupercomputerStructureBlock(MATRIX_MACHINE_PROPERTIES));

    public static final DeferredBlock<TianshuSupercomputerStructureBlock> PHASE_CHANGE_COOLING_UNIT =
            registerBlock("phase_change_cooling_unit", () -> new TianshuSupercomputerStructureBlock(MATRIX_MACHINE_PROPERTIES));

    public static final DeferredBlock<TianshuSupercomputerGlassBlock> TIANSHU_SUPERCOMPUTER_GLASS =
            registerBlock("tianshu_supercomputer_glass", () -> new TianshuSupercomputerGlassBlock(MATRIX_GLASS_PROPERTIES));

    public static final DeferredBlock<TianshuSupercomputerControllerBlock> TIANSHU_SUPERCOMPUTER_CONTROLLER =
            registerControllerBlock("tianshu_supercomputer_controller",
                    () -> new TianshuSupercomputerControllerBlock(MATRIX_MACHINE_PROPERTIES));

    public static final DeferredBlock<TianshuSupercomputerPortBlock> TIANSHU_SUPERCOMPUTER_PORT =
            registerBlock("tianshu_supercomputer_port", () -> new TianshuSupercomputerPortBlock(MATRIX_MACHINE_PROPERTIES));

    public static final DeferredBlock<TianshuSupercomputingUnitBlock> BASELINE_SUPERCOMPUTING_UNIT = registerBlock(
            "baseline_supercomputing_unit", () -> new TianshuSupercomputingUnitBlock(
                    MATRIX_MACHINE_PROPERTIES, TianshuMultiblockComponent.MAIN_BASELINE));
    public static final DeferredBlock<TianshuSupercomputingUnitBlock> QUANTUM_SUPERCOMPUTING_UNIT = registerBlock(
            "quantum_supercomputing_unit", () -> new TianshuSupercomputingUnitBlock(
                    MATRIX_MACHINE_PROPERTIES, TianshuMultiblockComponent.MAIN_QUANTUM));
    public static final DeferredBlock<TianshuSupercomputingUnitBlock> OVERLOAD_SUPERCOMPUTING_UNIT = registerBlock(
            "overload_supercomputing_unit", () -> new TianshuSupercomputingUnitBlock(
                    MATRIX_MACHINE_PROPERTIES, TianshuMultiblockComponent.MAIN_OVERLOAD));
    public static final DeferredBlock<TianshuSupercomputingUnitBlock> MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT = registerBlock(
            "multidimensional_supercomputing_unit", () -> new TianshuSupercomputingUnitBlock(
                    MATRIX_MACHINE_PROPERTIES, TianshuMultiblockComponent.MAIN_MULTIDIMENSIONAL));
    public static final DeferredBlock<TianshuSupercomputingUnitBlock> BLANK_SUPERCOMPUTING_UNIT = registerBlock(
            "blank_supercomputing_unit", () -> new TianshuSupercomputingUnitBlock(
                    MATRIX_MACHINE_PROPERTIES, TianshuMultiblockComponent.BLANK_CORE));
    public static final DeferredBlock<TianshuSupercomputingUnitBlock> STORAGE_SUPERCOMPUTING_UNIT = registerBlock(
            "storage_supercomputing_unit", () -> new TianshuSupercomputingUnitBlock(
                    MATRIX_MACHINE_PROPERTIES, TianshuMultiblockComponent.STORAGE_CORE));
    public static final DeferredBlock<TianshuSupercomputingUnitBlock> PARALLEL_SUPERCOMPUTING_UNIT = registerBlock(
            "parallel_supercomputing_unit", () -> new TianshuSupercomputingUnitBlock(
                    MATRIX_MACHINE_PROPERTIES, TianshuMultiblockComponent.PARALLEL_CORE));
    public static final DeferredBlock<TianshuSupercomputingUnitBlock> AMPLIFIER_SUPERCOMPUTING_UNIT = registerBlock(
            "amplifier_supercomputing_unit", () -> new TianshuSupercomputingUnitBlock(
                    MATRIX_MACHINE_PROPERTIES, TianshuMultiblockComponent.AMPLIFIER_CORE));
    public static final DeferredBlock<TianshuPatternStorageBlock> CLOSED_LOOP_PATTERN_STORAGE = registerBlock(
            "closed_loop_pattern_storage", () -> new TianshuPatternStorageBlock(MATRIX_MACHINE_PROPERTIES));
    public static final DeferredBlock<TianshuSeedStorageBlock> CLOSED_LOOP_SEED_STORAGE = registerBlock(
            "closed_loop_seed_storage", () -> new TianshuSeedStorageBlock(MATRIX_MACHINE_PROPERTIES));

    public static final DeferredBlock<MatrixCasingBlock> MATTER_WARPING_MATRIX_CASING =
            registerBlock("matter_warping_matrix_casing", () -> new MatrixCasingBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.MATRIX_CASING));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_CONSTRAINT_FRAME =
            registerBlock("matter_warping_matrix_constraint_frame", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME));

    public static final DeferredBlock<MatrixGlassBlock> MATTER_WARPING_MATRIX_GLASS =
            registerBlock("matter_warping_matrix_glass", () -> new MatrixGlassBlock(
                    MATRIX_GLASS_PROPERTIES, MatrixMultiblockComponent.MATRIX_GLASS));

    public static final DeferredBlock<MatrixControllerBlock> MATTER_WARPING_MATRIX_CONTROLLER =
            registerControllerBlock("matter_warping_matrix_controller",
                    () -> new MatrixControllerBlock(MATRIX_MACHINE_PROPERTIES));

    public static final DeferredBlock<MatrixPortBlock> MATTER_WARPING_MATRIX_PORT =
            registerBlock("matter_warping_matrix_port", () -> new MatrixPortBlock(MATRIX_MACHINE_PROPERTIES));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_STABLE_MAIN_CORE =
            registerBlock("matter_warping_matrix_stable_main_core", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.STABLE_MAIN_CORE));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_QUANTUM_MAIN_CORE =
            registerBlock("matter_warping_matrix_quantum_main_core", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.QUANTUM_MAIN_CORE));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_OVERLOAD_MAIN_CORE =
            registerBlock("matter_warping_matrix_overload_main_core", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.OVERLOAD_MAIN_CORE));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_CREATIVE_MAIN_CORE =
            registerBlock("matter_warping_matrix_creative_main_core", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.CREATIVE_MAIN_CORE));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_BLANK_SUB_CORE =
            registerBlock("matter_warping_matrix_blank_sub_core", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.BLANK_SUB_CORE));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_THREAD_SUB_CORE_T1 =
            registerBlock("matter_warping_matrix_thread_sub_core_t1", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.THREAD_SUB_CORE_T1));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_THREAD_SUB_CORE_T2 =
            registerBlock("matter_warping_matrix_thread_sub_core_t2", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.THREAD_SUB_CORE_T2));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_MULTIPLIER_SUB_CORE_T1 =
            registerBlock("matter_warping_matrix_multiplier_sub_core_t1", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.MULTIPLIER_SUB_CORE_T1));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_MULTIPLIER_SUB_CORE_T2 =
            registerBlock("matter_warping_matrix_multiplier_sub_core_t2", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.MULTIPLIER_SUB_CORE_T2));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_COOLING_SUB_CORE_T1 =
            registerBlock("matter_warping_matrix_cooling_sub_core_t1", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.COOLING_SUB_CORE_T1));

    public static final DeferredBlock<MatrixFormedBlock> MATTER_WARPING_MATRIX_COOLING_SUB_CORE_T2 =
            registerBlock("matter_warping_matrix_cooling_sub_core_t2", () -> new MatrixFormedBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.COOLING_SUB_CORE_T2));

    public static final DeferredBlock<MatrixPatternStorageBlock> MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1 =
            registerBlock("matter_warping_matrix_pattern_storage_t1", () -> new MatrixPatternStorageBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.PATTERN_STORAGE_T1));

    public static final DeferredBlock<MatrixPatternStorageBlock> MATTER_WARPING_MATRIX_PATTERN_STORAGE_T2 =
            registerBlock("matter_warping_matrix_pattern_storage_t2", () -> new MatrixPatternStorageBlock(
                    MATRIX_MACHINE_PROPERTIES, MatrixMultiblockComponent.PATTERN_STORAGE_T2));

    private ModBlocks() {
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockFactory) {
        return registerBlock(name, blockFactory, () -> true);
    }

    private static <T extends Block> DeferredBlock<T> registerControllerBlock(
            String name, Supplier<T> blockFactory) {
        var registered = BLOCKS.register(name, blockFactory);
        ModItems.ITEMS.register(name,
                () -> new BlockItem(registered.get(), new Item.Properties().stacksTo(1)));
        return registered;
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            Supplier<T> blockFactory,
            Supplier<Boolean> shouldRegisterItem) {
        return registerBlock(name, blockFactory, shouldRegisterItem, shouldRegisterItem);
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            Supplier<T> blockFactory,
            Supplier<Boolean> shouldRegisterBlock,
            Supplier<Boolean> shouldRegisterItem) {
        if (!shouldRegisterBlock.get()) {
            return null;
        }

        var registered = BLOCKS.register(name, blockFactory);
        if (shouldRegisterItem.get()) {
            ModItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        }
        return registered;
    }

    public static boolean hasOverloadedPowerSupply() {
        return OVERLOADED_POWER_SUPPLY != null;
    }

    private static boolean isAppFluxLoaded() {
        return ModList.get().isLoaded(APPFLUX_MODID);
    }
}
