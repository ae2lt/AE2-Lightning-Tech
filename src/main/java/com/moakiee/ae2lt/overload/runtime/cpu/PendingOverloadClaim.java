package com.moakiee.ae2lt.overload.runtime.cpu;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;

/**
 * One concrete claim against one pending overload output entry.
 */
public record PendingOverloadClaim(
        PendingOverloadOutputKey key,
        long claimedAmount,
        boolean routesToRequester,
        long requesterAmount,
        AEKey exactExpectedKey,
        List<OverloadConsumerCredit> consumerCredits,
        boolean sharedReusableSeedPool
) {
    public PendingOverloadClaim {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(exactExpectedKey, "exactExpectedKey");
        if (claimedAmount <= 0) {
            throw new IllegalArgumentException("claimedAmount must be > 0");
        }
        consumerCredits = OverloadConsumerCredit.normalize(consumerCredits);
        if (!OverloadConsumerCredit.fitsWithin(consumerCredits, claimedAmount)) {
            throw new IllegalArgumentException("consumer credits are outside the claim");
        }
        long maximumRequester = claimedAmount - OverloadConsumerCredit.total(consumerCredits);
        if (requesterAmount < 0 || requesterAmount > maximumRequester
                || (!routesToRequester && requesterAmount != 0)) {
            throw new IllegalArgumentException("requesterAmount is outside the claim");
        }
    }

    public PendingOverloadClaim(
            PendingOverloadOutputKey key,
            long claimedAmount,
            boolean routesToRequester,
            AEKey exactExpectedKey,
            List<OverloadConsumerCredit> consumerCredits,
            boolean sharedReusableSeedPool) {
        this(key, claimedAmount, routesToRequester,
                routesToRequester
                        ? claimedAmount - OverloadConsumerCredit.total(consumerCredits) : 0L,
                exactExpectedKey, consumerCredits, sharedReusableSeedPool);
    }

    /** Legacy single-owner constructor. */
    public PendingOverloadClaim(
            PendingOverloadOutputKey key,
            long claimedAmount,
            boolean routesToRequester,
            AEKey exactExpectedKey,
            long reusableSeedAmount,
            @Nullable UUID reusableSeedGroupId,
            boolean sharedReusableSeedPool) {
        this(key, claimedAmount, routesToRequester, exactExpectedKey,
                legacyCredits(reusableSeedAmount, reusableSeedGroupId), sharedReusableSeedPool);
    }

    public long reusableSeedAmount() {
        return OverloadConsumerCredit.total(consumerCredits);
    }

    /** Legacy single-owner view. Returns null when this claim has multiple consumers. */
    public @Nullable UUID reusableSeedGroupId() {
        return consumerCredits.size() == 1 ? consumerCredits.get(0).consumerId() : null;
    }

    private static List<OverloadConsumerCredit> legacyCredits(
            long amount, @Nullable UUID consumerId) {
        if (amount < 0) throw new IllegalArgumentException("reusableSeedAmount must not be negative");
        if (amount == 0) return List.of();
        return List.of(new OverloadConsumerCredit(
                Objects.requireNonNull(consumerId, "reusableSeedGroupId"), amount));
    }
}
