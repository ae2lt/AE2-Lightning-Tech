package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.thunderbolt.core.planner.Sat;
import com.moakiee.thunderbolt.core.planner.PositiveIntegerLinearSolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClosedLoopPatternAnalyzer {
    public static final int MAX_MEMBERS = 6;
    public enum StructureStatus {
        VALID,
        NOT_CONNECTED,
        NOT_MINIMAL,
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

    /**
     * Requires every member to participate in one strongly connected material cycle and rejects a
     * composite whenever a proper subset is already a valid closed loop of its own.
     */
    public static StructureStatus validateMinimalStructure(List<IPatternDetails> members) {
        if (members == null || members.isEmpty() || members.size() > MAX_MEMBERS) {
            return StructureStatus.INVALID;
        }
        var prepared = prepareBalances(members);
        if (prepared == null) return StructureStatus.INVALID;
        if (!isStronglyConnected(prepared)) return StructureStatus.NOT_CONNECTED;
        if (members.size() == 1) return StructureStatus.VALID;

        int fullMask = (1 << members.size()) - 1;
        for (int mask = 1; mask < fullMask; mask++) {
            var subset = new ArrayList<IPatternDetails>();
            for (int member = 0; member < members.size(); member++) {
                if ((mask & (1 << member)) != 0) subset.add(members.get(member));
            }
            var subsetStatus = hasBalancedClosedLoop(subset);
            if (subsetStatus == SubsetLoopStatus.ERROR) return StructureStatus.INVALID;
            if (subsetStatus == SubsetLoopStatus.YES) return StructureStatus.NOT_MINIMAL;
        }
        return StructureStatus.VALID;
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
            scaled.add(new Balance(consumed, produced));
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
            result.add(new MemberFlow(
                    Map.copyOf(inputSeed), Map.copyOf(outputSeed)));
        }
        return List.copyOf(result);
    }

    /** Classification used by the CPU's shared single-seed ledger. */
    static boolean hasSingleSeedInputPerMember(List<MemberFlow> flows) {
        if (flows == null || flows.isEmpty()) return false;
        for (var flow : flows) {
            if (flow == null) return false;
            int positiveTypes = 0;
            for (var amount : flow.inputSeed().values()) {
                if (amount != null && amount > 0 && ++positiveTypes > 1) return false;
            }
            if (positiveTypes != 1) return false;
        }
        return true;
    }

    private static void enumerateOrders(
            List<Member> source, AEKey requestedOutput, boolean[] used,
            ArrayList<Member> current, OrderedAnalysis[] best, long[] bestSeedTotal) {
        if (current.size() == source.size()) {
            var analysis = analyze(current, requestedOutput);
            if (analysis == null) return;
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
        var inputs = details.getInputs();
        var overload = details instanceof com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails value
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
            for (var candidate : possible) {
                if (concrete.equals(candidate.what())) {
                    templateAmount = candidate.amount();
                    break;
                }
            }
            long amount = Sat.mul(templateAmount, input.getMultiplier());
            if (amount <= 0) return null;
            consumed.merge(concrete, amount, Sat::add);
            var remaining = input.getRemainingKey(concrete);
            if (remaining != null) produced.merge(remaining, amount, Sat::add);
        }
        for (var output : details.getOutputs()) {
            if (output.what() == null || output.amount() <= 0) return null;
            produced.merge(output.what(), output.amount(), Sat::add);
        }
        return new Balance(consumed, produced);
    }

    private static List<LoopOutput> possibleLoopOutputs(List<IPatternDetails> members) {
        var result = new ArrayList<LoopOutput>();
        for (var member : members) {
            if (member == null) return null;
            var overload = member instanceof
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

    private static boolean isStronglyConnected(List<Balance> balances) {
        int size = balances.size();
        var edges = new boolean[size][size];
        for (int producer = 0; producer < size; producer++) {
            for (int consumer = 0; consumer < size; consumer++) {
                for (var key : balances.get(producer).produced().keySet()) {
                    if (balances.get(producer).produced().getOrDefault(key, 0L) > 0
                            && balances.get(consumer).consumed().getOrDefault(key, 0L) > 0) {
                        edges[producer][consumer] = true;
                        break;
                    }
                }
            }
        }
        if (size == 1) return edges[0][0];
        return reachesEveryMember(edges, false) && reachesEveryMember(edges, true);
    }

    private static boolean reachesEveryMember(boolean[][] edges, boolean reverse) {
        var visited = new boolean[edges.length];
        var pending = new java.util.ArrayDeque<Integer>();
        visited[0] = true;
        pending.add(0);
        while (!pending.isEmpty()) {
            int current = pending.removeFirst();
            for (int next = 0; next < edges.length; next++) {
                boolean linked = reverse ? edges[next][current] : edges[current][next];
                if (linked && !visited[next]) {
                    visited[next] = true;
                    pending.addLast(next);
                }
            }
        }
        for (var member : visited) if (!member) return false;
        return true;
    }

    private static SubsetLoopStatus hasBalancedClosedLoop(List<IPatternDetails> members) {
        var balances = prepareBalances(members);
        if (balances == null) return SubsetLoopStatus.ERROR;
        if (!isStronglyConnected(balances)) return SubsetLoopStatus.NO;
        var possibleOutputs = new java.util.LinkedHashSet<AEKey>();
        for (var balance : balances) possibleOutputs.addAll(balance.produced().keySet());
        for (var output : possibleOutputs) {
            var solved = solveCoefficients(members, output);
            if (solved.status() == PositiveIntegerLinearSolver.Status.SOLVED) {
                return SubsetLoopStatus.YES;
            }
            if (solved.status() != PositiveIntegerLinearSolver.Status.INFEASIBLE) {
                return SubsetLoopStatus.ERROR;
            }
        }
        return SubsetLoopStatus.NO;
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
        for (var output : outputs) {
            for (var possible : possibleInputs) {
                if (possible.what() == null) continue;
                if (ignoreSecondary
                        && possible.what().dropSecondary().equals(output.key().dropSecondary())) {
                    return new LoopMatch(output.key(), isDurabilityFuzzy(possible.what(), output.key()));
                }
                if (!ignoreSecondary && output.fuzzy()
                        && possible.what().equals(output.key())) {
                    return new LoopMatch(output.key(), true);
                }
                if (!ignoreSecondary && !output.fuzzy()
                        && possible.what().equals(output.key())) {
                    return new LoopMatch(output.key(), false);
                }
            }
        }
        return null;
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
            Map<AEKey, Long> outputSeed) {
        public MemberFlow {
            inputSeed = Map.copyOf(inputSeed);
            outputSeed = Map.copyOf(outputSeed);
        }
    }
    private record Balance(Map<AEKey, Long> consumed, Map<AEKey, Long> produced) { }
    private record LoopOutput(AEKey key, boolean fuzzy) { }
    private record LoopMatch(AEKey key, boolean forbidden) { }
    private enum SubsetLoopStatus { YES, NO, ERROR }

    private ClosedLoopPatternAnalyzer() { }
}
