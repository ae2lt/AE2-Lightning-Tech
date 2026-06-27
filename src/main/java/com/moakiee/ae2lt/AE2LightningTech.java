package com.moakiee.ae2lt;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModDataComponents;
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
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.config.AE2LTConfigMigration;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.ExtendedOverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem.CellOutcome;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEItems;

import com.moakiee.ae2lt.api.AE2LTCapabilities;
import com.moakiee.ae2lt.api.frequency.FrequencyApi;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.grid.api.FrequencyApiBridge;
import com.moakiee.ae2lt.me.GridLightningEnergyHandler;
import com.moakiee.ae2lt.me.cell.InfiniteCellHandler;

import com.moakiee.ae2lt.logic.EjectModeRegistry;
import com.moakiee.ae2lt.logic.MachineAdapterRegistry;
import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;
import com.moakiee.ae2lt.logic.research.ResearchNoteGenerator;
import com.moakiee.ae2lt.logic.research.ResearchNoteModulationHandler;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorMaterials;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDecoder;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(AE2LightningTech.MODID)
public class AE2LightningTech {
    public static final String MODID = "ae2lt";

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2lt"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.OVERLOAD_CRYSTAL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 方块
                        output.accept(ModBlocks.SILICON_BLOCK);
                        output.accept(ModBlocks.OVERLOAD_CRYSTAL_BLOCK);
                        output.accept(ModBlocks.OVERLOAD_MACHINE_FRAME);
                        output.accept(ModBlocks.FIRMAMENT_CONVERSION_CORE);
                        output.accept(ModBlocks.OVERLOAD_TNT);
                        // 机器
                        output.accept(ModBlocks.LIGHTNING_COLLECTOR);
                        output.accept(ModBlocks.TESLA_COIL);
                        output.accept(ModBlocks.ATMOSPHERIC_IONIZER);
                        output.accept(ModBlocks.LIGHTNING_SIMULATION_CHAMBER);
                        output.accept(ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER);
                        output.accept(ModBlocks.OVERLOAD_PROCESSING_FACTORY);
                        output.accept(ModBlocks.OVERLOAD_DEVICE_WORKBENCH);
                        output.accept(ModBlocks.CRYSTAL_CATALYZER);
                        // 网络设备
                        output.accept(ModBlocks.OVERLOADED_CONTROLLER);
                        output.accept(ModBlocks.OVERLOADED_PATTERN_PROVIDER);
                        output.accept(ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER);
                        output.accept(ModBlocks.OVERLOADED_INTERFACE);
                        if (ModBlocks.hasOverloadedPowerSupply()) {
                            output.accept(ModBlocks.OVERLOADED_POWER_SUPPLY);
                        }
                        output.accept(ModBlocks.WIRELESS_RECEIVER);
                        output.accept(ModBlocks.WIRELESS_OVERLOADED_CONTROLLER);
                        output.accept(ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER);
                        // 线缆
                        output.accept(ModItems.OVERLOADED_CABLE);
                        output.accept(ModItems.OVERLOADED_CABLE_WHITE);
                        output.accept(ModItems.OVERLOADED_CABLE_ORANGE);
                        output.accept(ModItems.OVERLOADED_CABLE_MAGENTA);
                        output.accept(ModItems.OVERLOADED_CABLE_LIGHT_BLUE);
                        output.accept(ModItems.OVERLOADED_CABLE_YELLOW);
                        output.accept(ModItems.OVERLOADED_CABLE_LIME);
                        output.accept(ModItems.OVERLOADED_CABLE_PINK);
                        output.accept(ModItems.OVERLOADED_CABLE_GRAY);
                        output.accept(ModItems.OVERLOADED_CABLE_LIGHT_GRAY);
                        output.accept(ModItems.OVERLOADED_CABLE_CYAN);
                        output.accept(ModItems.OVERLOADED_CABLE_PURPLE);
                        output.accept(ModItems.OVERLOADED_CABLE_BLUE);
                        output.accept(ModItems.OVERLOADED_CABLE_BROWN);
                        output.accept(ModItems.OVERLOADED_CABLE_GREEN);
                        output.accept(ModItems.OVERLOADED_CABLE_RED);
                        output.accept(ModItems.OVERLOADED_CABLE_BLACK);
                        // 材料
                        output.accept(ModItems.OVERLOAD_CRYSTAL);
                        output.accept(ModItems.OVERLOAD_CRYSTAL_DUST);
                        output.accept(ModItems.FIRMAMENT_DUST);
                        output.accept(ModItems.FIRMAMENT_MIXTURE);
                        output.accept(ModItems.FIRMAMENT_ALLOY_INGOT);
                        output.accept(ModItems.FIRMAMENT_ESSENCE);
                        output.accept(ModItems.INACTIVE_FIRMAMENT_SPIRIT_CORE);
                        output.accept(ModItems.FIRMAMENT_SPIRIT_CORE_OCULUS);
                        output.accept(ModItems.FIRMAMENT_SPIRIT_CORE_CORE);
                        output.accept(ModItems.FIRMAMENT_SPIRIT_CORE_CONDUIT);
                        output.accept(ModItems.FIRMAMENT_SPIRIT_CORE_STRIDE);
                        output.accept(ModItems.FIRMAMENT_SUPERCONDUCTING_WIRE);
                        output.accept(ModItems.OVERLOAD_ALLOY);
                        output.accept(ModItems.OVERLOAD_ALLOY_BLANK);
                        output.accept(ModItems.OVERLOAD_ALLOY_PLATE);
                        output.accept(ModItems.OVERLOAD_SINGULARITY);
                        output.accept(ModItems.ULTIMATE_OVERLOAD_CORE);
                        output.accept(ModItems.LIGHTNING_COLLAPSE_MATRIX);
                        output.accept(ModItems.FLOATING_MATTER);
                        output.accept(ModItems.UNOVERLOADED_CIRCUIT_BOARD);
                        output.accept(ModItems.OVERLOAD_CIRCUIT_BOARD);
                        output.accept(ModItems.OVERLOAD_PROCESSOR);
                        output.accept(ModItems.OVERLOAD_INSCRIBER_PRESS);
                        output.accept(ModItems.ELECTRO_CHIME_CRYSTAL);
                        output.accept(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL);
                        output.accept(ModItems.CLEAR_CONDENSATE);
                        output.accept(ModItems.RAIN_CONDENSATE);
                        output.accept(ModItems.THUNDERSTORM_CONDENSATE);
                        // 存储组件
                        output.accept(ModItems.LIGHTNING_ITEM_CELL_HOUSING);
                        // 元件
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_I);
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_II);
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_III);
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_IV);
                        output.accept(ModItems.LIGHTNING_STORAGE_COMPONENT_V);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_I);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_II);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_III);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_IV);
                        output.accept(ModItems.LIGHTNING_CELL_COMPONENT_V);
                        // 无限存储单元
                        output.accept(ModItems.INFINITE_STORAGE_CELL);
                        output.accept(FixedInfiniteCellItem.createDisplayedResultStack(CellOutcome.HIGH_VOLTAGE));
                        output.accept(FixedInfiniteCellItem.createDisplayedResultStack(CellOutcome.EXTREME_HIGH_VOLTAGE));
                        // 工具
                        output.accept(ModItems.OVERLOAD_PATTERN);
                        output.accept(ModItems.OVERLOAD_PATTERN_ENCODER);
                        output.accept(ModItems.OVERLOADED_WIRELESS_CONNECT_TOOL);
                        output.accept(ModItems.OVERLOADED_FREQUENCY_CARD);
                        output.accept(ModItems.OVERLOADED_PATTERN_PROVIDER_UPGRADE);
                        output.accept(ModItems.OVERLOADED_FILTER_COMPONENT);
                        // 苍穹织雷装备 + 模块
                        output.accept(ModItems.OVERLOAD_MODULE_BASE);
                        output.accept(ModItems.CELESTWEAVE_OCULUS);
                        output.accept(ModItems.CELESTWEAVE_CORE);
                        output.accept(ModItems.CELESTWEAVE_CONDUIT);
                        output.accept(ModItems.CELESTWEAVE_STRIDE);
                        output.accept(ModItems.ENERGY_MODULE_T1);
                        output.accept(ModItems.ENERGY_MODULE_T2);
                        output.accept(ModItems.ENERGY_MODULE_T3);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_NIGHT_VISION);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_WATER_BREATHING);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_REACH_EXTENSION);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_MATRIX_SHIELD);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_PHASE_SHIELD);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_REFLECT);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_UNDYING);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_DASH);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_FLIGHT);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_PURIFICATION);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_SATURATION);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_DIG_AFFINITY);
                        output.accept(ModItems.CELESTWEAVE_SUBMODULE_PHASE_FLIGHT);
                        // 电磁炮 + 模块
                        output.accept(ModItems.ELECTROMAGNETIC_RAILGUN);
                        output.accept(ModItems.RAILGUN_MODULE_CORE);
                        output.accept(ModItems.RAILGUN_MODULE_COMPUTE);
                        output.accept(ModItems.RAILGUN_MODULE_ACCELERATION);
                        output.accept(ModItems.RAILGUN_MODULE_OVERLOAD_EXECUTION);
                        // 水晶生长
                        output.accept(ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL);
                        output.accept(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL);
                        output.accept(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL);
                        output.accept(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL);
                        output.accept(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD);
                        output.accept(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD);
                        output.accept(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD);
                        output.accept(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER);
                        // Fumo
                        output.accept(ModFumos.MOAKIEE_FUMO_ITEM.get());
                        output.accept(ModFumos.CYSTRYSU_FUMO_ITEM.get());
                        output.accept(ModFumos.PIGMEE_FUMO_ITEM.get());
                    })
                    .build());

    public AE2LightningTech(IEventBus modEventBus, ModContainer modContainer) {
        AE2LTConfigMigration.runIfNeeded();
        ModFumos.register();
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        CelestweaveArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModMobEffects.EFFECTS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModStructureTypes.STRUCTURE_TYPES.register(modEventBus);
        ModStructureTypes.STRUCTURE_PIECES.register(modEventBus);
        ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModAEKeyTypes::register);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, AE2LTCommonConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPost);
        NeoForge.EVENT_BUS.register(new ResearchNoteModulationHandler());
    }

    // Prevents automation from accessing the workbench inventory
    private static final IItemHandler WORKBENCH_REJECTING_ITEM_HANDLER = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public net.minecraft.world.item.ItemStack getStackInSlot(int slot) { return net.minecraft.world.item.ItemStack.EMPTY; }
        @Override public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) { return stack; }
        @Override public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) { return net.minecraft.world.item.ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) { return false; }
    };

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LIGHTNING_COLLECTOR.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.FIRMAMENT_CONVERSION_CORE.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.TESLA_COIL.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        // TeslaCoil 是双格高方块,UPPER 半部分没有 BlockEntity;
        // 把 UPPER 的 ItemHandler 查询代理到下方 LOWER 的 BE,
        // 让漏斗/导管从顶面和上半身四面也能输入物品。
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, context) -> {
                    if (state.getValue(TeslaCoilBlock.HALF) != DoubleBlockHalf.UPPER) {
                        return null;
                    }
                    if (level.getBlockEntity(pos.below()) instanceof TeslaCoilBlockEntity be) {
                        return be.getAutomationInventory();
                    }
                    return null;
                },
                ModBlocks.TESLA_COIL.get());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ATMOSPHERIC_IONIZER.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CRYSTAL_CATALYZER.get(),
                (blockEntity, side) -> blockEntity.getAutomationInventory());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.OVERLOAD_DEVICE_WORKBENCH.get(),
                (blockEntity, side) -> WORKBENCH_REJECTING_ITEM_HANDLER);

        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, context) -> RailgunEnergyBuffer.asEnergyStorage(stack),
                ModItems.ELECTROMAGNETIC_RAILGUN.get());

        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, context) -> ArmorEnergyBuffer.asEnergyStorage(stack),
                ModItems.CELESTWEAVE_OCULUS.get(),
                ModItems.CELESTWEAVE_CORE.get(),
                ModItems.CELESTWEAVE_CONDUIT.get(),
                ModItems.CELESTWEAVE_STRIDE.get());

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, side) -> blockEntity.getFluidHandlerCapability(side));

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.CRYSTAL_CATALYZER.get(),
                (blockEntity, side) -> blockEntity.getFluidHandlerCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.TESLA_COIL.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CRYSTAL_CATALYZER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.OVERLOADED_CONTROLLER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.WIRELESS_OVERLOADED_CONTROLLER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorageCapability(side));

        // Expose IN_WORLD_GRID_NODE_HOST so ME cables can connect to our block entity
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LIGHTNING_COLLECTOR.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOADED_CONTROLLER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.TESLA_COIL.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOAD_DEVICE_WORKBENCH.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ATMOSPHERIC_IONIZER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.CRYSTAL_CATALYZER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.OVERLOADED_INTERFACE.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        if (ModBlocks.hasOverloadedPowerSupply()) {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    ModBlockEntities.OVERLOADED_POWER_SUPPLY.get(),
                    (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
        }

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.WIRELESS_OVERLOADED_CONTROLLER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.WIRELESS_RECEIVER.get(),
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);

        // Public, addon-facing lightning energy capability. Each registered BE
        // bridges the AE2 grid's lightning-typed storage through the
        // ILightningEnergyHandler API so external mods don't have to reflect into
        // grid internals. CrystalCatalyzer is intentionally not registered: it does
        // not interact with lightning energy on the grid, so a handler there would
        // be misleading. See PLAN_public_api_design.md sections 3.1, 5.1.
        event.registerBlockEntity(
                AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK,
                ModBlockEntities.LIGHTNING_COLLECTOR.get(),
                (blockEntity, side) -> new GridLightningEnergyHandler(blockEntity));

        event.registerBlockEntity(
                AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK,
                ModBlockEntities.LIGHTNING_SIMULATION_CHAMBER.get(),
                (blockEntity, side) -> new GridLightningEnergyHandler(blockEntity));

        event.registerBlockEntity(
                AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK,
                ModBlockEntities.LIGHTNING_ASSEMBLY_CHAMBER.get(),
                (blockEntity, side) -> new GridLightningEnergyHandler(blockEntity));

        event.registerBlockEntity(
                AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK,
                ModBlockEntities.OVERLOAD_PROCESSING_FACTORY.get(),
                (blockEntity, side) -> new GridLightningEnergyHandler(blockEntity));

        // TeslaCoil 是双高方块：UPPER 半部分 newBlockEntity 返回 null，
        // 单用 registerBlockEntity 会让 UPPER 位置 capability 查询拿到 null，
        // 与 README 公开契约不符。改用 registerBlock 在 block 层面统一处理：
        // UPPER 转发到 pos.below() 的 LOWER BE，与 TeslaCoilBlock 自身
        // useWithoutItem / useItemOn 已采用的 UPPER→LOWER 委托一致。
        event.registerBlock(
                AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    if (state.getValue(TeslaCoilBlock.HALF) == DoubleBlockHalf.UPPER) {
                        var lowerPos = pos.below();
                        var lowerState = level.getBlockState(lowerPos);
                        if (lowerState.is(state.getBlock())
                                && lowerState.getValue(TeslaCoilBlock.HALF) == DoubleBlockHalf.LOWER
                                && level.getBlockEntity(lowerPos) instanceof TeslaCoilBlockEntity be) {
                            return new GridLightningEnergyHandler(be);
                        }
                        return null;
                    }
                    if (blockEntity instanceof TeslaCoilBlockEntity be) {
                        return new GridLightningEnergyHandler(be);
                    }
                    return null;
                },
                ModBlocks.TESLA_COIL.get());

        event.registerBlock(
                AECapabilities.GENERIC_INTERNAL_INV,
                (level, pos, state, blockEntity, context) -> {
                    if (blockEntity instanceof OverloadedPatternProviderBlockEntity be) {
                        var logic = (com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic) be.getLogic();
                        return new com.moakiee.ae2lt.logic.InsertOnlyReturnInvWrapper(
                                (com.moakiee.ae2lt.logic.UnlimitedReturnInventory) logic.getInternalReturnInv(),
                                logic);
                    }
                    return null;
                },
                ModBlocks.OVERLOADED_PATTERN_PROVIDER.get(),
                ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get());

        event.registerBlock(
                AECapabilities.GENERIC_INTERNAL_INV,
                (level, pos, state, blockEntity, context) -> {
                    if (blockEntity instanceof OverloadedInterfaceBlockEntity be) {
                        var logic = be.getInterfaceLogic();
                        if (logic instanceof com.moakiee.ae2lt.logic.OverloadedInterfaceLogic ol) {
                            return ol.getProxiedStorage();
                        }
                    }
                    return null;
                },
                ModBlocks.OVERLOADED_INTERFACE.get());
    }

    /**
     * After all registries are frozen, bind the AE2 BlockEntityType to the Block.
     * This sets the blockEntityType / class / ticker fields inside AEBaseEntityBlock
     * so that newBlockEntity() and getBlockEntity() work correctly.
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        FrequencyApi.setProvider(new FrequencyApiBridge());
        event.enqueueWork(() -> {
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
            StorageCells.addCellHandler(InfiniteCellHandler.INSTANCE);
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

            // ae2wtlib: make the overloaded frequency card installable into
            // wireless terminal upgrade slots. Guarded so the integration class
            // (which references ae2wtlib API types) is only loaded when present.
            if (net.neoforged.fml.ModList.get().isLoaded("ae2wtlib")) {
                com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration.register();
            }

            // Railgun: wireless link handler so AE2 wireless access points can bind it.
            GridLinkables.register(
                    ModItems.ELECTROMAGNETIC_RAILGUN.get(),
                    com.moakiee.ae2lt.event.railgun.RailgunGridLinkHandler.INSTANCE);

        });
    }

    private static void registerOverloadTntDispenseBehavior() {
        net.minecraft.world.level.block.DispenserBlock.registerBehavior(
                ModBlocks.OVERLOAD_TNT.get().asItem(),
                new net.minecraft.core.dispenser.DefaultDispenseItemBehavior() {
                    @Override
                    protected net.minecraft.world.item.ItemStack execute(
                            net.minecraft.core.dispenser.BlockSource source,
                            net.minecraft.world.item.ItemStack stack) {
                        var level = source.level();
                        var pos = source.pos().relative(
                                source.state().getValue(
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
        EjectModeRegistry.onServerStart(event.getServer());
        WirelessFrequencyManager.onServerStart(event.getServer());
        WirelessLinkRegistry.onServerStart(event.getServer());
        ResearchNoteGenerator.onServerStarting();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        EjectModeRegistry.onServerStop();
        WirelessLinkRegistry.onServerStop();
        WirelessFrequencyManager.onServerStop();
        ResearchNoteGenerator.onServerStopped();
        com.moakiee.ae2lt.registry.ModDamageTypes.clearCache();
    }

    private void onServerTickPost(ServerTickEvent.Post event) {
        WirelessFrequencyManager.flushPendingDeviceNotifications();
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            registry.tick(event.getServer());
        }
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
