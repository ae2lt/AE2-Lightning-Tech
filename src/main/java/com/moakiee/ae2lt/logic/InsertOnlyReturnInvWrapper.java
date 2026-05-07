package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

/**
 * A read-empty wrapper around {@link UnlimitedReturnInventory} exposed via
 * the capability system. Automation (pipes, hoppers) sees every slot as empty
 * and can freely push items in; extraction is blocked.
 * <p>
 * AE2's {@code registerGenericAdapters} bridge automatically converts this
 * into {@code IItemHandler} and {@code IFluidHandler}.
 * <p>
 * The real inventory data (for GUI, {@code injectIntoNetwork}, {@code addDrops})
 * is accessed directly through {@code logic.getReturnInv()}, bypassing this wrapper.
 */
public class InsertOnlyReturnInvWrapper implements GenericInternalInventory {

    private final UnlimitedReturnInventory delegate;
    private final OverloadedPatternProviderLogic logic;

    public InsertOnlyReturnInvWrapper(UnlimitedReturnInventory delegate,
                                      OverloadedPatternProviderLogic logic) {
        this.delegate = delegate;
        this.logic = logic;
    }

    @Override
    public int size() {
        return delegate.size();
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
        return delegate.isAllowedIn(slot, what);
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (what == null || amount <= 0) return 0;
        long affordable = logic.maxAffordableExternalReturn(what, amount);
        if (affordable <= 0) return 0;
        long inserted = delegate.insert(slot, what, affordable, mode);
        if (inserted > 0 && mode == Actionable.MODULATE) {
            logic.consumeExternalReturnPower(what, inserted);
        }
        return inserted;
    }

    @Override
    public long extract(int slot, AEKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        delegate.setStack(slot, stack);
    }

    @Override
    public void beginBatch() {
        delegate.beginBatch();
    }

    @Override
    public void endBatch() {
        delegate.endBatch();
    }

    @Override
    public void endBatchSuppressed() {
        delegate.endBatchSuppressed();
    }

    @Override
    public void onChange() {
        delegate.onChange();
    }
}
