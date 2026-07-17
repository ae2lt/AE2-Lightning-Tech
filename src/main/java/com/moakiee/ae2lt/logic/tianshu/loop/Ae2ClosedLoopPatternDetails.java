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
import com.moakiee.ae2lt.logic.tianshu.TianshuCraftingCpuHost;
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
    private final Set<AEKey> universallyFuzzySeedKeys;
    /**
     * Host variants are extracted before the CPU can assign them to individual P2 accounts.
     * A multi-unit P2 bundle could therefore be split into incompatible concrete states (for
     * example {@code A + X} for a {@code 2A} slot). Until host extraction is bundle-aware, those
     * bootstrap keys must use the exact planned representation.
     */
    private final Set<AEKey> exactOnlyHostSeedKeys;
    private final boolean singleSeedInputPerMember;
    private final ClosedLoopConsumerRouting.RoutingPlan consumerRouting;

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
                    seed.what(), Sat.mul(seed.amount(), payload.executionSeedMultiplier())));
        }
        for (var input : payload.externalInputs()) allInputs.add(input);
        inputs = new IInput[allInputs.size()];
        int slot = 0;
        for (var seed : payload.seeds()) {
            inputs[slot++] = new ExactInput(new GenericStack(
                    seed.what(), Sat.mul(seed.amount(), payload.executionSeedMultiplier())), true);
        }
        for (var input : payload.externalInputs()) inputs[slot++] = new ExactInput(input, false);

        var rawMembers = new ArrayList<IPatternDetails>(payload.memberPatterns().size());
        for (var member : payload.memberPatterns()) {
            var memberStack = member.pattern().toItemStack(level.registryAccess());
            if (memberStack.getItem() instanceof com.moakiee.ae2lt.item.ClosedLoopPatternItem) {
                throw new IllegalArgumentException(
                        "closed-loop runtime payload must contain only flattened ordinary members");
            }
            var details = PatternDetailsHelper.decodePattern(memberStack, level);
            if (details == null || details instanceof TianshuClosedLoopPatternDetails) {
                throw new IllegalArgumentException("closed-loop member pattern is no longer decodable");
            }
            rawMembers.add(details);
        }
        var seedAmounts = new java.util.LinkedHashMap<AEKey, Long>();
        for (var seed : payload.seeds()) seedAmounts.merge(seed.what(), seed.amount(), Sat::add);
        this.cycleKeys = ClosedLoopCycleKeys.analyze(rawMembers, seedAmounts.keySet());

        var analyzedMembers = new ArrayList<ClosedLoopPatternAnalyzer.Member>(
                payload.memberPatterns().size());
        for (int i = 0; i < payload.memberPatterns().size(); i++) {
            analyzedMembers.add(new ClosedLoopPatternAnalyzer.Member(
                    rawMembers.get(i),
                    payload.memberPatterns().get(i).copiesPerCycle(),
                    payload.memberPatterns().get(i).seedWaveCopies()));
        }
        var memberFlows = ClosedLoopPatternAnalyzer.deriveMemberFlows(
                analyzedMembers, payload.seeds());
        if (memberFlows.size() != rawMembers.size()) {
            throw new IllegalArgumentException("closed-loop member seed transitions are unavailable");
        }
        validateFuzzyOutputSeedConsumers(analyzedMembers, memberFlows);
        var acceptedVariants = new LinkedHashMap<AEKey, Set<AEKey>>();
        var anyFuzzySeeds = new java.util.LinkedHashSet<AEKey>();
        var universallyFuzzySeeds = new java.util.LinkedHashSet<AEKey>();
        collectAcceptedSeedVariants(
                rawMembers, memberFlows, seedAmounts.keySet(), acceptedVariants,
                anyFuzzySeeds, universallyFuzzySeeds);
        this.acceptedSeedVariants = Map.copyOf(acceptedVariants);
        this.universallyFuzzySeedKeys = Set.copyOf(universallyFuzzySeeds);
        this.singleSeedInputPerMember = isSharedSeedPoolSafe(
                ClosedLoopPatternAnalyzer.hasSingleSeedInputPerMember(memberFlows),
                seedAmounts.keySet(), acceptedVariants, anyFuzzySeeds);
        this.consumerRouting = ClosedLoopConsumerRouting.compile(
                payload.patternId(), memberFlows);
        if (!this.consumerRouting.bootstrapSeed().equals(Map.copyOf(seedAmounts))) {
            throw new IllegalArgumentException(
                    "closed-loop consumer bootstrap does not match the encoded seed state");
        }

        var decodedMembers = new ArrayList<ExpandedMember>(payload.memberPatterns().size());
        int memberIndex = 0;
        for (var member : payload.memberPatterns()) {
            var details = rawMembers.get(memberIndex);
            var memberFlow = memberFlows.get(memberIndex);
            var item = (com.moakiee.ae2lt.item.ClosedLoopPatternItem) definition.getItem();
            var persistenceDefinition = AEItemKey.of(item.createExecutionMemberStack(
                    payload, memberIndex, level.registryAccess()));
            if (persistenceDefinition == null) {
                throw new IllegalArgumentException("closed-loop member persistence key is unavailable");
            }
            decodedMembers.add(new ExpandedMember(
                    ClosedLoopExpandedPatternDetails.wrap(
                            details, memberSeedAmounts(seedAmounts, memberFlow.inputSeed().keySet()),
                            cycleKeys, payload.patternId(),
                            singleSeedInputPerMember,
                            memberFlow.inputSeedBySlot(),
                            payload.memberPatterns().size() == 1,
                            persistenceDefinition, memberIndex),
                    member.copiesPerCycle(), member.seedWaveCopies(), memberFlow));
            memberIndex++;
        }
        members = List.copyOf(decodedMembers);
        this.exactOnlyHostSeedKeys = exactOnlyHostSeedKeys(
                memberFlows,
                payload.memberPatterns().stream()
                        .map(ClosedLoopMemberPattern::seedWaveCopies)
                        .toList(),
                consumerRouting);
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
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            var member = members.get(memberIndex);
            var consumer = consumerRouting.consumers().get(memberIndex);
            var producer = consumerRouting.producers().get(memberIndex);
            for (var slice : splitMemberFlow(
                    member.flow(), member.seedWaveCopies(), producer.targets())) {
                long count = expandedSliceCount(
                        macroFirings,
                        payload.seedWaveRepetitions(),
                        slice.copiesPerCycle());
                var sharedCredits = singleSeedInputPerMember
                        ? selectCredits(
                                slice.outputSeedCredits(), producer.wrappedTargets(), true)
                        : Map.<UUID, Map<AEKey, Long>>of();
                var consumerCredits = singleSeedInputPerMember
                        ? selectCredits(
                                slice.outputSeedCredits(), producer.wrappedTargets(), false)
                        : slice.outputSeedCredits();
                result.put(new ExecuteLoopPattern(
                        member.details(),
                        consumer.consumerId(),
                        scaledCounter(consumer.bootstrapSeed(), payload.executionSeedMultiplier()),
                        counter(slice.inputSeed()),
                        counters(consumerCredits),
                        counters(sharedCredits)), count);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public boolean acceptsTimeWheelPool(TimeWheelCraftingCpuPoolHost host) {
        return owningTianshuId != null
                && host instanceof TianshuCraftingCpuHost tianshu
                && owningTianshuId.equals(tianshu.getTianshuId());
    }

    @Override
    public Map<AEKey, Long> totalReusableSeedRequirements() {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var seed : payload.seeds()) {
            result.merge(seed.what(),
                    Sat.mul(seed.amount(), payload.executionSeedMultiplier()), Sat::add);
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
        if (exactOnlyHostSeedKeys.contains(planned)) return false;
        if (universallyFuzzySeedKeys.contains(planned)
                && planned.dropSecondary().equals(actual.dropSecondary())) {
            return true;
        }
        return acceptedSeedVariants.getOrDefault(planned, Set.of()).contains(actual);
    }

    /**
     * Mirrors the bundle registration performed by {@code LoopSeedLedgerBook}. The flow splitter
     * matters here: {@code 2A} across two executions is two safe unit bundles, while {@code 2A}
     * in one execution is one indivisible two-unit P2 bundle.
     */
    static Set<AEKey> exactOnlyHostSeedKeys(
            List<ClosedLoopPatternAnalyzer.MemberFlow> memberFlows,
            List<Long> copiesPerCycle,
            ClosedLoopConsumerRouting.RoutingPlan routing) {
        if (memberFlows == null || copiesPerCycle == null || routing == null
                || memberFlows.size() != copiesPerCycle.size()
                || memberFlows.size() != routing.consumers().size()
                || memberFlows.size() != routing.producers().size()) {
            throw new IllegalArgumentException("closed-loop host bundle metadata is inconsistent");
        }

        var bundleUnitsByConsumer = new LinkedHashMap<UUID, Map<AEKey, Long>>();
        for (int memberIndex = 0; memberIndex < memberFlows.size(); memberIndex++) {
            long copies = copiesPerCycle.get(memberIndex);
            if (copies <= 0) {
                throw new IllegalArgumentException("closed-loop member copies must be positive");
            }
            var consumer = routing.consumers().get(memberIndex);
            var producer = routing.producers().get(memberIndex);
            var units = bundleUnitsByConsumer.computeIfAbsent(
                    consumer.consumerId(), ignored -> new LinkedHashMap<>());
            for (var slice : splitMemberFlow(
                    memberFlows.get(memberIndex), copies, producer.targets())) {
                for (var input : slice.inputSeed().entrySet()) {
                    if (input.getValue() <= 0) continue;
                    var previous = units.putIfAbsent(input.getKey(), input.getValue());
                    if (previous != null && previous.longValue() != input.getValue()) {
                        // This is the same fail-closed sentinel used by the runtime ledger.
                        units.put(input.getKey(), 0L);
                    }
                }
            }
        }

        var result = new java.util.LinkedHashSet<AEKey>();
        for (var consumer : routing.consumers()) {
            var units = bundleUnitsByConsumer.getOrDefault(consumer.consumerId(), Map.of());
            for (var bootstrap : consumer.bootstrapSeed().entrySet()) {
                if (bootstrap.getValue() > 0
                        && units.getOrDefault(bootstrap.getKey(), 0L) != 1L) {
                    result.add(bootstrap.getKey());
                }
            }
        }
        return Set.copyOf(result);
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

    static void collectAcceptedSeedVariants(
            List<IPatternDetails> members,
            List<ClosedLoopPatternAnalyzer.MemberFlow> memberFlows,
            Set<AEKey> seeds,
            Map<AEKey, Set<AEKey>> accepted,
            Set<AEKey> anyFuzzySeeds) {
        collectAcceptedSeedVariants(
                members, memberFlows, seeds, accepted, anyFuzzySeeds,
                new java.util.LinkedHashSet<>());
    }

    static void collectAcceptedSeedVariants(
            List<IPatternDetails> members,
            List<ClosedLoopPatternAnalyzer.MemberFlow> memberFlows,
            Set<AEKey> seeds,
            Map<AEKey, Set<AEKey>> accepted,
            Set<AEKey> anyFuzzySeeds,
            Set<AEKey> universallyFuzzySeeds) {
        var rules = new LinkedHashMap<AEKey, ExecuteLoopPattern.SeedVariantRule>();
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            var details = members.get(memberIndex);
            var provider = CraftingPatternDelegates.forProviderLookup(details);
            var overload = provider instanceof OverloadedProviderOnlyPatternDetails candidate
                    ? candidate : null;
            var inputs = ClosedLoopExpandedPatternDetails.pinReusableSeedInputs(
                    details, memberFlows.get(memberIndex).inputSeedBySlot());
            for (var mapped : memberFlows.get(memberIndex).inputSeedBySlot().entrySet()) {
                int slot = mapped.getKey();
                var seed = mapped.getValue();
                if (!seeds.contains(seed) || slot < 0 || slot >= inputs.length) continue;
                boolean fuzzy = overload != null && overload.isFuzzyInput(slot);
                var possible = inputs[slot].getPossibleInputs();
                var exact = new java.util.LinkedHashSet<AEKey>();
                var fuzzyIdentities = new java.util.LinkedHashSet<AEKey>();
                exact.add(seed);
                boolean matchesSlot = false;
                for (var option : possible) {
                    if (option.what() == null) continue;
                    boolean matches = seed.equals(option.what())
                            || (fuzzy && seed.dropSecondary()
                                    .equals(option.what().dropSecondary()));
                    if (!matches) continue;
                    matchesSlot = true;
                    exact.add(option.what());
                    if (fuzzy) fuzzyIdentities.add(option.what().dropSecondary());
                }
                if (!matchesSlot) continue;
                if (fuzzy) anyFuzzySeeds.add(seed);
                var slotRule = new ExecuteLoopPattern.SeedVariantRule(
                        exact, fuzzyIdentities);
                rules.merge(seed, slotRule, ExecuteLoopPattern.SeedVariantRule::intersect);
            }
        }
        for (var seed : seeds) {
            var rule = rules.getOrDefault(seed,
                    new ExecuteLoopPattern.SeedVariantRule(Set.of(seed), Set.of()));
            accepted.put(seed, rule.exactVariants());
            if (rule.fuzzyIdentities().contains(seed.dropSecondary())) {
                universallyFuzzySeeds.add(seed);
            }
        }
    }

    static void validateFuzzyOutputSeedConsumers(
            List<ClosedLoopPatternAnalyzer.Member> members,
            List<ClosedLoopPatternAnalyzer.MemberFlow> memberFlows) {
        if (!ClosedLoopPatternAnalyzer.hasSafeDynamicSeedRouting(members, memberFlows)) {
            throw new IllegalArgumentException(
                    "dynamic closed-loop seed output cannot reach a compatible P2 bundle");
        }
    }

    /**
     * The global single-seed pool is safe only when its physical state has one exact
     * representation. A fuzzy/alternative state must stay in its fixed consumer account: sharing
     * it with another loop that accepts only the planned key can otherwise strand that loop after
     * the first component-changing transition.
     */
    static boolean isSharedSeedPoolSafe(
            boolean structurallySingleSeed,
            Set<AEKey> seeds,
            Map<AEKey, Set<AEKey>> acceptedVariants,
            Set<AEKey> fuzzySeeds) {
        if (!structurallySingleSeed) return false;
        for (var seed : seeds) {
            if (fuzzySeeds.contains(seed)) return false;
            for (var variant : acceptedVariants.getOrDefault(seed, Set.of())) {
                if (!seed.equals(variant)) return false;
            }
        }
        return true;
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
        var self = UUID.nameUUIDFromBytes("ae2lt:test-flow-slice".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        return splitMemberFlow(
                flow, copiesPerCycle, Map.of(self, flow.outputSeed()));
    }

    static List<MemberFlowSlice> splitMemberFlow(
            ClosedLoopPatternAnalyzer.MemberFlow flow,
            long copiesPerCycle,
            Map<UUID, Map<AEKey, Long>> outputSeedCredits) {
        if (flow == null || copiesPerCycle <= 0) return List.of();
        var boundaries = new TreeSet<Long>();
        boundaries.add(0L);
        boundaries.add(copiesPerCycle);
        addRemainderBoundaries(boundaries, flow.inputSeed(), copiesPerCycle);
        addRemainderBoundaries(boundaries, flow.outputSeed(), copiesPerCycle);
        addCreditBoundaries(
                boundaries, flow.outputSeed(), outputSeedCredits, copiesPerCycle);

        var points = new ArrayList<>(boundaries);
        var slices = new ArrayList<MemberFlowSlice>(Math.max(1, points.size() - 1));
        for (int i = 0; i + 1 < points.size(); i++) {
            long start = points.get(i);
            long end = points.get(i + 1);
            if (end <= start) continue;
            slices.add(new MemberFlowSlice(
                    perCopyAt(flow.inputSeed(), copiesPerCycle, start),
                    perCopyAt(flow.outputSeed(), copiesPerCycle, start),
                    perCopyCreditsAt(
                            flow.outputSeed(), outputSeedCredits, copiesPerCycle, start),
                    end - start));
        }
        return List.copyOf(slices);
    }

    static long expandedSliceCount(
            long macroFirings, long seedWaveRepetitions, long sliceCopies) {
        if (macroFirings <= 0L || seedWaveRepetitions <= 0L || sliceCopies <= 0L) return 0L;
        return Sat.mul(macroFirings, Sat.mul(seedWaveRepetitions, sliceCopies));
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
        return Collections.unmodifiableMap(result);
    }

    private static appeng.api.stacks.KeyCounter counter(Map<AEKey, Long> values) {
        var result = new appeng.api.stacks.KeyCounter();
        for (var entry : values.entrySet()) {
            if (entry.getValue() > 0) result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** Values retain bootstrap sizing where available; keys are this member's actual loop inputs. */
    static Map<AEKey, Long> memberSeedAmounts(
            Map<AEKey, Long> bootstrap, Set<AEKey> memberInputs) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var key : memberInputs) {
            result.put(key, Math.max(1L, bootstrap.getOrDefault(key, 1L)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static appeng.api.stacks.KeyCounter scaledCounter(
            Map<AEKey, Long> values, long scale) {
        var result = new appeng.api.stacks.KeyCounter();
        for (var entry : values.entrySet()) {
            long amount = Sat.mul(entry.getValue(), scale);
            if (amount > 0) result.add(entry.getKey(), amount);
        }
        return result;
    }

    private static Map<UUID, appeng.api.stacks.KeyCounter> counters(
            Map<UUID, Map<AEKey, Long>> values) {
        var result = new LinkedHashMap<UUID, appeng.api.stacks.KeyCounter>();
        for (var entry : values.entrySet()) {
            var counter = counter(entry.getValue());
            if (!counter.isEmpty()) result.put(entry.getKey(), counter);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<UUID, Map<AEKey, Long>> perCopyCreditsAt(
            Map<AEKey, Long> outputSeed,
            Map<UUID, Map<AEKey, Long>> values,
            long copiesPerCycle,
            long copyIndex) {
        var result = new LinkedHashMap<UUID, Map<AEKey, Long>>();
        for (var output : outputSeed.entrySet()) {
            long total = Math.max(0L, output.getValue());
            if (total <= 0) continue;
            long copyStart = creditPrefix(total, copiesPerCycle, copyIndex);
            long copyEnd = creditPrefix(total, copiesPerCycle, copyIndex + 1L);
            long targetStart = 0L;
            for (var target : values.entrySet()) {
                long targetAmount = Math.max(
                        0L, target.getValue().getOrDefault(output.getKey(), 0L));
                long targetEnd = Sat.add(targetStart, targetAmount);
                long overlapStart = Math.max(copyStart, targetStart);
                long overlapEnd = Math.min(copyEnd, targetEnd);
                if (overlapEnd > overlapStart) {
                    var targetCredits = new LinkedHashMap<>(
                            result.getOrDefault(target.getKey(), Map.of()));
                    targetCredits.put(output.getKey(), overlapEnd - overlapStart);
                    result.put(target.getKey(), Collections.unmodifiableMap(targetCredits));
                }
                targetStart = targetEnd;
            }
            if (targetStart != total) {
                throw new IllegalArgumentException(
                        "consumer credits do not match member seed output for "
                                + output.getKey());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<UUID, Map<AEKey, Long>> selectCredits(
            Map<UUID, Map<AEKey, Long>> credits,
            Map<UUID, Map<AEKey, Long>> wrappedCredits,
            boolean selectWrapped) {
        var result = new LinkedHashMap<UUID, Map<AEKey, Long>>();
        for (var target : credits.entrySet()) {
            var selected = new LinkedHashMap<AEKey, Long>();
            var wrappedForTarget = wrappedCredits.getOrDefault(target.getKey(), Map.of());
            for (var entry : target.getValue().entrySet()) {
                boolean wrapped = wrappedForTarget.getOrDefault(entry.getKey(), 0L) > 0;
                if (wrapped == selectWrapped) selected.put(entry.getKey(), entry.getValue());
            }
            if (!selected.isEmpty()) {
                result.put(target.getKey(), Collections.unmodifiableMap(selected));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Adds only the copy boundaries where a flattened consumer allocation can change. Credits for
     * one output key share one cursor; their remainders must not all be front-loaded into copy 0.
     */
    private static void addCreditBoundaries(
            Set<Long> boundaries,
            Map<AEKey, Long> outputSeed,
            Map<UUID, Map<AEKey, Long>> credits,
            long copiesPerCycle) {
        var creditedKeys = new java.util.LinkedHashSet<AEKey>();
        for (var target : credits.values()) {
            for (var entry : target.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    creditedKeys.add(entry.getKey());
                }
            }
        }
        for (var key : creditedKeys) {
            if (outputSeed.getOrDefault(key, 0L) <= 0) {
                throw new IllegalArgumentException(
                        "consumer credit has no matching member seed output for " + key);
            }
        }

        for (var output : outputSeed.entrySet()) {
            long total = Math.max(0L, output.getValue());
            if (total <= 0) continue;
            long cursor = 0L;
            for (var target : credits.values()) {
                long amount = Math.max(0L, target.getOrDefault(output.getKey(), 0L));
                cursor = Sat.add(cursor, amount);
                addCreditBoundary(boundaries, total, copiesPerCycle, cursor);
            }
            if (cursor != total) {
                throw new IllegalArgumentException(
                        "consumer credits do not match member seed output for "
                                + output.getKey());
            }
        }
    }

    private static void addCreditBoundary(
            Set<Long> boundaries,
            long total,
            long copiesPerCycle,
            long flattenedOffset) {
        if (flattenedOffset <= 0L) {
            boundaries.add(0L);
            return;
        }
        if (flattenedOffset >= total) {
            boundaries.add(copiesPerCycle);
            return;
        }
        long low = 0L;
        long high = copiesPerCycle;
        while (low < high) {
            long mid = low + ((high - low) >>> 1);
            if (creditPrefix(total, copiesPerCycle, mid) < flattenedOffset) low = mid + 1L;
            else high = mid;
        }
        long boundary = low;
        if (creditPrefix(total, copiesPerCycle, boundary) != flattenedOffset
                && boundary > 0L) {
            boundaries.add(boundary - 1L);
        }
        boundaries.add(boundary);
    }

    private static long creditPrefix(
            long total, long copiesPerCycle, long copyIndex) {
        if (total <= 0L || copiesPerCycle <= 0L || copyIndex <= 0L) return 0L;
        if (copyIndex >= copiesPerCycle) return total;
        long quotient = total / copiesPerCycle;
        long remainder = total % copiesPerCycle;
        return quotient * copyIndex + Math.min(copyIndex, remainder);
    }

    record MemberFlowSlice(
            Map<AEKey, Long> inputSeed,
            Map<AEKey, Long> outputSeed,
            Map<UUID, Map<AEKey, Long>> outputSeedCredits,
            long copiesPerCycle) {
        MemberFlowSlice {
            inputSeed = Collections.unmodifiableMap(new LinkedHashMap<>(inputSeed));
            outputSeed = Collections.unmodifiableMap(new LinkedHashMap<>(outputSeed));
            var copiedCredits = new LinkedHashMap<UUID, Map<AEKey, Long>>();
            for (var entry : outputSeedCredits.entrySet()) {
                copiedCredits.put(entry.getKey(),
                        Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
            }
            outputSeedCredits = Collections.unmodifiableMap(copiedCredits);
        }
    }

    private record ExpandedMember(
            IPatternDetails details,
            long copiesPerCycle,
            long seedWaveCopies,
            ClosedLoopPatternAnalyzer.MemberFlow flow) {
    }
}
