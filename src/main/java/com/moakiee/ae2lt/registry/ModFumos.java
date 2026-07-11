package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.item.FumoBlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registers AE2LT fumo decoration blocks.
 * 所有 Fumo 方块均由 AE2LT 自己注册，不再向 ME Placement Tool 让渡 Moakiee/Cystrysu。
 */
public final class ModFumos {

    public static DeferredBlock<FumoBlock> MOAKIEE_FUMO;
    public static DeferredBlock<FumoBlock> CYSTRYSU_FUMO;
    public static DeferredBlock<FumoBlock> PIGMEE_FUMO;
    public static DeferredItem<FumoBlockItem> MOAKIEE_FUMO_ITEM;
    public static DeferredItem<FumoBlockItem> CYSTRYSU_FUMO_ITEM;
    public static DeferredItem<FumoBlockItem> PIGMEE_FUMO_ITEM;

    private ModFumos() {
    }

    public static void register() {
        MOAKIEE_FUMO = ModBlocks.BLOCKS.register("moakiee_fumo", FumoBlock::new);
        MOAKIEE_FUMO_ITEM = ModItems.ITEMS.register("moakiee_fumo",
                () -> new FumoBlockItem(MOAKIEE_FUMO.get(), new Item.Properties()));
        CYSTRYSU_FUMO = ModBlocks.BLOCKS.register("cystrysu_fumo", FumoBlock::new);
        CYSTRYSU_FUMO_ITEM = ModItems.ITEMS.register("cystrysu_fumo",
                () -> new FumoBlockItem(CYSTRYSU_FUMO.get(), new Item.Properties()));

        PIGMEE_FUMO = ModBlocks.BLOCKS.register("pigmee_fumo", FumoBlock::new);
        PIGMEE_FUMO_ITEM = ModItems.ITEMS.register("pigmee_fumo",
                () -> new FumoBlockItem(PIGMEE_FUMO.get(), new Item.Properties(),
                        "tooltip.ae2lt.pigmee_fumo"));
    }
}
