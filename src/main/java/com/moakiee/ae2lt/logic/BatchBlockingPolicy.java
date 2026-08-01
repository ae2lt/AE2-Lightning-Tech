package com.moakiee.ae2lt.logic;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;

/**
 * Pure decision logic for the blocking checks between physical batch chunks.
 */
final class BatchBlockingPolicy {

    static boolean isBlocked(
            boolean craftingLocked,
            boolean blockingEnabled,
            boolean targetContainsPatternInput,
            boolean samePatternBlockingEnabled,
            @Nullable IPatternDetails lastSuccessfulPattern,
            IPatternDetails currentPattern) {
        if (craftingLocked) {
            return true;
        }
        if (!blockingEnabled || !targetContainsPatternInput) {
            return false;
        }
        return !samePatternBlockingEnabled
                || !samePattern(lastSuccessfulPattern, currentPattern);
    }

    /** Compare catalog-canonical details by identity on the dispatch hot path. */
    static boolean samePattern(
            @Nullable IPatternDetails previous,
            IPatternDetails current) {
        return previous == current;
    }

    private BatchBlockingPolicy() {
    }
}
