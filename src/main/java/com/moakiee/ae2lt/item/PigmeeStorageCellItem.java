package com.moakiee.ae2lt.item;

import appeng.api.stacks.AEKeyType;
import appeng.items.storage.BasicStorageCell;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.registry.ModItems;

/**
 * A tiny Pigmee-themed item storage cell.
 *
 * <p>It follows AE2's 1k item cell rules, but exposes only 256 total bytes and
 * sixteen item types.</p>
 */
public final class PigmeeStorageCellItem extends BasicStorageCell {
    public static final int TOTAL_BYTES = 256;
    public static final int BYTES_PER_TYPE = 8;
    public static final int TOTAL_TYPES = 16;
    public static final double IDLE_DRAIN = 0.5;

    public PigmeeStorageCellItem(Properties properties) {
        // BasicStorageCell accepts capacity in KiB, so use its smallest valid value and
        // override getBytes below to expose the intended 256-byte capacity. The core/housing
        // pair is required by 1.20.1 and drives the built-in disassembly (the 1.21 JSON
        // recipe type ae2:storage_cell_disassembly does not exist here).
        super(
                properties.stacksTo(1),
                ModItems.PIGMEE_STORAGE_COMPONENT.get(),
                ModItems.PIGMEE_ITEM_CELL_HOUSING.get(),
                IDLE_DRAIN,
                1,
                BYTES_PER_TYPE,
                TOTAL_TYPES,
                AEKeyType.items());
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return TOTAL_BYTES;
    }
}
