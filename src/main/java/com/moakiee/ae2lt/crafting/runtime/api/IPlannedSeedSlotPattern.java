package com.moakiee.ae2lt.crafting.runtime.api;

import appeng.api.stacks.AEKey;
import java.util.Map;

/**
 * Optional execution metadata that identifies the logical reusable seed selected for each input
 * slot. This disambiguates a planned key from an ordinary alternative exposed by another slot.
 */
public interface IPlannedSeedSlotPattern {
    /** Input slot index to the planned reusable-seed key consumed by that slot. */
    Map<Integer, AEKey> plannedSeedInputSlots();
}
