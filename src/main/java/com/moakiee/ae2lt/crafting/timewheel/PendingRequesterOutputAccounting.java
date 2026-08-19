package com.moakiee.ae2lt.crafting.timewheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps deferred requester-output credits within the amount the job still needs.
 *
 * <p>A requester may temporarily reject an already-produced final output, leaving both the
 * physical item and a deferred-delivery credit in the CPU. If another produced output reaches the
 * requester first, the remaining job demand shrinks and some of those older credits become normal
 * surplus. Keeping them reserved would prevent the job from ever completing.
 */
final class PendingRequesterOutputAccounting {
    private PendingRequesterOutputAccounting() {
    }

    /**
     * Caps all deferred credits to {@code remainingDemand}.
     *
     * <p>When a newly completed key is known, credits for that exact key are retained last and are
     * therefore released first. This preserves other fuzzy/component variants where possible.
     *
     * @return the number of credits released as ordinary CPU inventory
     */
    static <K> Reconciliation<K> capToRemainingDemand(
            List<Credit<K>> pending, long remainingDemand, K newlyCompletedKey) {
        if (pending == null || pending.isEmpty()) {
            return new Reconciliation<>(List.of(), 0L);
        }

        var ordered = new ArrayList<Credit<K>>();
        Credit<K> preferred = null;
        for (var entry : pending) {
            long amount = Math.max(0L, entry.amount());
            if (amount <= 0) continue;
            var credit = new Credit<>(entry.key(), amount);
            if (newlyCompletedKey != null && Objects.equals(newlyCompletedKey, entry.key())) {
                preferred = credit;
            } else {
                ordered.add(credit);
            }
        }
        if (preferred != null) {
            ordered.add(preferred);
        }

        long creditsLeft = Math.max(0L, remainingDemand);
        long released = 0L;
        var retainedCredits = new ArrayList<Credit<K>>();
        for (var entry : ordered) {
            long retained = Math.min(entry.amount(), creditsLeft);
            creditsLeft -= retained;
            if (retained > 0) {
                retainedCredits.add(new Credit<>(entry.key(), retained));
            }
            released = addSaturated(released, entry.amount() - retained);
        }
        return new Reconciliation<>(List.copyOf(retainedCredits), released);
    }

    private static long addSaturated(long left, long right) {
        if (right <= 0) return left;
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    record Credit<K>(K key, long amount) {
    }

    record Reconciliation<K>(List<Credit<K>> retained, long released) {
    }
}
