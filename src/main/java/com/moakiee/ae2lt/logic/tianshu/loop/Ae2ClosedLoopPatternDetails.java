package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import com.moakiee.thunderbolt.ae2.crafting.PatternFiringExpander;
import com.moakiee.thunderbolt.ae2.crafting.ExecuteLoopPattern;
import com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.planner.Sat;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelPoolRestrictedPattern;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;
import com.moakiee.thunderbolt.ae2.timewheel.ReusableSeedPattern;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import java.util.UUID;

public final class Ae2ClosedLoopPatternDetails
        implements TianshuClosedLoopPatternDetails, PatternFiringExpander,
        TimeWheelPoolRestrictedPattern, ReusableSeedPattern {
    private final AEItemKey definition;
    private final ClosedLoopPatternPayload payload;
    private final IInput[] inputs;
    private final List<ExpandedMember> members;
    private final UUID owningTianshuId;
    private final Map<AEKey, Long> availableSeedSnapshot;
    private final Set<AEKey> cycleKeys;
    private final Map<AEKey, Set<AEKey>> acceptedSeedVariants;
    private final Set<AEKey> fuzzySeedKeys;
    private final boolean singleSeedInputPerMember;

    public Ae2ClosedLoopPatternDetails(AEItemKey definition, ClosedLoopPatternPayload payload, Level level) {
        this(definition, payload, level, null, Map.of());
    }

    public Ae2ClosedLoopPatternDetails(AEItemKey definition, ClosedLoopPatternPayload payload,
                                       Level level, UUID owningTianshuId) {
        this(definition, payload, level, owningTianshuId, Map.of());
    }

    public Ae2ClosedLoopPatternDetails(AEItemKey definition, ClosedLoopPatternPayload payload,
                                       Level level, UUID owningTianshuId,
                                       Map<AEKey, Long> availableSeedSnapshot) {
        this(definition, payload, level, owningTianshuId,
                ignored -> Map.copyOf(availableSeedSnapshot));
    }

    public Ae2ClosedLoopPatternDetails(
            AEItemKey definition,
            ClosedLoopPatternPayload payload,
            Level level,
            UUID owningTianshuId,
            Function<ReusableSeedPattern, Map<AEKey, Long>> availableSeedSnapshotFactory) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.owningTianshuId = owningTianshuId;
        Objects.requireNonNull(availableSeedSnapshotFactory, "availableSeedSnapshotFactory");
        var allInputs = new ArrayList<GenericStack>(payload.seeds().size() + payload.externalInputs().size());
        for (var seed : payload.seeds()) {
            allInputs.add(new GenericStack(
                    seed.what(), Sat.mul(seed.amount(), payload.seedMultiplier())));
        }
        for (var input : payload.externalInputs()) allInputs.add(input);
        inputs = new IInput[allInputs.size()];
        int slot = 0;
        for (var seed : payload.seeds()) {
            inputs[slot++] = new ExactInput(new GenericStack(
                    seed.what(), Sat.mul(seed.amount(), payload.seedMultiplier())), true);
        }
        for (var input : payload.externalInputs()) inputs[slot++] = new ExactInput(input, false);

        var rawMembers = new ArrayList<IPatternDetails>(payload.memberPatterns().size());
        for (var member : payload.memberPatterns()) {
            var details = PatternDetailsHelper.decodePattern(
                    member.pattern().toItemStack(level.registryAccess()), level);
            if (details == null || details instanceof TianshuClosedLoopPatternDetails) {
                throw new IllegalArgumentException("closed-loop member pattern is no longer decodable");
            }
            rawMembers.add(details);
        }
        var seedAmounts = new java.util.LinkedHashMap<AEKey, Long>();
        for (var seed : payload.seeds()) seedAmounts.merge(seed.what(), seed.amount(), Sat::add);
        var acceptedVariants = new LinkedHashMap<AEKey, Set<AEKey>>();
        var fuzzySeeds = new java.util.LinkedHashSet<AEKey>();
        collectAcceptedSeedVariants(
                rawMembers, seedAmounts.keySet(), acceptedVariants, fuzzySeeds);
        this.acceptedSeedVariants = Map.copyOf(acceptedVariants);
        this.fuzzySeedKeys = Set.copyOf(fuzzySeeds);
        this.cycleKeys = ClosedLoopCycleKeys.analyze(rawMembers, seedAmounts.keySet());

        var analyzedMembers = new ArrayList<ClosedLoopPatternAnalyzer.Member>(
                payload.memberPatterns().size());
        for (int i = 0; i < payload.memberPatterns().size(); i++) {
            analyzedMembers.add(new ClosedLoopPatternAnalyzer.Member(
                    rawMembers.get(i), payload.memberPatterns().get(i).copiesPerCycle()));
        }
        var memberFlows = ClosedLoopPatternAnalyzer.deriveMemberFlows(
                analyzedMembers, payload.seeds());
        if (memberFlows.size() != rawMembers.size()) {
            throw new IllegalArgumentException("closed-loop member seed transitions are unavailable");
        }
        this.singleSeedInputPerMember =
                ClosedLoopPatternAnalyzer.hasSingleSeedInputPerMember(memberFlows);

        var decodedMembers = new ArrayList<ExpandedMember>(payload.memberPatterns().size());
        int memberIndex = 0;
        for (var member : payload.memberPatterns()) {
            var details = rawMembers.get(memberIndex);
            var item = (com.moakiee.ae2lt.item.ClosedLoopPatternItem) definition.getItem();
            var persistenceDefinition = AEItemKey.of(item.createExecutionMemberStack(
                    payload, memberIndex, level.registryAccess()));
            if (persistenceDefinition == null) {
                throw new IllegalArgumentException("closed-loop member persistence key is unavailable");
            }
            decodedMembers.add(new ExpandedMember(
                    ClosedLoopExpandedPatternDetails.wrap(
                            details, seedAmounts, cycleKeys, payload.patternId(),
                            singleSeedInputPerMember,
                            payload.memberPatterns().size() == 1,
                            persistenceDefinition, memberIndex),
                    member.copiesPerCycle(), memberFlows.get(memberIndex)));
            memberIndex++;
        }
        members = List.copyOf(decodedMembers);
        this.availableSeedSnapshot = Map.copyOf(
                availableSeedSnapshotFactory.apply(this));
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs.clone();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return payload.netOutputs();
    }

    @Override
    public ClosedLoopPatternPayload closedLoopPayload() {
        return payload;
    }

    @Override
    public Map<IPatternDetails, Long> expandPatternFirings(long macroFirings) {
        if (macroFirings <= 0) return Map.of();
        var result = new LinkedHashMap<IPatternDetails, Long>();
        for (var member : members) {
            for (var slice : splitMemberFlow(member.flow(), member.copiesPerCycle())) {
                long count = Sat.mul(macroFirings, slice.copiesPerCycle());
                result.put(new ExecuteLoopPattern(
                        member.details(),
                        counter(slice.inputSeed()),
                        counter(slice.outputSeed())), count);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public boolean acceptsTimeWheelPool(TimeWheelCraftingCpuPoolHost host) {
        return owningTianshuId != null
                && host instanceof TianshuSupercomputerPortBlockEntity port
                && owningTianshuId.equals(port.getTianshuId());
    }

    @Override
    public Map<AEKey, Long> totalReusableSeedRequirements() {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var seed : payload.seeds()) {
            result.merge(seed.what(), Sat.mul(seed.amount(), payload.seedMultiplier()), Sat::add);
        }
        return Map.copyOf(result);
    }

    @Override
    public Object reusableSeedStorageScope() {
        return owningTianshuId != null ? owningTianshuId : payload.patternId();
    }

    @Override
    public boolean acceptsReusableSeedVariant(AEKey planned, AEKey actual) {
        if (planned == null || actual == null) return false;
        if (planned.equals(actual)) return true;
        if (fuzzySeedKeys.contains(planned)
                && planned.dropSecondary().equals(actual.dropSecondary())) {
            return true;
        }
        return acceptedSeedVariants.getOrDefault(planned, Set.of()).contains(actual);
    }

    @Override
    public UUID reusableSeedGroupId() {
        return payload.patternId();
    }

    @Override
    public Set<AEKey> reusableSeedCycleKeys() {
        return cycleKeys;
    }

    @Override
    public boolean hasSingleSeedInputPerMember() {
        return singleSeedInputPerMember;
    }

    @Override
    public Map<AEKey, Long> availableReusableSeedSnapshot() {
        return availableSeedSnapshot;
    }

    private static void collectAcceptedSeedVariants(
            List<IPatternDetails> members,
            Set<AEKey> seeds,
            Map<AEKey, Set<AEKey>> accepted,
            Set<AEKey> fuzzySeeds) {
        for (var details : members) {
            var provider = CraftingPatternDelegates.forProviderLookup(details);
            var overload = provider instanceof OverloadedProviderOnlyPatternDetails candidate
                    ? candidate : null;
            var inputs = details.getInputs();
            for (int slot = 0; slot < inputs.length; slot++) {
                boolean fuzzy = overload != null && overload.isFuzzyInput(slot);
                var possible = inputs[slot].getPossibleInputs();
                for (var seed : seeds) {
                    boolean matchesSlot = false;
                    for (var option : possible) {
                        if (option.what() != null && (seed.equals(option.what())
                                || (fuzzy && seed.dropSecondary()
                                        .equals(option.what().dropSecondary())))) {
                            matchesSlot = true;
                            break;
                        }
                    }
                    if (!matchesSlot) continue;
                    if (fuzzy) fuzzySeeds.add(seed);
                    var variants = new java.util.LinkedHashSet<AEKey>(
                            accepted.getOrDefault(seed, Set.of()));
                    for (var option : possible) {
                        if (option.what() != null && (!fuzzy
                                || seed.dropSecondary().equals(option.what().dropSecondary()))) {
                            variants.add(option.what());
                        }
                    }
                    accepted.put(seed, Set.copyOf(variants));
                }
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Ae2ClosedLoopPatternDetails other && definition.equals(other.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    private static final class ExactInput implements IInput {
        private final GenericStack[] possibleInputs;

        private final boolean returned;

        private ExactInput(GenericStack input, boolean returned) {
            possibleInputs = new GenericStack[] { input };
            this.returned = returned;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs.clone();
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return possibleInputs[0].what().equals(input);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return returned && possibleInputs[0].what().equals(template) ? template : null;
        }
    }

    /**
     * Converts one atomic member-group transition into a bounded number of per-execution
     * transitions. Most flows produce one slice. Remainders produce at most one extra boundary
     * per seed key, so this never expands once per copy even for very large coefficients.
     */
    static List<MemberFlowSlice> splitMemberFlow(
            ClosedLoopPatternAnalyzer.MemberFlow flow, long copiesPerCycle) {
        if (flow == null || copiesPerCycle <= 0) return List.of();
        var boundaries = new TreeSet<Long>();
        boundaries.add(0L);
        boundaries.add(copiesPerCycle);
        addRemainderBoundaries(boundaries, flow.inputSeed(), copiesPerCycle);
        addRemainderBoundaries(boundaries, flow.outputSeed(), copiesPerCycle);

        var points = new ArrayList<>(boundaries);
        var slices = new ArrayList<MemberFlowSlice>(Math.max(1, points.size() - 1));
        for (int i = 0; i + 1 < points.size(); i++) {
            long start = points.get(i);
            long end = points.get(i + 1);
            if (end <= start) continue;
            slices.add(new MemberFlowSlice(
                    perCopyAt(flow.inputSeed(), copiesPerCycle, start),
                    perCopyAt(flow.outputSeed(), copiesPerCycle, start),
                    end - start));
        }
        return List.copyOf(slices);
    }

    private static void addRemainderBoundaries(
            Set<Long> boundaries, Map<AEKey, Long> values, long copiesPerCycle) {
        for (var amount : values.values()) {
            if (amount == null || amount <= 0) continue;
            long remainder = amount % copiesPerCycle;
            if (remainder > 0) boundaries.add(remainder);
        }
    }

    private static Map<AEKey, Long> perCopyAt(
            Map<AEKey, Long> values, long copiesPerCycle, long copyIndex) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var entry : values.entrySet()) {
            long total = entry.getValue();
            if (total <= 0) continue;
            long amount = total / copiesPerCycle;
            if (copyIndex < total % copiesPerCycle) amount = Sat.add(amount, 1L);
            if (amount > 0) result.put(entry.getKey(), amount);
        }
        return Map.copyOf(result);
    }

    private static appeng.api.stacks.KeyCounter counter(Map<AEKey, Long> values) {
        var result = new appeng.api.stacks.KeyCounter();
        for (var entry : values.entrySet()) {
            if (entry.getValue() > 0) result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }

    record MemberFlowSlice(
            Map<AEKey, Long> inputSeed,
            Map<AEKey, Long> outputSeed,
            long copiesPerCycle) {
        MemberFlowSlice {
            inputSeed = Map.copyOf(inputSeed);
            outputSeed = Map.copyOf(outputSeed);
        }
    }

    private record ExpandedMember(
            IPatternDetails details,
            long copiesPerCycle,
            ClosedLoopPatternAnalyzer.MemberFlow flow) {
    }
}
