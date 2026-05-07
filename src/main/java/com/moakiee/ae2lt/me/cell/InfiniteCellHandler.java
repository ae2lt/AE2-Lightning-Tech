package com.moakiee.ae2lt.me.cell;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.item.InfiniteStorageCellItem;

public final class InfiniteCellHandler implements ICellHandler {

    public static final InfiniteCellHandler INSTANCE = new InfiniteCellHandler();

    private InfiniteCellHandler() {}

    @Override
    public boolean isCell(ItemStack is) {
        Item item = is.getItem();
        return item instanceof InfiniteStorageCellItem || item instanceof FixedInfiniteCellItem;
    }

    @Override
    public @Nullable StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        if (is.getItem() instanceof InfiniteStorageCellItem cell) {
            return InfiniteCellInventory.create(
                    is, null, host,
                    cell.getBytesPerType(), cell.getMaxTypes(),
                    cell.getCapacityLo(), cell.getCapacityHi(),
                    cell.getIdleDrain());
        }
        if (is.getItem() instanceof FixedInfiniteCellItem) {
            if (FixedInfiniteCellItem.isOuterCell(is)) {
                FixedInfiniteCellItem.initializeOuterCell(is);
            }
            return new FixedInfiniteCellInventory(is, 32, host);
        }
        return null;
    }
}
