package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatternDispatchPenaltyTrackerTest {
    @Test
    void expiredPenaltyIsRemovedAndStartsFreshAfterAnotherRejection() {
        var tracker = new PatternDispatchPenaltyTracker<String, String>();

        tracker.recordRejection("target", "pattern", 10, false);
        assertTrue(tracker.shouldSkip("target", "pattern", 14));
        assertFalse(tracker.shouldSkip("target", "pattern", 15));

        tracker.recordRejection("target", "pattern", 15, false);
        assertTrue(tracker.shouldSkip("target", "pattern", 19));
        assertFalse(tracker.shouldSkip("target", "pattern", 20));
    }

    @Test
    void unrelatedLookupPurgesAllExpiredPairs() {
        var tracker = new PatternDispatchPenaltyTracker<String, String>();
        tracker.recordRejection("first", "a", 0, false);
        tracker.recordRejection("second", "b", 0, false);
        assertEquals(2, tracker.trackedPenaltyCount());

        assertFalse(tracker.shouldSkip("other", "pattern", 5));
        assertEquals(0, tracker.trackedPenaltyCount());
    }
}
