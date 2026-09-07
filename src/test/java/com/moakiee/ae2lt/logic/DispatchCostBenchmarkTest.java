package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

/** Repeatable scheduler-only cost measurement, not a Minecraft MSPT benchmark. */
class DispatchCostBenchmarkTest {
    @Test
    void measuresAllocationAndPatternHashCallsWithoutChangingDispatchWork() {
        var platformBean = ManagementFactory.getThreadMXBean();
        var bean = platformBean instanceof com.sun.management.ThreadMXBean supported ? supported : null;
        boolean allocations = bean != null && bean.isThreadAllocatedMemorySupported();
        if (allocations) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        for (int round = 0; round < 3; round++) {
            var dispatch = new ProviderWirelessDispatch();
            var pattern = new CountingPattern();
            var targets = new ArrayList<WirelessConnection>();
            for (int i = 0; i < 512; i++) {
                targets.add(new WirelessConnection(Level.OVERWORLD,
                        new BlockPos(i, 64, 0), Direction.NORTH));
            }
            var accepted = new ProviderWirelessDispatch.BatchAttemptResult(512, 512,
                    true, false, ProviderTarget.BaselineStatus.NONE, WirelessPushOutcome.SUCCESS);
            ProviderWirelessDispatch.BatchAttempt attempt = (target, allowance, exploratory, preserve) -> accepted;
            for (int tick = 0; tick < 300; tick++) dispatchTick(dispatch, targets, pattern, attempt, tick);
            pattern.hashCalls = 0;
            long allocatedBefore = allocations ? bean.getThreadAllocatedBytes(thread) : 0;
            long start = System.nanoTime();
            for (int tick = 300; tick < 800; tick++) dispatchTick(dispatch, targets, pattern, attempt, tick);
            long elapsed = System.nanoTime() - start;
            long allocated = allocations ? bean.getThreadAllocatedBytes(thread) - allocatedBefore : -1;
            System.out.printf("dispatch-cost round=%d targets=512 ticks=500 ns/tick=%d bytes/visit=%.2f patternHashCalls=%d%n",
                    round, elapsed / 500, allocated / (512.0 * 500), pattern.hashCalls);
        }
    }

    private static void dispatchTick(ProviderWirelessDispatch dispatch, List<WirelessConnection> targets,
            IPatternDetails pattern, ProviderWirelessDispatch.BatchAttempt attempt, int tick) {
        dispatch.prepare(targets, tick, false, WirelessDispatchMode.EVEN_DISTRIBUTION);
        long supplied = Long.MAX_VALUE / 4;
        long remaining = dispatch.dispatchBatch(WirelessDispatchMode.EVEN_DISTRIBUTION,
                pattern, supplied, tick, false, attempt, ignored -> true, ignored -> {
                    throw new AssertionError("live target removed");
                });
        assertEquals(512L * 512, supplied - remaining);
    }

    private static final class CountingPattern implements IPatternDetails {
        private int hashCalls;
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(); }
        public int hashCode() { hashCalls++; return 31; }
        public boolean equals(Object other) { throw new AssertionError("unexpected pattern equality"); }
    }
}
