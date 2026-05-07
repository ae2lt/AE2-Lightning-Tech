package com.moakiee.ae2lt.overload.cpu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;

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

    public boolean isEmpty() {
        return pendingByKey.isEmpty();
    }

    public void registerExpectedOutputs(OverloadPatternReference patternReference,
                                        OverloadPatternDetails patternDetails,
                                        List<GenericStack> actualOutputs,
                                        @Nullable AEKey finalOutputKey,
                                        long pushedCopies) {
        Objects.requireNonNull(patternReference, "patternReference");
        Objects.requireNonNull(patternDetails, "patternDetails");
        Objects.requireNonNull(actualOutputs, "actualOutputs");
        if (pushedCopies <= 0) {
            throw new IllegalArgumentException("pushedCopies must be > 0");
        }
        if (actualOutputs.size() != patternDetails.outputs().size()) {
            throw new IllegalArgumentException("output count mismatch between overload details and AE2 pattern details");
        }

        for (int outputIndex = 0; outputIndex < patternDetails.outputs().size(); outputIndex++) {
            var output = patternDetails.outputs().get(outputIndex);
            if (output.matchMode() != MatchMode.ID_ONLY) {
                continue;
            }

            var itemId = itemIdOf(output);
            var exactExpectedKey = actualOutputs.get(outputIndex).what();
            var amount = output.amountPerCraft() * pushedCopies;
            var key = new PendingOverloadOutputKey(owner.craftingId(), patternReference.patternIdentity(),
                    output.slotIndex());
            var existing = pendingByKey.get(key);
            if (existing != null) {
                existing.addExpected(amount);
                continue;
            }

            var pending = new PendingOverloadOutput(
                    key,
                    owner,
                    patternReference,
                    itemId,
                    exactExpectedKey,
                    amount,
                    routesToRequester(output, finalOutputKey),
                    nextSequence++);
            pendingByKey.put(key, pending);
            pendingByItemId.computeIfAbsent(itemId, ignored -> new LinkedHashSet<>()).add(key);
        }
    }

    public OverloadClaimResult claimByItemId(ResourceLocation itemId, long amount, boolean mutate) {
        Objects.requireNonNull(itemId, "itemId");
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

            long claimable = Math.min(pending.remainingAmount(), remaining);
            if (claimable <= 0) {
                continue;
            }

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
                    pending.exactExpectedKey()));
            remaining -= claimable;
        }

        long claimedAmount = amount - remaining;
        return claimedAmount > 0 ? new OverloadClaimResult(claimedAmount, claims) : OverloadClaimResult.EMPTY;
    }

    public long getRemainingForItem(ResourceLocation itemId) {
        Objects.requireNonNull(itemId, "itemId");
        var keys = pendingByItemId.get(itemId);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        long total = 0;
        for (var key : keys) {
            var pending = pendingByKey.get(key);
            if (pending != null) {
                total += pending.remainingAmount();
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
            pendingTag.put(TAG_EXACT_TEMPLATE, pending.exactExpectedKey().toTagGeneric(registries));
            pendingTag.putLong(TAG_REMAINING, pending.remainingAmount());
            pendingTag.putBoolean(TAG_ROUTES_TO_REQUESTER, pending.routesToRequester());
            pendingTag.putLong(TAG_REGISTERED_ORDER, pending.registeredOrder());
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
                    com.moakiee.ae2lt.overload.pattern.SourcePatternSnapshot.fromTag(
                            pendingTag.getCompound(TAG_SOURCE_PATTERN)));
            var key = new PendingOverloadOutputKey(
                    owner.craftingId(),
                    pendingTag.getString(TAG_PATTERN_IDENTITY),
                    pendingTag.getInt(TAG_OUTPUT_SLOT));
            var pending = new PendingOverloadOutput(
                    key,
                    owner,
                    patternReference,
                    ResourceLocation.parse(pendingTag.getString(TAG_ITEM_ID)),
                    loadExactExpectedKey(pendingTag, registries),
                    pendingTag.getLong(TAG_REMAINING),
                    pendingTag.getBoolean(TAG_ROUTES_TO_REQUESTER),
                    pendingTag.getLong(TAG_REGISTERED_ORDER));
            state.pendingByKey.put(key, pending);
            state.pendingByItemId.computeIfAbsent(pending.itemId(), ignored -> new LinkedHashSet<>()).add(key);
            state.nextSequence = Math.max(state.nextSequence, pending.registeredOrder() + 1);
        }

        return state;
    }

    private static AEKey loadExactExpectedKey(CompoundTag pendingTag, HolderLookup.Provider registries) {
        if (!pendingTag.contains(TAG_EXACT_TEMPLATE, CompoundTag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("pending overload entry is missing an exact expected key");
        }

        var key = AEKey.fromTagGeneric(registries, pendingTag.getCompound(TAG_EXACT_TEMPLATE).copy());
        if (key == null) {
            throw new IllegalArgumentException("pending overload entry has an invalid exact expected key");
        }
        return key;
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
        return outputKey != null && outputKey.equals(finalOutputKey);
    }
}
