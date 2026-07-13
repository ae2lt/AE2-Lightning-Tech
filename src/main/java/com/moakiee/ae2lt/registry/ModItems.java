package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.BulkLightningStorageCellItem;
import com.moakiee.ae2lt.item.DebugLightningRodItem;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.item.FloatingMatterItem;
import com.moakiee.ae2lt.item.InfiniteStorageCellItem;
import com.moakiee.ae2lt.item.LightningStorageComponentItem;
import com.moakiee.ae2lt.item.LightningCollapseMatrixItem;
import com.moakiee.ae2lt.item.RisingItem;
import com.moakiee.ae2lt.item.CelestweaveConduitItem;
import com.moakiee.ae2lt.item.CelestweaveCoreItem;
import com.moakiee.ae2lt.item.CelestweaveOculusItem;
import com.moakiee.ae2lt.item.CelestweaveStrideItem;
import com.moakiee.ae2lt.item.OverloadCrystalItem;
import com.moakiee.ae2lt.item.OverloadPatternEncoderItem;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.OverloadedPatternProviderUpgradeItem;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.item.NightVisionSubmoduleItem;
import com.moakiee.ae2lt.item.WaterBreathingSubmoduleItem;
import com.moakiee.ae2lt.item.ResistanceSubmoduleItem;
import com.moakiee.ae2lt.item.ReflectSubmoduleItem;
import com.moakiee.ae2lt.item.UndyingSubmoduleItem;
import com.moakiee.ae2lt.item.DashSubmoduleItem;
import com.moakiee.ae2lt.item.FlightSubmoduleItem;
import com.moakiee.ae2lt.item.PurificationSubmoduleItem;
import com.moakiee.ae2lt.item.SaturationSubmoduleItem;
import com.moakiee.ae2lt.item.DigAffinitySubmoduleItem;
import com.moakiee.ae2lt.item.ReachSubmoduleItem;
import com.moakiee.ae2lt.item.PhaseFlightSubmoduleItem;
import com.moakiee.ae2lt.item.PerfectElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.ResearchNoteItem;
import com.moakiee.ae2lt.item.WeatherCondensateItem;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;
import com.moakiee.ae2lt.celestweave.ArmorEnergyModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorEnergyRules;
import com.moakiee.ae2lt.celestweave.module.ResistanceSubmodule;
import com.moakiee.ae2lt.part.OverloadedCablePart;
import com.moakiee.ae2lt.part.TianshuPatternEncodingTerminalPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.client.StorageCellModels;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import appeng.items.parts.PartItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2LightningTech.MODID);

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL = ITEMS.registerItem(
            "overload_crystal",
            OverloadCrystalItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL_DUST =
            ITEMS.registerSimpleItem("overload_crystal_dust", new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_DUST =
            ITEMS.registerItem("firmament_dust", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_MIXTURE =
            ITEMS.registerItem("firmament_mixture", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_ALLOY_INGOT =
            ITEMS.registerItem("firmament_alloy_ingot", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_ESSENCE =
            ITEMS.registerItem("firmament_essence", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> INACTIVE_FIRMAMENT_SPIRIT_CORE =
            ITEMS.registerItem("inactive_firmament_spirit_core", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_SPIRIT_CORE_OCULUS =
            ITEMS.registerItem("firmament_spirit_core_oculus", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_SPIRIT_CORE_CORE =
            ITEMS.registerItem("firmament_spirit_core_core", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_SPIRIT_CORE_CONDUIT =
            ITEMS.registerItem("firmament_spirit_core_conduit", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_SPIRIT_CORE_STRIDE =
            ITEMS.registerItem("firmament_spirit_core_stride", RisingItem::new, new Item.Properties());

    public static final DeferredItem<RisingItem> FIRMAMENT_SUPERCONDUCTING_WIRE =
            ITEMS.registerItem("firmament_superconducting_wire", RisingItem::new, new Item.Properties());

    public static final DeferredItem<Item> UNOVERLOADED_CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("unoverloaded_circuit_board", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("overload_circuit_board", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_PROCESSOR =
            ITEMS.registerSimpleItem("overload_processor", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_INSCRIBER_PRESS =
            ITEMS.registerSimpleItem("overload_inscriber_press", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_ALLOY =
            ITEMS.registerSimpleItem("overload_alloy", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_ALLOY_BLANK =
            ITEMS.registerSimpleItem("overload_alloy_blank", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_ALLOY_PLATE =
            ITEMS.registerSimpleItem("overload_alloy_plate", new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_SINGULARITY =
            ITEMS.registerSimpleItem("overload_singularity", new Item.Properties());

    public static final DeferredItem<Item> ULTIMATE_OVERLOAD_CORE =
            ITEMS.registerSimpleItem("ultimate_overload_core", new Item.Properties());

    public static final DeferredItem<LightningCollapseMatrixItem> LIGHTNING_COLLAPSE_MATRIX = ITEMS.registerItem(
            "lightning_collapse_matrix",
            LightningCollapseMatrixItem::new,
            new Item.Properties());

    public static final DeferredItem<FloatingMatterItem> FLOATING_MATTER = ITEMS.registerItem(
            "floating_matter",
            FloatingMatterItem::new,
            new Item.Properties());

    public static final DeferredItem<DebugLightningRodItem> DEBUG_LIGHTNING_ROD = ITEMS.registerItem(
            "debug_lightning_rod",
            DebugLightningRodItem::new,
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<ElectroChimeCrystalItem> ELECTRO_CHIME_CRYSTAL = ITEMS.registerItem(
            "electro_chime_crystal",
            ElectroChimeCrystalItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<PerfectElectroChimeCrystalItem> PERFECT_ELECTRO_CHIME_CRYSTAL = ITEMS.registerItem(
            "perfect_electro_chime_crystal",
            PerfectElectroChimeCrystalItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<WeatherCondensateItem> CLEAR_CONDENSATE = ITEMS.register(
            "clear_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.CLEAR, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<WeatherCondensateItem> RAIN_CONDENSATE = ITEMS.register(
            "rain_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.RAIN, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<WeatherCondensateItem> THUNDERSTORM_CONDENSATE = ITEMS.register(
            "thunderstorm_condensate",
            () -> new WeatherCondensateItem(WeatherCondensateItem.Type.THUNDERSTORM, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> LIGHTNING_ITEM_CELL_HOUSING =
            ITEMS.registerSimpleItem("lightning_item_cell_housing", new Item.Properties());

    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_I =
            registerLightningStorageComponent("lightning_storage_component_i", 256, 32);
    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_II =
            registerLightningStorageComponent("lightning_storage_component_ii", 1024, 128);
    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_III =
            registerLightningStorageComponent("lightning_storage_component_iii", 4096, 512);
    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_IV =
            registerLightningStorageComponent("lightning_storage_component_iv", 16384, 2048);
    public static final DeferredItem<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_V =
            registerLightningStorageComponent("lightning_storage_component_v", 65536, 8192);

    public static final DeferredItem<BulkLightningStorageCellItem> BULK_LIGHTNING_STORAGE_COMPONENT =
            ITEMS.register(
                    "bulk_lightning_storage_component",
                    () -> new BulkLightningStorageCellItem(new Item.Properties(), 32));

    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_I =
            ITEMS.registerSimpleItem("lightning_cell_component_i", new Item.Properties());
    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_II =
            ITEMS.registerSimpleItem("lightning_cell_component_ii", new Item.Properties());
    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_III =
            ITEMS.registerSimpleItem("lightning_cell_component_iii", new Item.Properties());
    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_IV =
            ITEMS.registerSimpleItem("lightning_cell_component_iv", new Item.Properties());
    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_V =
            ITEMS.registerSimpleItem("lightning_cell_component_v", new Item.Properties());

    public static final DeferredItem<InfiniteStorageCellItem> INFINITE_STORAGE_CELL =
            ITEMS.register("infinite_storage_cell",
                    () -> new InfiniteStorageCellItem(
                            new Item.Properties(),
                            Long.MAX_VALUE, Long.MAX_VALUE,
                            8, Integer.MAX_VALUE,
                            32));

    /** Easter egg cell: behaviour determined by NBT (CellType / CellSeed). */
    public static final DeferredItem<FixedInfiniteCellItem> MYSTERIOUS_CELL =
            ITEMS.register("mysterious_cell",
                    () -> new FixedInfiniteCellItem(new Item.Properties()));

    public static final DeferredItem<ResearchNoteItem> RESEARCH_NOTE =
            ITEMS.registerItem("research_note", ResearchNoteItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<Item> CHARRED_RITUAL_FRAGMENT =
            ITEMS.registerSimpleItem("charred_ritual_fragment", new Item.Properties());

    public static final DeferredItem<Item> OVERLOADED_WIRELESS_CONNECT_TOOL = ITEMS.registerItem(
            "overloaded_wireless_connect_tool",
            OverloadedWirelessConnectorItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOADED_FREQUENCY_CARD = ITEMS.registerItem(
            "overloaded_frequency_card",
            OverloadedFrequencyCardItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOADED_PATTERN_PROVIDER_UPGRADE = ITEMS.registerItem(
            "overloaded_pattern_provider_upgrade",
            OverloadedPatternProviderUpgradeItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_PATTERN = ITEMS.registerItem(
            "overload_pattern",
            OverloadPatternItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> CLOSED_LOOP_PATTERN = ITEMS.registerItem(
            "closed_loop_pattern",
            ClosedLoopPatternItem::new,
            new Item.Properties());

    public static final DeferredItem<Item> OVERLOAD_PATTERN_ENCODER = ITEMS.registerItem(
            "overload_pattern_encoder",
            OverloadPatternEncoderItem::new,
            new Item.Properties());

    public static final DeferredItem<PartItem<TianshuPatternEncodingTerminalPart>> TIANSHU_PATTERN_ENCODING_TERMINAL =
            ITEMS.register("tianshu_pattern_encoding_terminal",
                    () -> new PartItem<>(new Item.Properties(),
                            TianshuPatternEncodingTerminalPart.class,
                            TianshuPatternEncodingTerminalPart::new));

    public static final DeferredItem<Item> TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL =
            net.neoforged.fml.ModList.get().isLoaded("ae2wtlib")
                    ? com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration.registerTerminalItem(ITEMS)
                    : null;

    public static final DeferredItem<Item> OVERLOADED_FILTER_COMPONENT = ITEMS.registerItem(
            "overloaded_filter_component",
            OverloadedFilterComponentItem::new,
            new Item.Properties().stacksTo(1));

    // ── Celestweave Armor ──────────────────────────────────────────────────────
    public static final DeferredItem<CelestweaveOculusItem> CELESTWEAVE_OCULUS = ITEMS.registerItem(
            "celestweave_oculus",
            CelestweaveOculusItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredItem<CelestweaveCoreItem> CELESTWEAVE_CORE = ITEMS.registerItem(
            "celestweave_core",
            CelestweaveCoreItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredItem<CelestweaveConduitItem> CELESTWEAVE_CONDUIT = ITEMS.registerItem(
            "celestweave_conduit",
            CelestweaveConduitItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredItem<CelestweaveStrideItem> CELESTWEAVE_STRIDE = ITEMS.registerItem(
            "celestweave_stride",
            CelestweaveStrideItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredItem<NightVisionSubmoduleItem> CELESTWEAVE_SUBMODULE_NIGHT_VISION = ITEMS.registerItem(
            "module_night_vision",
            NightVisionSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<WaterBreathingSubmoduleItem> CELESTWEAVE_SUBMODULE_WATER_BREATHING = ITEMS.registerItem(
            "module_water_breathing",
            WaterBreathingSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<ReachSubmoduleItem> CELESTWEAVE_SUBMODULE_REACH_EXTENSION = ITEMS.registerItem(
            "module_reach_extension",
            ReachSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<ResistanceSubmoduleItem> CELESTWEAVE_SUBMODULE_MATRIX_SHIELD = ITEMS.registerItem(
            "module_matrix_shield",
            properties -> new ResistanceSubmoduleItem(
                    properties,
                    ResistanceSubmodule.T1),
            new Item.Properties());

    public static final DeferredItem<ResistanceSubmoduleItem> CELESTWEAVE_SUBMODULE_PHASE_SHIELD = ITEMS.registerItem(
            "module_phase_shield",
            properties -> new ResistanceSubmoduleItem(
                    properties,
                    ResistanceSubmodule.T2),
            new Item.Properties());

    public static final DeferredItem<ReflectSubmoduleItem> CELESTWEAVE_SUBMODULE_REFLECT = ITEMS.registerItem(
            "module_reflect",
            ReflectSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<UndyingSubmoduleItem> CELESTWEAVE_SUBMODULE_UNDYING = ITEMS.registerItem(
            "module_undying",
            UndyingSubmoduleItem::new,
            new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredItem<DashSubmoduleItem> CELESTWEAVE_SUBMODULE_DASH = ITEMS.registerItem(
            "module_dash",
            DashSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<FlightSubmoduleItem> CELESTWEAVE_SUBMODULE_FLIGHT = ITEMS.registerItem(
            "module_creative_flight",
            FlightSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<PurificationSubmoduleItem> CELESTWEAVE_SUBMODULE_PURIFICATION = ITEMS.registerItem(
            "module_purification",
            PurificationSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<SaturationSubmoduleItem> CELESTWEAVE_SUBMODULE_SATURATION = ITEMS.registerItem(
            "module_saturation",
            SaturationSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<DigAffinitySubmoduleItem> CELESTWEAVE_SUBMODULE_DIG_AFFINITY = ITEMS.registerItem(
            "module_dig_affinity",
            DigAffinitySubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<PhaseFlightSubmoduleItem> CELESTWEAVE_SUBMODULE_PHASE_FLIGHT = ITEMS.registerItem(
            "module_phase_flight",
            PhaseFlightSubmoduleItem::new,
            new Item.Properties());

    public static final DeferredItem<ArmorEnergyModuleItem> ENERGY_MODULE_T1 = ITEMS.register(
            "energy_module_t1",
            () -> new ArmorEnergyModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    ArmorEnergyRules.MODULE_T1_CAPACITY_FE,
                    ArmorEnergyRules.MODULE_T1_LEGACY_CAPACITY_FE));

    public static final DeferredItem<ArmorEnergyModuleItem> ENERGY_MODULE_T2 = ITEMS.register(
            "energy_module_t2",
            () -> new ArmorEnergyModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
                    ArmorEnergyRules.MODULE_T2_CAPACITY_FE,
                    ArmorEnergyRules.MODULE_T2_LEGACY_CAPACITY_FE));

    public static final DeferredItem<ArmorEnergyModuleItem> ENERGY_MODULE_T3 = ITEMS.register(
            "energy_module_t3",
            () -> new ArmorEnergyModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(),
                    ArmorEnergyRules.MODULE_T3_CAPACITY_FE,
                    ArmorEnergyRules.MODULE_T3_LEGACY_CAPACITY_FE));

    public static final DeferredItem<Item> OVERLOAD_MODULE_BASE =
            ITEMS.registerSimpleItem("overload_module_base", new Item.Properties());

    // ── Electromagnetic Railgun (终末期 BiS 武器) ─────────────────────────────
    public static final DeferredItem<ElectromagneticRailgunItem> ELECTROMAGNETIC_RAILGUN = ITEMS.registerItem(
            "electromagnetic_railgun",
            ElectromagneticRailgunItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    public static final DeferredItem<RailgunModuleItem> RAILGUN_MODULE_CORE = ITEMS.register(
            "railgun_module_core",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    RailgunModuleType.CORE));

    public static final DeferredItem<RailgunModuleItem> RAILGUN_MODULE_COMPUTE = ITEMS.register(
            "railgun_module_compute",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    RailgunModuleType.COMPUTE));

    public static final DeferredItem<RailgunModuleItem> RAILGUN_MODULE_ACCELERATION = ITEMS.register(
            "railgun_module_acceleration",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    RailgunModuleType.ACCELERATION));

    public static final DeferredItem<RailgunModuleItem> RAILGUN_MODULE_OVERLOAD_EXECUTION = ITEMS.register(
            "railgun_module_overload_execution",
            () -> new RailgunModuleItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.EPIC),
                    RailgunModuleType.OVERLOAD_EXECUTION));

    public static final DeferredItem<Item> MATTER_WARPING_MATRIX_PATTERN_STORAGE_UPGRADE =
            ITEMS.registerSimpleItem("matter_warping_matrix_pattern_storage_upgrade", new Item.Properties());

    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE =
            registerOverloadedCable("overloaded_cable", AEColor.TRANSPARENT);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_WHITE =
            registerOverloadedCable("overloaded_cable_white", AEColor.WHITE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_ORANGE =
            registerOverloadedCable("overloaded_cable_orange", AEColor.ORANGE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_MAGENTA =
            registerOverloadedCable("overloaded_cable_magenta", AEColor.MAGENTA);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIGHT_BLUE =
            registerOverloadedCable("overloaded_cable_light_blue", AEColor.LIGHT_BLUE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_YELLOW =
            registerOverloadedCable("overloaded_cable_yellow", AEColor.YELLOW);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIME =
            registerOverloadedCable("overloaded_cable_lime", AEColor.LIME);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_PINK =
            registerOverloadedCable("overloaded_cable_pink", AEColor.PINK);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_GRAY =
            registerOverloadedCable("overloaded_cable_gray", AEColor.GRAY);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_LIGHT_GRAY =
            registerOverloadedCable("overloaded_cable_light_gray", AEColor.LIGHT_GRAY);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_CYAN =
            registerOverloadedCable("overloaded_cable_cyan", AEColor.CYAN);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_PURPLE =
            registerOverloadedCable("overloaded_cable_purple", AEColor.PURPLE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BLUE =
            registerOverloadedCable("overloaded_cable_blue", AEColor.BLUE);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BROWN =
            registerOverloadedCable("overloaded_cable_brown", AEColor.BROWN);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_GREEN =
            registerOverloadedCable("overloaded_cable_green", AEColor.GREEN);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_RED =
            registerOverloadedCable("overloaded_cable_red", AEColor.RED);
    public static final DeferredItem<ColoredPartItem<OverloadedCablePart>> OVERLOADED_CABLE_BLACK =
            registerOverloadedCable("overloaded_cable_black", AEColor.BLACK);

    private ModItems() {
    }

    public static void registerStorageCellModels() {
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_I);
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_II);
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_III);
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_IV);
        registerStorageCellModel(LIGHTNING_STORAGE_COMPONENT_V);
        registerStorageCellModel(INFINITE_STORAGE_CELL);
        registerStorageCellModel(MYSTERIOUS_CELL, "256k_item_cell");
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

    private static DeferredItem<LightningStorageComponentItem> registerLightningStorageComponent(
            String id,
            int totalBytes,
            double idleDrain) {
        return ITEMS.register(id, () -> new LightningStorageComponentItem(totalBytes, idleDrain));
    }

    private static void registerStorageCellModel(DeferredItem<? extends Item> item) {
        StorageCellModels.registerModel(
                item.get(),
                ResourceLocation.fromNamespaceAndPath(
                        AE2LightningTech.MODID,
                        "block/drive/cells/" + item.getId().getPath()));
    }

    private static void registerStorageCellModel(DeferredItem<? extends Item> item, String modelName) {
        StorageCellModels.registerModel(
                item.get(),
                ResourceLocation.fromNamespaceAndPath("ae2", "block/drive/cells/" + modelName));
    }

    private static DeferredItem<ColoredPartItem<OverloadedCablePart>> registerOverloadedCable(String id, AEColor color) {
        return ITEMS.register(
                id,
                () -> new ColoredPartItem<>(
                        new Item.Properties(),
                        OverloadedCablePart.class,
                        OverloadedCablePart::new,
                        color));
    }
}
