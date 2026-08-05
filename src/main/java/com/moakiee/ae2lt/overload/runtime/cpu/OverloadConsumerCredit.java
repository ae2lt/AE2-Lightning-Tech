package com.moakiee.ae2lt.overload.runtime.cpu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One consumer's ownership share in an overload output slot. */
public record OverloadConsumerCredit(UUID consumerId, long amount) {
    public OverloadConsumerCredit {
        Objects.requireNonNull(consumerId, "consumerId");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    /** Merges duplicate consumers while preserving first-seen claim order. */
    public static List<OverloadConsumerCredit> normalize(
            Collection<OverloadConsumerCredit> credits) {
        Objects.requireNonNull(credits, "credits");
        var amounts = new LinkedHashMap<UUID, Long>();
        for (var credit : credits) {
            Objects.requireNonNull(credit, "consumer credit");
            amounts.merge(credit.consumerId(), credit.amount(), OverloadConsumerCredit::addSaturated);
        }
        return fromAmounts(amounts);
    }

    public static List<OverloadConsumerCredit> fromAmounts(Map<UUID, Long> amounts) {
        Objects.requireNonNull(amounts, "amounts");
        var result = new ArrayList<OverloadConsumerCredit>(amounts.size());
        for (var entry : amounts.entrySet()) {
            var consumerId = Objects.requireNonNull(entry.getKey(), "consumerId");
            var amount = Objects.requireNonNull(entry.getValue(), "consumer amount");
            if (amount < 0) {
                throw new IllegalArgumentException("consumer amount must not be negative");
            }
            if (amount > 0) {
                result.add(new OverloadConsumerCredit(consumerId, amount));
            }
        }
        return List.copyOf(result);
    }

    public static long total(Collection<OverloadConsumerCredit> credits) {
        Objects.requireNonNull(credits, "credits");
        long total = 0L;
        for (var credit : credits) {
            total = addSaturated(total, Objects.requireNonNull(credit, "consumer credit").amount());
        }
        return total;
    }

    /** Exact bounded-sum check that cannot hide over-allocation behind saturation. */
    public static boolean fitsWithin(
            Collection<OverloadConsumerCredit> credits, long maximumAmount) {
        Objects.requireNonNull(credits, "credits");
        if (maximumAmount < 0L) return false;
        long remaining = maximumAmount;
        for (var credit : credits) {
            long amount = Objects.requireNonNull(credit, "consumer credit").amount();
            if (amount > remaining) return false;
            remaining -= amount;
        }
        return true;
    }

    /** Returns the ordered prefix that fits within {@code maximumAmount}. */
    public static List<OverloadConsumerCredit> limit(
            Collection<OverloadConsumerCredit> credits, long maximumAmount) {
        if (maximumAmount <= 0) return List.of();
        long remaining = maximumAmount;
        var result = new ArrayList<OverloadConsumerCredit>();
        for (var credit : credits) {
            if (remaining <= 0) break;
            long amount = Math.min(credit.amount(), remaining);
            if (amount > 0) {
                result.add(new OverloadConsumerCredit(credit.consumerId(), amount));
                remaining -= amount;
            }
        }
        return List.copyOf(result);
    }

    static long addSaturated(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
