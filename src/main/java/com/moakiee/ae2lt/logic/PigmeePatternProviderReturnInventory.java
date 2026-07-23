package com.moakiee.ae2lt.logic;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PigmeePatternProviderReturnInventory extends GenericStackInv {
    public static final int SLOT_COUNT = 9;

    private boolean draining;

    public PigmeePatternProviderReturnInventory(Runnable listener) {
        super(listener, SLOT_COUNT);
        useRegisteredCapacities();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canInsert() {
        return !draining;
    }

    public boolean drainInto(MEStorage storage, IActionSource source) {
        boolean changed = false;
        draining = true;
        try {
            for (int slot = 0; slot < size(); slot++) {
                var stack = getStack(slot);
                if (stack == null) {
                    continue;
                }
                long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
                if (inserted <= 0) {
                    continue;
                }
                long remaining = stack.amount() - inserted;
                setStack(slot, remaining > 0 ? new GenericStack(stack.what(), remaining) : null);
                changed = true;
            }
        } finally {
            draining = false;
        }
        return changed;
    }

    public void addDrops(List<ItemStack> drops, Level level, BlockPos pos) {
        for (int slot = 0; slot < size(); slot++) {
            var stack = getStack(slot);
            if (stack != null) {
                stack.what().addDrops(stack.amount(), drops, level, pos);
            }
        }
    }
}
