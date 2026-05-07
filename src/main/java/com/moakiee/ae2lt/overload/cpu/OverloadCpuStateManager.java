package com.moakiee.ae2lt.overload.cpu;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;

import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;

/**
 * Runtime registry for overload-side CPU waiting state.
 * <p>
 * Lifecycle skeleton:
 * <ul>
 *   <li>create/get when an overload pattern copy is pushed successfully</li>
 *   <li>register only outputs whose match mode is ID_ONLY</li>
 *   <li>decrement during CPU insert-path matching when an incoming item can be
 *       recognized by item id but not by AE2's exact waitingFor key</li>
 *   <li>remove entries when remaining amount reaches zero</li>
 *   <li>clear the entire CPU state on finish, cancel, or job replacement</li>
 * </ul>
 * This manager intentionally does not change AE2's native waitingFor structure.
 */
public final class OverloadCpuStateManager {
    public static final OverloadCpuStateManager INSTANCE = new OverloadCpuStateManager();

    private final Map<Object, OverloadCpuState> states = new WeakHashMap<>();

    private OverloadCpuStateManager() {
    }

    public synchronized OverloadCpuState getOrCreate(CraftingCpuLogic logic) {
        Objects.requireNonNull(logic, "logic");
        var link = logic.getLastLink();
        if (link == null) {
            throw new IllegalStateException("crafting logic has no active link");
        }
        return getOrCreate(logic, link.getCraftingID());
    }

