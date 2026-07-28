package com.moakiee.ae2lt.logic;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

/**
 * Pure decision logic for the blocking checks between physical batch chunks.
 */
final class BatchBlockingPolicy {

    static boolean isBlocked(
            boolean craftingLocked,
            boolean blockingEnabled,
            boolean targetContainsPatternInput,
            boolean samePatternBlockingEnabled,
            @Nullable Object lastSuccessfulPattern,
            Object currentPattern) {
        if (craftingLocked) {
            return true;
        }
        if (!blockingEnabled || !targetContainsPatternInput) {
            return false;
        }
        return !samePatternBlockingEnabled
                || !samePattern(lastSuccessfulPattern, currentPattern);
    }

    /**
     * Check the cached hash first because pattern implementations may have a
     * comparatively expensive structural equality check.
     */
    static boolean samePattern(@Nullable Object previous, Object current) {
        return previous != null
                && previous.hashCode() == current.hashCode()
                && Objects.equals(previous, current);
    }

    private BatchBlockingPolicy() {
    }
}
