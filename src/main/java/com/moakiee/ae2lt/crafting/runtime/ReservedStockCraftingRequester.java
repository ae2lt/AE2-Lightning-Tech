package com.moakiee.ae2lt.crafting.runtime;

import appeng.api.stacks.AEKey;
import java.util.Map;

import com.moakiee.thunderbolt.core.crafting.support.CraftingStockPolicy;

/** Optional calculation context limiting how much pre-existing network stock a plan may consume. */
public interface ReservedStockCraftingRequester extends CraftingStockPolicy {
    long usablePreexistingStock(AEKey key, long snapshotAmount);

    /** Whether this reservation groups keys that differ only in secondary data/components. */
    default boolean groupsSecondaryVariants(AEKey key) {
        return false;
    }

    /**
     * Allocate the usable aggregate of an ignore-secondary reservation back to one exact variant.
     * Implementations must ensure the sum returned across the group never exceeds total stock minus
     * the configured reserve.
     */
    default long usablePreexistingStock(
            AEKey exactVariant, long exactAmount, Map<AEKey, Long> groupSnapshot) {
        return usablePreexistingStock(exactVariant, exactAmount);
    }
}
