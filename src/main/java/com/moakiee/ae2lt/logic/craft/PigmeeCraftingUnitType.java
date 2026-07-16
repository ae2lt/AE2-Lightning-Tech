package com.moakiee.ae2lt.logic.craft;

import appeng.block.crafting.ICraftingUnitType;

import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.world.item.Item;

/** Vanilla AE2 crafting-unit parameters for the Pigmee mental arithmetic unit. */
public enum PigmeeCraftingUnitType implements ICraftingUnitType {
    INSTANCE;

    public static final long STORAGE_BYTES = 256L;

    @Override
    public long getStorageBytes() {
        return STORAGE_BYTES;
    }

    @Override
    public int getAcceleratorThreads() {
        // Vanilla CraftingCpuLogic always adds one base operation to this value.
        return 0;
    }

    @Override
    public Item getItemFromType() {
        return ModBlocks.PIGMEE_MENTALMATH_UNIT.get().asItem();
    }
}
