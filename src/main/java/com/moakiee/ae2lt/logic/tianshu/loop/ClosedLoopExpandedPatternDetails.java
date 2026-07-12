package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelTaskPersistenceDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

/** Execution snapshot for one member of an already-planned closed loop. */
public class ClosedLoopExpandedPatternDetails
        implements IPatternDetails, TimeWheelTaskPersistenceDefinition {
    protected final IPatternDetails delegate;
    protected final Set<appeng.api.stacks.AEKey> seedKeys;
    protected final Map<appeng.api.stacks.AEKey, Long> sharedOutputAmounts;
    private final AEItemKey persistenceDefinition;
    protected final int batchParallelism;

    public ClosedLoopExpandedPatternDetails(IPatternDetails delegate,
                                            Map<appeng.api.stacks.AEKey, Long> seedAmounts,
                                            AEItemKey persistenceDefinition,
                                            int batchParallelism) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.seedKeys = Set.copyOf(seedAmounts.keySet());
        this.sharedOutputAmounts = sharedOutputAmounts(delegate, seedAmounts);
        this.persistenceDefinition = Objects.requireNonNull(
                persistenceDefinition, "persistenceDefinition");
        this.batchParallelism = Math.max(1, batchParallelism);
    }

    public static ClosedLoopExpandedPatternDetails wrap(
            IPatternDetails delegate, Set<appeng.api.stacks.AEKey> seedKeys,
            boolean singleMemberLoop, AEItemKey persistenceDefinition, int batchParallelism) {
        var seedAmounts = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        for (var seedKey : seedKeys) seedAmounts.put(seedKey, 1L);
        return wrap(delegate, seedAmounts, singleMemberLoop, persistenceDefinition, batchParallelism);
    }

    public static ClosedLoopExpandedPatternDetails wrap(
            IPatternDetails delegate, Map<appeng.api.stacks.AEKey, Long> seedAmounts,
            boolean singleMemberLoop, AEItemKey persistenceDefinition, int batchParallelism) {
        boolean reusableSeed = singleMemberLoop && seedAmounts.size() == 1
                && hasSeedInput(delegate, seedAmounts.keySet());
        if (delegate instanceof IMolecularAssemblerSupportedPattern molecular) {
            return reusableSeed
                    ? new SingleSeedClosedLoopMolecularPatternDetails(
                            molecular, seedAmounts, persistenceDefinition, batchParallelism)
                    : new ClosedLoopMolecularPatternDetails(
                            molecular, seedAmounts, persistenceDefinition, batchParallelism);
        }
        return reusableSeed
                ? new SingleSeedClosedLoopPatternDetails(
                        delegate, seedAmounts, persistenceDefinition, batchParallelism)
                : new ClosedLoopExpandedPatternDetails(
                        delegate, seedAmounts, persistenceDefinition, batchParallelism);
    }

    public IPatternDetails delegate() {
        return delegate;
    }

    @Override
    public AEItemKey timeWheelPersistenceDefinition() {
        return persistenceDefinition;
    }

    @Override public AEItemKey getDefinition() { return delegate.getDefinition(); }
    @Override public IInput[] getInputs() { return delegate.getInputs(); }
    @Override public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
    @Override public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }
    @Override public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ClosedLoopExpandedPatternDetails other) {
            return getDefinition().equals(other.getDefinition());
        }
        return obj instanceof IPatternDetails other && getDefinition().equals(other.getDefinition());
    }

    @Override public int hashCode() { return getDefinition().hashCode(); }

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
}
