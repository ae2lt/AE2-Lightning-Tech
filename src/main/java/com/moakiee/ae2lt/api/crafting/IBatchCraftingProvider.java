package com.moakiee.ae2lt.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

/**
 * Optional crafting-provider contract for CPU-side batch dispatch.
 *
 * <p>The AE2 CPU mixin pre-extracts up to {@code maxCraft} homogeneous copies of a pattern's
 * inputs, then hands this method a SINGLE-COPY input template plus {@code maxCraft}.
 * Implementations return how many copies were not accepted; the CPU reinjects that leftover and
 * treats the accepted copy count as if that many vanilla {@link #pushPattern} calls had succeeded.
 */
public interface IBatchCraftingProvider extends ICraftingProvider {
    /**
     * Optional hint: how many copies this provider could accept for this pattern right now.
     *
     * <p>This is only an advisory upper bound on how many copies the CPU pre-extracts before
     * calling {@link #pushBatch}; it is NOT a correctness constraint. {@code pushBatch} is the
     * real gatekeeper (it returns the leftover it could not accept), and the CPU's own op budget
     * also limits dispatch. Returning {@code 0} opts this provider out for now (busy / full /
     * offline). The default returns {@link Integer#MAX_VALUE} when not busy, i.e. "no extra cap
     * beyond what pushBatch and the CPU budget already enforce". Override with a tighter, accurate
     * value if you want to stop the CPU from over-extracting inputs that pushBatch would only
     * reinject.
     */
    default int getBatchCapacity(IPatternDetails details) {
        return isBusy() ? 0 : Integer.MAX_VALUE;
    }

    /**
     * Try to consume up to {@code maxCraft} copies of {@code details}.
     *
     * @param details the pattern being crafted
     * @param oneCopyTemplate single-copy input template (NOT multiplied by {@code maxCraft})
     * @param maxCraft maximum copies the caller is willing to dispatch
     * @return leftover copy count in {@code [0, maxCraft]}
     */
    int pushBatch(IPatternDetails details, KeyCounter[] oneCopyTemplate, int maxCraft);

    @Override
    default boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return pushBatch(patternDetails, inputHolder, 1) == 0;
    }
}
