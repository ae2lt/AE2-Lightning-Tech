package com.moakiee.ae2lt.logic;

import appeng.api.stacks.AEKey;

/**
 * Per-slot key filter for the overloaded return inventory.
 * <p>
 * Stand-in for AE2 1.21's {@code AEKeySlotFilter}, which does not exist in
 * AE2 15.4.10 (Forge 1.20.1): that version's GenericStackInv only supports a
 * type-level {@code AEKeyFilter}, so the slot-aware filtering is applied by
 * the return inventory itself.
 */
@FunctionalInterface
interface ReturnSlotFilter {
    boolean isAllowed(int slot, AEKey what);
}
