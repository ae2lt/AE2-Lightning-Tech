package com.moakiee.ae2lt.crafting.runtime;

/**
 * Common ordering used when AE2's concrete CPUs and Thunderbolt extended CPUs compete for an
 * automatically submitted job.
 */
public final class CraftingCpuSelectionOrder {
    /**
     * Compares two eligible CPUs using AE2's ordering. A negative result means the first CPU wins.
     */
    public static int compare(
            boolean firstPreferred,
            int firstCoProcessors,
            long firstAvailableStorage,
            boolean secondPreferred,
            int secondCoProcessors,
            long secondAvailableStorage,
            boolean prioritizePower) {
        if (firstPreferred != secondPreferred) {
            return Boolean.compare(secondPreferred, firstPreferred);
        }

        int coProcessorOrder = prioritizePower
                ? Integer.compare(secondCoProcessors, firstCoProcessors)
                : Integer.compare(firstCoProcessors, secondCoProcessors);
        if (coProcessorOrder != 0) {
            return coProcessorOrder;
        }
        return Long.compare(firstAvailableStorage, secondAvailableStorage);
    }

    private CraftingCpuSelectionOrder() {
    }
}
