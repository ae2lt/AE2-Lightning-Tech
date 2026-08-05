package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PendingRequesterOutputAccountingTest {
    @Test
    void leavesLegitimateDeferredOutputReserved() {
        var result = PendingRequesterOutputAccounting.capToRemainingDemand(
                credits("a", 10L), 80L, "a");

        assertEquals(0L, result.released());
        assertEquals(credits("a", 10L), result.retained());
    }

    @Test
    void releasesOnlyTheCreditBeyondRemainingDemand() {
        var result = PendingRequesterOutputAccounting.capToRemainingDemand(
                credits("a", 44L), 20L, "a");

        assertEquals(24L, result.released());
        assertEquals(credits("a", 20L), result.retained());
    }

    @Test
    void clearsLegacyCreditAfterJobDemandReachedZero() {
        var result = PendingRequesterOutputAccounting.capToRemainingDemand(
                credits("a", 44L), 0L, null);

        assertEquals(44L, result.released());
        assertTrue(result.retained().isEmpty());
    }

    @Test
    void releasesTheNewlyCompletedVariantBeforeOtherVariants() {
        var pending = List.of(
                new PendingRequesterOutputAccounting.Credit<>("a", 44L),
                new PendingRequesterOutputAccounting.Credit<>("b", 20L));
        var result = PendingRequesterOutputAccounting.capToRemainingDemand(
                pending, 30L, "a");

        assertEquals(34L, result.released());
        assertEquals(List.of(
                new PendingRequesterOutputAccounting.Credit<>("b", 20L),
                new PendingRequesterOutputAccounting.Credit<>("a", 10L)),
                result.retained());
    }

    private static List<PendingRequesterOutputAccounting.Credit<String>> credits(
            String key, long amount) {
        return List.of(new PendingRequesterOutputAccounting.Credit<>(key, amount));
    }
}
