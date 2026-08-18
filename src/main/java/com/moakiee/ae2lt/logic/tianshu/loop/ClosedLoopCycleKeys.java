package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds the material SCC that can carry a contracted loop's reusable state. */
final class ClosedLoopCycleKeys {
    static Set<AEKey> analyze(List<IPatternDetails> members, Collection<AEKey> seeds) {
        var forward = new HashMap<AEKey, Set<AEKey>>();
        var reverse = new HashMap<AEKey, Set<AEKey>>();
        for (var details : members) {
            var explicitOutputs = new LinkedHashSet<AEKey>();
            for (var output : details.getOutputs()) {
                if (output.what() != null) explicitOutputs.add(output.what());
            }
            for (var input : details.getInputs()) {
                for (var candidate : input.getPossibleInputs()) {
                    var consumed = candidate.what();
                    if (consumed == null) continue;
                    forward.computeIfAbsent(consumed, ignored -> new LinkedHashSet<>());
                    reverse.computeIfAbsent(consumed, ignored -> new LinkedHashSet<>());
                    for (var output : explicitOutputs) {
                        forward.computeIfAbsent(consumed, ignored -> new LinkedHashSet<>()).add(output);
                        reverse.computeIfAbsent(output, ignored -> new LinkedHashSet<>()).add(consumed);
                        forward.computeIfAbsent(output, ignored -> new LinkedHashSet<>());
                    }
                    var remainder = input.getRemainingKey(consumed);
                    if (remainder != null) {
                        forward.computeIfAbsent(consumed, ignored -> new LinkedHashSet<>()).add(remainder);
                        reverse.computeIfAbsent(remainder, ignored -> new LinkedHashSet<>()).add(consumed);
                        forward.computeIfAbsent(remainder, ignored -> new LinkedHashSet<>());
                    }
                }
            }
        }

        var result = new LinkedHashSet<AEKey>();
        for (var seed : seeds) {
            if (seed == null) continue;
            var reachable = reachable(seed, forward);
            var canReturn = reachable(seed, reverse);
            reachable.retainAll(canReturn);
            result.addAll(reachable);
            result.add(seed);
        }
        return Set.copyOf(result);
    }

    private static Set<AEKey> reachable(AEKey start, Map<AEKey, Set<AEKey>> graph) {
        var result = new HashSet<AEKey>();
        var queue = new ArrayDeque<AEKey>();
        result.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            for (var next : graph.getOrDefault(current, Set.of())) {
                if (result.add(next)) queue.addLast(next);
            }
        }
        return result;
    }

    private ClosedLoopCycleKeys() {
    }
}
