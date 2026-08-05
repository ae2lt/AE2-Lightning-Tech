package com.moakiee.ae2lt.overload.runtime.cpu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of attempting to claim incoming items against overload-side pending
 * outputs.
 */
public record OverloadClaimResult(
        long claimedAmount,
        List<PendingOverloadClaim> claims
) {
    public static final OverloadClaimResult EMPTY = new OverloadClaimResult(0, List.of());

    public OverloadClaimResult {
        if (claimedAmount < 0) {
            throw new IllegalArgumentException("claimedAmount must be >= 0");
        }
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        long remaining = claimedAmount;
        for (var claim : claims) {
            if (claim.claimedAmount() > remaining) {
                throw new IllegalArgumentException("claims exceed claimedAmount");
            }
            remaining -= claim.claimedAmount();
        }
        if (remaining != 0L) {
            throw new IllegalArgumentException("claims do not add up to claimedAmount");
        }
    }

    public boolean claimedAnything() {
        return claimedAmount > 0;
    }

    public long claimedForRequester() {
        long total = 0;
        for (var claim : claims) {
            total = addSaturated(total, claim.requesterAmount());
        }
        return total;
    }

    public long claimedForInventory() {
        return claimedAmount - claimedForRequester();
    }

    /** Consumer ownership aggregated across every concrete pending-output claim. */
    public List<OverloadConsumerCredit> consumerCredits() {
        var amounts = new LinkedHashMap<UUID, Long>();
        for (var claim : claims) {
            for (var credit : claim.consumerCredits()) {
                amounts.merge(
                        credit.consumerId(), credit.amount(), OverloadConsumerCredit::addSaturated);
            }
        }
        return OverloadConsumerCredit.fromAmounts(amounts);
    }

    public long consumerCreditAmount(UUID consumerId) {
        Objects.requireNonNull(consumerId, "consumerId");
        long total = 0L;
        for (var credit : consumerCredits()) {
            if (credit.consumerId().equals(consumerId)) {
                total = OverloadConsumerCredit.addSaturated(total, credit.amount());
            }
        }
        return total;
    }

    /**
     * Keeps every inventory/reusable-seed claim but caps the requester-routed portion to what the
     * requester actually accepted. The unaccepted tail remains pending and can be retried later.
     */
    public OverloadClaimResult limitRequester(long acceptedRequesterAmount) {
        long requesterRemaining = Math.max(0L, acceptedRequesterAmount);
        long total = 0L;
        var limited = new ArrayList<PendingOverloadClaim>(claims.size());
        for (var claim : claims) {
            long inventoryPart = claim.claimedAmount() - claim.requesterAmount();
            long accepted = Math.min(claim.requesterAmount(), requesterRemaining);
            requesterRemaining -= accepted;
            long kept = inventoryPart + accepted;
            if (kept <= 0) continue;
            limited.add(new PendingOverloadClaim(
                    claim.key(),
                    kept,
                    claim.routesToRequester(),
                    accepted,
                    claim.exactExpectedKey(),
                    OverloadConsumerCredit.limit(claim.consumerCredits(), kept),
                    claim.sharedReusableSeedPool()));
            total = addSaturated(total, kept);
        }
        return total > 0 ? new OverloadClaimResult(total, limited) : EMPTY;
    }

    /**
     * Keeps public output beyond {@code maximumRequesterAmount} as ordinary inventory, commits only
     * the accepted part within that request limit, and leaves the rejected in-range part pending.
     */
    public OverloadClaimResult partitionRequester(
            long maximumRequesterAmount, long acceptedRequesterAmount) {
        long requestLimit = Math.max(0L, maximumRequesterAmount);
        long acceptedRemaining = Math.max(0L, acceptedRequesterAmount);
        long total = 0L;
        var partitioned = new ArrayList<PendingOverloadClaim>(claims.size());
        for (var claim : claims) {
            long publicAmount = claim.requesterAmount();
            long requested = Math.min(publicAmount, requestLimit);
            requestLimit -= requested;
            long accepted = Math.min(requested, acceptedRemaining);
            acceptedRemaining -= accepted;
            long excess = publicAmount - requested;
            long inventoryPart = claim.claimedAmount() - publicAmount;
            long kept = addSaturated(addSaturated(inventoryPart, excess), accepted);
            if (kept <= 0) continue;
            partitioned.add(new PendingOverloadClaim(
                    claim.key(),
                    kept,
                    claim.routesToRequester(),
                    accepted,
                    claim.exactExpectedKey(),
                    OverloadConsumerCredit.limit(claim.consumerCredits(), kept),
                    claim.sharedReusableSeedPool()));
            total = addSaturated(total, kept);
        }
        return total > 0 ? new OverloadClaimResult(total, partitioned) : EMPTY;
    }

    private static long addSaturated(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
