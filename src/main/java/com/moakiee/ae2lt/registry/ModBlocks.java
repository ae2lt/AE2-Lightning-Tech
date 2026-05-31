package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.AtmosphericIonizerBlock;
import com.moakiee.ae2lt.block.BuddingOverloadCrystalBlock;
import com.moakiee.ae2lt.block.CrystalCatalyzerBlock;
import com.moakiee.ae2lt.block.LightningAssemblyChamberBlock;
import com.moakiee.ae2lt.block.LightningCollectorBlock;
import com.moakiee.ae2lt.block.LightningSimulationChamberBlock;
import com.moakiee.ae2lt.block.OverloadProcessingFactoryBlock;
import com.moakiee.ae2lt.block.OverloadTntBlock;
import com.moakiee.ae2lt.block.OverloadCrystalClusterBlock;
import com.moakiee.ae2lt.block.OverloadedControllerBlock;
import com.moakiee.ae2lt.block.OverloadedInterfaceBlock;
import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.block.AdvancedWirelessOverloadedControllerBlock;
import com.moakiee.ae2lt.block.WirelessOverloadedControllerBlock;
import com.moakiee.ae2lt.block.WirelessReceiverBlock;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AE2LightningTech.MODID);

    private static BlockBehaviour.Properties buddingProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(3.0F, 5.0F)
                .sound(SoundType.AMETHYST)
                .randomTicks()
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties clusterProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(1.5F)
                .sound(SoundType.AMETHYST_CLUSTER)
                .forceSolidOn()
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties overloadCrystalBlockProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(3.0F, 5.0F)
                .sound(SoundType.STONE)
                .forceSolidOn()
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties siliconBlockProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL)
                .forceSolidOn()
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties overloadMachineFrameProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL)
                .forceSolidOn()
                .requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<Block> OVERLOAD_CRYSTAL_BLOCK =
            registerBlock("overload_crystal_block", properties -> new Block(properties.apply(overloadCrystalBlockProperties())));

    public static final DeferredBlock<Block> SILICON_BLOCK =
            registerBlock("silicon_block", properties -> new Block(properties.apply(siliconBlockProperties())));

    public static final DeferredBlock<Block> OVERLOAD_MACHINE_FRAME =
            registerBlock("overload_machine_frame", properties -> new Block(properties.apply(overloadMachineFrameProperties())));

    public static final DeferredBlock<OverloadTntBlock> OVERLOAD_TNT =
            registerBlock("overload_tnt", properties ->
                    new OverloadTntBlock(properties.apply(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.TNT))));

    public static final DeferredBlock<LightningCollectorBlock> LIGHTNING_COLLECTOR =
            registerBlock("lightning_collector", properties ->
                    new LightningCollectorBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<LightningSimulationChamberBlock> LIGHTNING_SIMULATION_CHAMBER =
            registerBlock("lightning_simulation_room", properties ->
                    new LightningSimulationChamberBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<LightningAssemblyChamberBlock> LIGHTNING_ASSEMBLY_CHAMBER =
            registerBlock("lightning_assembly_chamber", properties ->
                    new LightningAssemblyChamberBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<OverloadProcessingFactoryBlock> OVERLOAD_PROCESSING_FACTORY =
            registerBlock("overload_processing_factory", properties ->
                    new OverloadProcessingFactoryBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<TeslaCoilBlock> TESLA_COIL =
            registerBlock("tesla_coil", properties ->
                    new TeslaCoilBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<AtmosphericIonizerBlock> ATMOSPHERIC_IONIZER =
            registerBlock("atmospheric_ionizer", properties ->
                    new AtmosphericIonizerBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<CrystalCatalyzerBlock> CRYSTAL_CATALYZER =
            registerBlock("crystal_catalyzer", properties ->
                    new CrystalCatalyzerBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<OverloadedControllerBlock> OVERLOADED_CONTROLLER =
            registerBlock("overloaded_controller", properties ->
                    new OverloadedControllerBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> FLAWLESS_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("flawless_budding_overload_crystal", properties ->
                    new BuddingOverloadCrystalBlock(properties.apply(buddingProperties())));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> FLAWED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("flawed_budding_overload_crystal", properties ->
                    new BuddingOverloadCrystalBlock(properties.apply(buddingProperties())));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> CRACKED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("cracked_budding_overload_crystal", properties ->
                    new BuddingOverloadCrystalBlock(properties.apply(buddingProperties())));

    public static final DeferredBlock<BuddingOverloadCrystalBlock> DAMAGED_BUDDING_OVERLOAD_CRYSTAL =
            registerBlock("damaged_budding_overload_crystal", properties ->
                    new BuddingOverloadCrystalBlock(properties.apply(buddingProperties())));

    public static final DeferredBlock<OverloadCrystalClusterBlock> SMALL_OVERLOAD_CRYSTAL_BUD =
            registerBlock("small_overload_crystal_bud", properties ->
                    new OverloadCrystalClusterBlock(3, 4,
                            properties.apply(clusterProperties()).sound(SoundType.SMALL_AMETHYST_BUD).lightLevel(s -> 1)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> MEDIUM_OVERLOAD_CRYSTAL_BUD =
            registerBlock("medium_overload_crystal_bud", properties ->
                    new OverloadCrystalClusterBlock(4, 3,
                            properties.apply(clusterProperties()).sound(SoundType.MEDIUM_AMETHYST_BUD).lightLevel(s -> 2)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> LARGE_OVERLOAD_CRYSTAL_BUD =
            registerBlock("large_overload_crystal_bud", properties ->
                    new OverloadCrystalClusterBlock(5, 3,
                            properties.apply(clusterProperties()).sound(SoundType.LARGE_AMETHYST_BUD).lightLevel(s -> 4)));

    public static final DeferredBlock<OverloadCrystalClusterBlock> OVERLOAD_CRYSTAL_CLUSTER =
            registerBlock("overload_crystal_cluster", properties ->
                    new OverloadCrystalClusterBlock(7, 3,
                            properties.apply(clusterProperties()).sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 5)));

    public static final DeferredBlock<OverloadedPatternProviderBlock<OverloadedPatternProviderBlockEntity>>
            OVERLOADED_PATTERN_PROVIDER =
            registerBlock("overloaded_pattern_provider", properties ->
                    new OverloadedPatternProviderBlock<OverloadedPatternProviderBlockEntity>(
                            properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<OverloadedInterfaceBlock> OVERLOADED_INTERFACE =
            registerBlock("overloaded_interface", properties ->
                    new OverloadedInterfaceBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<WirelessReceiverBlock> WIRELESS_RECEIVER =
            registerBlock("wireless_receiver", properties ->
                    new WirelessReceiverBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<WirelessOverloadedControllerBlock> WIRELESS_OVERLOADED_CONTROLLER =
            registerBlock("wireless_overloaded_controller", properties ->
                    new WirelessOverloadedControllerBlock(properties.apply(BlockBehaviour.Properties.of())));

    public static final DeferredBlock<AdvancedWirelessOverloadedControllerBlock> ADVANCED_WIRELESS_OVERLOADED_CONTROLLER =
            registerBlock("advanced_wireless_overloaded_controller", properties ->
                    new AdvancedWirelessOverloadedControllerBlock(properties.apply(BlockBehaviour.Properties.of())));

    private ModBlocks() {
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Function<Function<BlockBehaviour.Properties, BlockBehaviour.Properties>, T> blockFactory) {
        return registerBlock(name, blockFactory, () -> true);
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            Function<Function<BlockBehaviour.Properties, BlockBehaviour.Properties>, T> blockFactory,
            Supplier<Boolean> shouldRegisterItem) {
        return registerBlock(name, blockFactory, shouldRegisterItem, shouldRegisterItem);
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            Function<Function<BlockBehaviour.Properties, BlockBehaviour.Properties>, T> blockFactory,
            Supplier<Boolean> shouldRegisterBlock,
            Supplier<Boolean> shouldRegisterItem) {
        if (!shouldRegisterBlock.get()) {
            return null;
        }

        var registered = BLOCKS.register(name, id -> blockFactory.apply(properties -> properties.setId(registeredBlockKey(id))));
        if (shouldRegisterItem.get()) {
            ModItems.ITEMS.registerSimpleBlockItem(registered);
        }
        return registered;
    }

    private static net.minecraft.resources.ResourceKey<Block> registeredBlockKey(net.minecraft.resources.Identifier id) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id);
    }
}
