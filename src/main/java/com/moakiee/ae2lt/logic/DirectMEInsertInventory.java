package com.moakiee.ae2lt.logic;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.logic.energy.PowerCostUtil;

/**
 * A virtual inventory that inserts items directly into the ME network.
 * Extraction is blocked — output is handled separately via config-based proxy.
 * Optionally applies a filter predicate to restrict what can be inserted.
 */
public class DirectMEInsertInventory implements GenericInternalInventory {

    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private @Nullable Predicate<AEKey> filter;

    public DirectMEInsertInventory(IManagedGridNode mainNode, IActionSource actionSource) {
        this.mainNode = mainNode;
        this.actionSource = actionSource;
    }

    public void setFilter(@Nullable Predicate<AEKey> filter) {
        this.filter = filter;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public @Nullable GenericStack getStack(int slot) {
        return null;
    }

    @Override
    public @Nullable AEKey getKey(int slot) {
        return null;
    }

    @Override
    public long getAmount(int slot) {
        return 0;
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getCapacity(AEKeyType space) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean canInsert() {
        return true;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean isSupportedType(AEKeyType type) {
        return true;
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey what) {
        return filter == null || filter.test(what);
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (filter != null && !filter.test(what)) return 0;
        var grid = mainNode.getGrid();
        if (grid == null) return 0;
        // EJECT pricing: the machine pushes items itself, so only the
        // network-injection stage (1 AE/op) is charged here. Active import
        // additionally pays 1 AE/op at extraction (2 AE/op total).
        long affordable = PowerCostUtil.maxAffordable(grid, what, amount);
        if (affordable <= 0) return 0;
        long inserted = grid.getStorageService().getInventory().insert(what, affordable, mode, actionSource);
        if (inserted > 0 && mode == Actionable.MODULATE) {
            PowerCostUtil.consume(grid, what, inserted);
        }
        return inserted;
    }

    @Override
    public long extract(int slot, AEKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {}

    @Override
    public void beginBatch() {}

    @Override
    public void endBatch() {}

    @Override
    public void endBatchSuppressed() {}

    @Override
    public void onChange() {}
}
