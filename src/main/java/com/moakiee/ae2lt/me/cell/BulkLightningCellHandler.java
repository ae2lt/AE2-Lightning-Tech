package com.moakiee.ae2lt.me.cell;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.BulkLightningStorageCellItem;

public final class BulkLightningCellHandler implements ICellHandler {
    public static final BulkLightningCellHandler INSTANCE = new BulkLightningCellHandler();

    private BulkLightningCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return stack.getItem() instanceof BulkLightningStorageCellItem;
    }

    @Override
    public @Nullable StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return isCell(stack) ? new BulkLightningCellInventory(stack, host) : null;
    }
}
