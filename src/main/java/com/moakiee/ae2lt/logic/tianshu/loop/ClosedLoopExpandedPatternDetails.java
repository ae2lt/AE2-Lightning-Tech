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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

/** Execution snapshot for one member of an already-planned closed loop. */
public class ClosedLoopExpandedPatternDetails
        implements IPatternDetails, TimeWheelTaskPersistenceDefinition, IPrioritizedCraftingTask,
        IProviderLookupPattern, ISeedPreservingCraftingTask {
    public static final int CLOSED_LOOP_DISPATCH_PRIORITY = 1_000;
    protected final IPatternDetails delegate;
    protected final Set<appeng.api.stacks.AEKey> seedKeys;
    protected final Map<appeng.api.stacks.AEKey, Long> sharedOutputAmounts;
    private final AEItemKey persistenceDefinition;
    private final int dispatchOrder;
    private final IInput[] executionInputs;
    private final Map<appeng.api.stacks.AEKey, Long> seedCreditPerCopy;

    public ClosedLoopExpandedPatternDetails(IPatternDetails delegate,
                                            Map<appeng.api.stacks.AEKey, Long> seedAmounts,
                                            AEItemKey persistenceDefinition) {
        this(delegate, seedAmounts, persistenceDefinition, 0);
    }

    public ClosedLoopExpandedPatternDetails(IPatternDetails delegate,
                                            Map<appeng.api.stacks.AEKey, Long> seedAmounts,
                                            AEItemKey persistenceDefinition,
                                            int dispatchOrder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.seedKeys = Set.copyOf(seedAmounts.keySet());
        this.executionInputs = pinReusableSeedInputs(delegate, this.seedKeys);
        this.seedCreditPerCopy = seedCreditPerCopy(this.executionInputs, this.seedKeys);
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
        boolean reusableSeed = singleMemberLoop && seedAmounts.size() == 1
                && hasSeedInput(delegate, seedAmounts.keySet());
        if (delegate instanceof IMolecularAssemblerSupportedPattern molecular) {
            return reusableSeed
                    ? new SingleSeedClosedLoopMolecularPatternDetails(
                            molecular, seedAmounts, persistenceDefinition, dispatchOrder)
                    : new ClosedLoopMolecularPatternDetails(
                            molecular, seedAmounts, persistenceDefinition, dispatchOrder);
        }
        return reusableSeed
                ? new SingleSeedClosedLoopPatternDetails(
                        delegate, seedAmounts, persistenceDefinition, dispatchOrder)
                : new ClosedLoopExpandedPatternDetails(
                        delegate, seedAmounts, persistenceDefinition, dispatchOrder);
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
    @Override public Map<appeng.api.stacks.AEKey, Long> seedCreditPerCopy() {
        return seedCreditPerCopy;
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
        var result = source.clone();
        for (int slot = 0; slot < source.length; slot++) {
            var input = source[slot];
            var possible = input.getPossibleInputs();
            boolean ignoreSecondary = details instanceof
                    com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails overload
                    && overload.isFuzzyInput(slot);
            appeng.api.stacks.AEKey selected = null;
            long selectedAmount = 0L;
            for (var seedKey : seedKeys) {
                for (var candidate : possible) {
                    if (candidate.what() == null
                            || !sameSeedIdentity(candidate.what(), seedKey, ignoreSecondary)) continue;
                    if (selected != null && !selected.equals(seedKey)) {
                        throw new IllegalArgumentException(
                                "fuzzy closed-loop seed slot matches multiple concrete seed variants");
                    }
                    selected = seedKey;
                    selectedAmount = candidate.amount();
                }
            }
            if (selected != null) {
                result[slot] = new PinnedSeedInput(
                        selected, selectedAmount, input.getMultiplier(), input.getRemainingKey(selected));
            }
        }
        return result;
    }

    private static boolean sameSeedIdentity(
            appeng.api.stacks.AEKey candidate, appeng.api.stacks.AEKey seed,
            boolean ignoreSecondary) {
        return candidate.equals(seed)
                || (ignoreSecondary
                        && candidate.dropSecondary().equals(seed.dropSecondary()));
    }

    private static final class PinnedSeedInput implements IInput {
        private final GenericStack[] possible;
        private final long multiplier;
        @Nullable
        private final appeng.api.stacks.AEKey remaining;

        private PinnedSeedInput(appeng.api.stacks.AEKey selected, long amount, long multiplier,
                                @Nullable appeng.api.stacks.AEKey remaining) {
            this.possible = new GenericStack[] {new GenericStack(selected, amount)};
            this.multiplier = multiplier;
            this.remaining = remaining;
        }

        @Override public GenericStack[] getPossibleInputs() { return possible.clone(); }
        @Override public long getMultiplier() { return multiplier; }
        @Override public boolean isValid(appeng.api.stacks.AEKey input, Level level) {
            return possible[0].what().equals(input);
        }
        @Override public @Nullable appeng.api.stacks.AEKey getRemainingKey(
                appeng.api.stacks.AEKey template) {
            return possible[0].what().equals(template) ? remaining : null;
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
            long amount = com.moakiee.thunderbolt.core.planner.Sat.mul(
                    possible[0].amount(), input.getMultiplier());
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

    private static Map<appeng.api.stacks.AEKey, Long> seedCreditPerCopy(
            IInput[] inputs, Set<appeng.api.stacks.AEKey> seedKeys) {
        var result = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        for (var input : inputs) {
            for (var possible : input.getPossibleInputs()) {
                if (possible.what() == null || !seedKeys.contains(possible.what())) continue;
                long amount = com.moakiee.thunderbolt.core.planner.Sat.mul(
                        possible.amount(), input.getMultiplier());
                if (amount > 0) result.merge(possible.what(), amount,
                        com.moakiee.thunderbolt.core.planner.Sat::add);
                break;
            }
        }
        return Map.copyOf(result);
    }
}
