package com.moakiee.ae2lt;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModLootModifiers;
import com.moakiee.ae2lt.registry.ModAEKeyTypes;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModMenuTypes;
import com.moakiee.ae2lt.registry.ModMobEffects;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.registry.ModSounds;
import com.moakiee.ae2lt.registry.ModStructureTypes;
import com.moakiee.ae2lt.registry.LegacyRegistryAliases;
import com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration;
import com.moakiee.ae2lt.integration.mekanism.MekanismArmorIntegration;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.config.AE2LTConfigMigration;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.ExtendedOverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem.CellOutcome;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import appeng.capabilities.Capabilities;
import appeng.api.config.Actionable;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.crafting.PatternDetailsHelper;
import java.util.EnumMap;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.features.GridLinkables;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;

import com.moakiee.ae2lt.api.AE2LTCapabilities;
import com.moakiee.ae2lt.api.lightning.ILightningEnergyHandler;
import com.moakiee.ae2lt.api.frequency.FrequencyApi;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.grid.api.FrequencyApiBridge;
import com.moakiee.ae2lt.me.GridLightningEnergyHandler;
import com.moakiee.ae2lt.me.cell.BulkLightningCellHandler;
import com.moakiee.ae2lt.me.cell.FixedInfiniteCellHandler;

import com.moakiee.ae2lt.logic.MachineAdapterRegistry;
import com.moakiee.ae2lt.logic.craft.BatchPatternEligibility;
import com.moakiee.thunderbolt.CoreConfig;
import com.moakiee.thunderbolt.ae2.batch.BatchExecutor;
import com.moakiee.thunderbolt.ae2.channel.ChannelProviderRegistry;
import com.moakiee.ae2lt.api.patternprovider.WirelessPatternProviderPolicy;
import com.moakiee.thunderbolt.core.craft.CraftingCoreRegistry;
import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorMaterials;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDecoder;
import com.moakiee.ae2lt.recipe.RecipeConflictScanner;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternDecoder;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mod(AE2LightningTech.MODID)
public class AE2LightningTech {
    public static final String MODID = "ae2lt";
    private static final Logger LOG = LogUtils.getLogger();
    private static final CraftingCoreRegistry CRAFTING_CORE_REGISTRY = new CraftingCoreRegistry();

    // Forge 1.20.1: capabilities are attached per object with a ResourceLocation id.
    private static final ResourceLocation BLOCK_ENTITY_CAP_PROVIDER_ID =
            ResourceLocation.fromNamespaceAndPath(MODID, "block_entity_cap_provider");

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static CraftingCoreRegistry craftingCoreRegistry() {
        return CRAFTING_CORE_REGISTRY;
    }

    public static final RegistryObject<CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2lt"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.OVERLOAD_CRYSTAL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 基础方块与水晶生长
                        acceptCreative(output, ModBlocks.SILICON_BLOCK);
                        acceptCreative(output, ModBlocks.OVERLOAD_CRYSTAL_BLOCK);
                        acceptCreative(output, ModBlocks.OVERLOAD_MACHINE_FRAME);
                        acceptCreative(output, ModBlocks.FIRMAMENT_CONVERSION_CORE);
                        acceptCreative(output, ModBlocks.OVERLOAD_TNT);
                        acceptCreative(output, ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD);
                        acceptCreative(output, ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD);
                        acceptCreative(output, ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD);
                        acceptCreative(output, ModBlocks.OVERLOAD_CRYSTAL_CLUSTER);

                        // 闪电收集与加工机器
                        acceptCreative(output, ModBlocks.LIGHTNING_COLLECTOR);
                        acceptCreative(output, ModBlocks.TESLA_COIL);
                        acceptCreative(output, ModBlocks.ATMOSPHERIC_IONIZER);
                        acceptCreative(output, ModBlocks.CRYSTAL_CATALYZER);
                        acceptCreative(output, ModBlocks.LIGHTNING_SIMULATION_CHAMBER);
                        acceptCreative(output, ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER);
                        acceptCreative(output, ModBlocks.OVERLOAD_PROCESSING_FACTORY);

                        // 过载 ME 网络设备
                        acceptCreative(output, ModBlocks.OVERLOADED_CONTROLLER);
                        acceptCreative(output, ModBlocks.OVERLOADED_PATTERN_PROVIDER);
                        acceptCreative(output, ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER);
                        acceptCreative(output, ModBlocks.OVERLOADED_INTERFACE);
                        if (ModBlocks.hasOverloadedPowerSupply()) {
                            acceptCreative(output, ModBlocks.OVERLOADED_POWER_SUPPLY);
                        }
                        acceptCreative(output, ModBlocks.WIRELESS_RECEIVER);
                        acceptCreative(output, ModBlocks.WIRELESS_OVERLOADED_CONTROLLER);
                        acceptCreative(output, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER);

