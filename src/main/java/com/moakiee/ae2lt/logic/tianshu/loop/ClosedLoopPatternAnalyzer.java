package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.thunderbolt.core.planner.Sat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClosedLoopPatternAnalyzer {
    public static ClosedLoopAnalysis analyze(
            List<Member> members, AEKey requestedOutput) {
        if (members == null || members.isEmpty() || requestedOutput == null) return null;
        var consumed = new LinkedHashMap<AEKey, Long>();
        var produced = new LinkedHashMap<AEKey, Long>();
        var perMember = new ArrayList<Balance>(members.size());
        var possibleLoopOutputs = new ArrayList<LoopOutput>();
        for (var member : members) {
            if (member == null || member.details() == null) return null;
            var overload = member.details() instanceof
                    com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails value
                    ? value : null;
            var memberOutputs = member.details().getOutputs();
            for (int slot = 0; slot < memberOutputs.size(); slot++) {
                var output = memberOutputs.get(slot);
                if (output == null || output.what() == null || output.amount() <= 0) return null;
                possibleLoopOutputs.add(new LoopOutput(
                        output.what(), overload != null && overload.isFuzzyOutput(slot)));
            }
        }
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
                long consumedPerCopy = balance.consumed().getOrDefault(key, 0L);
                long producedPerCopy = balance.produced().getOrDefault(key, 0L);
                long held = balances.getOrDefault(key, 0L);
                if (consumedPerCopy == 0L) {
                    balances.put(key, Sat.add(held, Sat.mul(producedPerCopy, member.copies())));
                    continue;
                }
                long firstDeficit = Math.max(0L, consumedPerCopy - held);
                if (firstDeficit > 0L) seedAmounts.merge(key, firstDeficit, Sat::add);
                long afterFirst = Sat.add(Math.max(held, consumedPerCopy) - consumedPerCopy, producedPerCopy);
                long remainingCopies = member.copies() - 1L;
                if (remainingCopies == 0L) {
                    balances.put(key, afterFirst);
                } else if (producedPerCopy >= consumedPerCopy) {
                    balances.put(key, Sat.add(afterFirst,
                            Sat.mul(producedPerCopy - consumedPerCopy, remainingCopies)));
                } else {
                    long decline = consumedPerCopy - producedPerCopy;
                    long requiredBeforeRemaining = Sat.add(consumedPerCopy,
                            Sat.mul(decline, remainingCopies - 1L));
                    long repeatedDeficit = Math.max(0L, requiredBeforeRemaining - afterFirst);
                    if (repeatedDeficit > 0L) seedAmounts.merge(key, repeatedDeficit, Sat::add);
                    balances.put(key, repeatedDeficit > 0L
                            ? producedPerCopy
                            : afterFirst - Sat.mul(decline, remainingCopies));
                }
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

    private static List<GenericStack> toStacks(Map<AEKey, Long> values) {
        var result = new ArrayList<GenericStack>(values.size());
        for (var entry : values.entrySet()) {
            if (entry.getValue() > 0) result.add(new GenericStack(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    public record Member(IPatternDetails details, long copies) { }
    private record Balance(Map<AEKey, Long> consumed, Map<AEKey, Long> produced) { }
    private record LoopOutput(AEKey key, boolean fuzzy) { }
    private record LoopMatch(AEKey key, boolean forbidden) { }

    private ClosedLoopPatternAnalyzer() { }
}
