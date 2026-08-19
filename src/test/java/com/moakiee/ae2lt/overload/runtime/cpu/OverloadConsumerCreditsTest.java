package com.moakiee.ae2lt.overload.runtime.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OverloadConsumerCreditsTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void metadataMergesDuplicateConsumersAndHasNoFalseSingleOwnerView() {
        var metadata = new OverloadReusableSeedMetadata(List.of(
                new OverloadConsumerCredit(FIRST, 2),
                new OverloadConsumerCredit(SECOND, 3),
                new OverloadConsumerCredit(FIRST, 4)));

        assertEquals(List.of(
                new OverloadConsumerCredit(FIRST, 6),
                new OverloadConsumerCredit(SECOND, 3)), metadata.consumerCredits());
        assertEquals(9, metadata.amount());
        assertNull(metadata.groupId());
    }

    @Test
    void partialClaimSplitsAcrossConsumersInStableOrder() {
        var credits = List.of(
                new OverloadConsumerCredit(FIRST, 2),
                new OverloadConsumerCredit(SECOND, 3));

        assertEquals(List.of(
                new OverloadConsumerCredit(FIRST, 2),
                new OverloadConsumerCredit(SECOND, 1)),
                OverloadConsumerCredit.limit(credits, 3));
    }

    @Test
    void legacyMetadataConstructorMapsGroupToConsumer() {
        var metadata = new OverloadReusableSeedMetadata(FIRST, true, 7);
        assertEquals(List.of(new OverloadConsumerCredit(FIRST, 7)), metadata.consumerCredits());
        assertEquals(FIRST, metadata.groupId());
        assertEquals(7, metadata.amount());
        assertEquals(true, metadata.sharedPool());
    }

    @Test
    void boundedValidationDoesNotHideDistinctConsumerOverflowBehindSaturation() {
        var credits = List.of(
                new OverloadConsumerCredit(FIRST, Long.MAX_VALUE),
                new OverloadConsumerCredit(SECOND, Long.MAX_VALUE));

        assertEquals(Long.MAX_VALUE, OverloadConsumerCredit.total(credits),
                "the reporting total intentionally saturates");
        assertFalse(OverloadConsumerCredit.fitsWithin(credits, Long.MAX_VALUE),
                "capacity validation must still reject the second consumer allocation");
    }
}
