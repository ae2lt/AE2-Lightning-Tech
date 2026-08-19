package com.moakiee.ae2lt.overload.runtime.cpu;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails;

/**
 * Per-CPU overload-side waiting state.
 * <p>
 * Recommended structure:
 * <ul>
 *   <li>a primary map keyed by (craftingId, patternIdentity, outputSlotIndex)</li>
 *   <li>a secondary index by item id for fast ID_ONLY claim lookup</li>
 *   <li>stable registration order so repeated claims are deterministic</li>
 * </ul>
 * This leaves AE2's native {@code waitingFor} untouched and tracks only the
 * extra semantics needed for overload ID_ONLY outputs.
 */
public final class OverloadCpuState {
    private static final String TAG_NEXT_SEQUENCE = "NextSequence";
    private static final String TAG_PENDING = "Pending";
    private static final String TAG_PATTERN_IDENTITY = "PatternIdentity";
    private static final String TAG_SOURCE_PATTERN = "SourcePattern";
    private static final String TAG_OUTPUT_SLOT = "OutputSlot";
    private static final String TAG_ITEM_ID = "ItemId";
    private static final String TAG_EXACT_TEMPLATE = "ExactTemplate";
    private static final String TAG_REMAINING = "RemainingAmount";
    private static final String TAG_ROUTES_TO_REQUESTER = "RoutesToRequester";
    private static final String TAG_REGISTERED_ORDER = "RegisteredOrder";
    private static final String TAG_REUSABLE_SEED_GROUP = "ReusableSeedGroup";
    private static final String TAG_SHARED_REUSABLE_SEED_POOL = "SharedReusableSeedPool";
    private static final String TAG_REMAINING_REUSABLE_SEED = "RemainingReusableSeed";
    private static final String TAG_CONSUMER_CREDITS = "ConsumerCredits";
    private static final String TAG_CONSUMER_ID = "ConsumerId";
    private static final String TAG_CONSUMER_AMOUNT = "Amount";

    private final OverloadCpuOwner owner;
    private final Map<PendingOverloadOutputKey, PendingOverloadOutput> pendingByKey = new LinkedHashMap<>();
    private final Map<ResourceLocation, LinkedHashSet<PendingOverloadOutputKey>> pendingByItemId = new LinkedHashMap<>();
    private long nextSequence = 1L;

