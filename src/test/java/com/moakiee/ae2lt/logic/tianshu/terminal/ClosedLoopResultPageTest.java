package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClosedLoopResultPageTest {
    @Test
    void emptyPageIsCanonicalEvenWhenTheRequestedOffsetIsPastTheEnd() {
        var page = ClosedLoopResultPage.from(
                7, ClosedLoopResultPage.Kind.SEEDS, List.of(), 42);

        assertEquals(7, page.revision());
        assertEquals(0, page.offset());
        assertEquals(0, page.total());
        assertEquals(List.of(), page.entries());
    }

    @Test
    void rejectsOffsetsCountsAndPagesOutsideTheProtocolBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ClosedLoopResultPage(
                0, ClosedLoopResultPage.Kind.EXTERNAL_INPUTS, -1, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ClosedLoopResultPage(
                0, ClosedLoopResultPage.Kind.EXTERNAL_INPUTS, 0,
                ClosedLoopResultPage.MAX_RESULTS + 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ClosedLoopResultPage(
                0, ClosedLoopResultPage.Kind.EXTERNAL_INPUTS, 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ClosedLoopResultPage(
                0, ClosedLoopResultPage.Kind.EXTERNAL_INPUTS, 0,
                ClosedLoopResultPage.PAGE_SIZE + 1,
                Collections.nCopies(ClosedLoopResultPage.PAGE_SIZE + 1, null)));
    }
}
