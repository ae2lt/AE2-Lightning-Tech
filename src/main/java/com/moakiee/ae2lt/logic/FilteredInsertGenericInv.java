package com.moakiee.ae2lt.logic;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

/**
 * Capability-boundary wrapper for the Overloaded ME Interface's proxied storage.
 * Applies the filter component to passive insertions (pipes, eject-mode
 * forwarding) while leaving display, extraction and internal paths
 * (crafting returns, GUI clicks) untouched — those use the delegate directly.
 */
public class FilteredInsertGenericInv implements GenericInternalInventory {

    private final GenericInternalInventory delegate;
    private final Predicate<AEKey> insertAllowed;

    public FilteredInsertGenericInv(GenericInternalInventory delegate,
                                    Predicate<AEKey> insertAllowed) {
        this.delegate = delegate;
        this.insertAllowed = insertAllowed;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public @Nullable GenericStack getStack(int slot) {
        return delegate.getStack(slot);
    }

    @Override
    public @Nullable AEKey getKey(int slot) {
        return delegate.getKey(slot);
    }

    @Override
    public long getAmount(int slot) {
        return delegate.getAmount(slot);
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return delegate.getMaxAmount(key);
    }

    @Override
    public long getCapacity(AEKeyType keyType) {
        return delegate.getCapacity(keyType);
    }

    @Override
    public boolean canInsert() {
        return delegate.canInsert();
    }

    @Override
    public boolean canExtract() {
        return delegate.canExtract();
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack newStack) {
        delegate.setStack(slot, newStack);
    }

    @Override
    public boolean isSupportedType(AEKeyType type) {
        return delegate.isSupportedType(type);
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey what) {
        return insertAllowed.test(what) && delegate.isAllowedIn(slot, what);
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (what == null || !insertAllowed.test(what)) return 0;
        return delegate.insert(slot, what, amount, mode);
    }

    @Override
    public long extract(int slot, AEKey what, long amount, Actionable mode) {
        return delegate.extract(slot, what, amount, mode);
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