    public OverloadCpuState(OverloadCpuOwner owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public OverloadCpuOwner owner() {
        return owner;
    }

    public Collection<PendingOverloadOutput> allPending() {
        return List.copyOf(pendingByKey.values());
    }

    /** Zero-copy view for manager-internal scans under the manager's monitor. */
    Collection<PendingOverloadOutput> pendingView() {
        return pendingByKey.values();
    }

    public boolean isEmpty() {
        return pendingByKey.isEmpty();
    }

    public void registerExpectedOutputs(OverloadPatternReference patternReference,
                                        OverloadPatternDetails patternDetails,
                                        List<GenericStack> actualOutputs,
                                        @Nullable AEKey finalOutputKey,
                                        long pushedCopies) {
        registerExpectedOutputs(patternReference, patternDetails, actualOutputs, finalOutputKey,
                pushedCopies, Map.of());
    }

    public void registerExpectedOutputs(OverloadPatternReference patternReference,
                                        OverloadPatternDetails patternDetails,
                                        List<GenericStack> actualOutputs,
                                        @Nullable AEKey finalOutputKey,
                                        long pushedCopies,
                                        Map<Integer, OverloadReusableSeedMetadata> reusableSeeds) {
        Objects.requireNonNull(patternReference, "patternReference");
        Objects.requireNonNull(patternDetails, "patternDetails");
        Objects.requireNonNull(actualOutputs, "actualOutputs");
        if (pushedCopies <= 0) {
            throw new IllegalArgumentException("pushedCopies must be > 0");
        }
        for (int outputIndex = 0; outputIndex < patternDetails.outputs().size(); outputIndex++) {
            var output = patternDetails.outputs().get(outputIndex);
            if (output.matchMode() != MatchMode.ID_ONLY) {
                continue;
            }

            int ae2SlotIndex = output.slotIndex();
            if (ae2SlotIndex < 0 || ae2SlotIndex >= actualOutputs.size()) {
                continue;
            }
            var actual = actualOutputs.get(ae2SlotIndex);
            if (!(actual.what() instanceof AEItemKey)) {
                continue;
            }

            var itemId = itemIdOf(output);
            var exactExpectedKey = actual.what();
            var amount = multiplySaturated(output.amountPerCraft(), pushedCopies);
            var reusableSeed = reusableSeeds.get(output.slotIndex());
            registerExpectedOutput(
                    patternReference,
                    output.slotIndex(),
                    itemId,
                    exactExpectedKey,
                    amount,
                    routesToRequester(output, finalOutputKey),
                    reusableSeed);
        }
    }

    /** Low-level registration after slot matching has already been resolved. */
    void registerExpectedOutput(
            OverloadPatternReference patternReference,
            int outputSlotIndex,
            ResourceLocation itemId,
            AEKey exactExpectedKey,
            long amount,
            boolean routesToRequester,
            @Nullable OverloadReusableSeedMetadata reusableSeed) {
        Objects.requireNonNull(patternReference, "patternReference");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(exactExpectedKey, "exactExpectedKey");
        if (outputSlotIndex < 0) throw new IllegalArgumentException("outputSlotIndex must be >= 0");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");

        var key = new PendingOverloadOutputKey(
                owner.craftingId(), patternReference.patternIdentity(), outputSlotIndex);
        var existing = pendingByKey.get(key);
        if (existing != null) {
            if (!existing.itemId().equals(itemId)
                    || !existing.exactExpectedKey().equals(exactExpectedKey)
                    || existing.routesToRequester() != routesToRequester) {
                throw new IllegalStateException(
                        "overload pending-output identity merged incompatible slots");
            }
            existing.addExpected(amount, reusableSeed);
            return;
        }

        var pending = new PendingOverloadOutput(
                key,
                owner,
                patternReference,
                itemId,
                exactExpectedKey,
                amount,
                routesToRequester,
                nextSequence++,
                reusableSeed != null ? reusableSeed.consumerCredits() : List.of(),
                reusableSeed != null && reusableSeed.sharedPool());
        pendingByKey.put(key, pending);
        pendingByItemId.computeIfAbsent(itemId, ignored -> new LinkedHashSet<>()).add(key);
    }

    public OverloadClaimResult claimByItemId(ResourceLocation itemId, long amount, boolean mutate) {
        return claimByItemId(itemId, amount, mutate, (consumer, expected) -> true);
    }

    public OverloadClaimResult claimByItemId(
            ResourceLocation itemId,
            long amount,
            boolean mutate,
            BiPredicate<UUID, AEKey> acceptsConsumerVariant) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(acceptsConsumerVariant, "acceptsConsumerVariant");
        if (amount <= 0) {
            return OverloadClaimResult.EMPTY;
        }

        var keys = pendingByItemId.get(itemId);
        if (keys == null || keys.isEmpty()) {
            return OverloadClaimResult.EMPTY;
        }

        long remaining = amount;
        var claims = new ArrayList<PendingOverloadClaim>();

        var ordered = keys.stream()
                .map(pendingByKey::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(PendingOverloadOutput::registeredOrder))
                .toList();

        for (var pending : ordered) {
            if (remaining <= 0) {
                break;
            }

            Predicate<UUID> acceptsConsumer = consumer -> acceptsConsumerVariant.test(
                    consumer, pending.exactExpectedKey());
            long acceptedCredits = pending.acceptedConsumerCreditAmount(acceptsConsumer);
            long compatibleAmount = addSaturated(
                    pending.remainingPublicAmount(), acceptedCredits);
            long claimable = Math.min(Math.min(pending.remainingAmount(), remaining), compatibleAmount);
            if (claimable <= 0) {
                continue;
            }

            var consumerCredits = pending.claimConsumerCredits(
                    claimable, mutate, acceptsConsumer);
            if (mutate) {
                pending.claim(claimable);
                if (pending.isSatisfied()) {
                    removeSatisfied(pending);
                }
            }

            claims.add(new PendingOverloadClaim(
                    pending.key(),
                    claimable,
                    pending.routesToRequester(),
                    pending.exactExpectedKey(),
                    consumerCredits,
                    pending.sharedReusableSeedPool()));
            remaining -= claimable;
        }

        long claimedAmount = amount - remaining;
        return claimedAmount > 0 ? new OverloadClaimResult(claimedAmount, claims) : OverloadClaimResult.EMPTY;
    }

