package com.moakiee.ae2lt.overload.cpu;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable per-job key for one logical overload output entry.
 * <p>
 * Repeated pushes of the same overload pattern/output slot within one job are
 * intentionally merged into the same key by accumulating remaining amount.
 */
public record PendingOverloadOutputKey(
        UUID craftingId,
        String patternIdentity,
        int outputSlotIndex
) {
    public PendingOverloadOutputKey {
        Objects.requireNonNull(craftingId, "craftingId");
        Objects.requireNonNull(patternIdentity, "patternIdentity");
        if (patternIdentity.isBlank()) {
            throw new IllegalArgumentException("patternIdentity must not be blank");
        }
        if (outputSlotIndex < 0) {
            throw new IllegalArgumentException("outputSlotIndex must be >= 0");
        }
    }
}
