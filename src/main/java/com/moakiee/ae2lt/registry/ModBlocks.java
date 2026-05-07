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
import com.moakiee.ae2lt.block.OverloadedPowerSupplyBlock;
import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.block.AdvancedWirelessOverloadedControllerBlock;
import com.moakiee.ae2lt.block.WirelessOverloadedControllerBlock;
import com.moakiee.ae2lt.block.WirelessReceiverBlock;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredBlock;
import net.minecraftforge.registries.DeferredRegister;

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

    public static final DeferredBlock<Block> OVERLOAD_CRYSTAL_BLOCK =
            registerBlock("overload_crystal_block", () -> new Block(OVERLOAD_CRYSTAL_BLOCK_PROPERTIES));

    public static final DeferredBlock<Block> SILICON_BLOCK =
            registerBlock("silicon_block", () -> new Block(SILICON_BLOCK_PROPERTIES));

    public static final DeferredBlock<Block> OVERLOAD_MACHINE_FRAME =
            registerBlock("overload_machine_frame", () -> new Block(OVERLOAD_MACHINE_FRAME_PROPERTIES));

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

    public static final DeferredBlock<OverloadedPatternProviderBlock> OVERLOADED_PATTERN_PROVIDER =
            registerBlock("overloaded_pattern_provider", OverloadedPatternProviderBlock::new);

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

    private ModBlocks() {
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockFactory) {
        return registerBlock(name, blockFactory, () -> true);
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

