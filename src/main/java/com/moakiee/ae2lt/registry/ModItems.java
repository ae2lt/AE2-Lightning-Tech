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
import com.moakiee.ae2lt.part.OverloadedCablePart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.api.client.StorageCellModels;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;

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

    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_I =
            registerLightningStorageComponent("lightning_storage_component_i", 256, 32);
    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_II =
            registerLightningStorageComponent("lightning_storage_component_ii", 1024, 128);
    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_III =
            registerLightningStorageComponent("lightning_storage_component_iii", 4096, 512);
    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_IV =
            registerLightningStorageComponent("lightning_storage_component_iv", 16384, 2048);
    public static final RegistryObject<LightningStorageComponentItem> LIGHTNING_STORAGE_COMPONENT_V =
            registerLightningStorageComponent("lightning_storage_component_v", 65536, 8192);

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

    private static RegistryObject<LightningStorageComponentItem> registerLightningStorageComponent(
            String id,
            int totalBytes,
            double idleDrain) {
        return ITEMS.register(id, () -> new LightningStorageComponentItem(totalBytes, idleDrain));
    }

    private static void registerStorageCellModel(RegistryObject<? extends Item> item) {
        StorageCellModels.registerModel(
                item.get(),
                new ResourceLocation(
                        AE2LightningTech.MODID,
                        "block/drive/cells/" + item.getId().getPath()));
    }

    private static void registerStorageCellModel(RegistryObject<? extends Item> item, String modelName) {
        StorageCellModels.registerModel(
                item.get(),
                new ResourceLocation("ae2", "block/drive/cells/" + modelName));
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

