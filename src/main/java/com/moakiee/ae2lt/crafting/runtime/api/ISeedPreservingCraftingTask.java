package com.moakiee.ae2lt.crafting.runtime.api;

import appeng.api.stacks.AEKey;
import java.util.Set;
import java.util.UUID;

/**
 * Marks an expanded task whose dispatched inputs remain part of a closed-loop seed reserve.
 * Values are physical seed units placed in flight by one ordinary pattern copy.
 */
public interface ISeedPreservingCraftingTask {
    /** Stable identity of the contracted cycle that owns this task's reusable seed reserve. */
    UUID reusableSeedGroupId();

    /** Keys that can represent the reusable state while this cycle is executing. */
    Set<AEKey> reusableSeedCycleKeys();

    /**
     * True only when every expanded member consumes exactly one positive {@code inputSeed} key.
     * Such flows cannot hold one seed lane while waiting for another and may share the CPU's
     * single-seed ledger. Other flows require a ledger dedicated to their group id.
     */
    boolean hasSingleSeedInputPerMember();
}
