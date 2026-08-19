package com.moakiee.ae2lt.overload.runtime.cpu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

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
    private final LinkedHashMap<UUID, Long> remainingConsumerCredits = new LinkedHashMap<>();
    private boolean sharedReusableSeedPool;
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
        this(key, owner, patternReference, itemId, exactExpectedKey, remainingAmount,
                routesToRequester, registeredOrder, null, false, 0L);
    }

    public PendingOverloadOutput(
            PendingOverloadOutputKey key,
            OverloadCpuOwner owner,
            OverloadPatternReference patternReference,
            ResourceLocation itemId,
            AEKey exactExpectedKey,
            long remainingAmount,
            boolean routesToRequester,
            long registeredOrder,
            @Nullable UUID reusableSeedGroupId,
            boolean sharedReusableSeedPool,
            long remainingReusableSeedAmount
    ) {
        this(key, owner, patternReference, itemId, exactExpectedKey, remainingAmount,
                routesToRequester, registeredOrder,
                legacyCredits(remainingReusableSeedAmount, reusableSeedGroupId),
                sharedReusableSeedPool);
    }

    public PendingOverloadOutput(
            PendingOverloadOutputKey key,
            OverloadCpuOwner owner,
            OverloadPatternReference patternReference,
            ResourceLocation itemId,
            AEKey exactExpectedKey,
            long remainingAmount,
            boolean routesToRequester,
            long registeredOrder,
            List<OverloadConsumerCredit> consumerCredits,
            boolean sharedReusableSeedPool
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
        var normalizedCredits = OverloadConsumerCredit.normalize(consumerCredits);
        if (!OverloadConsumerCredit.fitsWithin(normalizedCredits, remainingAmount)) {
            throw new IllegalArgumentException("consumer credits are outside pending output");
        }
        for (var credit : normalizedCredits) {
            remainingConsumerCredits.put(credit.consumerId(), credit.amount());
        }
        this.sharedReusableSeedPool = sharedReusableSeedPool;
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

    public List<OverloadConsumerCredit> consumerCredits() {
        return OverloadConsumerCredit.fromAmounts(remainingConsumerCredits);
    }

    public long remainingReusableSeedAmount() {
        return OverloadConsumerCredit.total(consumerCredits());
    }

    /** Output units that are not owned by a reusable-seed consumer. */
    public long remainingPublicAmount() {
        return Math.max(0L, remainingAmount - remainingReusableSeedAmount());
    }

    /** Legacy single-owner view. Returns null when this output has multiple consumers. */
    public @Nullable UUID reusableSeedGroupId() {
        return remainingConsumerCredits.size() == 1
                ? remainingConsumerCredits.keySet().iterator().next() : null;
    }

    public boolean sharedReusableSeedPool() { return sharedReusableSeedPool; }

    public void addExpected(long amount) {
        addExpected(amount, null);
    }

    public void addExpected(long amount, @Nullable OverloadReusableSeedMetadata reusableSeed) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (reusableSeed != null) {
            if (!OverloadConsumerCredit.fitsWithin(reusableSeed.consumerCredits(), amount)) {
                throw new IllegalArgumentException("consumer credits exceed added output");
            }
            boolean hadConsumerCredits = !remainingConsumerCredits.isEmpty();
            if (hadConsumerCredits
                    && sharedReusableSeedPool != reusableSeed.sharedPool()) {
                throw new IllegalArgumentException("overload output changed legacy pool semantics");
            }
            var updatedCredits = new LinkedHashMap<>(remainingConsumerCredits);
            for (var credit : reusableSeed.consumerCredits()) {
                updatedCredits.merge(
                        credit.consumerId(), credit.amount(), OverloadConsumerCredit::addSaturated);
            }
            long updatedRemaining = addSaturated(remainingAmount, amount);
            if (!OverloadConsumerCredit.fitsWithin(
                    OverloadConsumerCredit.fromAmounts(updatedCredits), updatedRemaining)) {
                throw new IllegalArgumentException(
                        "consumer credits exceed saturated pending output capacity");
            }
            remainingConsumerCredits.clear();
            remainingConsumerCredits.putAll(updatedCredits);
            if (!hadConsumerCredits) {
                sharedReusableSeedPool = reusableSeed.sharedPool();
            }
        }
        remainingAmount = addSaturated(remainingAmount, amount);
    }

    public long claim(long requestedAmount) {
        if (requestedAmount <= 0) {
            return 0;
        }

        long claimed = Math.min(remainingAmount, requestedAmount);
        remainingAmount -= claimed;
        return claimed;
    }

    public List<OverloadConsumerCredit> claimConsumerCredits(long claimedOutput, boolean mutate) {
        return claimConsumerCredits(claimedOutput, mutate, ignored -> true);
    }

    /**
     * Claims only credits whose consumer can accept the concrete returned variant. Disallowed
     * credits remain pending; they must not be silently re-keyed to an unusable component state.
     */
    public List<OverloadConsumerCredit> claimConsumerCredits(
            long claimedOutput, boolean mutate, Predicate<UUID> acceptsConsumer) {
        long remaining = Math.max(0L, claimedOutput);
        if (remaining == 0 || remainingConsumerCredits.isEmpty()) return List.of();
        Objects.requireNonNull(acceptsConsumer, "acceptsConsumer");

        var claimed = new ArrayList<OverloadConsumerCredit>();
        var iterator = remainingConsumerCredits.entrySet().iterator();
        while (iterator.hasNext() && remaining > 0) {
            var entry = iterator.next();
            if (!acceptsConsumer.test(entry.getKey())) continue;
            long amount = Math.min(entry.getValue(), remaining);
            if (amount <= 0) continue;
            claimed.add(new OverloadConsumerCredit(entry.getKey(), amount));
            remaining -= amount;
            if (mutate) {
                long left = entry.getValue() - amount;
                if (left == 0) iterator.remove();
                else entry.setValue(left);
            }
        }
        return List.copyOf(claimed);
    }

    public long acceptedConsumerCreditAmount(Predicate<UUID> acceptsConsumer) {
        Objects.requireNonNull(acceptsConsumer, "acceptsConsumer");
        long total = 0L;
        for (var entry : remainingConsumerCredits.entrySet()) {
            if (acceptsConsumer.test(entry.getKey())) {
                total = addSaturated(total, entry.getValue());
            }
        }
        return total;
    }

    /** Claims the exact consumer allocation selected by an earlier simulation preview. */
    public List<OverloadConsumerCredit> claimConsumerCredits(
            Collection<OverloadConsumerCredit> requestedCredits, boolean mutate) {
        var requested = OverloadConsumerCredit.normalize(requestedCredits);
        if (requested.isEmpty() || remainingConsumerCredits.isEmpty()) return List.of();

        var claimed = new ArrayList<OverloadConsumerCredit>(requested.size());
        for (var request : requested) {
            long available = remainingConsumerCredits.getOrDefault(request.consumerId(), 0L);
            long amount = Math.min(available, request.amount());
            if (amount <= 0) continue;
            claimed.add(new OverloadConsumerCredit(request.consumerId(), amount));
            if (mutate) {
                long left = available - amount;
                if (left == 0L) remainingConsumerCredits.remove(request.consumerId());
                else remainingConsumerCredits.put(request.consumerId(), left);
            }
        }
        return List.copyOf(claimed);
    }

    /** Legacy total-only claim API. */
    public long claimReusableSeed(long claimedOutput, boolean mutate) {
        return OverloadConsumerCredit.total(claimConsumerCredits(claimedOutput, mutate));
    }

    public boolean isSatisfied() {
        return remainingAmount <= 0;
    }

    private static long addSaturated(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static List<OverloadConsumerCredit> legacyCredits(
            long amount, @Nullable UUID consumerId) {
        if (amount < 0) {
            throw new IllegalArgumentException("remainingReusableSeedAmount must not be negative");
        }
        if (amount == 0) return List.of();
        return List.of(new OverloadConsumerCredit(
                Objects.requireNonNull(consumerId, "reusableSeedGroupId"), amount));
    }
}
