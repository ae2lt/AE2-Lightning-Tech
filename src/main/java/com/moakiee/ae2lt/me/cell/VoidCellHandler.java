package com.moakiee.ae2lt.me.cell;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;

import com.moakiee.ae2lt.item.VoidStorageCellItem;

public final class VoidCellHandler implements ICellHandler {
    public static final VoidCellHandler INSTANCE = new VoidCellHandler();

    private VoidCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return stack.getItem() instanceof VoidStorageCellItem;
    }

    @Override
    public @Nullable StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return isCell(stack) ? new VoidCellInventory(stack, host) : null;
    }
}