    /** Commits an already-limited preview without consuming any unaccepted requester tail. */
    public OverloadClaimResult commitPreview(OverloadClaimResult preview) {
        Objects.requireNonNull(preview, "preview");
        if (!preview.claimedAnything()) return OverloadClaimResult.EMPTY;

        long total = 0L;
        var committed = new ArrayList<PendingOverloadClaim>(preview.claims().size());
        for (var requested : preview.claims()) {
            var pending = pendingByKey.get(requested.key());
            if (pending == null) continue;
            var availableCredits = pending.claimConsumerCredits(
                    requested.consumerCredits(), false);
            long requestedPublic = Math.max(
                    0L, requested.claimedAmount() - requested.reusableSeedAmount());
            long committedPublic = Math.min(requestedPublic, pending.remainingPublicAmount());
            var consumerCredits = pending.claimConsumerCredits(availableCredits, true);
            long amount = addSaturated(
                    OverloadConsumerCredit.total(consumerCredits), committedPublic);
            if (amount <= 0) continue;
            pending.claim(amount);
            long committedRequester = Math.min(requested.requesterAmount(), committedPublic);
            committed.add(new PendingOverloadClaim(
                    pending.key(),
                    amount,
                    pending.routesToRequester(),
                    committedRequester,
                    pending.exactExpectedKey(),
                    consumerCredits,
                    pending.sharedReusableSeedPool()));
            total = addSaturated(total, amount);
            if (pending.isSatisfied()) removeSatisfied(pending);
        }
        return total > 0 ? new OverloadClaimResult(total, committed) : OverloadClaimResult.EMPTY;
    }

    public long getRemainingForItem(ResourceLocation itemId) {
        var total = getRemainingForItemExact(itemId);
        return total.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE : total.longValue();
    }

    BigInteger getRemainingForItemExact(ResourceLocation itemId) {
        Objects.requireNonNull(itemId, "itemId");
        var keys = pendingByItemId.get(itemId);
        if (keys == null || keys.isEmpty()) {
            return BigInteger.ZERO;
        }

        var total = BigInteger.ZERO;
        for (var key : keys) {
            var pending = pendingByKey.get(key);
            if (pending != null) {
                total = total.add(BigInteger.valueOf(pending.remainingAmount()));
            }
        }
        return total;
    }

    public boolean hasExactPending(AEKey incoming) {
        return getRemainingForExactKey(incoming).signum() > 0;
    }

    BigInteger getRemainingForExactKey(AEKey incoming) {
        if (incoming == null) return BigInteger.ZERO;
        var total = BigInteger.ZERO;
        for (var pending : pendingByKey.values()) {
            if (pending.remainingAmount() > 0
                    && pending.exactExpectedKey().equals(incoming)) {
                total = total.add(BigInteger.valueOf(pending.remainingAmount()));
            }
        }
        return total;
    }

    public void clear() {
        pendingByKey.clear();
        pendingByItemId.clear();
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "registries");
        var tag = new CompoundTag();
        tag.putLong(TAG_NEXT_SEQUENCE, nextSequence);

