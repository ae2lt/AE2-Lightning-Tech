package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProductiveDispatchSchedulerTest {
    @Test
    void failedNinetyNinePercentIsProbedOnceInsteadOfBeforeEverySuccess() {
        var workers = new ArrayList<Worker>();
        for (int i = 0; i < 99; i++) workers.add(new Worker(false));
        var productive = new Worker(true);
        workers.add(productive);

        int used = ProductiveDispatchScheduler.run(
                1_000,
                32,
                workers,
                Worker::dispatch);

        assertEquals(1_000, used);
        for (int i = 0; i < 99; i++) {
            assertEquals(1, workers.get(i).calls);
        }
        assertEquals(1_000, productive.successes);
        assertEquals(33, productive.calls);
    }

    @Test
    void allFailedCandidatesStopAfterTheDiscoveryPass() {
        var workers = List.of(new Worker(false), new Worker(false), new Worker(false));

        int used = ProductiveDispatchScheduler.run(
                16_384,
                32,
                workers,
                Worker::dispatch);

        assertEquals(0, used);
        for (var worker : workers) assertEquals(1, worker.calls);
    }

    @Test
    void productiveWorkersKeepIndependentCopyLimitsWhileSharingDispatches() {
        var first = new LimitedWorker(5);
        var second = new LimitedWorker(5);

        int used = ProductiveDispatchScheduler.run(
                10,
                32,
                List.of(first, second),
                LimitedWorker::dispatch);

        assertEquals(10, used);
        assertEquals(5, first.successes);
        assertEquals(5, second.successes);
    }

    private static final class Worker {
        private final boolean productive;
        private int calls;
        private int successes;

        private Worker(boolean productive) {
            this.productive = productive;
        }

        private int dispatch(int allowance) {
            calls++;
            int used = productive ? allowance : 0;
            successes += used;
            return used;
        }
    }

    private static final class LimitedWorker {
        private final int limit;
        private int successes;

        private LimitedWorker(int limit) {
            this.limit = limit;
        }

        private int dispatch(int allowance) {
            int used = Math.min(allowance, limit - successes);
            successes += used;
            return used;
        }
    }
}
