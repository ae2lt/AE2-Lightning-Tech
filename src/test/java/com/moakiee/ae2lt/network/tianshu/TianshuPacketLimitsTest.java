package com.moakiee.ae2lt.network.tianshu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

class TianshuPacketLimitsTest {
    @Test
    void acceptsOnlyCountsInsideTheSharedBound() {
        assertDoesNotThrow(() -> TianshuPacketLimits.requireListSize("test", 0));
        assertDoesNotThrow(() -> TianshuPacketLimits.requireListSize(
                "test", TianshuPacketLimits.MAX_LIST_ENTRIES));
        assertThrows(IllegalArgumentException.class,
                () -> TianshuPacketLimits.requireListSize("test", -1));
        assertThrows(IllegalArgumentException.class,
                () -> TianshuPacketLimits.requireListSize(
                        "test", TianshuPacketLimits.MAX_LIST_ENTRIES + 1));
        assertThrows(DecoderException.class,
                () -> TianshuPacketLimits.requireDecodedListSize(
                        "test", TianshuPacketLimits.MAX_LIST_ENTRIES + 1));
    }
}
