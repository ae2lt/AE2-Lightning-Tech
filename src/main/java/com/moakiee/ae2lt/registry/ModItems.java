package com.moakiee.ae2lt.registry;

import java.util.function.Function;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.DebugLightningRodItem;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.item.InfiniteStorageCellItem;
import com.moakiee.ae2lt.item.LightningStorageComponentItem;
import com.moakiee.ae2lt.item.OverloadCrystalItem;
import com.moakiee.ae2lt.item.OverloadPatternEncoderItem;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.item.PerfectElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.ResearchNoteItem;
import com.moakiee.ae2lt.item.WeatherCondensateItem;
import com.moakiee.ae2lt.item.BulkLightningStorageCellItem;
import com.moakiee.ae2lt.item.CelestweaveConduitItem;
import com.moakiee.ae2lt.item.CelestweaveCoreItem;
import com.moakiee.ae2lt.item.CelestweaveOculusItem;
import com.moakiee.ae2lt.item.CelestweaveStrideItem;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.item.DashSubmoduleItem;
import com.moakiee.ae2lt.item.DigAffinitySubmoduleItem;
import com.moakiee.ae2lt.item.ExtendedOverloadedPatternProviderUpgradeItem;
import com.moakiee.ae2lt.item.FlightSubmoduleItem;
import com.moakiee.ae2lt.item.FloatingMatterItem;
import com.moakiee.ae2lt.item.MekanismProtectionSubmoduleItem;
import com.moakiee.ae2lt.item.MovementAssistSubmoduleItem;
import com.moakiee.ae2lt.item.NightVisionSubmoduleItem;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.OverloadedPatternProviderUpgradeItem;
import com.moakiee.ae2lt.item.PhaseFlightSubmoduleItem;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;
import com.moakiee.ae2lt.item.PhaseLockSubmoduleItem;
import com.moakiee.ae2lt.item.PigmeeCoreItem;
import com.moakiee.ae2lt.item.PigmeeStorageCellItem;
import com.moakiee.ae2lt.item.PurificationSubmoduleItem;
import com.moakiee.ae2lt.item.ReachSubmoduleItem;
import com.moakiee.ae2lt.item.ReflectSubmoduleItem;
import com.moakiee.ae2lt.item.ResistanceSubmoduleItem;
import com.moakiee.ae2lt.item.RisingItem;
import com.moakiee.ae2lt.item.SaturationSubmoduleItem;
import com.moakiee.ae2lt.item.TianshuWirelessPatternEncodingTerminalItem;
import com.moakiee.ae2lt.item.UndyingSubmoduleItem;
import com.moakiee.ae2lt.item.WaterBreathingSubmoduleItem;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;
import com.moakiee.ae2lt.celestweave.ArmorEnergyModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorEnergyRules;
import com.moakiee.ae2lt.celestweave.module.MekanismProtectionSubmodule;
import com.moakiee.ae2lt.celestweave.module.ResistanceSubmodule;
import com.moakiee.ae2lt.part.OverloadedCablePart;
import com.moakiee.ae2lt.part.TianshuPatternEncodingTerminalPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.api.client.StorageCellModels;
import appeng.api.parts.PartModels;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AE2LightningTech.MODID);

    public static final RegistryObject<OverloadCrystalItem> OVERLOAD_CRYSTAL = registerItem(
            "overload_crystal",
            OverloadCrystalItem::new,
            new Item.Properties());

    public static final RegistryObject<Item> OVERLOAD_CRYSTAL_DUST =
            registerSimpleItem("overload_crystal_dust", new Item.Properties());

    public static final RegistryObject<Item> UNOVERLOADED_CIRCUIT_BOARD =
            registerSimpleItem("unoverloaded_circuit_board", new Item.Properties());

    public static final RegistryObject<Item> OVERLOAD_CIRCUIT_BOARD =
            registerSimpleItem("overload_circuit_board", new Item.Properties());

    public static final RegistryObject<Item> OVERLOAD_PROCESSOR =
            registerSimpleItem("overload_processor", new Item.Properties());

    public static final RegistryObject<Item> OVERLOAD_INSCRIBER_PRESS =
            registerSimpleItem("overload_inscriber_press", new Item.Properties());

    public static final RegistryObject<Item> OVERLOAD_ALLOY =
            registerSimpleItem("overload_alloy", new Item.Properties());

    public static final RegistryObject<Item> OVERLOAD_ALLOY_BLANK =
            registerSimpleItem("overload_alloy_blank", new Item.Properties());

    public static final RegistryObject<Item> OVERLOAD_ALLOY_PLATE =
            registerSimpleItem("overload_alloy_plate", new Item.Properties());

    public static final RegistryObject<Item> OVERLOAD_SINGULARITY =
            registerSimpleItem("overload_singularity", new Item.Properties());

    public static final RegistryObject<Item> ULTIMATE_OVERLOAD_CORE =
            registerSimpleItem("ultimate_overload_core", new Item.Properties());

    public static final RegistryObject<Item> LIGHTNING_COLLAPSE_MATRIX =
            registerSimpleItem("lightning_collapse_matrix", new Item.Properties());

    public static final RegistryObject<DebugLightningRodItem> DEBUG_LIGHTNING_ROD = registerItem(
            "debug_lightning_rod",
            DebugLightningRodItem::new,
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final RegistryObject<ElectroChimeCrystalItem> ELECTRO_CHIME_CRYSTAL = registerItem(
            "electro_chime_crystal",
            ElectroChimeCrystalItem::new,
            new Item.Properties().stacksTo(1));

    public static final RegistryObject<PerfectElectroChimeCrystalItem> PERFECT_ELECTRO_CHIME_CRYSTAL = registerItem(
            "perfect_electro_chime_crystal",
            PerfectElectroChimeCrystalItem::new,
            new Item.Properties().stacksTo(1));

    public static final RegistryObject<WeatherCondensateItem> CLEAR_CONDENSATE = ITEMS.register(
            "clear_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.CLEAR, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<WeatherCondensateItem> RAIN_CONDENSATE = ITEMS.register(
            "rain_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.RAIN, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<WeatherCondensateItem> THUNDERSTORM_CONDENSATE = ITEMS.register(
            "thunderstorm_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.THUNDERSTORM, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> LIGHTNING_ITEM_CELL_HOUSING =
            registerSimpleItem("lightning_item_cell_housing", new Item.Properties());

    public static final RegistryObject<Item> LIGHTNING_CELL_COMPONENT_I =
            registerSimpleItem("lightning_cell_component_i", new Item.Properties());
    public static final RegistryObject<Item> LIGHTNING_CELL_COMPONENT_II =
            registerSimpleItem("lightning_cell_component_ii", new Item.Properties());
    public static final RegistryObject<Item> LIGHTNING_CELL_COMPONENT_III =
            registerSimpleItem("lightning_cell_component_iii", new Item.Properties());
    public static final RegistryObject<Item> LIGHTNING_CELL_COMPONENT_IV =
            registerSimpleItem("lightning_cell_component_iv", new Item.Properties());
    public static final RegistryObject<Item> LIGHTNING_CELL_COMPONENT_V =
            registerSimpleItem("lightning_cell_component_v", new Item.Properties());

    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_I =
            registerLightningStorageComponent("lightning_storage_component_i", LIGHTNING_CELL_COMPONENT_I, 256, 32);
    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_II =
            registerLightningStorageComponent("lightning_storage_component_ii", LIGHTNING_CELL_COMPONENT_II, 1024, 128);
    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_III =
            registerLightningStorageComponent("lightning_storage_component_iii", LIGHTNING_CELL_COMPONENT_III, 4096, 512);
    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_IV =
            registerLightningStorageComponent("lightning_storage_component_iv", LIGHTNING_CELL_COMPONENT_IV, 16384, 2048);
    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_V =
            registerLightningStorageComponent("lightning_storage_component_v", LIGHTNING_CELL_COMPONENT_V, 65536, 8192);

    public static final RegistryObject<InfiniteStorageCellItem> INFINITE_STORAGE_CELL =
            ITEMS.register("infinite_storage_cell",
                    () -> new InfiniteStorageCellItem(
                            new Item.Properties(),
                            Long.MAX_VALUE, Long.MAX_VALUE,
                            8, Integer.MAX_VALUE,
                            32));

    /** Easter egg cell: behaviour determined by NBT (CellType / CellSeed). */
    public static final RegistryObject<FixedInfiniteCellItem> MYSTERIOUS_CELL =
            ITEMS.register("mysterious_cell",
                    () -> new FixedInfiniteCellItem(new Item.Properties()));

    public static final RegistryObject<ResearchNoteItem> RESEARCH_NOTE =
            registerItem("research_note", ResearchNoteItem::new, new Item.Properties().stacksTo(16));

    public static final RegistryObject<Item> CHARRED_RITUAL_FRAGMENT =
            registerSimpleItem("charred_ritual_fragment", new Item.Properties());

    public static final RegistryObject<OverloadedWirelessConnectorItem> OVERLOADED_WIRELESS_CONNECT_TOOL = registerItem(
            "overloaded_wireless_connect_tool",
            OverloadedWirelessConnectorItem::new,
            new Item.Properties());

    public static final RegistryObject<OverloadPatternItem> OVERLOAD_PATTERN = registerItem(
            "overload_pattern",
            OverloadPatternItem::new,
            new Item.Properties());

    public static final RegistryObject<OverloadPatternEncoderItem> OVERLOAD_PATTERN_ENCODER = registerItem(
            "overload_pattern_encoder",
            OverloadPatternEncoderItem::new,
            new Item.Properties());

    public static final RegistryObject<OverloadedFilterComponentItem> OVERLOADED_FILTER_COMPONENT = registerItem(
            "overloaded_filter_component",
            OverloadedFilterComponentItem::new,
            new Item.Properties().stacksTo(1));

    public static final RegistryObject<RisingItem> FIRMAMENT_DUST =
            registerItem("firmament_dust", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> FIRMAMENT_MIXTURE =
            registerItem("firmament_mixture", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> FIRMAMENT_ALLOY_INGOT =
            registerItem("firmament_alloy_ingot", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> FIRMAMENT_ESSENCE =
            registerItem("firmament_essence", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> INACTIVE_FIRMAMENT_SPIRIT_CORE =
            registerItem("inactive_firmament_spirit_core", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> FIRMAMENT_SPIRIT_CORE_OCULUS =
            registerItem("firmament_spirit_core_oculus", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> FIRMAMENT_SPIRIT_CORE_CORE =
            registerItem("firmament_spirit_core_core", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> FIRMAMENT_SPIRIT_CORE_CONDUIT =
            registerItem("firmament_spirit_core_conduit", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> FIRMAMENT_SPIRIT_CORE_STRIDE =
            registerItem("firmament_spirit_core_stride", RisingItem::new, new Item.Properties());

    public static final RegistryObject<RisingItem> FIRMAMENT_SUPERCONDUCTING_WIRE =
            registerItem("firmament_superconducting_wire", RisingItem::new, new Item.Properties());

    public static final RegistryObject<Item> BASIC_TOPOLOGICAL_LATTICE =
            registerSimpleItem("basic_topological_lattice", new Item.Properties());

    public static final RegistryObject<Item> DENSE_TOPOLOGICAL_LATTICE =
            registerSimpleItem("dense_topological_lattice", new Item.Properties());

    public static final RegistryObject<Item> ENTANGLED_TOPOLOGICAL_LATTICE =
            registerSimpleItem("entangled_topological_lattice", new Item.Properties());

    public static final RegistryObject<Item> HYPERDIMENSIONAL_TOPOLOGICAL_LATTICE =
            registerSimpleItem("hyperdimensional_topological_lattice", new Item.Properties());

    public static final RegistryObject<FloatingMatterItem> FLOATING_MATTER = ITEMS.register(
            "floating_matter",
            () -> new FloatingMatterItem(new Item.Properties()));

    public static final RegistryObject<Item> PIGMEE_ITEM_CELL_HOUSING =
            registerSimpleItem("pigmee_item_cell_housing", new Item.Properties());

    public static final RegistryObject<PigmeeCoreItem> PIGMEE_CORE =
            registerItem("pigmee_core", PigmeeCoreItem::new, new Item.Properties().stacksTo(1));

    public static final RegistryObject<Item> PIGMEE_STORAGE_COMPONENT =
            registerSimpleItem("pigmee_storage_component", new Item.Properties());

    public static final RegistryObject<PigmeeStorageCellItem> PIGMEE_STORAGE_CELL =
            ITEMS.register("pigmee_storage_cell", () -> new PigmeeStorageCellItem(new Item.Properties()));

    public static final RegistryObject<BulkLightningStorageCellItem> BULK_LIGHTNING_STORAGE_COMPONENT =
            ITEMS.register(
                    "bulk_lightning_storage_component",
                    () -> new BulkLightningStorageCellItem(new Item.Properties(), 32));

    public static final RegistryObject<Item> BULK_LIGHTNING_CELL_COMPONENT =
            registerSimpleItem("bulk_lightning_cell_component", new Item.Properties());

    public static final RegistryObject<OverloadedFrequencyCardItem> OVERLOADED_FREQUENCY_CARD = registerItem(
            "overloaded_frequency_card",
            OverloadedFrequencyCardItem::new,
            new Item.Properties());

    public static final RegistryObject<OverloadedPatternProviderUpgradeItem> OVERLOADED_PATTERN_PROVIDER_UPGRADE = registerItem(
            "overloaded_pattern_provider_upgrade",
            OverloadedPatternProviderUpgradeItem::new,
            new Item.Properties());

    public static final RegistryObject<ExtendedOverloadedPatternProviderUpgradeItem> EXTENDED_OVERLOADED_PATTERN_PROVIDER_UPGRADE = registerItem(
            "extended_overloaded_pattern_provider_upgrade",
            ExtendedOverloadedPatternProviderUpgradeItem::new,
            new Item.Properties());

    public static final RegistryObject<ClosedLoopPatternItem> CLOSED_LOOP_PATTERN = registerItem(
            "closed_loop_pattern",
            ClosedLoopPatternItem::new,
            new Item.Properties());

    static {
        PartModels.registerModels(PartModelsHelper.createModels(TianshuPatternEncodingTerminalPart.class));
    }

    public static final RegistryObject<PartItem<TianshuPatternEncodingTerminalPart>> TIANSHU_PATTERN_ENCODING_TERMINAL =
            ITEMS.register("tianshu_pattern_encoding_terminal",
                    () -> new PartItem<>(new Item.Properties(),
                            TianshuPatternEncodingTerminalPart.class,
                            TianshuPatternEncodingTerminalPart::new));

    // Always registers the AE2-only base item. The ae2wtlib-aware TianshuWTItem subclass is
    // intentionally NOT referenced here: any bytecode-level reference to the ae2wtlib types would
    // be resolved while this class is being verified, crashing the game when ae2wtlib is absent.
    public static final RegistryObject<TianshuWirelessPatternEncodingTerminalItem>
            TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL = ITEMS.register(
                    "wireless_tianshu_pattern_encoding_terminal",
                    TianshuWirelessPatternEncodingTerminalItem::new);

    // ── Celestweave Armor ──────────────────────────────────────────────────────
    public static final RegistryObject<CelestweaveOculusItem> CELESTWEAVE_OCULUS = registerItem(
            "celestweave_oculus",
            CelestweaveOculusItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<CelestweaveCoreItem> CELESTWEAVE_CORE = registerItem(
            "celestweave_core",
            CelestweaveCoreItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<CelestweaveConduitItem> CELESTWEAVE_CONDUIT = registerItem(
            "celestweave_conduit",
            CelestweaveConduitItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<CelestweaveStrideItem> CELESTWEAVE_STRIDE = registerItem(
            "celestweave_stride",
            CelestweaveStrideItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<NightVisionSubmoduleItem> CELESTWEAVE_SUBMODULE_NIGHT_VISION = registerItem(
            "module_night_vision",
            NightVisionSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<WaterBreathingSubmoduleItem> CELESTWEAVE_SUBMODULE_WATER_BREATHING = registerItem(
            "module_water_breathing",
            WaterBreathingSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<ReachSubmoduleItem> CELESTWEAVE_SUBMODULE_REACH_EXTENSION = registerItem(
            "module_reach_extension",
            ReachSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<ResistanceSubmoduleItem> CELESTWEAVE_SUBMODULE_MATRIX_SHIELD = registerItem(
            "module_matrix_shield",
            properties -> new ResistanceSubmoduleItem(
                    properties,
                    ResistanceSubmodule.T1),
            new Item.Properties());

    public static final RegistryObject<ResistanceSubmoduleItem> CELESTWEAVE_SUBMODULE_PHASE_SHIELD = registerItem(
            "module_phase_shield",
            properties -> new ResistanceSubmoduleItem(
                    properties,
                    ResistanceSubmodule.T2),
            new Item.Properties());

    public static final RegistryObject<ReflectSubmoduleItem> CELESTWEAVE_SUBMODULE_REFLECT = registerItem(
            "module_reflect",
            ReflectSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<UndyingSubmoduleItem> CELESTWEAVE_SUBMODULE_UNDYING = registerItem(
            "module_undying",
            UndyingSubmoduleItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<DashSubmoduleItem> CELESTWEAVE_SUBMODULE_DASH = registerItem(
            "module_dash",
            DashSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<FlightSubmoduleItem> CELESTWEAVE_SUBMODULE_FLIGHT = registerItem(
            "module_creative_flight",
            FlightSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<PurificationSubmoduleItem> CELESTWEAVE_SUBMODULE_PURIFICATION = registerItem(
            "module_purification",
            PurificationSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<MekanismProtectionSubmoduleItem> CELESTWEAVE_SUBMODULE_RADIATION_PROTECTION =
            registerItem(
                    "module_radiation_protection",
                    properties -> new MekanismProtectionSubmoduleItem(
                            properties,
                            MekanismProtectionSubmodule.RADIATION),
                    new Item.Properties());

    public static final RegistryObject<MekanismProtectionSubmoduleItem> CELESTWEAVE_SUBMODULE_LASER_PROTECTION =
            registerItem(
                    "module_laser_protection",
                    properties -> new MekanismProtectionSubmoduleItem(
                            properties,
                            MekanismProtectionSubmodule.LASER),
                    new Item.Properties());

    public static final RegistryObject<SaturationSubmoduleItem> CELESTWEAVE_SUBMODULE_SATURATION = registerItem(
            "module_saturation",
            SaturationSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<DigAffinitySubmoduleItem> CELESTWEAVE_SUBMODULE_DIG_AFFINITY = registerItem(
            "module_dig_affinity",
            DigAffinitySubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<MovementAssistSubmoduleItem> CELESTWEAVE_SUBMODULE_MOVEMENT_ASSIST = registerItem(
            "module_movement_assist",
            MovementAssistSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<PhaseFlightSubmoduleItem> CELESTWEAVE_SUBMODULE_PHASE_FLIGHT = registerItem(
            "module_phase_flight",
            PhaseFlightSubmoduleItem::new,
            new Item.Properties());

    public static final RegistryObject<PhaseLockSubmoduleItem> CELESTWEAVE_SUBMODULE_PHASE_LOCK = registerItem(
            "module_phase_lock",
            PhaseLockSubmoduleItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    /** Generated runtime projection; intentionally omitted from creative tabs and recipes. */
    public static final RegistryObject<PhaseLockProjectionItem> PHASE_LOCK_PROJECTION = registerItem(
            "phase_lock_projection",
            properties -> new PhaseLockProjectionItem(properties, net.minecraft.world.entity.EquipmentSlot.CHEST),
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<PhaseLockProjectionItem> PHASE_LOCK_PROJECTION_HEAD = registerItem(
            "phase_lock_projection_head",
            properties -> new PhaseLockProjectionItem(properties, net.minecraft.world.entity.EquipmentSlot.HEAD),
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<PhaseLockProjectionItem> PHASE_LOCK_PROJECTION_LEGS = registerItem(
            "phase_lock_projection_legs",
            properties -> new PhaseLockProjectionItem(properties, net.minecraft.world.entity.EquipmentSlot.LEGS),
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<PhaseLockProjectionItem> PHASE_LOCK_PROJECTION_FEET = registerItem(
            "phase_lock_projection_feet",
            properties -> new PhaseLockProjectionItem(properties, net.minecraft.world.entity.EquipmentSlot.FEET),
            new Item.Properties().rarity(Rarity.EPIC));

    public static final RegistryObject<ArmorEnergyModuleItem> ENERGY_MODULE_T1 = ITEMS.register(
            "energy_module_t1",
            () -> new ArmorEnergyModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    ArmorEnergyRules.MODULE_T1_CAPACITY_FE,
                    ArmorEnergyRules.MODULE_T1_LEGACY_CAPACITY_FE));

    public static final RegistryObject<ArmorEnergyModuleItem> ENERGY_MODULE_T2 = ITEMS.register(
            "energy_module_t2",
            () -> new ArmorEnergyModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
                    ArmorEnergyRules.MODULE_T2_CAPACITY_FE,
                    ArmorEnergyRules.MODULE_T2_LEGACY_CAPACITY_FE));

    public static final RegistryObject<ArmorEnergyModuleItem> ENERGY_MODULE_T3 = ITEMS.register(
            "energy_module_t3",
            () -> new ArmorEnergyModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(),
                    ArmorEnergyRules.MODULE_T3_CAPACITY_FE,
                    ArmorEnergyRules.MODULE_T3_LEGACY_CAPACITY_FE));

    public static final RegistryObject<Item> OVERLOAD_MODULE_BASE =
            registerSimpleItem("overload_module_base", new Item.Properties());

    // ── Electromagnetic Railgun ────────────────────────────────────────────────
    public static final RegistryObject<ElectromagneticRailgunItem> ELECTROMAGNETIC_RAILGUN = registerItem(
            "electromagnetic_railgun",
            ElectromagneticRailgunItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    public static final RegistryObject<RailgunModuleItem> RAILGUN_MODULE_CORE = ITEMS.register(
            "railgun_module_core",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    RailgunModuleType.CORE));

    public static final RegistryObject<RailgunModuleItem> RAILGUN_MODULE_COMPUTE = ITEMS.register(
            "railgun_module_compute",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    RailgunModuleType.COMPUTE));

    public static final RegistryObject<RailgunModuleItem> RAILGUN_MODULE_ACCELERATION = ITEMS.register(
            "railgun_module_acceleration",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    RailgunModuleType.ACCELERATION));

    public static final RegistryObject<RailgunModuleItem> RAILGUN_MODULE_RANGE = ITEMS.register(
            "railgun_module_range",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    RailgunModuleType.RANGE));

    public static final RegistryObject<RailgunModuleItem> RAILGUN_MODULE_OVERLOAD_EXECUTION = ITEMS.register(
            "railgun_module_overload_execution",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
                    RailgunModuleType.OVERLOAD_EXECUTION));

    public static final RegistryObject<Item> MATTER_WARPING_MATRIX_PATTERN_STORAGE_UPGRADE =
            registerSimpleItem("matter_warping_matrix_pattern_storage_upgrade", new Item.Properties());

    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE =
            registerOverloadedCable("overloaded_cable", AEColor.TRANSPARENT);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_WHITE =
            registerOverloadedCable("overloaded_cable_white", AEColor.WHITE);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_ORANGE =
            registerOverloadedCable("overloaded_cable_orange", AEColor.ORANGE);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_MAGENTA =
            registerOverloadedCable("overloaded_cable_magenta", AEColor.MAGENTA);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIGHT_BLUE =
            registerOverloadedCable("overloaded_cable_light_blue", AEColor.LIGHT_BLUE);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_YELLOW =
            registerOverloadedCable("overloaded_cable_yellow", AEColor.YELLOW);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIME =
            registerOverloadedCable("overloaded_cable_lime", AEColor.LIME);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_PINK =
            registerOverloadedCable("overloaded_cable_pink", AEColor.PINK);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_GRAY =
            registerOverloadedCable("overloaded_cable_gray", AEColor.GRAY);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIGHT_GRAY =
            registerOverloadedCable("overloaded_cable_light_gray", AEColor.LIGHT_GRAY);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_CYAN =
            registerOverloadedCable("overloaded_cable_cyan", AEColor.CYAN);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_PURPLE =
            registerOverloadedCable("overloaded_cable_purple", AEColor.PURPLE);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BLUE =
            registerOverloadedCable("overloaded_cable_blue", AEColor.BLUE);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BROWN =
            registerOverloadedCable("overloaded_cable_brown", AEColor.BROWN);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_GREEN =
            registerOverloadedCable("overloaded_cable_green", AEColor.GREEN);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_RED =
            registerOverloadedCable("overloaded_cable_red", AEColor.RED);
    public static final RegistryObject<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BLACK =
            registerOverloadedCable("overloaded_cable_black", AEColor.BLACK);

    private ModItems() {
    }

    public static void registerStorageCellModels() {
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_I);
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_II);
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_III);
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_IV);
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_V);
        registerStorageCellModel(BULK_LIGHTNING_STORAGE_COMPONENT);
        registerStorageCellModel(INFINITE_STORAGE_CELL);
        registerStorageCellModel(MYSTERIOUS_CELL, "256k_item_cell");
        registerStorageCellModel(PIGMEE_STORAGE_CELL);
    }

    public static ColoredPartItem<OverloadedCablePart> getOverloadedCable(AEColor color) {
        return switch (color) {
            case TRANSPARENT -> OVERLOADED_CABLE.get();
            case WHITE -> OVERLOADED_CABLE_WHITE.get();
            case ORANGE -> OVERLOADED_CABLE_ORANGE.get();
            case MAGENTA -> OVERLOADED_CABLE_MAGENTA.get();
            case LIGHT_BLUE -> OVERLOADED_CABLE_LIGHT_BLUE.get();
            case YELLOW -> OVERLOADED_CABLE_YELLOW.get();
            case LIME -> OVERLOADED_CABLE_LIME.get();
            case PINK -> OVERLOADED_CABLE_PINK.get();
            case GRAY -> OVERLOADED_CABLE_GRAY.get();
            case LIGHT_GRAY -> OVERLOADED_CABLE_LIGHT_GRAY.get();
            case CYAN -> OVERLOADED_CABLE_CYAN.get();
            case PURPLE -> OVERLOADED_CABLE_PURPLE.get();
            case BLUE -> OVERLOADED_CABLE_BLUE.get();
            case BROWN -> OVERLOADED_CABLE_BROWN.get();
            case GREEN -> OVERLOADED_CABLE_GREEN.get();
            case RED -> OVERLOADED_CABLE_RED.get();
            case BLACK -> OVERLOADED_CABLE_BLACK.get();
        };
    }

    private static RegistryObject<LightningStorageComponentItem> registerLightningStorageComponent(
            String id,
            RegistryObject<Item> coreItem,
            int totalBytes,
            double idleDrain) {
        return ITEMS.register(id, () -> new LightningStorageComponentItem(coreItem.get(), totalBytes, idleDrain));
    }

    private static void registerStorageCellModel(RegistryObject<? extends Item> item) {
        StorageCellModels.registerModel(
                item.get(),
                ResourceLocation.fromNamespaceAndPath(
                        AE2LightningTech.MODID,
                        "block/drive/cells/" + item.getId().getPath()));
    }

    private static void registerStorageCellModel(RegistryObject<? extends Item> item, String modelName) {
        StorageCellModels.registerModel(
                item.get(),
                ResourceLocation.fromNamespaceAndPath("ae2", "block/drive/cells/" + modelName));
    }

    private static RegistryObject<ColoredPartItem<OverloadedCablePart>> registerOverloadedCable(String id, AEColor color) {
        return ITEMS.register(
                id,
                () -> new ColoredPartItem<>(
                        new Item.Properties(),
                        OverloadedCablePart.class,
                        OverloadedCablePart::new,
                        color));
    }

    private static RegistryObject<Item> registerSimpleItem(String id, Item.Properties properties) {
        return ITEMS.register(id, () -> new Item(properties));
    }

    private static <T extends Item> RegistryObject<T> registerItem(
            String id,
            Function<Item.Properties, T> factory,
            Item.Properties properties) {
        return ITEMS.register(id, () -> factory.apply(properties));
    }
}

