package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.planner.Sat;
import com.moakiee.thunderbolt.core.planner.PositiveIntegerLinearSolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClosedLoopPatternAnalyzer {
    public static final int MAX_MEMBERS = 6;
    public enum StructureStatus {
        VALID,
        INVALID
    }

    /**
     * Builds the exact mass-balance inequalities for the selected members and solves them without
     * a coefficient or probe bound. A non-solved status is deliberately preserved so callers can
     * distinguish an impossible loop from overflow, invalid input, or a solver failure.
     */
    public static PositiveIntegerLinearSolver.Result solveCoefficients(
            List<IPatternDetails> members, AEKey requestedOutput) {
        if (members == null || members.isEmpty() || requestedOutput == null) {
            return solverResult(PositiveIntegerLinearSolver.Status.INVALID_INPUT);
        }

        var possibleLoopOutputs = possibleLoopOutputs(members);
        if (possibleLoopOutputs == null) {
            return solverResult(PositiveIntegerLinearSolver.Status.INVALID_INPUT);
        }
        var balances = new ArrayList<Balance>(members.size());
        var consumed = new LinkedHashMap<AEKey, Long>();
        var produced = new LinkedHashMap<AEKey, Long>();
        for (var member : members) {
            var balance = balance(member, possibleLoopOutputs);
            if (balance == null) {
                return solverResult(PositiveIntegerLinearSolver.Status.INVALID_INPUT);
            }
            balances.add(balance);
            mergeScaled(consumed, balance.consumed(), 1L);
            mergeScaled(produced, balance.produced(), 1L);
        }

        var cycleKeys = new java.util.LinkedHashSet<AEKey>();
        for (var key : consumed.keySet()) {
            if (produced.getOrDefault(key, 0L) > 0) cycleKeys.add(key);
        }
        if (cycleKeys.isEmpty()) {
            return solverResult(PositiveIntegerLinearSolver.Status.INFEASIBLE);
        }

        var constraints = new ArrayList<PositiveIntegerLinearSolver.Constraint>(cycleKeys.size() + 1);
        for (var key : cycleKeys) {
            constraints.add(new PositiveIntegerLinearSolver.Constraint(
                    netRow(balances, key), 0L));
        }
        constraints.add(new PositiveIntegerLinearSolver.Constraint(
                netRow(balances, requestedOutput), 1L));

        var solved = PositiveIntegerLinearSolver.solve(members.size(), constraints);
        if (!solved.solved()) return solved;

        var analyzed = new ArrayList<Member>(members.size());
        var coefficients = solved.coefficients();
        for (int i = 0; i < members.size(); i++) {
            analyzed.add(new Member(members.get(i), coefficients[i]));
        }
        return analyze(analyzed, requestedOutput) != null
                ? solved : solverResult(PositiveIntegerLinearSolver.Status.INTERNAL_ERROR);
    }

    /** Checks only the static member boundary; loop membership is decided by input-seed flows. */
    public static StructureStatus validateStructure(List<IPatternDetails> members) {
        if (members == null || members.isEmpty() || members.size() > MAX_MEMBERS) {
            return StructureStatus.INVALID;
        }
        return prepareBalances(members) != null ? StructureStatus.VALID : StructureStatus.INVALID;
    }

    public static ClosedLoopAnalysis analyze(
            List<Member> members, AEKey requestedOutput) {
        if (members == null || members.isEmpty() || requestedOutput == null) return null;
        var consumed = new LinkedHashMap<AEKey, Long>();
        var produced = new LinkedHashMap<AEKey, Long>();
        var perMember = new ArrayList<Balance>(members.size());
        var details = new ArrayList<IPatternDetails>(members.size());
        for (var member : members) {
            if (member == null || member.details() == null) return null;
            details.add(member.details());
        }
        var possibleLoopOutputs = possibleLoopOutputs(details);
        if (possibleLoopOutputs == null) return null;
        for (var member : members) {
            var balance = balance(member.details(), possibleLoopOutputs);
            if (balance == null || member.copies() <= 0) return null;
            perMember.add(balance);
            mergeScaled(consumed, balance.consumed(), member.copies());
            mergeScaled(produced, balance.produced(), member.copies());
        }

        var cycleKeys = new java.util.LinkedHashSet<AEKey>();
        for (var key : consumed.keySet()) if (produced.getOrDefault(key, 0L) > 0) cycleKeys.add(key);
        if (cycleKeys.isEmpty()) return null;

        var net = new LinkedHashMap<AEKey, Long>();
        for (var entry : produced.entrySet()) {
            net.put(entry.getKey(), entry.getValue() - consumed.getOrDefault(entry.getKey(), 0L));
        }
        for (var entry : consumed.entrySet()) net.putIfAbsent(entry.getKey(), -entry.getValue());
        if (net.getOrDefault(requestedOutput, 0L) <= 0) return null;
        for (var key : cycleKeys) if (net.getOrDefault(key, 0L) < 0) return null;

        var balances = new HashMap<AEKey, Long>();
        var seedAmounts = new LinkedHashMap<AEKey, Long>();
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            var member = members.get(memberIndex);
            var balance = perMember.get(memberIndex);
            for (var key : cycleKeys) {
                long consumedByGroup = Sat.mul(
                        balance.consumed().getOrDefault(key, 0L), member.copies());
                long producedByGroup = Sat.mul(
                        balance.produced().getOrDefault(key, 0L), member.copies());
                long held = balances.getOrDefault(key, 0L);
                // copiesPerCycle is one atomic dispatch group. Outputs from copy 1 are therefore
                // not available to satisfy copy 2 inside the same member group.
                long deficit = Math.max(0L, consumedByGroup - held);
                if (deficit > 0L) seedAmounts.merge(key, deficit, Sat::add);
                long afterConsumption = Math.max(held, consumedByGroup) - consumedByGroup;
                balances.put(key, Sat.add(afterConsumption, producedByGroup));
            }
        }
        if (seedAmounts.isEmpty()) return null;

        var seeds = toStacks(seedAmounts);
        var external = new LinkedHashMap<AEKey, Long>();
        var outputs = new LinkedHashMap<AEKey, Long>();
        for (var entry : net.entrySet()) {
            if (entry.getValue() < 0 && !cycleKeys.contains(entry.getKey())) {
                external.put(entry.getKey(), -entry.getValue());
            } else if (entry.getValue() > 0) {
                outputs.put(entry.getKey(), entry.getValue());
            }
        }
        return new ClosedLoopAnalysis(seeds, toStacks(external), toStacks(outputs));
    }

    /** Chooses the member-group order with the smallest total simultaneous seed requirement. */
    public static OrderedAnalysis analyzeBestOrder(
            List<Member> members, AEKey requestedOutput) {
        if (members == null || members.isEmpty() || members.size() > MAX_MEMBERS) return null;
        var source = List.copyOf(members);
        var used = new boolean[source.size()];
        var current = new ArrayList<Member>(source.size());
        var best = new OrderedAnalysis[1];
        var bestSeedTotal = new long[] {Long.MAX_VALUE};
        enumerateOrders(source, requestedOutput, used, current, best, bestSeedTotal);
        return best[0];
    }

    /**
     * Derives the explicit virtual-inventory transition for every ordered member dispatch group.
     * Values are totals for {@link Member#copies()} executions, not per-copy averages.
     */
    public static List<MemberFlow> deriveMemberFlows(
            List<Member> members, List<GenericStack> seeds) {
        if (members == null || members.isEmpty() || seeds == null || seeds.isEmpty()) {
            return List.of();
        }
        var details = new ArrayList<IPatternDetails>(members.size());
        for (var member : members) {
            if (member == null || member.details() == null || member.copies() <= 0) return List.of();
            details.add(member.details());
        }
        var possibleOutputs = possibleLoopOutputs(details);
        if (possibleOutputs == null) return List.of();

        var scaled = new ArrayList<Balance>(members.size());
        var totalConsumed = new LinkedHashMap<AEKey, Long>();
        var totalProduced = new LinkedHashMap<AEKey, Long>();
        for (var member : members) {
            var perCopy = balance(member.details(), possibleOutputs);
            if (perCopy == null) return List.of();
            var consumed = scaledCopy(perCopy.consumed(), member.copies());
            var produced = scaledCopy(perCopy.produced(), member.copies());
            scaled.add(new Balance(consumed, produced, perCopy.inputSeedBySlot()));
            mergeScaled(totalConsumed, consumed, 1L);
            mergeScaled(totalProduced, produced, 1L);
        }

        var cycleKeys = new java.util.LinkedHashSet<AEKey>();
        for (var key : totalConsumed.keySet()) {
            if (totalProduced.getOrDefault(key, 0L) > 0) cycleKeys.add(key);
        }
        var seedAmounts = new LinkedHashMap<AEKey, Long>();
        for (var seed : seeds) {
            if (seed != null && seed.what() != null && seed.amount() > 0) {
                seedAmounts.merge(seed.what(), seed.amount(), Sat::add);
            }
        }
        if (!cycleKeys.containsAll(seedAmounts.keySet())) return List.of();

        var required = new ArrayList<Map<AEKey, Long>>(java.util.Collections.nCopies(
                members.size() + 1, Map.of()));
        required.set(members.size(), Map.copyOf(seedAmounts));
        for (int memberIndex = members.size() - 1; memberIndex >= 0; memberIndex--) {
            var balance = scaled.get(memberIndex);
            var after = required.get(memberIndex + 1);
            var before = new LinkedHashMap<AEKey, Long>();
            for (var key : cycleKeys) {
                long consumed = balance.consumed().getOrDefault(key, 0L);
                long produced = balance.produced().getOrDefault(key, 0L);
                long retainedAfter = after.getOrDefault(key, 0L);
                long neededBefore = Sat.add(consumed, Math.max(0L, retainedAfter - produced));
                if (neededBefore > 0) before.put(key, neededBefore);
            }
            required.set(memberIndex, Map.copyOf(before));
        }
        // The reusable state prepared before member 1 must be the same state retained after the
        // last member. Any amount beyond this state is a public net output, not extra seed.
        if (!required.getFirst().equals(seedAmounts)) return List.of();

        var result = new ArrayList<MemberFlow>(members.size());
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            var balance = scaled.get(memberIndex);
            var before = required.get(memberIndex);
            var after = required.get(memberIndex + 1);
            var inputSeed = new LinkedHashMap<AEKey, Long>();
            var outputSeed = new LinkedHashMap<AEKey, Long>();
            for (var entry : balance.consumed().entrySet()) {
                if (cycleKeys.contains(entry.getKey()) && entry.getValue() > 0) {
                    inputSeed.put(entry.getKey(), entry.getValue());
                }
            }
            // A member without a reusable-state input is a producer in a dependency chain, not a
            // closed-loop transition. It cannot be made safe by assigning it a dedicated ledger.
            if (inputSeed.isEmpty()) return List.of();
            for (var entry : balance.produced().entrySet()) {
                var key = entry.getKey();
                long produced = entry.getValue();
                long seedOutput = 0L;
                if (cycleKeys.contains(key)) {
                    long carried = Math.max(0L,
                            before.getOrDefault(key, 0L)
                                    - balance.consumed().getOrDefault(key, 0L));
                    seedOutput = Math.min(produced,
                            Math.max(0L, after.getOrDefault(key, 0L) - carried));
                    if (seedOutput > 0) outputSeed.put(key, seedOutput);
                }
            }
            var inputSeedBySlot = new LinkedHashMap<Integer, AEKey>();
            for (var entry : balance.inputSeedBySlot().entrySet()) {
                if (cycleKeys.contains(entry.getValue())) {
                    inputSeedBySlot.put(entry.getKey(), entry.getValue());
                }
            }
            result.add(new MemberFlow(
                    Map.copyOf(inputSeed), Map.copyOf(outputSeed),
                    Map.copyOf(inputSeedBySlot)));
        }
        var immutable = List.copyOf(result);
        return hasSafeDynamicSeedRouting(members, immutable) ? immutable : List.of();
    }

    /**
     * Validates physical component-state capabilities after mass balance has selected planned
     * keys. Exact outputs may feed every P2. Late-bound ID_ONLY outputs may only feed one-unit
     * bundles whose consumers are also ID_ONLY; dispatch-known dynamic remainders may feed larger
     * bundles, but every slot using that planned key must accept their full variant domain.
     */
    static boolean hasSafeDynamicSeedRouting(
            List<Member> members, List<MemberFlow> flows) {
        if (members == null || flows == null || members.size() != flows.size()) return false;
        var requiredDomains = new LinkedHashMap<AEKey, DynamicSeedDomain>();
        try {
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                var member = members.get(memberIndex);
                var details = member.details();
                var flow = flows.get(memberIndex);
                var provider = CraftingPatternDelegates.forProviderLookup(details);
                var overload = provider instanceof OverloadedProviderOnlyPatternDetails candidate
                        ? candidate : null;
                var exactCapacity = new LinkedHashMap<AEKey, Long>();
                var dynamicCapacity = new LinkedHashMap<AEKey, DynamicSeedDomain>();

                var outputs = details.getOutputs();
                for (int slot = 0; slot < outputs.size(); slot++) {
                    var output = outputs.get(slot);
                    long amount = Sat.mul(output.amount(), member.copies());
                    if (output.what() == null || amount <= 0) return false;
                    if (overload != null && overload.isFuzzyOutput(slot)) {
                        dynamicCapacity.computeIfAbsent(
                                output.what(), ignored -> new DynamicSeedDomain())
                                .markFuzzy(true);
                    } else {
                        exactCapacity.merge(output.what(), amount, Sat::add);
                    }
                }

                var executionInputs = ClosedLoopExpandedPatternDetails.pinReusableSeedInputs(
                        details, flow.inputSeedBySlot());
                for (var mapped : flow.inputSeedBySlot().entrySet()) {
                    int slot = mapped.getKey();
                    if (slot < 0 || slot >= executionInputs.length) return false;
                    var input = executionInputs[slot];
                    var selected = mapped.getValue();
                    var plannedRemainder = input.getRemainingKey(selected);
                    if (plannedRemainder == null) continue;
                    long amount = Sat.mul(input.getMultiplier(), member.copies());
                    if (amount <= 0) return false;

                    var domain = new DynamicSeedDomain();
                    boolean dynamic = overload != null && overload.isFuzzyInput(slot)
                            && plannedRemainder.dropSecondary().equals(selected.dropSecondary());
                    if (dynamic) domain.markFuzzy(false);
                    for (var possible : input.getPossibleInputs()) {
                        if (possible.what() == null) return false;
                        var actualRemainder = input.getRemainingKey(possible.what());
                        if (actualRemainder == null) return false;
                        if (!plannedRemainder.equals(actualRemainder)) {
                            dynamic = true;
                            domain.addVariant(actualRemainder);
                        }
                    }
                    if (dynamic) {
                        dynamicCapacity.merge(
                                plannedRemainder, domain, DynamicSeedDomain::merge);
                    } else {
                        exactCapacity.merge(plannedRemainder, amount, Sat::add);
                    }
                }

                for (var output : flow.outputSeed().entrySet()) {
                    long exact = exactCapacity.getOrDefault(output.getKey(), 0L);
                    if (output.getValue() <= exact) continue;
                    var domain = dynamicCapacity.get(output.getKey());
                    if (domain == null) return false;
                    requiredDomains.merge(
                            output.getKey(), domain.copy(), DynamicSeedDomain::merge);
                }
            }

            for (var required : requiredDomains.entrySet()) {
                var planned = required.getKey();
                var domain = required.getValue();
                for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                    var flow = flows.get(memberIndex);
                    if (flow.inputSeed().getOrDefault(planned, 0L) <= 0) continue;
                    var details = members.get(memberIndex).details();
                    var provider = CraftingPatternDelegates.forProviderLookup(details);
                    var overload = provider instanceof OverloadedProviderOnlyPatternDetails candidate
                            ? candidate : null;
                    var inputs = ClosedLoopExpandedPatternDetails.pinReusableSeedInputs(
                            details, flow.inputSeedBySlot());
                    long bundleUnits = 0L;
                    boolean mapped = false;
                    for (var seedSlot : flow.inputSeedBySlot().entrySet()) {
                        if (!planned.equals(seedSlot.getValue())) continue;
                        mapped = true;
                        int slot = seedSlot.getKey();
                        boolean fuzzy = overload != null && overload.isFuzzyInput(slot);
                        if (domain.fuzzy && !fuzzy) return false;
                        var possible = inputs[slot].getPossibleInputs();
                        long selectedAmount = 0L;
                        for (var candidate : possible) {
                            if (candidate.what() == null) return false;
                            boolean selected = planned.equals(candidate.what())
                                    || (fuzzy && planned.dropSecondary()
                                            .equals(candidate.what().dropSecondary()));
                            if (selected && selectedAmount == 0L) {
                                selectedAmount = candidate.amount();
                            }
                        }
                        if (selectedAmount <= 0) return false;
                        for (var variant : domain.variants) {
                            boolean accepted = fuzzy
                                    ? planned.dropSecondary().equals(variant.dropSecondary())
                                    : java.util.Arrays.stream(possible)
                                            .anyMatch(candidate -> variant.equals(candidate.what()));
                            if (!accepted) return false;
                        }
                        bundleUnits = Sat.add(bundleUnits,
                                Sat.mul(selectedAmount, inputs[slot].getMultiplier()));
                    }
                    if (!mapped || bundleUnits <= 0) return false;
                    // Consumer routing may split one logical input bundle across producer edges.
                    // Without a pending-bundle quarantine, only unit bundles are safe for any
                    // component-changing state. Runtime still enforces whole bundles defensively
                    // for legacy or third-party execution wrappers.
                    if (bundleUnits > 1L) return false;
                }
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Every encoded member must consume at least one positive reusable-state type. */
    public static boolean hasInputSeedPerMember(
            List<Member> members, List<GenericStack> seeds) {
        if (members == null || members.isEmpty()) return false;
        var flows = deriveMemberFlows(members, seeds);
        if (flows.size() != members.size()) return false;
        for (var flow : flows) {
            if (positiveInputSeedTypes(flow) < 1) return false;
        }
        return true;
    }

    /** Classification used by the CPU's shared single-seed ledger. */
    static boolean hasSingleSeedInputPerMember(List<MemberFlow> flows) {
        if (flows == null || flows.isEmpty()) return false;
        for (var flow : flows) {
            if (positiveInputSeedTypes(flow) != 1) return false;
        }
        return true;
    }

    private static int positiveInputSeedTypes(MemberFlow flow) {
        if (flow == null) return 0;
        int positiveTypes = 0;
        for (var amount : flow.inputSeed().values()) {
            if (amount != null && amount > 0) positiveTypes++;
        }
        return positiveTypes;
    }

    private static void enumerateOrders(
            List<Member> source, AEKey requestedOutput, boolean[] used,
            ArrayList<Member> current, OrderedAnalysis[] best, long[] bestSeedTotal) {
        if (current.size() == source.size()) {
            var analysis = analyze(current, requestedOutput);
            if (analysis == null || !hasInputSeedPerMember(current, analysis.seeds())) return;
            long total = 0L;
            for (var seed : analysis.seeds()) total = Sat.add(total, seed.amount());
            if (best[0] == null || total < bestSeedTotal[0]) {
                best[0] = new OrderedAnalysis(List.copyOf(current), analysis);
                bestSeedTotal[0] = total;
            }
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            if (used[i]) continue;
            used[i] = true;
            current.add(source.get(i));
            enumerateOrders(source, requestedOutput, used, current, best, bestSeedTotal);
            current.removeLast();
            used[i] = false;
        }
    }

    private static Balance balance(IPatternDetails details, List<LoopOutput> possibleLoopOutputs) {
        if (details == null || details instanceof TianshuClosedLoopPatternDetails) return null;
        var consumed = new LinkedHashMap<AEKey, Long>();
        var produced = new LinkedHashMap<AEKey, Long>();
        var inputSeedBySlot = new LinkedHashMap<Integer, AEKey>();
        var inputs = details.getInputs();
        var providerDetails = com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates
                .forProviderLookup(details);
        var overload = providerDetails instanceof
                com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails value
                ? value : null;
        for (int slot = 0; slot < inputs.length; slot++) {
            var input = inputs[slot];
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || possible[0].what() == null) return null;
            var loopMatch = matchingLoopOutput(
                    possible, possibleLoopOutputs, overload != null && overload.isFuzzyInput(slot));
            if (loopMatch != null && loopMatch.forbidden()) return null;
            AEKey concrete = loopMatch != null ? loopMatch.key() : null;
            if (concrete == null) concrete = possible[0].what();
            long templateAmount = possible[0].amount();
            boolean fuzzySlot = overload != null && overload.isFuzzyInput(slot);
            if (fuzzySlot) {
                Long matchedAmount = null;
                for (var candidate : possible) {
                    if (candidate.what() == null || !concrete.dropSecondary()
                            .equals(candidate.what().dropSecondary())) continue;
                    if (matchedAmount != null && matchedAmount != candidate.amount()) return null;
                    matchedAmount = candidate.amount();
                }
                if (matchedAmount == null) return null;
                templateAmount = matchedAmount;
            } else {
                for (var candidate : possible) {
                    if (concrete.equals(candidate.what())) {
                        templateAmount = candidate.amount();
                        break;
                    }
                }
            }
            long amount = Sat.mul(templateAmount, input.getMultiplier());
            if (amount <= 0) return null;
            consumed.merge(concrete, amount, Sat::add);
            inputSeedBySlot.put(slot, concrete);
            var remaining = input.getRemainingKey(concrete);
            // AE2 returns one remainder per completed input-template operation, not one per
            // physical item consumed by that template. E.g. a 2A template returning A is 2A->1A.
            if (remaining != null) {
                produced.merge(remaining, input.getMultiplier(), Sat::add);
            }
        }
        for (var output : details.getOutputs()) {
            if (output.what() == null || output.amount() <= 0) return null;
            produced.merge(output.what(), output.amount(), Sat::add);
        }
        return new Balance(consumed, produced, Map.copyOf(inputSeedBySlot));
    }

    private static List<LoopOutput> possibleLoopOutputs(List<IPatternDetails> members) {
        var result = new ArrayList<LoopOutput>();
        for (var member : members) {
            if (member == null) return null;
            var providerDetails = com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates
                    .forProviderLookup(member);
            var overload = providerDetails instanceof
                    com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails value
                    ? value : null;
            var outputs = member.getOutputs();
            for (int slot = 0; slot < outputs.size(); slot++) {
                var output = outputs.get(slot);
                if (output == null || output.what() == null || output.amount() <= 0) return null;
                result.add(new LoopOutput(
                        output.what(), overload != null && overload.isFuzzyOutput(slot)));
            }
        }
        return List.copyOf(result);
    }

    private static List<Balance> prepareBalances(List<IPatternDetails> members) {
        var outputs = possibleLoopOutputs(members);
        if (outputs == null) return null;
        var result = new ArrayList<Balance>(members.size());
        for (var member : members) {
            var balance = balance(member, outputs);
            if (balance == null) return null;
            result.add(balance);
        }
        return List.copyOf(result);
    }

    private static long[] netRow(List<Balance> balances, AEKey key) {
        var result = new long[balances.size()];
        for (int i = 0; i < balances.size(); i++) {
            var balance = balances.get(i);
            result[i] = balance.produced().getOrDefault(key, 0L)
                    - balance.consumed().getOrDefault(key, 0L);
        }
        return result;
    }

    private static PositiveIntegerLinearSolver.Result solverResult(
            PositiveIntegerLinearSolver.Status status) {
        return new PositiveIntegerLinearSolver.Result(status, new long[0]);
    }

    private static LoopMatch matchingLoopOutput(
            GenericStack[] possibleInputs, List<LoopOutput> outputs, boolean ignoreSecondary) {
        LoopMatch forbidden = null;
        for (var output : outputs) {
            for (var possible : possibleInputs) {
                if (possible.what() == null) continue;
                if (ignoreSecondary
                        && possible.what().dropSecondary().equals(output.key().dropSecondary())) {
                    var match = new LoopMatch(
                            output.key(), isDurabilityFuzzy(possible.what(), output.key()));
                    if (!match.forbidden()) return match;
                    if (forbidden == null) forbidden = match;
                }
                if (!ignoreSecondary && output.fuzzy()
                        && possible.what().equals(output.key())) {
                    if (forbidden == null) forbidden = new LoopMatch(output.key(), true);
                }
                if (!ignoreSecondary && !output.fuzzy()
                        && possible.what().equals(output.key())) {
                    return new LoopMatch(output.key(), false);
                }
            }
        }
        return forbidden;
    }

    /**
     * Uses the same containment rules as balance analysis for discovery graph edges.
     * Keeping this here prevents automatic discovery from accepting a fuzzy edge that
     * the final analyzer would later interpret differently.
     */
    static boolean acceptsLoopOutput(
            GenericStack[] possibleInputs,
            boolean inputFuzzy,
            AEKey output,
            boolean outputFuzzy) {
        if (possibleInputs == null || output == null) return false;
        var match = matchingLoopOutput(
                possibleInputs, List.of(new LoopOutput(output, outputFuzzy)), inputFuzzy);
        return match != null && !match.forbidden();
    }

    private static boolean isDurabilityFuzzy(AEKey input, AEKey output) {
        if (input.equals(output)) return false;
        if (input instanceof appeng.api.stacks.AEItemKey itemInput
                && output instanceof appeng.api.stacks.AEItemKey itemOutput
                && itemInput.getItem() == itemOutput.getItem()) {
            return itemInput.toStack().isDamageableItem() || itemOutput.toStack().isDamageableItem();
        }
        return false;
    }

    private static void mergeScaled(
            Map<AEKey, Long> target, Map<AEKey, Long> source, long copies) {
        for (var entry : source.entrySet()) {
            target.merge(entry.getKey(), Sat.mul(entry.getValue(), copies), Sat::add);
        }
    }

    private static Map<AEKey, Long> scaledCopy(Map<AEKey, Long> source, long copies) {
        var result = new LinkedHashMap<AEKey, Long>();
        mergeScaled(result, source, copies);
        return Map.copyOf(result);
    }

    private static List<GenericStack> toStacks(Map<AEKey, Long> values) {
        var result = new ArrayList<GenericStack>(values.size());
        for (var entry : values.entrySet()) {
            if (entry.getValue() > 0) result.add(new GenericStack(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    public record Member(IPatternDetails details, long copies) { }
    public record OrderedAnalysis(List<Member> members, ClosedLoopAnalysis analysis) {
        public OrderedAnalysis {
            members = List.copyOf(members);
        }
    }
    public record MemberFlow(
            Map<AEKey, Long> inputSeed,
            Map<AEKey, Long> outputSeed,
            Map<Integer, AEKey> inputSeedBySlot) {
        public MemberFlow(Map<AEKey, Long> inputSeed, Map<AEKey, Long> outputSeed) {
            this(inputSeed, outputSeed, Map.of());
        }

        public MemberFlow {
            inputSeed = Map.copyOf(inputSeed);
            outputSeed = Map.copyOf(outputSeed);
            inputSeedBySlot = Map.copyOf(inputSeedBySlot);
        }
    }
    private record Balance(
            Map<AEKey, Long> consumed,
            Map<AEKey, Long> produced,
            Map<Integer, AEKey> inputSeedBySlot) { }
    private record LoopOutput(AEKey key, boolean fuzzy) { }
    private record LoopMatch(AEKey key, boolean forbidden) { }

    private static final class DynamicSeedDomain {
        private boolean fuzzy;
        private boolean lateBound;
        private final Set<AEKey> variants = new LinkedHashSet<>();

        private DynamicSeedDomain markFuzzy(boolean lateBound) {
            this.fuzzy = true;
            this.lateBound |= lateBound;
            return this;
        }

        private DynamicSeedDomain addVariant(AEKey variant) {
            if (variant != null) variants.add(variant);
            return this;
        }

        private DynamicSeedDomain merge(DynamicSeedDomain other) {
            if (other == null) return this;
            fuzzy |= other.fuzzy;
            lateBound |= other.lateBound;
            variants.addAll(other.variants);
            return this;
        }

        private DynamicSeedDomain copy() {
            return new DynamicSeedDomain().merge(this);
        }
    }
    private ClosedLoopPatternAnalyzer() { }
}
