package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moakiee.ae2lt.crafting.runtime.api.CraftingTaskPriorities;
import com.moakiee.thunderbolt.core.crafting.loop.IPrioritizedCraftingTask;
import org.junit.jupiter.api.Test;

class TimeWheelTaskPriorityTest {

    @Test
    void closedLoopWorkRunsBeforeOrdinaryDownstreamPatterns() {
        var loop = new PrioritizedPattern(1_000, 0);
        var downstream = new OrdinaryPattern();

        assertTrue(CraftingTaskPriorities.compare(loop, downstream) < 0);
        assertTrue(CraftingTaskPriorities.compare(downstream, loop) > 0);
    }

    @Test
    void memberOrderBreaksTiesInsideOneClosedLoopPriority() {
        var producer = new PrioritizedPattern(1_000, 0);
        var consumer = new PrioritizedPattern(1_000, 1);

        assertTrue(CraftingTaskPriorities.compare(producer, consumer) < 0);
        assertTrue(CraftingTaskPriorities.compare(consumer, producer) > 0);
    }

    @Test
    void explicitPriorityDominatesMemberOrder() {
        var urgent = new PrioritizedPattern(2_000, 99);
        var orderedFirstButLowerPriority = new PrioritizedPattern(1_000, 0);

        assertTrue(CraftingTaskPriorities.compare(
                urgent, orderedFirstButLowerPriority) < 0);
    }

    @Test
    void activeOrdinaryTaskWinsATieUntilItFinishes() {
        var producer = new OrdinaryPattern();
        var downstream = new OrdinaryPattern();

        assertTrue(CraftingTaskPriorities.compare(producer, downstream, producer) < 0);
        assertTrue(CraftingTaskPriorities.compare(downstream, producer, producer) > 0);
    }

    @Test
    void explicitPriorityStillPreemptsTheActiveOrdinaryTask() {
        var activeOrdinary = new OrdinaryPattern();
        var urgent = new PrioritizedPattern(1_000, 0);

        assertTrue(CraftingTaskPriorities.compare(urgent, activeOrdinary, activeOrdinary) < 0);
        assertTrue(CraftingTaskPriorities.compare(activeOrdinary, urgent, activeOrdinary) > 0);
    }

    private static class OrdinaryPattern { }

    private static final class PrioritizedPattern extends OrdinaryPattern
            implements IPrioritizedCraftingTask {
        private final int priority;
        private final int order;

        private PrioritizedPattern(int priority, int order) {
            this.priority = priority;
            this.order = order;
        }

        @Override public int dispatchPriority() { return priority; }
        @Override public int dispatchOrder() { return order; }
    }
}
