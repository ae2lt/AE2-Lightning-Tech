package com.moakiee.ae2lt.overload.runtime.cpu;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consumer ownership attached to one ID_ONLY overload output slot. */
public record OverloadReusableSeedMetadata(
        List<OverloadConsumerCredit> consumerCredits,
        boolean sharedPool) {
    public OverloadReusableSeedMetadata {
        consumerCredits = OverloadConsumerCredit.normalize(consumerCredits);
        if (consumerCredits.isEmpty()) {
            throw new IllegalArgumentException("consumerCredits must not be empty");
        }
    }

    public OverloadReusableSeedMetadata(List<OverloadConsumerCredit> consumerCredits) {
        this(consumerCredits, false);
    }

    public OverloadReusableSeedMetadata(Map<UUID, Long> consumerCredits) {
        this(OverloadConsumerCredit.fromAmounts(consumerCredits), false);
    }

    /** Legacy single-owner constructor. The old group id is treated as the consumer id. */
    public OverloadReusableSeedMetadata(UUID groupId, boolean sharedPool, long amount) {
        this(List.of(new OverloadConsumerCredit(groupId, amount)), sharedPool);
    }

    public long amount() {
        return OverloadConsumerCredit.total(consumerCredits);
    }

    /**
     * Legacy single-owner view. Multi-consumer metadata intentionally has no single group id.
     */
    public UUID groupId() {
        return consumerCredits.size() == 1 ? consumerCredits.getFirst().consumerId() : null;
    }
}
