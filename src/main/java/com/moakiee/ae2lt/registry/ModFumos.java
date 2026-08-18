package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.item.FumoBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers AE2LT fumo decoration blocks.
 * 所有 Fumo 方块均由 AE2LT 自己注册，不再向 ME Placement Tool 让渡 Moakiee/Cystrysu。
 */
public final class ModFumos {

    public static RegistryObject<FumoBlock> MOAKIEE_FUMO;
    public static RegistryObject<FumoBlock> CYSTRYSU_FUMO;
    public static RegistryObject<FumoBlock> PIGMEE_FUMO;
    public static RegistryObject<FumoBlock> CREATIVE_PIGMEE_FUMO;
    public static RegistryObject<FumoBlock> HYPERDIMENSIONAL_PIGMEE_FUMO;
    public static RegistryObject<BlockItem> MOAKIEE_FUMO_ITEM;
    public static RegistryObject<BlockItem> CYSTRYSU_FUMO_ITEM;
    public static RegistryObject<FumoBlockItem> PIGMEE_FUMO_ITEM;
    public static RegistryObject<FumoBlockItem> CREATIVE_PIGMEE_FUMO_ITEM;
    public static RegistryObject<FumoBlockItem> HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM;

    private ModFumos() {
    }

    public static void register() {
        MOAKIEE_FUMO = ModBlocks.BLOCKS.register("moakiee_fumo", FumoBlock::new);
        MOAKIEE_FUMO_ITEM = ModItems.ITEMS.register("moakiee_fumo",
                () -> new BlockItem(MOAKIEE_FUMO.get(), new Item.Properties()));
        CYSTRYSU_FUMO = ModBlocks.BLOCKS.register("cystrysu_fumo", FumoBlock::new);
        CYSTRYSU_FUMO_ITEM = ModItems.ITEMS.register("cystrysu_fumo",
                () -> new BlockItem(CYSTRYSU_FUMO.get(), new Item.Properties()));

        PIGMEE_FUMO = ModBlocks.BLOCKS.register("pigmee_fumo", FumoBlock::new);
        PIGMEE_FUMO_ITEM = ModItems.ITEMS.register("pigmee_fumo",
                () -> new FumoBlockItem(PIGMEE_FUMO.get(), new Item.Properties(),
                        "tooltip.ae2lt.pigmee_fumo"));

        CREATIVE_PIGMEE_FUMO = ModBlocks.BLOCKS.register("creative_pigmee_fumo", FumoBlock::new);
        CREATIVE_PIGMEE_FUMO_ITEM = ModItems.ITEMS.register("creative_pigmee_fumo",
                () -> new FumoBlockItem(
                        CREATIVE_PIGMEE_FUMO.get(),
                        new Item.Properties().rarity(Rarity.EPIC),
                        "tooltip.ae2lt.creative_pigmee_fumo"));

        HYPERDIMENSIONAL_PIGMEE_FUMO =
                ModBlocks.BLOCKS.register("hyperdimensional_pigmee_fumo", FumoBlock::new);
        HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM =
                ModItems.ITEMS.register("hyperdimensional_pigmee_fumo",
                        () -> new FumoBlockItem(
                                HYPERDIMENSIONAL_PIGMEE_FUMO.get(),
                                new Item.Properties().rarity(Rarity.EPIC),
                                "tooltip.ae2lt.hyperdimensional_pigmee_fumo"));
    }
}