        var pendingList = new ListTag();
        for (var pending : pendingByKey.values()) {
            var pendingTag = new CompoundTag();
            pendingTag.putString(TAG_PATTERN_IDENTITY, pending.key().patternIdentity());
            pendingTag.put(TAG_SOURCE_PATTERN, pending.patternReference().sourcePattern().toTag());
            pendingTag.putInt(TAG_OUTPUT_SLOT, pending.key().outputSlotIndex());
            pendingTag.putString(TAG_ITEM_ID, pending.itemId().toString());
            pendingTag.put(TAG_EXACT_TEMPLATE, pending.exactExpectedKey().toTagGeneric());
            pendingTag.putLong(TAG_REMAINING, pending.remainingAmount());
            pendingTag.putBoolean(TAG_ROUTES_TO_REQUESTER, pending.routesToRequester());
            pendingTag.putLong(TAG_REGISTERED_ORDER, pending.registeredOrder());
            if (!pending.consumerCredits().isEmpty()) {
                writeConsumerCredits(pendingTag, pending.consumerCredits());
                pendingTag.putBoolean(
                        TAG_SHARED_REUSABLE_SEED_POOL, pending.sharedReusableSeedPool());
                // Keep the legacy fields for single-consumer downgrade compatibility.
                if (pending.reusableSeedGroupId() != null) {
                    pendingTag.putUUID(TAG_REUSABLE_SEED_GROUP, pending.reusableSeedGroupId());
                    pendingTag.putLong(
                            TAG_REMAINING_REUSABLE_SEED, pending.remainingReusableSeedAmount());
                }
            }
            pendingList.add(pendingTag);
        }
        tag.put(TAG_PENDING, pendingList);
        return tag;
    }

    public static OverloadCpuState fromTag(OverloadCpuOwner owner, CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(registries, "registries");

        var state = new OverloadCpuState(owner);
        state.nextSequence = Math.max(1L, tag.getLong(TAG_NEXT_SEQUENCE));

        var pendingList = tag.getList(TAG_PENDING, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            var pendingTag = pendingList.getCompound(i);
            var patternReference = new OverloadPatternReference(
                    pendingTag.getString(TAG_PATTERN_IDENTITY),
                    com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot.fromTag(
                            pendingTag.getCompound(TAG_SOURCE_PATTERN)));
            var key = new PendingOverloadOutputKey(
                    owner.craftingId(),
                    pendingTag.getString(TAG_PATTERN_IDENTITY),
                    pendingTag.getInt(TAG_OUTPUT_SLOT));
            var pending = new PendingOverloadOutput(
                    key,
                    owner,
                    patternReference,
                    parseRequiredId(pendingTag.getString(TAG_ITEM_ID)),
                    loadExactExpectedKey(pendingTag, registries),
                    pendingTag.getLong(TAG_REMAINING),
                    pendingTag.getBoolean(TAG_ROUTES_TO_REQUESTER),
                    pendingTag.getLong(TAG_REGISTERED_ORDER),
                    readConsumerCredits(pendingTag),
                    pendingTag.getBoolean(TAG_SHARED_REUSABLE_SEED_POOL));
            state.pendingByKey.put(key, pending);
            state.pendingByItemId.computeIfAbsent(pending.itemId(), ignored -> new LinkedHashSet<>()).add(key);
            state.nextSequence = Math.max(state.nextSequence, pending.registeredOrder() + 1);
        }

        return state;
    }

    static void writeConsumerCredits(
            CompoundTag tag, Collection<OverloadConsumerCredit> consumerCredits) {
        Objects.requireNonNull(tag, "tag");
        var creditsTag = new ListTag();
        for (var credit : OverloadConsumerCredit.normalize(consumerCredits)) {
            var creditTag = new CompoundTag();
            creditTag.putUUID(TAG_CONSUMER_ID, credit.consumerId());
            creditTag.putLong(TAG_CONSUMER_AMOUNT, credit.amount());
            creditsTag.add(creditTag);
        }
        tag.put(TAG_CONSUMER_CREDITS, creditsTag);
    }

    static List<OverloadConsumerCredit> readConsumerCredits(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains(TAG_CONSUMER_CREDITS, CompoundTag.TAG_LIST)) {
            var credits = new ArrayList<OverloadConsumerCredit>();
            var creditsTag = tag.getList(TAG_CONSUMER_CREDITS, CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < creditsTag.size(); i++) {
                var creditTag = creditsTag.getCompound(i);
                if (!creditTag.hasUUID(TAG_CONSUMER_ID)) continue;
                long amount = creditTag.getLong(TAG_CONSUMER_AMOUNT);
                if (amount <= 0) continue;
                credits.add(new OverloadConsumerCredit(
                        creditTag.getUUID(TAG_CONSUMER_ID), amount));
            }
            return OverloadConsumerCredit.normalize(credits);
        }

        // Backward compatibility with the old single-group representation.
        if (tag.hasUUID(TAG_REUSABLE_SEED_GROUP)) {
            long amount = tag.getLong(TAG_REMAINING_REUSABLE_SEED);
            if (amount > 0) {
                return List.of(new OverloadConsumerCredit(
                        tag.getUUID(TAG_REUSABLE_SEED_GROUP), amount));
            }
        }
        return List.of();
    }

    private static AEKey loadExactExpectedKey(CompoundTag pendingTag, HolderLookup.Provider registries) {
        if (!pendingTag.contains(TAG_EXACT_TEMPLATE, CompoundTag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("pending overload entry is missing an exact expected key");
        }

        var key = AEKey.fromTagGeneric(pendingTag.getCompound(TAG_EXACT_TEMPLATE).copy());
        if (key == null) {
            throw new IllegalArgumentException("pending overload entry has an invalid exact expected key");
        }
        return key;
    }

    private static ResourceLocation parseRequiredId(String value) {
        var id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid pending overload item id: " + value);
        }
        return id;
    }

    private void removeSatisfied(PendingOverloadOutput pending) {
        pendingByKey.remove(pending.key());
        var keys = pendingByItemId.get(pending.itemId());
        if (keys != null) {
            keys.remove(pending.key());
            if (keys.isEmpty()) {
                pendingByItemId.remove(pending.itemId());
            }
        }
    }

    private static ResourceLocation itemIdOf(OverloadPatternDetails.OutputSlot output) {
        var key = AEItemKey.of(output.template());
        if (key == null) {
            throw new IllegalArgumentException("output template must resolve to an item key");
        }
        return key.getId();
    }

    private static boolean routesToRequester(OverloadPatternDetails.OutputSlot output, @Nullable AEKey finalOutputKey) {
        if (finalOutputKey == null) {
            return false;
        }

        var outputKey = AEItemKey.of(output.template());
        if (outputKey == null) {
            return false;
        }

        return OutputRouteDecision.routesToRequester(
                output.matchMode(),
                outputKey.equals(finalOutputKey),
                outputKey.dropSecondary().equals(finalOutputKey.dropSecondary()));
    }

    private static long multiplySaturated(long left, long right) {
        if (left <= 0 || right <= 0) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long addSaturated(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
