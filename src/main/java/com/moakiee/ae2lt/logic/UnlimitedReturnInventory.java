package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeySlotFilter;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;

/**
 * 18-slot return inventory with unlimited per-type capacity and automatic
 * same-type merging. Replaces the vanilla 9-slot return inventory.
 * <p>
 * Insert-only: extraction is blocked to prevent players from using it
 * as storage. Items leave via {@link #injectIntoNetwork} (AE2 doWork)
 * or {@link #addDrops} (block broken).
 */
public class UnlimitedReturnInventory extends PatternProviderReturnInventory {

    private UnlimitedReturnInventory(Runnable listener) {
        super(listener);
    }

    public static UnlimitedReturnInventory create(Runnable listener,
                                                  @Nullable AEKeySlotFilter filter) {
        var inv = new UnlimitedReturnInventory(listener);
        if (filter != null) {
            inv.setFilter(filter);
        }
        return inv;
    }

    public static UnlimitedReturnInventory create(Runnable listener,
                                                  @Nullable AEKeySlotFilter filter,
                                                  int slots) {
        int saved = PatternProviderReturnInventory.NUMBER_OF_SLOTS;
        PatternProviderReturnInventory.NUMBER_OF_SLOTS = slots;
        var inv = new UnlimitedReturnInventory(listener);
        PatternProviderReturnInventory.NUMBER_OF_SLOTS = saved;
        if (filter != null) {
            inv.setFilter(filter);
        }
        return inv;
    }

    /**
     * Merge-insert: find an existing slot holding the same key and stack onto
     * it; otherwise use the first empty slot. If all 18 type-slots are
     * occupied by different keys, reject the insert.
     */
    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (what == null || amount <= 0) return 0;
        if (!isAllowedIn(slot, what)) return 0;

        for (int i = 0; i < size(); i++) {
            if (what.equals(getKey(i))) {
                if (mode == Actionable.MODULATE) {
                    setStack(i, new GenericStack(what, getAmount(i) + amount));
                }
                return amount;
            }
        }

        for (int i = 0; i < size(); i++) {
            if (getKey(i) == null) {
                if (mode == Actionable.MODULATE) {
                    setStack(i, new GenericStack(what, amount));
                }
                return amount;
            }
        }

        return 0;
    }

    @Override
    public long extract(int slot, AEKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getCapacity(AEKeyType space) {
        return Long.MAX_VALUE;
    }
}
