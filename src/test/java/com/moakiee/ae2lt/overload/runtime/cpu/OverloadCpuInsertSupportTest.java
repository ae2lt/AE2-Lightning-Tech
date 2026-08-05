package com.moakiee.ae2lt.overload.runtime.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OverloadCpuInsertSupportTest {
    @Test
    void exactPendingIsRemovedFromTheNativeStrictMatch() {
        assertEquals(0L, OverloadCpuInsertSupport.nativeStrictMatch(4L, 4L, 4L));
        assertEquals(3L, OverloadCpuInsertSupport.nativeStrictMatch(7L, 7L, 4L));
    }

    @Test
    void partialOfferProtectsStrictExcessBeforeExactPending() {
        assertEquals(2L, OverloadCpuInsertSupport.nativeStrictMatch(2L, 7L, 4L));
        assertEquals(3L, OverloadCpuInsertSupport.nativeStrictMatch(5L, 7L, 4L));
        assertEquals(0L, OverloadCpuInsertSupport.nativeStrictMatch(2L, 4L, 5L));
    }

    @Test
    void malformedNegativeInputsAreTreatedAsNoMatch() {
        assertEquals(0L, OverloadCpuInsertSupport.nativeStrictMatch(-1L, 2L, 1L));
        assertEquals(3L, OverloadCpuInsertSupport.nativeStrictMatch(3L, 3L, -2L));
    }
}
