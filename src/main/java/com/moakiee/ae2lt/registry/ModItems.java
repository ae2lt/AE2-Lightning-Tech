package com.moakiee.ae2lt.registry;

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
import com.moakiee.ae2lt.item.PerfectElectroChimeCrystalItem;
import com.moakiee.ae2lt.item.ResearchNoteItem;
import com.moakiee.ae2lt.item.WeatherCondensateItem;
import com.moakiee.ae2lt.part.OverloadedCablePart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.client.StorageCellModels;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2LightningTech.MODID);

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL = ITEMS.registerItem(
            "overload_crystal",
            OverloadCrystalItem::new);

    public static final DeferredItem<Item> OVERLOAD_CRYSTAL_DUST =
            ITEMS.registerSimpleItem("overload_crystal_dust");

    public static final DeferredItem<Item> UNOVERLOADED_CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("unoverloaded_circuit_board");

    public static final DeferredItem<Item> OVERLOAD_CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("overload_circuit_board");

    public static final DeferredItem<Item> OVERLOAD_PROCESSOR =
            ITEMS.registerSimpleItem("overload_processor");

    public static final DeferredItem<Item> OVERLOAD_INSCRIBER_PRESS =
            ITEMS.registerSimpleItem("overload_inscriber_press");

    public static final DeferredItem<Item> OVERLOAD_ALLOY =
            ITEMS.registerSimpleItem("overload_alloy");

    public static final DeferredItem<Item> OVERLOAD_ALLOY_BLANK =
            ITEMS.registerSimpleItem("overload_alloy_blank");

    public static final DeferredItem<Item> OVERLOAD_ALLOY_PLATE =
            ITEMS.registerSimpleItem("overload_alloy_plate");

    public static final DeferredItem<Item> OVERLOAD_SINGULARITY =
            ITEMS.registerSimpleItem("overload_singularity");

    public static final DeferredItem<Item> ULTIMATE_OVERLOAD_CORE =
            ITEMS.registerSimpleItem("ultimate_overload_core");

    public static final DeferredItem<Item> LIGHTNING_COLLAPSE_MATRIX =
            ITEMS.registerSimpleItem("lightning_collapse_matrix");

    public static final DeferredItem<DebugLightningRodItem> DEBUG_LIGHTNING_ROD = ITEMS.registerItem(
            "debug_lightning_rod",
            DebugLightningRodItem::new,
            properties -> properties.stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<ElectroChimeCrystalItem> ELECTRO_CHIME_CRYSTAL = ITEMS.registerItem(
            "electro_chime_crystal",
            ElectroChimeCrystalItem::new,
            properties -> properties.stacksTo(1));

    public static final DeferredItem<PerfectElectroChimeCrystalItem> PERFECT_ELECTRO_CHIME_CRYSTAL = ITEMS.registerItem(
            "perfect_electro_chime_crystal",
            PerfectElectroChimeCrystalItem::new,
            properties -> properties.stacksTo(1));

    public static final DeferredItem<WeatherCondensateItem> CLEAR_CONDENSATE = ITEMS.registerItem(
            "clear_condensate",
            properties -> new WeatherCondensateItem(WeatherCondensateItem.Type.CLEAR, properties),
            properties -> properties.stacksTo(1));

    public static final DeferredItem<WeatherCondensateItem> RAIN_CONDENSATE = ITEMS.registerItem(
            "rain_condensate",
            properties -> new WeatherCondensateItem(WeatherCondensateItem.Type.RAIN, properties),
            properties -> properties.stacksTo(1));

    public static final DeferredItem<WeatherCondensateItem> THUNDERSTORM_CONDENSATE = ITEMS.registerItem(
            "thunderstorm_condensate",
            properties -> new WeatherCondensateItem(WeatherCondensateItem.Type.THUNDERSTORM, properties),
            properties -> properties.stacksTo(1));

    public static final DeferredItem<Item> LIGHTNING_ITEM_CELL_HOUSING =
            ITEMS.registerSimpleItem("lightning_item_cell_housing");

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

    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_I =
            ITEMS.registerSimpleItem("lightning_cell_component_i");
    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_II =
            ITEMS.registerSimpleItem("lightning_cell_component_ii");
    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_III =
            ITEMS.registerSimpleItem("lightning_cell_component_iii");
    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_IV =
            ITEMS.registerSimpleItem("lightning_cell_component_iv");
    public static final DeferredItem<Item> LIGHTNING_CELL_COMPONENT_V =
            ITEMS.registerSimpleItem("lightning_cell_component_v");

    public static final DeferredItem<InfiniteStorageCellItem> INFINITE_STORAGE_CELL =
            ITEMS.registerItem("infinite_storage_cell",
                    properties -> new InfiniteStorageCellItem(
                            properties,
                            Long.MAX_VALUE, Long.MAX_VALUE,
                            8, Integer.MAX_VALUE,
                            32));

    /** Easter egg cell: behaviour determined by NBT (CellType / CellSeed). */
    public static final DeferredItem<FixedInfiniteCellItem> MYSTERIOUS_CELL =
            ITEMS.registerItem("mysterious_cell", FixedInfiniteCellItem::new);

    public static final DeferredItem<ResearchNoteItem> RESEARCH_NOTE =
            ITEMS.registerItem("research_note", ResearchNoteItem::new, properties -> properties.stacksTo(16));

    public static final DeferredItem<Item> CHARRED_RITUAL_FRAGMENT =
            ITEMS.registerSimpleItem("charred_ritual_fragment");

    public static final DeferredItem<Item> OVERLOAD_PATTERN = ITEMS.registerItem(
            "overload_pattern",
            OverloadPatternItem::new);

    public static final DeferredItem<Item> OVERLOAD_PATTERN_ENCODER = ITEMS.registerItem(
            "overload_pattern_encoder",
            OverloadPatternEncoderItem::new);

    public static final DeferredItem<Item> OVERLOADED_FILTER_COMPONENT = ITEMS.registerItem(
            "overloaded_filter_component",
            OverloadedFilterComponentItem::new,
            properties -> properties.stacksTo(1));

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
        return ITEMS.registerItem(
                id,
                properties -> new LightningStorageComponentItem(properties, totalBytes, idleDrain));
    }

    private static void registerStorageCellModel(DeferredItem<? extends Item> item) {
        StorageCellModels.registerModel(
                item.get(),
                Identifier.fromNamespaceAndPath(
                        AE2LightningTech.MODID,
                        "block/drive/cells/" + item.getId().getPath()));
    }

    private static void registerStorageCellModel(DeferredItem<? extends Item> item, String modelName) {
        StorageCellModels.registerModel(
                item.get(),
                Identifier.fromNamespaceAndPath("ae2", "block/drive_" + modelName));
    }

    private static DeferredItem<ColoredPartItem<OverloadedCablePart>> registerOverloadedCable(String id, AEColor color) {
        return ITEMS.registerItem(
                id,
                properties -> new ColoredPartItem<>(
                        properties,
                        OverloadedCablePart.class,
                        OverloadedCablePart::new,
                        color));
    }
}
