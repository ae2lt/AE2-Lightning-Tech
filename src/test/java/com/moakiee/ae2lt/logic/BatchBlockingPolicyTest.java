package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class BatchBlockingPolicyTest {

    @Test
    void vanillaBlockingStopsTheNextPhysicalChunk() {
        assertTrue(BatchBlockingPolicy.isBlocked(
                false, true, true, false, null, new Pattern()));
    }

    @Test
    void samePatternBlockingUsesCanonicalIdentityWithoutPatternEquality() {
        var details = new Pattern();

        assertFalse(BatchBlockingPolicy.isBlocked(
                false, true, true, true, details, details));
        assertTrue(BatchBlockingPolicy.isBlocked(
                false, true, true, true, details, new Pattern()));
    }

    @Test
    void craftingLockAlwaysStopsTheNextChunk() {
        var pattern = new Pattern();

        assertTrue(BatchBlockingPolicy.isBlocked(
                true, false, false, true, pattern, pattern));
    }

    @Test
    void disabledBlockingDoesNotInspectPatternHistory() {
        assertFalse(BatchBlockingPolicy.isBlocked(
                false, false, true, false, null, new Pattern()));
    }

    private static final class Pattern implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("third-party equality must not run");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("third-party hashCode must not run");
        }
    }
}
