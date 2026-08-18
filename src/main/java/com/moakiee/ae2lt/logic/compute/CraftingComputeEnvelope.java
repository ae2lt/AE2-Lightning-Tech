package com.moakiee.ae2lt.logic.compute;

public record CraftingComputeEnvelope(
        long storageBytes,
        int successfulDispatchesPerTick,
        long maxCopiesPerTick,
        boolean unboundedBatch,
        boolean dispatchCapped) {
}
