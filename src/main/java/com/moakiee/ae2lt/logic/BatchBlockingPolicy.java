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
            @Nullable ProviderPatternKey lastSuccessfulPattern,
            ProviderPatternKey currentPattern) {
        if (craftingLocked) {
            return true;
        }
        if (!blockingEnabled || !targetContainsPatternInput) {
            return false;
        }
        return !samePatternBlockingEnabled
                || !samePattern(lastSuccessfulPattern, currentPattern);
    }

    /** Compare provider-owned keys without invoking third-party pattern equality. */
    static boolean samePattern(
            @Nullable ProviderPatternKey previous,
            ProviderPatternKey current) {
        return Objects.equals(previous, current);
    }

    private BatchBlockingPolicy() {
    }
}
