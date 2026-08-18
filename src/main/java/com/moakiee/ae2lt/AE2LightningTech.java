package com.moakiee.ae2lt;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModAEKeyTypes;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModMenuTypes;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.api.lightning.ILightningEnergyHandler;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadProcessingFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
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
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEItems;

import com.moakiee.ae2lt.api.AE2LTCapabilities;
import com.moakiee.ae2lt.api.frequency.FrequencyApi;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.api.FrequencyApiBridge;
import com.moakiee.ae2lt.me.GridLightningEnergyHandler;
import com.moakiee.ae2lt.me.cell.InfiniteCellHandler;

import com.moakiee.ae2lt.logic.EjectModeRegistry;
import com.moakiee.ae2lt.logic.MachineAdapterRegistry;
import com.moakiee.ae2lt.logic.research.ResearchNoteGenerator;
import com.moakiee.ae2lt.logic.research.ResearchNoteModulationHandler;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDecoder;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.EnumMap;

@Mod(AE2LightningTech.MODID)
public class AE2LightningTech {
    public static final String MODID = "ae2lt";
    private static final ResourceLocation BLOCK_ENTITY_CAP_PROVIDER_ID =
            new ResourceLocation(MODID, "block_entity_cap_provider");

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2lt"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.OVERLOAD_CRYSTAL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 方块
                        acceptCreative(output, ModBlocks.SILICON_BLOCK);
                        acceptCreative(output, ModBlocks.OVERLOAD_CRYSTAL_BLOCK);
                        acceptCreative(output, ModBlocks.OVERLOAD_MACHINE_FRAME);
                        acceptCreative(output, ModBlocks.OVERLOAD_TNT);
                        // 机器
                        acceptCreative(output, ModBlocks.LIGHTNING_COLLECTOR);
                        acceptCreative(output, ModBlocks.TESLA_COIL);
                        acceptCreative(output, ModBlocks.ATMOSPHERIC_IONIZER);
                        acceptCreative(output, ModBlocks.LIGHTNING_SIMULATION_CHAMBER);
                        acceptCreative(output, ModBlocks.LIGHTNING_ASSEMBLY_CHAMBER);
                        acceptCreative(output, ModBlocks.OVERLOAD_PROCESSING_FACTORY);
                        acceptCreative(output, ModBlocks.CRYSTAL_CATALYZER);
                        // 网络设备
                        acceptCreative(output, ModBlocks.OVERLOADED_CONTROLLER);
                        acceptCreative(output, ModBlocks.OVERLOADED_PATTERN_PROVIDER);
                        acceptCreative(output, ModBlocks.OVERLOADED_INTERFACE);
                        if (ModBlocks.hasOverloadedPowerSupply()) {
                            acceptCreative(output, ModBlocks.OVERLOADED_POWER_SUPPLY);
                        }
                        acceptCreative(output, ModBlocks.WIRELESS_RECEIVER);
                        acceptCreative(output, ModBlocks.WIRELESS_OVERLOADED_CONTROLLER);
                        acceptCreative(output, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER);
                        // 线缆
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
                        // 材料
                        acceptCreative(output, ModItems.OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModItems.OVERLOAD_CRYSTAL_DUST);
                        acceptCreative(output, ModItems.OVERLOAD_ALLOY);
                        acceptCreative(output, ModItems.OVERLOAD_ALLOY_BLANK);
                        acceptCreative(output, ModItems.OVERLOAD_ALLOY_PLATE);
                        acceptCreative(output, ModItems.OVERLOAD_SINGULARITY);
                        acceptCreative(output, ModItems.ULTIMATE_OVERLOAD_CORE);
                        acceptCreative(output, ModItems.LIGHTNING_COLLAPSE_MATRIX);
                        acceptCreative(output, ModItems.UNOVERLOADED_CIRCUIT_BOARD);
                        acceptCreative(output, ModItems.OVERLOAD_CIRCUIT_BOARD);
                        acceptCreative(output, ModItems.OVERLOAD_PROCESSOR);
                        acceptCreative(output, ModItems.OVERLOAD_INSCRIBER_PRESS);
                        acceptCreative(output, ModItems.ELECTRO_CHIME_CRYSTAL);
                        acceptCreative(output, ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL);
                        acceptCreative(output, ModItems.CLEAR_CONDENSATE);
                        acceptCreative(output, ModItems.RAIN_CONDENSATE);
                        acceptCreative(output, ModItems.THUNDERSTORM_CONDENSATE);
                        // 存储组件
                        acceptCreative(output, ModItems.LIGHTNING_ITEM_CELL_HOUSING);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_I);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_II);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_III);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_IV);
                        acceptCreative(output, ModItems.LIGHTNING_STORAGE_COMPONENT_V);
                        // 元件
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_I);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_II);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_III);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_IV);
                        acceptCreative(output, ModItems.LIGHTNING_CELL_COMPONENT_V);
                        // 无限存储单元
                        acceptCreative(output, ModItems.INFINITE_STORAGE_CELL);
                        output.accept(FixedInfiniteCellItem.createDisplayedResultStack(CellOutcome.HIGH_VOLTAGE));
                        output.accept(FixedInfiniteCellItem.createDisplayedResultStack(CellOutcome.EXTREME_HIGH_VOLTAGE));
                        // 工具
                        acceptCreative(output, ModItems.OVERLOAD_PATTERN);
                        acceptCreative(output, ModItems.OVERLOAD_PATTERN_ENCODER);
                        acceptCreative(output, ModItems.OVERLOADED_WIRELESS_CONNECT_TOOL);
                        acceptCreative(output, ModItems.OVERLOADED_FILTER_COMPONENT);
                        // 水晶生长
                        acceptCreative(output, ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL);
                        acceptCreative(output, ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD);
                        acceptCreative(output, ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD);
                        acceptCreative(output, ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD);
                        acceptCreative(output, ModBlocks.OVERLOAD_CRYSTAL_CLUSTER);
                        // Fumo
                        output.accept(ModFumos.MOAKIEE_FUMO_ITEM.get());
                        output.accept(ModFumos.CYSTRYSU_FUMO_ITEM.get());
                        output.accept(ModFumos.PIGMEE_FUMO_ITEM.get());
                    })
                    .build());

    public AE2LightningTech() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();

        ModFumos.register();
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModRecipeTypes.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModAEKeyTypes::register);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AE2LTCommonConfig.SPEC);

        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, this::attachBlockEntityCapabilities);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.register(new ResearchNoteModulationHandler());
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ILightningEnergyHandler.class);
    }

    private void attachBlockEntityCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!hasAttachedCapabilitySupport(event.getObject())) {
            return;
        }

        var provider = new AttachedBlockEntityCapabilityProvider(event.getObject());
        event.addCapability(BLOCK_ENTITY_CAP_PROVIDER_ID, provider);
        event.addListener(provider::invalidate);
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
                || blockEntity instanceof OverloadedControllerBlockEntity
                || blockEntity instanceof LightningSimulationChamberBlockEntity
                || blockEntity instanceof LightningAssemblyChamberBlockEntity
                || blockEntity instanceof TeslaCoilBlockEntity
                || blockEntity instanceof OverloadProcessingFactoryBlockEntity
                || blockEntity instanceof AtmosphericIonizerBlockEntity
                || blockEntity instanceof CrystalCatalyzerBlockEntity
                || blockEntity instanceof OverloadedPatternProviderBlockEntity
                || blockEntity instanceof OverloadedInterfaceBlockEntity
                || blockEntity instanceof OverloadedPowerSupplyBlockEntity
                || blockEntity instanceof WirelessOverloadedControllerBlockEntity
                || blockEntity instanceof AdvancedWirelessOverloadedControllerBlockEntity
                || blockEntity instanceof WirelessReceiverBlockEntity;
    }

    private static IItemHandlerModifiable getItemHandlerCapability(BlockEntity blockEntity) {
        if (blockEntity instanceof LightningCollectorBlockEntity be) {
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
        return null;
    }

    private static IFluidHandler getFluidHandlerCapability(BlockEntity blockEntity, net.minecraft.core.Direction side) {
        if (blockEntity instanceof OverloadProcessingFactoryBlockEntity be) {
            return be.getFluidHandlerCapability(side);
        }
        if (blockEntity instanceof CrystalCatalyzerBlockEntity be) {
            return be.getFluidHandlerCapability(side);
        }
        return null;
    }

    private static IEnergyStorage getEnergyCapability(BlockEntity blockEntity, net.minecraft.core.Direction side) {
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
        if (blockEntity instanceof OverloadedInterfaceBlockEntity be) {
            var logic = be.getInterfaceLogic();
            if (logic instanceof com.moakiee.ae2lt.logic.OverloadedInterfaceLogic ol) {
                return ol.getProxiedStorage();
            }
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
        event.enqueueWork(() -> {
            NetworkInit.register();

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

        });
    }

    private static void registerOverloadTntDispenseBehavior() {
        net.minecraft.world.level.block.DispenserBlock.registerBehavior(
                ModBlocks.OVERLOAD_TNT.get().asItem(),
                new net.minecraft.core.dispenser.DefaultDispenseItemBehavior() {
                    @Override
                    protected net.minecraft.world.item.ItemStack execute(
                            net.minecraft.core.BlockSource source,
                            net.minecraft.world.item.ItemStack stack) {
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
        var inductionId = new ResourceLocation("appflux", "induction_card");
        Item inductionCard = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(inductionId);
        if (inductionCard == null || inductionCard == net.minecraft.world.item.Items.AIR) {
            return;
        }

        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get(), 1, "group.pattern_provider.name");
        Upgrades.add(inductionCard, ModBlocks.OVERLOADED_INTERFACE.get(), 1);
    }

    private void onServerStarting(ServerStartingEvent event) {
        EjectModeRegistry.onServerStart(event.getServer());
        WirelessFrequencyManager.onServerStart(event.getServer());
        ResearchNoteGenerator.onServerStarting();
    }

    private void onServerStopped(ServerStoppedEvent event) {
        EjectModeRegistry.onServerStop();
        WirelessFrequencyManager.onServerStop();
        ResearchNoteGenerator.onServerStopped();
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            WirelessFrequencyManager.flushPendingDeviceNotifications();
        }
    }

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



