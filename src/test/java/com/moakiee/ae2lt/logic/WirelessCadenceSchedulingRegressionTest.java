package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class WirelessCadenceSchedulingRegressionTest {
    private final IPatternDetails pattern = new EmptyPattern();

    @Test
    void acceptingTheEntireAllowanceDoesNotProveTheMachineDrainedOnlyThatMuch() {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2000, false);
        // The dispatcher allowed only 512 copies. Inferring 2000 / 512 times
        // the elapsed time here creates a self-sustaining slow refill schedule.
        int delay = cadence.recordSuccess("target", pattern, 10, 512, true);
        assertTrue(delay <= 5, "caller-limited success extended the refill interval: " + delay);
    }

    @Test
    void fullMachineDoesNotErasePreviouslyProvenSingleChunkRefills() {
        var cadence = new WirelessBatchCadence<String>();
        for (int tick = 0; tick <= 30; tick += 10) {
            cadence.recordSuccess("target", pattern, tick, 512, false, ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        assertTrue(cadence.usesSingleChunkRefill("target", pattern));
        cadence.recordFailure("target", pattern, 31);
        assertTrue(cadence.usesSingleChunkRefill("target", pattern));
        cadence.clear();
        assertFalse(cadence.usesSingleChunkRefill("target", pattern));
    }

    @Test
    void knownRejectionDoesNotAlsoTriggerAnUnnecessaryCapacityAudit() {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2048, false);
        cadence.recordFailure("target", pattern, 30);
        cadence.recordSuccess("target", pattern, 60, 1024, false);
        assertFalse(cadence.shouldReopenReservoirTail("target", pattern));
    }

    private static final class EmptyPattern implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(); }
        public boolean equals(Object other) { throw new AssertionError("unexpected equality"); }
        public int hashCode() { return 31; }
    }
}
