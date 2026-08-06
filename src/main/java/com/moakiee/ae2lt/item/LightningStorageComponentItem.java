package com.moakiee.ae2lt.item;

import appeng.items.storage.BasicStorageCell;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.me.key.LightningKeyType;

public final class LightningStorageComponentItem extends BasicStorageCell {
    private static final int BYTES_PER_TYPE = 8;
    private static final int TOTAL_TYPES = 2;
    private final int usableCapacity;
    private final int totalBytes;

    public LightningStorageComponentItem(int usableCapacity, double idleDrain) {
        super(
                new Properties().stacksTo(1),
                idleDrain,
                1,
                BYTES_PER_TYPE,
                TOTAL_TYPES,
                LightningKeyType.INSTANCE);
        this.usableCapacity = usableCapacity;
        if ((usableCapacity & 7) != 0) {
            throw new IllegalArgumentException(
                    "Lightning storage component capacity must be a multiple of 8: " + usableCapacity);
        }
        // AE2 basic cells consume bytes for each stored type before any stack amount can be inserted.
        // Treat the configured tier values as the planned effective lightning capacity and reserve
        // the two type-overhead buckets up front so a cell that stores both voltage tiers still lands
        // on the intended 64 / 256 / 1024 / 4096 / 16384 capacity progression.
        this.totalBytes = usableCapacity + BYTES_PER_TYPE * TOTAL_TYPES;
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return this.totalBytes;
    }

    public int getUsableCapacity() {
        return usableCapacity;
    }
}
