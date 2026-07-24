package com.moakiee.ae2lt.item;

import appeng.api.stacks.AEKeyType;
import appeng.items.storage.BasicStorageCell;
import net.minecraft.world.item.ItemStack;

/**
 * A tiny Pigmee-themed item storage cell.
 *
 * <p>It follows AE2's 1k item cell rules, but exposes only 256 total bytes and one
 * item type.</p>
 */
public final class PigmeeStorageCellItem extends BasicStorageCell {
    public static final int TOTAL_BYTES = 256;
    public static final int BYTES_PER_TYPE = 8;
    public static final int TOTAL_TYPES = 1;
    public static final double IDLE_DRAIN = 0.5;

    public PigmeeStorageCellItem(Properties properties) {
        // BasicStorageCell accepts capacity in KiB, so use its smallest valid value and
        // override getBytes below to expose the intended 256-byte capacity.
        super(
                properties.stacksTo(1),
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
