package com.moakiee.ae2lt.crafting.timewheel;

import java.util.ArrayDeque;
import java.util.List;

/** Work-conserving successful-dispatch scheduler with a one-shot discovery phase. */
public final class ProductiveDispatchScheduler {
    private ProductiveDispatchScheduler() {
    }

    public static <T> int run(
            int totalSuccessfulDispatches,
            int productiveQuantum,
            List<T> candidates,
            DispatchWorker<T> worker) {
        int total = Math.max(0, totalSuccessfulDispatches);
        int remaining = total;
        int quantum = Math.max(1, productiveQuantum);
        var productive = new ArrayDeque<T>();

        // Probe every candidate exactly once. A zero allowance still lets the caller perform
        // maintenance without permitting another successful dispatch.
        for (var candidate : candidates) {
            int allowance = remaining > 0 ? 1 : 0;
            int used = checkedUsage(worker.dispatch(candidate, allowance), allowance);
            remaining -= used;
            if (used > 0) productive.addLast(candidate);
        }

        while (remaining > 0 && !productive.isEmpty()) {
            int roundSize = productive.size();
            boolean roundProgress = false;
            for (int i = 0; i < roundSize && remaining > 0; i++) {
                var candidate = productive.removeFirst();
                int allowance = Math.min(quantum, remaining);
                int used = checkedUsage(worker.dispatch(candidate, allowance), allowance);
                if (used <= 0) continue;
                remaining -= used;
                roundProgress = true;
                productive.addLast(candidate);
            }
            if (!roundProgress) break;
        }
        return total - remaining;
    }

    private static int checkedUsage(int used, int allowance) {
        if (used < 0 || used > allowance) {
            throw new IllegalStateException(
                    "Dispatch worker used " + used + " successful dispatches from allowance " + allowance);
        }
        return used;
    }

    @FunctionalInterface
    public interface DispatchWorker<T> {
        int dispatch(T candidate, int successfulDispatchAllowance);
    }
}
