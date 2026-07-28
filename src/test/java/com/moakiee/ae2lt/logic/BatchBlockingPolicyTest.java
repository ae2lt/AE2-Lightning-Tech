package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BatchBlockingPolicyTest {

    @Test
    void vanillaBlockingStopsTheNextPhysicalChunk() {
        assertTrue(BatchBlockingPolicy.isBlocked(
                false, true, true, false, null, new Pattern("current", 1)));
    }

    @Test
    void samePatternBlockingAllowsOnlyTheExactPreviousPattern() {
        var previous = new Pattern("same", 7);
        var equalCurrent = new Pattern("same", 7);
        var other = new Pattern("other", 7);

        assertFalse(BatchBlockingPolicy.isBlocked(
                false, true, true, true, previous, equalCurrent));
        assertTrue(BatchBlockingPolicy.isBlocked(
                false, true, true, true, previous, other));
    }

    @Test
    void hashCollisionDoesNotReplaceFullEquality() {
        var previous = new Pattern("first", 31);
        var collision = new Pattern("second", 31);

        assertTrue(BatchBlockingPolicy.isBlocked(
                false, true, true, true, previous, collision));
    }

    @Test
    void craftingLockAlwaysStopsTheNextChunk() {
        var pattern = new Pattern("same", 1);

        assertTrue(BatchBlockingPolicy.isBlocked(
                true, false, false, true, pattern, pattern));
    }

    @Test
    void disabledBlockingDoesNotInspectPatternHistory() {
        assertFalse(BatchBlockingPolicy.isBlocked(
                false, false, true, false, null, new Pattern("current", 1)));
    }

    private record Pattern(String id, int hash) {
        @Override
        public int hashCode() {
            return hash;
        }
    }
}