                        // 过载 ME 线缆（默认色、原版染料色顺序）
                        acceptCreative(output, ModItems.OVERLOADED_CABLE);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_WHITE);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_ORANGE);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_MAGENTA);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_LIGHT_BLUE);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_YELLOW);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_LIME);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_PINK);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_GRAY);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_LIGHT_GRAY);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_CYAN);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_PURPLE);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_BLUE);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_BROWN);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_GREEN);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_RED);
                        acceptCreative(output, ModItems.OVERLOADED_CABLE_BLACK);

                        // 闪电存储（外壳、容量元件、存储组件、成品元件）
                        acceptCreative(output, ModItems.LIGHTNING_ITEM_CELL_HOUSING);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_I);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_II);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_III);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_IV);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_V);
                        acceptCreative(output, ModItems.BULK_LIGHTNING_STORAGE_COMPONENT);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_I);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_II);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_III);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_IV);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_V);
                        acceptCreative(output, ModItems.BULK_LIGHTNING_CELL_COMPONENT);
                        acceptCreative(output, ModItems.INFINITE_STORAGE_CELL);
                        output.accept(FixedInfiniteCellItem.createDisplayedResultStack(CellOutcome.HIGH_VOLTAGE));
                        output.accept(FixedInfiniteCellItem.createDisplayedResultStack(CellOutcome.EXTREME_HIGH_VOLTAGE));

                        // 天枢超算阵列
                        acceptCreative(output, ModBlocks.TIANSHU_SUPERCOMPUTER_CASING);
                        acceptCreative(output, ModBlocks.PHASE_CHANGE_COOLING_UNIT);
                        acceptCreative(output, ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS);
                        acceptCreative(output, ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER);
                        acceptCreative(output, ModBlocks.TIANSHU_SUPERCOMPUTER_PORT);
                        acceptCreative(output, ModBlocks.BASELINE_SUPERCOMPUTING_UNIT);
                        acceptCreative(output, ModBlocks.QUANTUM_SUPERCOMPUTING_UNIT);
                        acceptCreative(output, ModBlocks.OVERLOAD_SUPERCOMPUTING_UNIT);
                        acceptCreative(output, ModBlocks.MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT);
                        acceptCreative(output, ModBlocks.TIANSHU_BLANK_UNIT);
                        acceptCreative(output, ModBlocks.STORAGE_SUPERCOMPUTING_UNIT);
                        acceptCreative(output, ModBlocks.PARALLEL_SUPERCOMPUTING_UNIT);
                        acceptCreative(output, ModBlocks.TIANSHU_AMPLIFIER_UNIT);
                        acceptCreative(output, ModBlocks.CLOSED_LOOP_PATTERN_STORAGE);
                        acceptCreative(output, ModBlocks.CLOSED_LOOP_SEED_STORAGE);
                        acceptCreative(output, ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL);
                        ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.ifPresent(output::accept);

                        // 天枢物质扭曲矩阵
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_CASING);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_GLASS);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_PORT);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_STABLE_MAIN_CORE);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_QUANTUM_MAIN_CORE);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_OVERLOAD_MAIN_CORE);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_MULTIDIMENSIONAL_MAIN_CORE);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_THREAD_UNIT_T1);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_THREAD_UNIT_T2);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_THERMAL_CONTROL_UNIT_T1);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_THERMAL_CONTROL_UNIT_T2);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1);
                        acceptCreative(output, ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T2);
                        acceptCreative(output, ModItems.MATTER_WARPING_MATRIX_PATTERN_STORAGE_UPGRADE);

                        // 基础材料与中间产物
                        acceptCreative(output, ModItems.OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModItems.OVERLOAD_CRYSTAL_DUST);
                        acceptCreative(output, ModItems.ELECTRO_CHIME_CRYSTAL);
                        acceptCreative(output, ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL);
                        acceptCreative(output, ModItems.CLEAR_CONDENSATE);
                        acceptCreative(output, ModItems.RAIN_CONDENSATE);
                        acceptCreative(output, ModItems.THUNDERSTORM_CONDENSATE);
                        acceptCreative(output, ModItems.FIRMAMENT_DUST);
                        acceptCreative(output, ModItems.FIRMAMENT_MIXTURE);
                        acceptCreative(output, ModItems.FIRMAMENT_ALLOY_INGOT);
                        acceptCreative(output, ModItems.FIRMAMENT_ESSENCE);
                        acceptCreative(output, ModItems.INACTIVE_FIRMAMENT_SPIRIT_CORE);
                        acceptCreative(output, ModItems.FIRMAMENT_SPIRIT_CORE_OCULUS);
                        acceptCreative(output, ModItems.FIRMAMENT_SPIRIT_CORE_CORE);
                        acceptCreative(output, ModItems.FIRMAMENT_SPIRIT_CORE_CONDUIT);
                        acceptCreative(output, ModItems.FIRMAMENT_SPIRIT_CORE_STRIDE);
                        acceptCreative(output, ModItems.FIRMAMENT_SUPERCONDUCTING_WIRE);
                        acceptCreative(output, ModItems.OVERLOAD_ALLOY_BLANK);
                        acceptCreative(output, ModItems.OVERLOAD_ALLOY);
                        acceptCreative(output, ModItems.OVERLOAD_ALLOY_PLATE);
                        acceptCreative(output, ModItems.OVERLOAD_INSCRIBER_PRESS);
                        acceptCreative(output, ModItems.UNOVERLOADED_CIRCUIT_BOARD);
                        acceptCreative(output, ModItems.OVERLOAD_CIRCUIT_BOARD);
                        acceptCreative(output, ModItems.OVERLOAD_PROCESSOR);
                        acceptCreative(output, ModItems.OVERLOAD_SINGULARITY);
                        acceptCreative(output, ModItems.ULTIMATE_OVERLOAD_CORE);
                        acceptCreative(output, ModItems.BASIC_TOPOLOGICAL_LATTICE);
                        acceptCreative(output, ModItems.DENSE_TOPOLOGICAL_LATTICE);
                        acceptCreative(output, ModItems.ENTANGLED_TOPOLOGICAL_LATTICE);
                        acceptCreative(output, ModItems.HYPERDIMENSIONAL_TOPOLOGICAL_LATTICE);
                        acceptCreative(output, ModItems.LIGHTNING_COLLAPSE_MATRIX);
                        acceptCreative(output, ModItems.FLOATING_MATTER);

                        // 样板、网络工具与升级件
                        acceptCreative(output, ModItems.OVERLOAD_PATTERN);
                        acceptCreative(output, ModItems.CLOSED_LOOP_PATTERN);
                        acceptCreative(output, ModItems.OVERLOAD_PATTERN_ENCODER);
                        acceptCreative(output, ModItems.OVERLOADED_WIRELESS_CONNECT_TOOL);
                        acceptCreative(output, ModItems.OVERLOADED_FREQUENCY_CARD);
                        acceptCreative(output, ModItems.OVERLOADED_PATTERN_PROVIDER_UPGRADE);
                        acceptCreative(output, ModItems.EXTENDED_OVERLOADED_PATTERN_PROVIDER_UPGRADE);
                        acceptCreative(output, ModItems.OVERLOADED_FILTER_COMPONENT);

                        // 苍穹织雷装备、能量模块
                        acceptCreative(output, ModBlocks.OVERLOAD_DEVICE_WORKBENCH);
                        acceptCreative(output, ModItems.OVERLOAD_MODULE_BASE);
                        acceptCreative(output, ModItems.CELESTWEAVE_OCULUS);
                        acceptCreative(output, ModItems.CELESTWEAVE_CORE);
                        acceptCreative(output, ModItems.CELESTWEAVE_CONDUIT);
                        acceptCreative(output, ModItems.CELESTWEAVE_STRIDE);
                        acceptCreative(output, ModItems.ENERGY_MODULE_T1);
                        acceptCreative(output, ModItems.ENERGY_MODULE_T2);
                        acceptCreative(output, ModItems.ENERGY_MODULE_T3);

                        // 苍穹织雷头部模块
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_NIGHT_VISION);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_WATER_BREATHING);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_SATURATION);

                        // 苍穹织雷胸部模块
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_REACH_EXTENSION);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_MATRIX_SHIELD);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_REFLECT);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_UNDYING);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_PURIFICATION);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_RADIATION_PROTECTION);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_LASER_PROTECTION);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_PHASE_LOCK);

                        // 苍穹织雷腿部模块
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_FLIGHT);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_PHASE_FLIGHT);

                        // 苍穹织雷足部模块
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_DASH);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_DIG_AFFINITY);
                        acceptCreative(output, ModItems.CELESTWEAVE_SUBMODULE_MOVEMENT_ASSIST);

                        // 电磁炮与模块
                        acceptCreative(output, ModItems.ELECTROMAGNETIC_RAILGUN);
                        acceptCreative(output, ModItems.RAILGUN_MODULE_CORE);
                        acceptCreative(output, ModItems.RAILGUN_MODULE_COMPUTE);
                        acceptCreative(output, ModItems.RAILGUN_MODULE_ACCELERATION);
                        acceptCreative(output, ModItems.RAILGUN_MODULE_RANGE);
                        acceptCreative(output, ModItems.RAILGUN_MODULE_OVERLOAD_EXECUTION);

                        // Fumo 收藏品（猪咪系列留在独立物品栏）
                        output.accept(ModFumos.MOAKIEE_FUMO_ITEM.get());
                        output.accept(ModFumos.CYSTRYSU_FUMO_ITEM.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> PIGMEE_TAB =
            CREATIVE_MODE_TABS.register("pigmee", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2lt.pigmee"))
                    .withTabsAfter(MAIN_TAB.getKey())
                    .icon(() -> ModFumos.PIGMEE_FUMO_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModFumos.PIGMEE_FUMO_ITEM.get());
                        output.accept(ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get());
                        acceptCreative(output, ModBlocks.PIGMEE_MENTALMATH_UNIT);
                        acceptCreative(output, ModBlocks.PIGMEE_PATTERN_PROVIDER);
                        acceptCreative(output, ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER);
                        acceptCreative(output, ModItems.PIGMEE_CORE);
                        acceptCreative(output, ModItems.PIGMEE_ITEM_CELL_HOUSING);
                        acceptCreative(output, ModItems.PIGMEE_STORAGE_COMPONENT);
                        acceptCreative(output, ModItems.PIGMEE_STORAGE_CELL);
                    })
                    .build());

    public AE2LightningTech(FMLJavaModLoadingContext loadingContext) {
        // Forge 47.4 injects the active loading context into the mod constructor.
        // Keeping the event bus and config registration on that injected instance
        // avoids the deprecated static context lookups and cannot select another mod.
        IEventBus modEventBus = loadingContext.getModEventBus();

        AE2LTConfigMigration.runIfNeeded();
        WirelessPatternProviderPolicy.setMaxDistanceSupplier(
                AE2LTCommonConfig::wirelessConnectorMaxDistance);
        ModFumos.register();
        LegacyRegistryAliases.register();
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ModMobEffects.EFFECTS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModStructureTypes.STRUCTURE_TYPES.register(modEventBus);
        ModStructureTypes.STRUCTURE_PIECES.register(modEventBus);
        ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        // Forge 1.20.1: the network channel must be created during mod construction
        // (registry phase). NetworkInit is otherwise loaded lazily on first packet use
        // (e.g. when the client opens the Lightning Assembly Chamber screen), which
        // fails with "Registration of impl channels is locked".
        NetworkInit.register();
        // AE2WTLib is optional. Defer its terminal definition until item registration so its
        // built-in terminals are created first and the registered item is the shared WUT instance.
        if (net.minecraftforge.fml.loading.FMLLoader.getLoadingModList()
                .getModFileById("ae2wtlib") != null) {
            modEventBus.addListener(Ae2wtlibIntegration::onRegister);
        }
        modEventBus.addListener(ModAEKeyTypes::register);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigChanged);
        loadingContext.registerConfig(ModConfig.Type.COMMON, AE2LTCommonConfig.SPEC);
        loadingContext.registerConfig(ModConfig.Type.CLIENT,
                com.moakiee.ae2lt.config.AE2LTClientConfig.SPEC, "ae2lt-client.toml");

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, this::attachBlockEntityCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, this::attachItemCapabilities);
    }

    // Prevents automation from accessing the workbench inventory
    private static final IItemHandlerModifiable WORKBENCH_REJECTING_ITEM_HANDLER = new IItemHandlerModifiable() {
        @Override public int getSlots() { return 1; }
        @Override public net.minecraft.world.item.ItemStack getStackInSlot(int slot) { return net.minecraft.world.item.ItemStack.EMPTY; }
        @Override public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) { return stack; }
        @Override public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) { return net.minecraft.world.item.ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) { return false; }
        @Override public void setStackInSlot(int slot, net.minecraft.world.item.ItemStack stack) { }
    };

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ILightningEnergyHandler.class);
    }

    /**
     * Forge 1.20.1 capability attachment: a single provider is attached to every
     * AE2LT block entity that exposes automation capabilities. The provider
     * dispatches each Forge/AE2 capability token to the matching getter on the
     * block entity, caching LazyOptionals per side.
     */
    private void attachBlockEntityCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!hasAttachedCapabilitySupport(event.getObject())) {
            return;
        }

        var provider = new AttachedBlockEntityCapabilityProvider(event.getObject());
        event.addCapability(BLOCK_ENTITY_CAP_PROVIDER_ID, provider);
        event.addListener(provider::invalidate);
    }

    /**
     * Item-side capabilities: railgun / celestweave energy storage and the
     * Tianshu wireless terminal's AE2 power sink. Forge 1.20.1 attaches item
     * capabilities through AttachCapabilitiesEvent<ItemStack> instead of the
     * NeoForge registerItem API.
     */
    private void attachItemCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        // AttachCapabilitiesEvent is generic and must be registered through addGenericListener.
        // Reuse this already-registered ItemStack listener for the optional Mekanism providers;
        // registering a second listener from RegisterCapabilitiesEvent crashes Forge's mod load.
        if (ModList.get().isLoaded("mekanism")) {
            MekanismArmorIntegration.attachCapabilities(event);
        }

        var stack = event.getObject();
        var item = stack.getItem();
        if (item == ModItems.ELECTROMAGNETIC_RAILGUN.get()) {
            attachItemEnergy(event, () -> RailgunEnergyBuffer.asEnergyStorage(stack));
        } else if (item == ModItems.CELESTWEAVE_OCULUS.get()
                || item == ModItems.CELESTWEAVE_CORE.get()
                || item == ModItems.CELESTWEAVE_CONDUIT.get()
                || item == ModItems.CELESTWEAVE_STRIDE.get()) {
            attachItemEnergy(event, () -> ArmorEnergyBuffer.asEnergyStorage(stack));
        } else if (ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.isPresent()
                && item == ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get()) {
            // PoweredItemCapabilities is package-private in AE2 1.20.1, so bridge the
            // IAEItemPowerStorage sink with a small IEnergyStorage adapter instead.
            attachItemEnergy(event,
                    () -> new ItemPowerSinkEnergyStorage(stack, (IAEItemPowerStorage) item));
        }
    }

    private static void attachItemEnergy(AttachCapabilitiesEvent<ItemStack> event,
            NonNullSupplier<IEnergyStorage> supplier) {
        event.addCapability(ResourceLocation.fromNamespaceAndPath(MODID, "item_energy"), new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                if (capability == ForgeCapabilities.ENERGY) {
                    return LazyOptional.of(supplier).cast();
                }
                return LazyOptional.empty();
            }
        });
    }

    /**
     * Forge Energy bridge over an AE2 IAEItemPowerStorage (used by the Tianshu
     * wireless terminal). AE2 1.20.1 keeps its own PoweredItemCapabilities
     * package-private, so expose the same surface through this adapter.
     */
    private static final class ItemPowerSinkEnergyStorage implements IEnergyStorage {
        private final ItemStack stack;
        private final IAEItemPowerStorage sink;

        private ItemPowerSinkEnergyStorage(ItemStack stack, IAEItemPowerStorage sink) {
            this.stack = stack;
            this.sink = sink;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return (int) sink.injectAEPower(stack, maxReceive,
                    Actionable.ofSimulate(simulate));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return (int) sink.extractAEPower(stack, maxExtract,
                    Actionable.ofSimulate(simulate));
        }

        @Override
        public int getEnergyStored() {
            return (int) sink.getAECurrentPower(stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) sink.getAEMaxPower(stack);
        }

        @Override
        public boolean canExtract() {
            return sink.getPowerFlow(stack).isAllowExtraction();
        }

        @Override
        public boolean canReceive() {
            return sink.getPowerFlow(stack).isAllowInsertion();
        }
    }

    private static final class AttachedBlockEntityCapabilityProvider implements ICapabilityProvider {
        private final BlockEntity blockEntity;
        private final EnumMap<Direction, LazyOptional<IFluidHandler>> fluidHandlers =
                new EnumMap<>(Direction.class);
        private final EnumMap<Direction, LazyOptional<IEnergyStorage>> energyHandlers =
                new EnumMap<>(Direction.class);
        private LazyOptional<IItemHandlerModifiable> itemHandler;
        private LazyOptional<IFluidHandler> nullSideFluidHandler;
        private LazyOptional<IEnergyStorage> nullSideEnergyHandler;
        private LazyOptional<IInWorldGridNodeHost> gridNodeHost;
        private LazyOptional<ICraftingMachine> craftingMachine;
        private LazyOptional<ILightningEnergyHandler> lightningEnergyHandler;
        private LazyOptional<GenericInternalInventory> genericInternalInventory;

        private AttachedBlockEntityCapabilityProvider(BlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemHandler().cast();
            }
            if (capability == ForgeCapabilities.FLUID_HANDLER) {
                return fluidHandler(side).cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energyHandler(side).cast();
            }
            if (capability == Capabilities.IN_WORLD_GRID_NODE_HOST) {
                return gridNodeHost().cast();
            }
            if (capability == Capabilities.CRAFTING_MACHINE) {
                return craftingMachine().cast();
            }
            if (capability == AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK) {
                return lightningEnergyHandler().cast();
            }
            if (capability == Capabilities.GENERIC_INTERNAL_INV) {
                return genericInternalInventory().cast();
            }
            return LazyOptional.empty();
        }

        private LazyOptional<IItemHandlerModifiable> itemHandler() {
            if (itemHandler == null) {
                var handler = getItemHandlerCapability(blockEntity);
                if (handler != null) {
                    itemHandler = LazyOptional.of(() -> handler);
                }
            }
            return itemHandler != null ? itemHandler : LazyOptional.empty();
        }

        private LazyOptional<IFluidHandler> fluidHandler(Direction side) {
            if (side == null) {
                if (nullSideFluidHandler == null) {
                    var handler = getFluidHandlerCapability(blockEntity, null);
                    if (handler != null) {
                        nullSideFluidHandler = LazyOptional.of(() -> handler);
                    }
                }
                return nullSideFluidHandler != null ? nullSideFluidHandler : LazyOptional.empty();
            }

            var cached = fluidHandlers.get(side);
            if (cached != null) {
                return cached;
            }

            var handler = getFluidHandlerCapability(blockEntity, side);
            if (handler == null) {
                return LazyOptional.empty();
            }

            var optional = LazyOptional.of(() -> handler);
            fluidHandlers.put(side, optional);
            return optional;
        }

        private LazyOptional<IEnergyStorage> energyHandler(Direction side) {
            if (side == null) {
                if (nullSideEnergyHandler == null) {
                    var handler = getEnergyCapability(blockEntity, null);
                    if (handler != null) {
                        nullSideEnergyHandler = LazyOptional.of(() -> handler);
                    }
                }
                return nullSideEnergyHandler != null ? nullSideEnergyHandler : LazyOptional.empty();
            }

            var cached = energyHandlers.get(side);
            if (cached != null) {
                return cached;
            }

            var handler = getEnergyCapability(blockEntity, side);
            if (handler == null) {
                return LazyOptional.empty();
            }

            var optional = LazyOptional.of(() -> handler);
            energyHandlers.put(side, optional);
            return optional;
        }

        private LazyOptional<IInWorldGridNodeHost> gridNodeHost() {
            if (gridNodeHost == null && blockEntity instanceof IInWorldGridNodeHost host) {
                gridNodeHost = LazyOptional.of(() -> host);
            }
            return gridNodeHost != null ? gridNodeHost : LazyOptional.empty();
        }

        private LazyOptional<ICraftingMachine> craftingMachine() {
            if (craftingMachine == null && blockEntity instanceof ICraftingMachine machine) {
                craftingMachine = LazyOptional.of(() -> machine);
            }
            return craftingMachine != null ? craftingMachine : LazyOptional.empty();
        }

        private LazyOptional<ILightningEnergyHandler> lightningEnergyHandler() {
            if (lightningEnergyHandler == null) {
                var handler = getLightningEnergyCapability(blockEntity);
                if (handler != null) {
                    lightningEnergyHandler = LazyOptional.of(() -> handler);
                }
            }
            return lightningEnergyHandler != null ? lightningEnergyHandler : LazyOptional.empty();
        }

        private LazyOptional<GenericInternalInventory> genericInternalInventory() {
            if (genericInternalInventory == null) {
                var inventory = getGenericInternalInventoryCapability(blockEntity);
                if (inventory != null) {
                    genericInternalInventory = LazyOptional.of(() -> inventory);
                }
            }
            return genericInternalInventory != null ? genericInternalInventory : LazyOptional.empty();
        }

        private void invalidate() {
            invalidate(itemHandler);
            invalidate(nullSideFluidHandler);
            invalidate(nullSideEnergyHandler);
            invalidate(gridNodeHost);
            invalidate(craftingMachine);
            invalidate(lightningEnergyHandler);
            invalidate(genericInternalInventory);
            fluidHandlers.values().forEach(AttachedBlockEntityCapabilityProvider::invalidate);
            energyHandlers.values().forEach(AttachedBlockEntityCapabilityProvider::invalidate);
            fluidHandlers.clear();
            energyHandlers.clear();
        }

        private static void invalidate(LazyOptional<?> optional) {
            if (optional != null) {
                optional.invalidate();
            }
        }
    }

    private static boolean hasAttachedCapabilitySupport(BlockEntity blockEntity) {
        return blockEntity instanceof LightningCollectorBlockEntity
                || blockEntity instanceof FirmamentConversionCoreBlockEntity
                || blockEntity instanceof OverloadedControllerBlockEntity
                || blockEntity instanceof LightningSimulationChamberBlockEntity
                || blockEntity instanceof LightningAssemblyChamberBlockEntity
                || blockEntity instanceof TeslaCoilBlockEntity
                || blockEntity instanceof OverloadProcessingFactoryBlockEntity
                || blockEntity instanceof AtmosphericIonizerBlockEntity
                || blockEntity instanceof CrystalCatalyzerBlockEntity
                || blockEntity instanceof OverloadedPatternProviderBlockEntity
                || blockEntity instanceof ExtendedOverloadedPatternProviderBlockEntity
                || blockEntity instanceof OverloadedInterfaceBlockEntity
                || blockEntity instanceof OverloadedPowerSupplyBlockEntity
                || blockEntity instanceof WirelessOverloadedControllerBlockEntity
                || blockEntity instanceof AdvancedWirelessOverloadedControllerBlockEntity
                || blockEntity instanceof WirelessReceiverBlockEntity
                || blockEntity instanceof OverloadDeviceWorkbenchBlockEntity
                || blockEntity instanceof PigmeeMentalmathUnitBlockEntity
                || blockEntity instanceof PigmeePatternProviderBlockEntity
                || blockEntity instanceof PigmeeMolecularAssemblerBlockEntity
                || blockEntity instanceof MatrixPortBlockEntity
                || blockEntity instanceof TianshuSupercomputerPortBlockEntity;
    }

    private static IItemHandlerModifiable getItemHandlerCapability(BlockEntity blockEntity) {
        if (blockEntity instanceof LightningCollectorBlockEntity be) {
            return be.getAutomationInventory();
        }
        if (blockEntity instanceof FirmamentConversionCoreBlockEntity be) {
            return be.getAutomationInventory();
        }
        if (blockEntity instanceof LightningSimulationChamberBlockEntity be) {
            return be.getAutomationInventory();
        }
        if (blockEntity instanceof LightningAssemblyChamberBlockEntity be) {
            return be.getAutomationInventory();
        }
        if (blockEntity instanceof TeslaCoilBlockEntity be) {
            return be.getAutomationInventory();
        }
        if (blockEntity instanceof OverloadProcessingFactoryBlockEntity be) {
            return be.getAutomationInventory();
        }
        if (blockEntity instanceof AtmosphericIonizerBlockEntity be) {
            return be.getAutomationInventory();
        }
        if (blockEntity instanceof CrystalCatalyzerBlockEntity be) {
            return be.getAutomationInventory();
        }
        if (blockEntity instanceof OverloadDeviceWorkbenchBlockEntity) {
            return WORKBENCH_REJECTING_ITEM_HANDLER;
        }
        if (blockEntity instanceof MatrixPortBlockEntity be) {
            return be.getPatternItemHandler();
        }
        return null;
    }

    private static IFluidHandler getFluidHandlerCapability(BlockEntity blockEntity, Direction side) {
        if (blockEntity instanceof OverloadProcessingFactoryBlockEntity be) {
            return be.getFluidHandlerCapability(side);
        }
        if (blockEntity instanceof CrystalCatalyzerBlockEntity be) {
            return be.getFluidHandlerCapability(side);
        }
        return null;
    }

    private static IEnergyStorage getEnergyCapability(BlockEntity blockEntity, Direction side) {
        if (blockEntity instanceof LightningSimulationChamberBlockEntity be) {
            return be.getEnergyStorageCapability(side);
        }
        if (blockEntity instanceof LightningAssemblyChamberBlockEntity be) {
            return be.getEnergyStorageCapability(side);
        }
        if (blockEntity instanceof OverloadProcessingFactoryBlockEntity be) {
            return be.getEnergyStorageCapability(side);
        }
        if (blockEntity instanceof TeslaCoilBlockEntity be) {
            return be.getEnergyStorageCapability(side);
        }
        if (blockEntity instanceof CrystalCatalyzerBlockEntity be) {
            return be.getEnergyStorageCapability(side);
        }
        if (blockEntity instanceof OverloadedControllerBlockEntity be) {
            return be.getEnergyStorageCapability(side);
        }
        if (blockEntity instanceof WirelessOverloadedControllerBlockEntity be) {
            return be.getEnergyStorageCapability(side);
        }
        if (blockEntity instanceof AdvancedWirelessOverloadedControllerBlockEntity be) {
            return be.getEnergyStorageCapability(side);
        }
        return null;
    }

    private static ILightningEnergyHandler getLightningEnergyCapability(BlockEntity blockEntity) {
        if (blockEntity instanceof LightningCollectorBlockEntity be) {
            return new GridLightningEnergyHandler(be);
        }
        if (blockEntity instanceof LightningSimulationChamberBlockEntity be) {
            return new GridLightningEnergyHandler(be);
        }
        if (blockEntity instanceof LightningAssemblyChamberBlockEntity be) {
            return new GridLightningEnergyHandler(be);
        }
        if (blockEntity instanceof OverloadProcessingFactoryBlockEntity be) {
            return new GridLightningEnergyHandler(be);
        }
        if (blockEntity instanceof TeslaCoilBlockEntity be) {
            return new GridLightningEnergyHandler(be);
        }
        return null;
    }

    private static GenericInternalInventory getGenericInternalInventoryCapability(BlockEntity blockEntity) {
        if (blockEntity instanceof OverloadedPatternProviderBlockEntity be) {
            var logic = (com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic) be.getLogic();
            return new com.moakiee.ae2lt.logic.InsertOnlyReturnInvWrapper(
                    (com.moakiee.ae2lt.logic.UnlimitedReturnInventory) logic.getInternalReturnInv(),
                    logic);
        }
        if (blockEntity instanceof ExtendedOverloadedPatternProviderBlockEntity be) {
            var logic = (com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic) be.getLogic();
            return new com.moakiee.ae2lt.logic.InsertOnlyReturnInvWrapper(
                    (com.moakiee.ae2lt.logic.UnlimitedReturnInventory) logic.getInternalReturnInv(),
                    logic);
        }
        if (blockEntity instanceof PigmeePatternProviderBlockEntity be) {
            return be.getReturnInventory();
        }
        if (blockEntity instanceof OverloadedInterfaceBlockEntity be) {
            return be.getExposedGenericInv();
        }
        return null;
    }
    /**
     * After all registries are frozen, bind the AE2 BlockEntityType to the Block.
     * This sets the blockEntityType / class / ticker fields inside AEBaseEntityBlock
     * so that newBlockEntity() and getBlockEntity() work correctly.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        FrequencyApi.setProvider(new FrequencyApiBridge());
        BatchExecutor.setBatchEligibleRule(BatchPatternEligibility::isEligible);
        event.enqueueWork(() -> {
            // Thunderbolt keeps controller discovery content-agnostic. Register the
            // AE2LT controller family before any grid can be created so infinite
            // channel mode can use AE2's native pathing with these nodes as roots.
            // Registering the base class also covers both wireless subclasses.
            ChannelProviderRegistry.registerController(OverloadedControllerBlockEntity.class);
            CoreConfig.setChannelsPerController(
                    AE2LTCommonConfig.overloadedControllerChannelsPerController());
            CoreConfig.setBatchCopyLimitedBlocks(
                    AE2LTCommonConfig.batchCopyLimitedBlocks());

            var lightningCollectorBlock = ModBlocks.LIGHTNING_COLLECTOR.get();
            var lightningCollectorBeType = ModBlockEntities.LIGHTNING_COLLECTOR.get();
            lightningCollectorBlock.setBlockEntity(
                    LightningCollectorBlockEntity.class,
                    lightningCollectorBeType,
                    null,
                    LightningCollectorBlockEntity::serverTick);

            var controllerBlock = ModBlocks.OVERLOADED_CONTROLLER.get();
            var controllerBeType = ModBlockEntities.OVERLOADED_CONTROLLER.get();
            controllerBlock.setBlockEntity(
                    OverloadedControllerBlockEntity.class,
                    controllerBeType,
                    null,
                    OverloadedControllerBlockEntity::serverTick);

            var lightningChamberBlock = ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get();
            var lightningChamberBeType = ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get();
            lightningChamberBlock.setBlockEntity(
                    LightningSimulationChamberBlockEntity.class,
                    lightningChamberBeType,
                    null,
                    LightningSimulationChamberBlockEntity::serverTick);

            var assemblyBlock = ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get();
            var assemblyBeType = ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get();
            assemblyBlock.setBlockEntity(
                    LightningAssemblyChamberBlockEntity.class,
                    assemblyBeType,
                    null,
                    LightningAssemblyChamberBlockEntity::serverTick);

            var overloadProcessingFactoryBlock = ModBlocks.OVERLOAD_PROCESSING_FACTORY.get();
            var overloadProcessingFactoryBeType = ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get();
            overloadProcessingFactoryBlock.setBlockEntity(
                    OverloadProcessingFactoryBlockEntity.class,
                    overloadProcessingFactoryBeType,
                    null,
                    OverloadProcessingFactoryBlockEntity::serverTick);

            var teslaCoilBlock = ModBlocks.TESLA_COIL.get();
            var teslaCoilBeType = ModBlockEntities.TESLA_COIL.get();
            teslaCoilBlock.setBlockEntity(
                    TeslaCoilBlockEntity.class,
                    teslaCoilBeType,
                    null,
                    TeslaCoilBlockEntity::serverTick);

            var atmosphericIonizerBlock = ModBlocks.ATMOSPHERIC_IONIZER.get();
            var atmosphericIonizerBeType = ModBlockEntities.ATMOSPHERIC_IONIZER.get();
            atmosphericIonizerBlock.setBlockEntity(
                    AtmosphericIonizerBlockEntity.class,
                    atmosphericIonizerBeType,
                    null,
                    AtmosphericIonizerBlockEntity::serverTick);

            var overloadDeviceWorkbenchBlock = ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get();
            var overloadDeviceWorkbenchBeType = ModBlockEntities.OVERLOAD_DEVICE_WORKBENCH.get();
            overloadDeviceWorkbenchBlock.setBlockEntity(
                    OverloadDeviceWorkbenchBlockEntity.class,
                    overloadDeviceWorkbenchBeType,
                    null,
                    null);

            var crystalCatalyzerBlock = ModBlocks.CRYSTAL_CATALYZER.get();
            var crystalCatalyzerBeType = ModBlockEntities.CRYSTAL_CATALYZER.get();
            crystalCatalyzerBlock.setBlockEntity(
                    CrystalCatalyzerBlockEntity.class,
                    crystalCatalyzerBeType,
                    null,
                    CrystalCatalyzerBlockEntity::serverTick);

            var block = ModBlocks.OVERLOADED_PATTERN_PROVIDER.get();
            var beType = ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get();
            block.setBlockEntity(
                    OverloadedPatternProviderBlockEntity.class,
                    beType,
                    null,
                    OverloadedPatternProviderBlockEntity::serverTick
            );

            var extendedPatternProviderBlock = ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get();
            var extendedPatternProviderBeType = ModBlockEntities.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get();
            extendedPatternProviderBlock.setBlockEntity(
                    ExtendedOverloadedPatternProviderBlockEntity.class,
                    extendedPatternProviderBeType,
                    null,
                    ExtendedOverloadedPatternProviderBlockEntity::serverTick
            );

            var pigmeeMentalmathUnitBlock = ModBlocks.PIGMEE_MENTALMATH_UNIT.get();
            var pigmeeMentalmathUnitBeType = ModBlockEntities.PIGMEE_MENTALMATH_UNIT.get();
            pigmeeMentalmathUnitBlock.setBlockEntity(
                    PigmeeMentalmathUnitBlockEntity.class,
                    pigmeeMentalmathUnitBeType,
                    null,
                    null);

            var pigmeePatternProviderBlock = ModBlocks.PIGMEE_PATTERN_PROVIDER.get();
            var pigmeePatternProviderBeType = ModBlockEntities.PIGMEE_PATTERN_PROVIDER.get();
            pigmeePatternProviderBlock.setBlockEntity(
                    PigmeePatternProviderBlockEntity.class,
                    pigmeePatternProviderBeType,
                    null,
                    PigmeePatternProviderBlockEntity::serverTick);

            var pigmeeAssemblerBlock = ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get();
            var pigmeeAssemblerBeType = ModBlockEntities.PIGMEE_MOLECULAR_ASSEMBLER.get();
            pigmeeAssemblerBlock.setBlockEntity(
                    PigmeeMolecularAssemblerBlockEntity.class,
                    pigmeeAssemblerBeType,
                    null,
                    null);

            var matrixPortBlock = ModBlocks.MATTER_WARPING_MATRIX_PORT.get();
            var matrixPortBeType = ModBlockEntities.MATRIX_PORT.get();
            matrixPortBlock.setBlockEntity(
                    MatrixPortBlockEntity.class,
                    matrixPortBeType,
                    null,
                    MatrixPortBlockEntity::serverTick);

            var tianshuPortBlock = ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get();
            var tianshuPortBeType = ModBlockEntities.TIANSHU_SUPERCOMPUTER_PORT.get();
            tianshuPortBlock.setBlockEntity(
                    TianshuSupercomputerPortBlockEntity.class,
                    tianshuPortBeType,
                    null,
                    TianshuSupercomputerPortBlockEntity::serverTick);

            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.TIANSHU_SEED_STORAGE.get(),
                    ModBlocks.CLOSED_LOOP_SEED_STORAGE.get().asItem());

            var interfaceBlock = ModBlocks.OVERLOADED_INTERFACE.get();
            var interfaceBeType = ModBlockEntities.OVERLOADED_INTERFACE.get();
            interfaceBlock.setBlockEntity(
                    OverloadedInterfaceBlockEntity.class,
                    interfaceBeType,
                    null,
                    OverloadedInterfaceBlockEntity::serverTick);

            if (ModBlocks.hasOverloadedPowerSupply()) {
                var powerSupplyBlock = ModBlocks.OVERLOADED_POWER_SUPPLY.get();
                var powerSupplyBeType = ModBlockEntities.OVERLOADED_POWER_SUPPLY.get();
                powerSupplyBlock.setBlockEntity(
                        OverloadedPowerSupplyBlockEntity.class,
                        powerSupplyBeType,
                        null,
                        OverloadedPowerSupplyBlockEntity::serverTick);
            }

            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    lightningCollectorBeType,
                    lightningCollectorBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.OVERLOADED_CONTROLLER.get(),
                    ModBlocks.OVERLOADED_CONTROLLER.get().asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get(),
                    ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get(),
                    ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get().asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    pigmeeMentalmathUnitBeType,
                    pigmeeMentalmathUnitBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    pigmeePatternProviderBeType,
                    pigmeePatternProviderBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    pigmeeAssemblerBeType,
                    pigmeeAssemblerBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    matrixPortBeType,
                    matrixPortBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    interfaceBeType,
                    interfaceBlock.asItem());
            if (ModBlocks.hasOverloadedPowerSupply()) {
                appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                        ModBlockEntities.OVERLOADED_POWER_SUPPLY.get(),
                        ModBlocks.OVERLOADED_POWER_SUPPLY.get().asItem());
            }
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                    ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get().asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    assemblyBeType,
                    assemblyBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    overloadProcessingFactoryBeType,
                    overloadProcessingFactoryBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    teslaCoilBeType,
                    teslaCoilBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    atmosphericIonizerBeType,
                    atmosphericIonizerBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    overloadDeviceWorkbenchBeType,
                    overloadDeviceWorkbenchBlock.asItem());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    crystalCatalyzerBeType,
                    crystalCatalyzerBlock.asItem());

            setupWirelessControllerBlock(
                    ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get(),
                    ModBlockEntities.WIRELESS_OVERLOADED_CONTROLLER.get(),
                    WirelessOverloadedControllerBlockEntity.class,
                    (level, pos, state, be) -> WirelessOverloadedControllerBlockEntity.wirelessServerTick(
                            level, pos, state, (WirelessOverloadedControllerBlockEntity) be));

            setupWirelessControllerBlock(
                    ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                    ModBlockEntities.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                    AdvancedWirelessOverloadedControllerBlockEntity.class,
                    (level, pos, state, be) ->
                            AdvancedWirelessOverloadedControllerBlockEntity.advancedWirelessServerTick(
                                    level,
                                    pos,
                                    state,
                                    (AdvancedWirelessOverloadedControllerBlockEntity) be));

            var wirelessReceiverBlock = ModBlocks.WIRELESS_RECEIVER.get();
            var wirelessReceiverBeType = ModBlockEntities.WIRELESS_RECEIVER.get();
            wirelessReceiverBlock.setBlockEntity(
                    WirelessReceiverBlockEntity.class,
                    wirelessReceiverBeType,
                    null,
                    (level, pos, state, be) -> ((WirelessReceiverBlockEntity) be).serverTick());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(
                    wirelessReceiverBeType,
                    wirelessReceiverBlock.asItem());

            MachineAdapterRegistry.init();
            PatternDetailsHelper.registerDecoder(OverloadPatternDecoder.INSTANCE);
            PatternDetailsHelper.registerDecoder(ClosedLoopPatternDecoder.INSTANCE);
            StorageCells.addCellHandler(BulkLightningCellHandler.INSTANCE);
            StorageCells.addCellHandler(FixedInfiniteCellHandler.INSTANCE);
            ModItems.registerStorageCellModels();
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.LIGHTNING_SIMULATION_CHAMBER.get(),
                    LightningSimulationChamberBlockEntity.SPEED_CARD_SLOTS);
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                    LightningAssemblyChamberBlockEntity.SPEED_CARD_SLOTS);
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.OVERLOAD_PROCESSING_FACTORY.get(),
                    OverloadProcessingFactoryBlockEntity.SPEED_CARD_SLOTS);

            Upgrades.add(AEItems.FUZZY_CARD, ModItems.OVERLOADED_FILTER_COMPONENT.get(), 1);
            Upgrades.add(AEItems.INVERTER_CARD, ModItems.OVERLOADED_FILTER_COMPONENT.get(), 1);
            Upgrades.add(AEItems.CRAFTING_CARD, ModBlocks.OVERLOADED_INTERFACE.get(), 1);
            Upgrades.add(AEItems.FUZZY_CARD, ModBlocks.OVERLOADED_INTERFACE.get(), 1);

            registerAppliedFluxInductionCardCompat();
            registerOverloadTntDispenseBehavior();

            // ae2wtlib is optional: full integration (WUT definition check + overloaded
            // frequency card upgrade slots) only runs when the implementation mod is present.
            if (net.minecraftforge.fml.ModList.get().isLoaded("ae2wtlib")) {
                GridLinkables.register(
                        ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get(),
                        WirelessTerminalItem.LINKABLE_HANDLER);
                Ae2wtlibIntegration.verifyTerminalRegistration();
                Ae2wtlibIntegration.register();
            }

        });
    }

    private void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == AE2LTCommonConfig.SPEC) {
            syncThunderboltConfig();
        }
    }

    private static void syncThunderboltConfig() {
        CoreConfig.setChannelsPerController(
                AE2LTCommonConfig.overloadedControllerChannelsPerController());
        CoreConfig.setBatchCopyLimitedBlocks(
                AE2LTCommonConfig.batchCopyLimitedBlocks());
    }

    private static void registerOverloadTntDispenseBehavior() {
        net.minecraft.world.level.block.DispenserBlock.registerBehavior(
                ModBlocks.OVERLOAD_TNT.get().asItem(),
                new net.minecraft.core.dispenser.DefaultDispenseItemBehavior() {
                    @Override
                    protected net.minecraft.world.item.ItemStack execute(
                            net.minecraft.core.BlockSource source,
                            net.minecraft.world.item.ItemStack stack) {
                        // 1.20.1 BlockSource exposes getLevel/getPos/getBlockState; the
                        // level()/pos()/state() record-style getters are 1.21-only.
                        var level = source.getLevel();
                        var pos = source.getPos().relative(
                                source.getBlockState().getValue(
                                        net.minecraft.world.level.block.DispenserBlock.FACING));
                        var tnt = new com.moakiee.ae2lt.entity.OverloadTntEntity(
                                level,
                                pos.getX() + 0.5D,
                                pos.getY(),
                                pos.getZ() + 0.5D,
                                null);
                        level.addFreshEntity(tnt);
                        level.playSound(
                                null,
                                tnt.getX(),
                                tnt.getY(),
                                tnt.getZ(),
                                net.minecraft.sounds.SoundEvents.TNT_PRIMED,
                                net.minecraft.sounds.SoundSource.BLOCKS,
                                1.0F,
                                1.0F);
                        level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, pos);
                        stack.shrink(1);
                        return stack;
                    }
                });
    }

    private static void registerAppliedFluxInductionCardCompat() {
        var inductionId = ResourceLocation.fromNamespaceAndPath("appflux", "induction_card");
        Item inductionCard = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(inductionId);
        if (inductionCard == null || inductionCard == net.minecraft.world.item.Items.AIR) {
            return;
        }

        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get(), 1, "group.pattern_provider.name");
        Upgrades.add(inductionCard, ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get(), 1, "group.pattern_provider.name");
        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_INTERFACE.get(), 1);
    }

    private void onServerStarting(ServerStartingEvent event) {
        WirelessFrequencyManager.onServerStart(event.getServer());
        WirelessLinkRegistry.onServerStart(event.getServer());
        var recipeConflicts = RecipeConflictScanner.scan(event.getServer().getRecipeManager());
        if (recipeConflicts.isEmpty()) {
            LOG.info("[AE2LT recipe conflict scan] no conflicts found");
        } else {
            LOG.warn(
                    "[AE2LT recipe conflict scan] {} matching recipe ids: {}",
                    recipeConflicts.size(),
                    recipeConflicts);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        WirelessLinkRegistry.onServerStop();
        WirelessFrequencyManager.onServerStop();
        CRAFTING_CORE_REGISTRY.clear();
        com.moakiee.ae2lt.registry.ModDamageTypes.clearCache();
    }

    /**
     * Forge 1.20.1 only has the combined ServerTickEvent; the NeoForge
     * ServerTickEvent.Post subclass does not exist. Filter by phase instead.
     */
    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CRAFTING_CORE_REGISTRY.tickAll();
        WirelessFrequencyManager.flushPendingDeviceNotifications();
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            registry.tick(event.getServer());
        }
    }

    /**
     * Forge 1.20.1 creative tab helper: RegistryObject is not an ItemLike in
     * this version, so unwrap the holder before passing it to the tab output.
     */
    private static void acceptCreative(CreativeModeTab.Output output, RegistryObject<? extends ItemLike> holder) {
        output.accept(holder.get());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setupWirelessControllerBlock(
            AEBaseEntityBlock block,
            BlockEntityType beType,
            Class beClass,
            net.minecraft.world.level.block.entity.BlockEntityTicker serverTicker) {
        block.setBlockEntity(beClass, beType, null, serverTicker);
        AEBaseBlockEntity.registerBlockEntityItem(beType, block.asItem());
    }
}
