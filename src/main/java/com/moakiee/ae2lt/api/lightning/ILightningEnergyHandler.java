package com.moakiee.ae2lt.api.lightning;

/**
 * Addon-facing handle for reading or writing lightning energy on an AE2 Lightning
 * Tech machine or grid network.
 *
 * <p>Acquire via the capabilities declared in
 * {@link com.moakiee.ae2lt.api.AE2LTCapabilities}:
 *
 * <pre>{@code
 * var handler = level.getCapability(
 *         AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK, pos, side);
 * if (handler != null) {
 *     long inserted = handler.insert(LightningTier.HIGH_VOLTAGE, 1000L, false);
 * }
 * }</pre>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>All amounts are non-negative {@code long} values. Implementations clamp to 0
 *       on negative input.</li>
 *   <li>{@code simulate=true} performs a dry run: nothing is mutated, the return
 *       value is what would have been moved.</li>
 *   <li>{@link #getCapacity(LightningTier)} may legitimately return
 *       {@link Long#MAX_VALUE} for grid-backed handlers, since AE2 storage capacity
 *       is the sum of attached cells and not a fixed number on the providing block.
 *       Treat the returned value as an upper bound, not a hard limit.</li>
 * </ul>
 *
 * <p>A single implementation may serve one or both tiers; queries for an
 * unsupported tier return 0 and report {@code canInsert/canExtract = false}.
 */
public interface ILightningEnergyHandler {

    /**
     * @return amount of {@code tier} lightning currently stored, or 0 if unsupported.
     */
    long getStored(LightningTier tier);

    /**
     * @return upper bound on stored {@code tier} lightning. May be
     *         {@link Long#MAX_VALUE} for grid-backed handlers.
     */
    default long getCapacity(LightningTier tier) {
        return Long.MAX_VALUE;
    }

    /**
     * Try to insert {@code amount} of {@code tier} lightning.
     *
     * @param simulate if true, do not actually move anything
     * @return amount actually inserted (or that would be inserted on simulate)
     */
    long insert(LightningTier tier, long amount, boolean simulate);

    /**
     * Try to extract up to {@code amount} of {@code tier} lightning.
     *
     * @param simulate if true, do not actually move anything
     * @return amount actually extracted (or that would be extracted on simulate)
     */
    long extract(LightningTier tier, long amount, boolean simulate);

    /**
     * @return whether this handler can ever accept the given tier. Implementations
     *         should return {@code false} cheaply for tiers they do not support; a
     *         true result does not guarantee non-zero room.
     */
    default boolean canInsert(LightningTier tier) {
        return true;
    }

    /**
     * @return whether this handler can ever yield the given tier. Implementations
     *         should return {@code false} cheaply for tiers they do not support; a
     *         true result does not guarantee non-zero stock.
     */
    default boolean canExtract(LightningTier tier) {
        return true;
    }

    /**
     * @return {@code true} when {@link #getStored(LightningTier)} is 0.
     */
    default boolean isEmpty(LightningTier tier) {
        return getStored(tier) == 0L;
    }

    /**
     * @return {@code true} when stored has reached the reported capacity. Returns
     *         {@code false} when capacity is unbounded (i.e. {@link Long#MAX_VALUE}).
     */
    default boolean isFull(LightningTier tier) {
        long cap = getCapacity(tier);
        if (cap <= 0L || cap == Long.MAX_VALUE) {
            return false;
        }
        return getStored(tier) >= cap;
    }
}
