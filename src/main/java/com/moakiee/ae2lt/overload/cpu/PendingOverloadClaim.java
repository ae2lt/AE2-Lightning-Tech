package com.moakiee.ae2lt.overload.cpu;

import java.util.Objects;

import appeng.api.stacks.AEKey;

/**
 * One concrete claim against one pending overload output entry.
 */
public record PendingOverloadClaim(
        PendingOverloadOutputKey key,
        long claimedAmount,
        boolean routesToRequester,
        AEKey exactExpectedKey
) {
    public PendingOverloadClaim {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(exactExpectedKey, "exactExpectedKey");
        if (claimedAmount <= 0) {
            throw new IllegalArgumentException("claimedAmount must be > 0");
        }
    }
}