    public synchronized OverloadCpuState getOrCreate(Object logic, UUID craftingId) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(craftingId, "craftingId");
        return states.computeIfAbsent(logic, ignored -> new OverloadCpuState(OverloadCpuOwner.from(craftingId, logic)));
    }

    public synchronized Optional<OverloadCpuState> get(CraftingCpuLogic logic) {
        Objects.requireNonNull(logic, "logic");
        return Optional.ofNullable(states.get(logic));
    }

    /**
     * Register the extra ID_ONLY outputs for one successfully pushed overload
     * pattern copy.
     */
    public synchronized void registerExpectedOutputs(CraftingCpuLogic logic,
                                                     OverloadPatternReference patternReference,
                                                     OverloadPatternDetails patternDetails,
                                                     List<GenericStack> actualOutputs,
                                                     @Nullable AEKey finalOutputKey,
                                                     long pushedCopies) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(patternReference, "patternReference");
        Objects.requireNonNull(patternDetails, "patternDetails");
        Objects.requireNonNull(actualOutputs, "actualOutputs");
        if (pushedCopies <= 0) {
            throw new IllegalArgumentException("pushedCopies must be > 0");
        }

        getOrCreate(logic).registerExpectedOutputs(
                patternReference,
                patternDetails,
                actualOutputs,
                finalOutputKey,
                pushedCopies);
    }

    public synchronized void registerExpectedOutputs(Object logic,
                                                     UUID craftingId,
                                                     OverloadPatternReference patternReference,
                                                     OverloadPatternDetails patternDetails,
                                                     List<GenericStack> actualOutputs,
                                                     @Nullable AEKey finalOutputKey,
                                                     long pushedCopies) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(craftingId, "craftingId");
        Objects.requireNonNull(patternReference, "patternReference");
        Objects.requireNonNull(patternDetails, "patternDetails");
        Objects.requireNonNull(actualOutputs, "actualOutputs");
        if (pushedCopies <= 0) {
            throw new IllegalArgumentException("pushedCopies must be > 0");
        }

        getOrCreate(logic, craftingId).registerExpectedOutputs(
                patternReference,
                patternDetails,
                actualOutputs,
                finalOutputKey,
                pushedCopies);
    }

    /**
     * Detects whether registering the given pattern would create ambiguous
     * concurrent ID_ONLY pending outputs for the same item id.
     * <p>
     * CPU-side claim only sees returned item id. If multiple unresolved outputs
     * with the same item id coexist, the CPU cannot reliably decide which one a
     * returned stack belongs to. In that case, the caller should defer the push
     * until the older pending output has been resolved.
     */
    public synchronized boolean hasAmbiguousOutputRegistration(CraftingCpuLogic logic,
                                                               OverloadPatternReference patternReference,
                                                               OverloadPatternDetails patternDetails) {
        return hasAmbiguousOutputRegistration((Object) logic, patternReference, patternDetails);
    }

    public synchronized boolean hasAmbiguousOutputRegistration(Object logic,
                                                               OverloadPatternReference patternReference,
                                                               OverloadPatternDetails patternDetails) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(patternReference, "patternReference");
        Objects.requireNonNull(patternDetails, "patternDetails");

        var state = states.get(logic);
        var batch = new java.util.LinkedHashMap<ResourceLocation, OutputRegistrationCandidate>();

        for (var output : patternDetails.outputs()) {
            if (output.matchMode() != MatchMode.ID_ONLY) {
                continue;
            }

            var itemId = itemIdOf(output);
            var candidate = new OutputRegistrationCandidate(
                    patternReference.patternIdentity(),
                    output.slotIndex());

            var batchExisting = batch.putIfAbsent(itemId, candidate);
            if (batchExisting != null && !batchExisting.equals(candidate)) {
                return true;
            }

            if (state == null) {
                continue;
            }

            for (var pending : state.allPending()) {
                if (!pending.itemId().equals(itemId)) {
                    continue;
                }

                var pendingKey = pending.key();
                if (!pendingKey.patternIdentity().equals(candidate.patternIdentity())
                        || pendingKey.outputSlotIndex() != candidate.outputSlotIndex()) {
                    return true;
                }
            }
        }

        return false;
    }

    public synchronized OverloadClaimResult claim(CraftingCpuLogic logic, AEKey incoming, long amount,
                                                  Actionable actionable) {
        return claim((Object) logic, incoming, amount, actionable);
    }

    public synchronized OverloadClaimResult claim(Object logic, AEKey incoming, long amount,
                                                  Actionable actionable) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(actionable, "actionable");
        if (amount <= 0) {
            return OverloadClaimResult.EMPTY;
        }

        var itemKey = asItemKey(incoming);
        if (itemKey == null) {
            return OverloadClaimResult.EMPTY;
        }

        var state = states.get(logic);
        if (state == null) {
            return OverloadClaimResult.EMPTY;
        }

        var result = state.claimByItemId(itemKey.getId(), amount, actionable == Actionable.MODULATE);
        if (actionable == Actionable.MODULATE && state.isEmpty()) {
            states.remove(logic);
        }
        return result;
    }

    public synchronized long getRemainingForItem(CraftingCpuLogic logic, ResourceLocation itemId) {
        return getRemainingForItem((Object) logic, itemId);
    }

    public synchronized long getRemainingForItem(Object logic, ResourceLocation itemId) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(itemId, "itemId");
        var state = states.get(logic);
        return state != null ? state.getRemainingForItem(itemId) : 0;
    }

    public synchronized List<PendingOverloadOutput> snapshotPending(CraftingCpuLogic logic) {
        return snapshotPending((Object) logic);
    }

    public synchronized List<PendingOverloadOutput> snapshotPending(Object logic) {
        Objects.requireNonNull(logic, "logic");
        var state = states.get(logic);
        return state != null ? List.copyOf(state.allPending()) : List.of();
    }

    /**
     * Lightweight emptiness probe that avoids {@link List#copyOf} in the common
     * "is there any overload state at all on this CPU" check used by mixins.
     */
    public synchronized boolean hasAnyPending(CraftingCpuLogic logic) {
        return hasAnyPending((Object) logic);
    }

    public synchronized boolean hasAnyPending(Object logic) {
        Objects.requireNonNull(logic, "logic");
        var state = states.get(logic);
        return state != null && !state.isEmpty();
    }

    /**
     * Clear all overload-side state for this CPU.
     * Call from job cancel, normal finish, and any path that replaces the active
     * crafting job.
     */
    public synchronized void clear(CraftingCpuLogic logic) {
        clear((Object) logic);
    }

    public synchronized void clear(Object logic) {
        Objects.requireNonNull(logic, "logic");
        states.remove(logic);
    }

    public synchronized @Nullable CompoundTag writeToTag(CraftingCpuLogic logic, HolderLookup.Provider registries) {
        return writeToTag((Object) logic, registries);
    }

    public synchronized @Nullable CompoundTag writeToTag(Object logic, HolderLookup.Provider registries) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(registries, "registries");
        var state = states.get(logic);
        return state != null && !state.isEmpty() ? state.toTag(registries) : null;
    }

    public synchronized void readFromTag(CraftingCpuLogic logic, CompoundTag tag, HolderLookup.Provider registries) {
        var link = logic.getLastLink();
        if (link == null) {
            throw new IllegalStateException("crafting logic has no active link");
        }
        readFromTag(logic, link.getCraftingID(), tag, registries);
    }

    public synchronized void readFromTag(Object logic, UUID craftingId, CompoundTag tag,
                                         HolderLookup.Provider registries) {
        Objects.requireNonNull(logic, "logic");
        Objects.requireNonNull(craftingId, "craftingId");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(registries, "registries");

        if (tag.isEmpty()) {
            states.remove(logic);
            return;
        }

        states.put(logic, OverloadCpuState.fromTag(OverloadCpuOwner.from(craftingId, logic), tag, registries));
    }

    private static AEItemKey asItemKey(AEKey key) {
        return key instanceof AEItemKey itemKey ? itemKey : null;
    }

    private static ResourceLocation itemIdOf(OverloadPatternDetails.OutputSlot output) {
        var key = AEItemKey.of(output.template());
        if (key == null) {
            throw new IllegalArgumentException("output template must resolve to an item key");
        }
        return key.getId();
    }

    private record OutputRegistrationCandidate(String patternIdentity, int outputSlotIndex) {
    }
}
