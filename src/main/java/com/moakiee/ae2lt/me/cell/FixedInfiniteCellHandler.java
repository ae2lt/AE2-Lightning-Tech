package com.moakiee.ae2lt.me.cell;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.FixedInfiniteCellItem;

/** AE2LT-specific handler for the non-indexed mysterious/fixed infinite cell. */
public final class FixedInfiniteCellHandler implements ICellHandler {
    public static final FixedInfiniteCellHandler INSTANCE = new FixedInfiniteCellHandler();

    private FixedInfiniteCellHandler() {}

    @Override
    public boolean isCell(ItemStack stack) {
        return stack.getItem() instanceof FixedInfiniteCellItem;
    }

    @Override
    public @Nullable StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (!(stack.getItem() instanceof FixedInfiniteCellItem)) return null;
        if (FixedInfiniteCellItem.isOuterCell(stack)) {
            FixedInfiniteCellItem.initializeOuterCell(stack);
        }
        return new FixedInfiniteCellInventory(stack, 32, host);
    }
}
