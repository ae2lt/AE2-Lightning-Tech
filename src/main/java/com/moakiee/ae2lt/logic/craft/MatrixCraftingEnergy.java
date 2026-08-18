package com.moakiee.ae2lt.logic.craft;

/**
 * Energy boundary for matrix execution.
 *
 * <p>One accepted pattern copy is one matrix operation and costs one AE. Capacity probes must not
 * consume power; the cluster commits power only after the provider accepted the corresponding
 * copies.
 */
public interface MatrixCraftingEnergy {
    MatrixCraftingEnergy UNLIMITED = new MatrixCraftingEnergy() {
        @Override
        public long affordableOperations(long requestedOperations) {
            return Math.max(0L, requestedOperations);
        }

        @Override
        public void consumeOperations(long acceptedOperations) {
        }
    };

    long affordableOperations(long requestedOperations);

    void consumeOperations(long acceptedOperations);
}
