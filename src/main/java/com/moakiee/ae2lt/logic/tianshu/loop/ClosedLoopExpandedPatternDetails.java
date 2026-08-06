package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.world.level.Level;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelTaskPersistenceDefinition;
import com.moakiee.thunderbolt.ae2.api.crafting.IPrioritizedCraftingTask;
import com.moakiee.thunderbolt.ae2.api.crafting.IProviderLookupPattern;
import com.moakiee.thunderbolt.ae2.api.crafting.ISeedPreservingCraftingTask;
import com.moakiee.thunderbolt.ae2.api.crafting.IPlannedSeedSlotPattern;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

/** Execution snapshot for one member of an already-planned closed loop. */
public class ClosedLoopExpandedPatternDetails
        implements IPatternDetails, TimeWheelTaskPersistenceDefinition, IPrioritizedCraftingTask,
        IProviderLookupPattern, ISeedPreservingCraftingTask, IPlannedSeedSlotPattern {
    public static final int CLOSED_LOOP_DISPATCH_PRIORITY = 1_000;
    protected final IPatternDetails delegate;
    protected final Set<appeng.api.stacks.AEKey> seedKeys;
    protected final Set<appeng.api.stacks.AEKey> cycleKeys;
    protected final Map<appeng.api.stacks.AEKey, Long> sharedOutputAmounts;
    private final AEItemKey persistenceDefinition;
    private final int dispatchOrder;
    private final IInput[] executionInputs;
    private final UUID seedGroupId;
    private final boolean singleSeedInputPerMember;
    private final Map<Integer, appeng.api.stacks.AEKey> plannedSeedInputSlots;

    public ClosedLoopExpandedPatternDetails(IPatternDetails delegate,
                                            Map<appeng.api.stacks.AEKey, Long> seedAmounts,
                                            AEItemKey persistenceDefinition) {
        this(delegate, seedAmounts, persistenceDefinition, 0);
    }

    public ClosedLoopExpandedPatternDetails(IPatternDetails delegate,
                                            Map<appeng.api.stacks.AEKey, Long> seedAmounts,
                                            AEItemKey persistenceDefinition,
                                            int dispatchOrder) {
        this(delegate, seedAmounts, seedAmounts.keySet(), fallbackGroupId(persistenceDefinition),
                persistenceDefinition, dispatchOrder);
    }

    public ClosedLoopExpandedPatternDetails(IPatternDetails delegate,
                                            Map<appeng.api.stacks.AEKey, Long> seedAmounts,
                                            Set<appeng.api.stacks.AEKey> cycleKeys,
                                            UUID seedGroupId,
                                            AEItemKey persistenceDefinition,
                                            int dispatchOrder) {
        this(delegate, seedAmounts, cycleKeys, seedGroupId, seedAmounts.size() == 1,
                persistenceDefinition, dispatchOrder);
    }

    public ClosedLoopExpandedPatternDetails(IPatternDetails delegate,
                                            Map<appeng.api.stacks.AEKey, Long> seedAmounts,
                                            Set<appeng.api.stacks.AEKey> cycleKeys,
                                            UUID seedGroupId,
                                            boolean singleSeedInputPerMember,
                                            AEItemKey persistenceDefinition,
                                            int dispatchOrder) {
        this(delegate, seedAmounts, cycleKeys, seedGroupId, singleSeedInputPerMember,
                Map.of(), persistenceDefinition, dispatchOrder);
    }

    public ClosedLoopExpandedPatternDetails(IPatternDetails delegate,
                                            Map<appeng.api.stacks.AEKey, Long> seedAmounts,
                                            Set<appeng.api.stacks.AEKey> cycleKeys,
                                            UUID seedGroupId,
                                            boolean singleSeedInputPerMember,
                                            Map<Integer, appeng.api.stacks.AEKey> plannedSeedInputSlots,
                                            AEItemKey persistenceDefinition,
                                            int dispatchOrder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.seedKeys = Set.copyOf(seedAmounts.keySet());
        this.cycleKeys = Set.copyOf(cycleKeys);
        this.seedGroupId = Objects.requireNonNull(seedGroupId, "seedGroupId");
        this.singleSeedInputPerMember = singleSeedInputPerMember;
        this.plannedSeedInputSlots = Map.copyOf(plannedSeedInputSlots);
        this.executionInputs = this.plannedSeedInputSlots.isEmpty()
                ? pinReusableSeedInputs(delegate, this.seedKeys)
                : pinReusableSeedInputs(delegate, this.plannedSeedInputSlots);
        this.sharedOutputAmounts = sharedOutputAmounts(delegate, seedAmounts);
        this.persistenceDefinition = Objects.requireNonNull(
                persistenceDefinition, "persistenceDefinition");
        this.dispatchOrder = Math.max(0, dispatchOrder);
    }

    public static ClosedLoopExpandedPatternDetails wrap(
            IPatternDetails delegate, Set<appeng.api.stacks.AEKey> seedKeys,
            boolean singleMemberLoop, AEItemKey persistenceDefinition) {
        var seedAmounts = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        for (var seedKey : seedKeys) seedAmounts.put(seedKey, 1L);
        return wrap(delegate, seedAmounts, singleMemberLoop, persistenceDefinition, 0);
    }

    public static ClosedLoopExpandedPatternDetails wrap(
            IPatternDetails delegate, Map<appeng.api.stacks.AEKey, Long> seedAmounts,
            boolean singleMemberLoop, AEItemKey persistenceDefinition) {
        return wrap(delegate, seedAmounts, singleMemberLoop, persistenceDefinition, 0);
    }

    public static ClosedLoopExpandedPatternDetails wrap(
            IPatternDetails delegate, Map<appeng.api.stacks.AEKey, Long> seedAmounts,
            boolean singleMemberLoop, AEItemKey persistenceDefinition, int dispatchOrder) {
        return wrap(delegate, seedAmounts, seedAmounts.keySet(), fallbackGroupId(persistenceDefinition),
                singleMemberLoop, persistenceDefinition, dispatchOrder);
    }

    public static ClosedLoopExpandedPatternDetails wrap(
            IPatternDetails delegate, Map<appeng.api.stacks.AEKey, Long> seedAmounts,
            Set<appeng.api.stacks.AEKey> cycleKeys, UUID seedGroupId,
            boolean singleMemberLoop, AEItemKey persistenceDefinition, int dispatchOrder) {
        return wrap(delegate, seedAmounts, cycleKeys, seedGroupId, seedAmounts.size() == 1,
                singleMemberLoop, persistenceDefinition, dispatchOrder);
    }

    public static ClosedLoopExpandedPatternDetails wrap(
            IPatternDetails delegate, Map<appeng.api.stacks.AEKey, Long> seedAmounts,
            Set<appeng.api.stacks.AEKey> cycleKeys, UUID seedGroupId,
            boolean singleSeedInputPerMember,
            boolean singleMemberLoop, AEItemKey persistenceDefinition, int dispatchOrder) {
        return wrap(delegate, seedAmounts, cycleKeys, seedGroupId, singleSeedInputPerMember,
                Map.of(), singleMemberLoop, persistenceDefinition, dispatchOrder);
    }

    public static ClosedLoopExpandedPatternDetails wrap(
            IPatternDetails delegate, Map<appeng.api.stacks.AEKey, Long> seedAmounts,
            Set<appeng.api.stacks.AEKey> cycleKeys, UUID seedGroupId,
            boolean singleSeedInputPerMember,
            Map<Integer, appeng.api.stacks.AEKey> plannedSeedInputSlots,
            boolean singleMemberLoop, AEItemKey persistenceDefinition, int dispatchOrder) {
        boolean reusableSeed = singleMemberLoop && seedAmounts.size() == 1
                && hasSeedInput(delegate, seedAmounts.keySet());
        if (delegate instanceof IMolecularAssemblerSupportedPattern molecular) {
            return reusableSeed
                    ? new SingleSeedClosedLoopMolecularPatternDetails(
                            molecular, seedAmounts, cycleKeys, seedGroupId,
                            singleSeedInputPerMember,
                            plannedSeedInputSlots,
                            persistenceDefinition, dispatchOrder)
                    : new ClosedLoopMolecularPatternDetails(
                            molecular, seedAmounts, cycleKeys, seedGroupId,
                            singleSeedInputPerMember,
                            plannedSeedInputSlots,
                            persistenceDefinition, dispatchOrder);
        }
        return reusableSeed
                ? new SingleSeedClosedLoopPatternDetails(
                        delegate, seedAmounts, cycleKeys, seedGroupId,
                        singleSeedInputPerMember,
                        plannedSeedInputSlots,
                        persistenceDefinition, dispatchOrder)
                : new ClosedLoopExpandedPatternDetails(
                        delegate, seedAmounts, cycleKeys, seedGroupId,
                        singleSeedInputPerMember,
                        plannedSeedInputSlots,
                        persistenceDefinition, dispatchOrder);
    }

    public IPatternDetails delegate() {
        return delegate;
    }

    @Override
    public IPatternDetails providerLookupPattern() {
        return delegate;
    }

    @Override
    public AEItemKey timeWheelPersistenceDefinition() {
        return persistenceDefinition;
    }

    @Override public int dispatchPriority() { return CLOSED_LOOP_DISPATCH_PRIORITY; }
    @Override public int dispatchOrder() { return dispatchOrder; }
    @Override public UUID reusableSeedGroupId() { return seedGroupId; }
    @Override public Set<appeng.api.stacks.AEKey> reusableSeedCycleKeys() { return cycleKeys; }
    @Override public boolean hasSingleSeedInputPerMember() {
        return singleSeedInputPerMember;
    }
    @Override public Map<Integer, appeng.api.stacks.AEKey> plannedSeedInputSlots() {
        return plannedSeedInputSlots;
    }

    @Override public AEItemKey getDefinition() { return delegate.getDefinition(); }
    @Override public IInput[] getInputs() { return executionInputs.clone(); }
    @Override public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
    @Override public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }
    @Override public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ClosedLoopExpandedPatternDetails other
                && persistenceDefinition.equals(other.persistenceDefinition);
    }

    @Override public int hashCode() { return persistenceDefinition.hashCode(); }

    protected boolean isReusableSeedInput(int slot, appeng.api.stacks.AEKey concreteKey) {
        if (concreteKey == null || !seedKeys.contains(concreteKey)) return false;
        var inputs = getInputs();
        if (slot < 0 || slot >= inputs.length) return false;
        return true;
    }

    protected long sharedBatchOutputAmount(appeng.api.stacks.AEKey outputKey) {
        return sharedOutputAmounts.getOrDefault(outputKey, 0L);
    }

    private static boolean hasSeedInput(
            IPatternDetails details, Set<appeng.api.stacks.AEKey> seedKeys) {
        var inputs = details.getInputs();
        for (int slot = 0; slot < inputs.length; slot++) {
            for (var possible : inputs[slot].getPossibleInputs()) {
                if (possible.what() == null || !seedKeys.contains(possible.what())) continue;
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces a fuzzy reusable-seed slot with the concrete seed variant selected by analysis.
     * This prevents successive loop rounds from silently switching NBT/damage variants.
     */
    public static IInput[] pinReusableSeedInputs(
            IPatternDetails details, Set<appeng.api.stacks.AEKey> seedKeys) {
        var source = details.getInputs();
        // AE2's real crafting details may return an array whose runtime component type is its
        // private input implementation. Cloning preserves that concrete array type, so storing
        // our PinnedSeedInput would throw ArrayStoreException. Normalize to the API interface.
        IInput[] result = java.util.Arrays.copyOf(source, source.length, IInput[].class);
        for (int slot = 0; slot < source.length; slot++) {
            var input = source[slot];
            var possible = input.getPossibleInputs();
            var providerDetails = com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates
                    .forProviderLookup(details);
            boolean ignoreSecondary = providerDetails instanceof
                    com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails overload
                    && overload.isFuzzyInput(slot);
            appeng.api.stacks.AEKey selected = null;
            long selectedAmount = 0L;
            for (var seedKey : seedKeys) {
                GenericStack firstMatch = null;
                GenericStack exactMatch = null;
                Long physicalAmount = null;
                for (var candidate : possible) {
                    if (candidate.what() == null
                            || !sameSeedIdentity(candidate.what(), seedKey, ignoreSecondary)) continue;
                    if (physicalAmount != null && physicalAmount != candidate.amount()) {
                        throw new IllegalArgumentException(
                                "fuzzy closed-loop seed candidates use different physical amounts");
                    }
                    physicalAmount = candidate.amount();
                    if (firstMatch == null) firstMatch = candidate;
                    if (candidate.what().equals(seedKey)) exactMatch = candidate;
                }
                var chosen = exactMatch != null ? exactMatch : firstMatch;
                if (chosen != null) {
                    if (selected != null && !selected.equals(seedKey)) {
                        throw new IllegalArgumentException(
                                "fuzzy closed-loop seed slot matches multiple concrete seed variants");
                    }
                    selected = seedKey;
                    selectedAmount = chosen.amount();
                }
            }
            if (selected != null) {
                var selectedRemainder = input.getRemainingKey(selected);
                var executionCandidates = new java.util.ArrayList<GenericStack>();
                if (ignoreSecondary) {
                    executionCandidates.add(new GenericStack(selected, selectedAmount));
                } else {
                    for (var candidate : possible) {
                        if (candidate.what() != null && transitionEquivalent(
                                input, selected, selectedAmount, selectedRemainder, candidate)) {
                            executionCandidates.add(candidate);
                        }
                    }
                }
                result[slot] = new PinnedSeedInput(
                        input,
                        executionCandidates.toArray(GenericStack[]::new),
                        selected,
                        input.getMultiplier(),
                        ignoreSecondary);
            }
        }
        return result;
    }

    /** Pins the exact logical seed selected for each slot by closed-loop analysis. */
    public static IInput[] pinReusableSeedInputs(
            IPatternDetails details,
            Map<Integer, appeng.api.stacks.AEKey> plannedSeedInputSlots) {
        var source = details.getInputs();
        IInput[] result = java.util.Arrays.copyOf(source, source.length, IInput[].class);
        for (var entry : plannedSeedInputSlots.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= source.length || entry.getValue() == null) {
                throw new IllegalArgumentException("closed-loop seed slot mapping is invalid");
            }
            result[slot] = pinReusableSeedInput(details, source[slot], slot, entry.getValue());
        }
        return result;
    }

    private static IInput pinReusableSeedInput(
            IPatternDetails details, IInput input, int slot,
            appeng.api.stacks.AEKey selected) {
        var providerDetails = com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates
                .forProviderLookup(details);
        boolean ignoreSecondary = providerDetails instanceof
                com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails overload
                && overload.isFuzzyInput(slot);
        var possible = input.getPossibleInputs();
        GenericStack firstMatch = null;
        GenericStack exactMatch = null;
        Long physicalAmount = null;
        for (var candidate : possible) {
            if (candidate.what() == null
                    || !sameSeedIdentity(candidate.what(), selected, ignoreSecondary)) continue;
            if (physicalAmount != null && physicalAmount != candidate.amount()) {
                throw new IllegalArgumentException(
                        "fuzzy closed-loop seed candidates use different physical amounts");
            }
            physicalAmount = candidate.amount();
            if (firstMatch == null) firstMatch = candidate;
            if (candidate.what().equals(selected)) exactMatch = candidate;
        }
        var chosen = exactMatch != null ? exactMatch : firstMatch;
        if (chosen == null) {
            throw new IllegalArgumentException("planned closed-loop seed is not valid for its slot");
        }
        var selectedRemainder = input.getRemainingKey(selected);
        var executionCandidates = new java.util.ArrayList<GenericStack>();
        if (ignoreSecondary) {
            executionCandidates.add(new GenericStack(selected, chosen.amount()));
        } else {
            for (var candidate : possible) {
                if (candidate.what() != null && transitionEquivalent(
                        input, selected, chosen.amount(), selectedRemainder, candidate)) {
                    executionCandidates.add(candidate);
                }
            }
        }
        return new PinnedSeedInput(
                input, executionCandidates.toArray(GenericStack[]::new), selected,
                input.getMultiplier(), ignoreSecondary);
    }

    private static boolean transitionEquivalent(
            IInput input,
            appeng.api.stacks.AEKey selected,
            long selectedAmount,
            @Nullable appeng.api.stacks.AEKey selectedRemainder,
            GenericStack candidate) {
        if (candidate.amount() != selectedAmount) return false;
        var candidateRemainder = input.getRemainingKey(candidate.what());
        if (selectedRemainder == null) return candidateRemainder == null;
        if (candidateRemainder == null) return false;
        if (selectedRemainder.equals(selected)) {
            return candidateRemainder.equals(candidate.what());
        }
        return selectedRemainder.equals(candidateRemainder);
    }

    private static boolean sameSeedIdentity(
            appeng.api.stacks.AEKey candidate, appeng.api.stacks.AEKey seed,
            boolean ignoreSecondary) {
        return candidate.equals(seed)
                || (ignoreSecondary
                        && candidate.dropSecondary().equals(seed.dropSecondary()));
    }

    private static final class PinnedSeedInput implements IInput {
        private final IInput source;
        private final GenericStack[] possible;
        private final appeng.api.stacks.AEKey selected;
        private final long multiplier;
        private final boolean ignoreSecondary;

        private PinnedSeedInput(
                                IInput source,
                                GenericStack[] possible,
                                appeng.api.stacks.AEKey selected,
                                long multiplier,
                                boolean ignoreSecondary) {
            this.source = source;
            this.possible = possible.clone();
            this.selected = selected;
            this.multiplier = multiplier;
            this.ignoreSecondary = ignoreSecondary;
        }

        @Override public GenericStack[] getPossibleInputs() { return possible.clone(); }
        @Override public long getMultiplier() { return multiplier; }
        @Override public boolean isValid(appeng.api.stacks.AEKey input, Level level) {
            boolean allowed = ignoreSecondary
                    ? sameSeedIdentity(selected, input, true)
                    : java.util.Arrays.stream(possible)
                            .anyMatch(candidate -> candidate.what().equals(input));
            // ID_ONLY is the provider contract: the underlying AE2 input often validates only
            // the encoded component state and would reject a legal runtime damage/NBT variant.
            return allowed && (ignoreSecondary || source.isValid(input, level));
        }
        @Override public @Nullable appeng.api.stacks.AEKey getRemainingKey(
                appeng.api.stacks.AEKey template) {
            boolean allowed = ignoreSecondary
                    ? sameSeedIdentity(selected, template, true)
                    : java.util.Arrays.stream(possible)
                            .anyMatch(candidate -> candidate.what().equals(template));
            if (!allowed) return null;
            // The runtime component state defines the returned container/tool state. Returning the
            // cached planned remainder here would silently turn e.g. damage N into encoded damage.
            return source.getRemainingKey(template);
        }
    }

    private static Map<appeng.api.stacks.AEKey, Long> sharedOutputAmounts(
            IPatternDetails details, Map<appeng.api.stacks.AEKey, Long> seedAmounts) {
        var returnedAsRemainder = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        for (var input : details.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length != 1 || possible[0].what() == null) continue;
            var key = possible[0].what();
            if (!seedAmounts.containsKey(key) || !key.equals(input.getRemainingKey(key))) continue;
            long amount = input.getMultiplier();
            returnedAsRemainder.merge(key, amount,
                    com.moakiee.thunderbolt.core.planner.Sat::add);
        }
        var result = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        for (var entry : seedAmounts.entrySet()) {
            long fromOutputs = Math.max(0L,
                    entry.getValue() - returnedAsRemainder.getOrDefault(entry.getKey(), 0L));
            if (fromOutputs > 0) result.put(entry.getKey(), fromOutputs);
        }
        return Map.copyOf(result);
    }

    private static UUID fallbackGroupId(AEItemKey persistenceDefinition) {
        return UUID.nameUUIDFromBytes(
                Objects.requireNonNull(persistenceDefinition, "persistenceDefinition")
                        .toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
