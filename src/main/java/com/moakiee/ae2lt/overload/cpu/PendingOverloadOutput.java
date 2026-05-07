package com.moakiee.ae2lt.overload.cpu;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;

/**
 * One overload-only pending output tracked beside AE2's native waiting list.
 * <p>
 * This only exists for outputs whose match mode is ID_ONLY. STRICT outputs stay
 * entirely inside AE2's normal waitingFor handling.
 */
public final class PendingOverloadOutput {
    private final PendingOverloadOutputKey key;
    private final OverloadCpuOwner owner;
    private final OverloadPatternReference patternReference;
    private final ResourceLocation itemId;
    private final AEKey exactExpectedKey;
    private final boolean routesToRequester;
    private final long registeredOrder;
    private long remainingAmount;

    public PendingOverloadOutput(
            PendingOverloadOutputKey key,
            OverloadCpuOwner owner,
            OverloadPatternReference patternReference,
            ResourceLocation itemId,
            AEKey exactExpectedKey,
            long remainingAmount,
            boolean routesToRequester,
            long registeredOrder
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.patternReference = Objects.requireNonNull(patternReference, "patternReference");
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.exactExpectedKey = Objects.requireNonNull(exactExpectedKey, "exactExpectedKey");
        if (remainingAmount <= 0) {
            throw new IllegalArgumentException("remainingAmount must be > 0");
        }
        this.remainingAmount = remainingAmount;
        this.routesToRequester = routesToRequester;
        this.registeredOrder = registeredOrder;
    }

    public PendingOverloadOutputKey key() {
        return key;
    }

    public OverloadCpuOwner owner() {
        return owner;
    }

    public OverloadPatternReference patternReference() {
        return patternReference;
    }

    public ResourceLocation itemId() {
        return itemId;
    }

    public AEKey exactExpectedKey() {
        return exactExpectedKey;
    }

    public long remainingAmount() {
        return remainingAmount;
    }

    public boolean routesToRequester() {
        return routesToRequester;
    }

    public int outputSlotIndex() {
        return key.outputSlotIndex();
    }

    public long registeredOrder() {
        return registeredOrder;
    }

    public void addExpected(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        remainingAmount += amount;
    }

    public long claim(long requestedAmount) {
        if (requestedAmount <= 0) {
            return 0;
        }

        long claimed = Math.min(remainingAmount, requestedAmount);
        remainingAmount -= claimed;
        return claimed;
    }

    public boolean isSatisfied() {
        return remainingAmount <= 0;
    }
}
