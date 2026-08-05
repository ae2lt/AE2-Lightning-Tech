package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OverloadInsertAccountingTest {
    @Test
    void strictDemandBeyondTheMirroredExactPendingPrefixIsProtectedFirst() {
        assertEquals(1L, OverloadInsertAccounting.strictPrefixBeforeExactOverload(
                2L, 2L, 1L));
        assertEquals(0L, OverloadInsertAccounting.strictPrefixBeforeExactOverload(
                2L, 1L, 1L));
        assertEquals(2L, OverloadInsertAccounting.strictPrefixBeforeExactOverload(
                2L, Long.MAX_VALUE, 1L));
    }

    @Test
    void exactOverloadPrefixIsRemovedFromTheOverlappingStrictWaitingMatch() {
        long exactOverload = 5L;
        long remainingOffer = 15L;

        assertEquals(20L,
                OverloadInsertAccounting.strictProbeAmount(remainingOffer, exactOverload));
        assertEquals(5L, OverloadInsertAccounting.strictMatchAfterExactOverload(
                remainingOffer,
                10L, // five overload items plus five independent strict items
                exactOverload));
        assertFalse(OverloadInsertAccounting.mayClaimOverloadRemainder(exactOverload));
    }

    @Test
    void partialOfferStillCountsIndependentStrictDemandAfterTheExactPrefix() {
        long exactOverload = 5L;
        long remainingOffer = 2L;

        assertEquals(7L,
                OverloadInsertAccounting.strictProbeAmount(remainingOffer, exactOverload));
        assertEquals(2L, OverloadInsertAccounting.strictMatchAfterExactOverload(
                remainingOffer, 7L, exactOverload));
    }

    @Test
    void noExactOverloadLeavesTheOrdinaryRemainderPathUnchanged() {
        assertEquals(9L, OverloadInsertAccounting.strictProbeAmount(9L, 0L));
        assertEquals(4L,
                OverloadInsertAccounting.strictMatchAfterExactOverload(9L, 4L, 0L));
        assertTrue(OverloadInsertAccounting.mayClaimOverloadRemainder(0L));
    }

    @Test
    void restoredProbeAmountSaturatesInsteadOfWrappingNegative() {
        assertEquals(Long.MAX_VALUE,
                OverloadInsertAccounting.strictProbeAmount(Long.MAX_VALUE - 2L, 5L));
    }

}
