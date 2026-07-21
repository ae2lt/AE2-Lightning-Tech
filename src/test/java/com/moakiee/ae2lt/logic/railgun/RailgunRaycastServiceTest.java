package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RailgunRaycastServiceTest {

    @Test
    void splitsLongRaysIntoFixedLengthQueries() {
        assertEquals(0, RailgunRaycastService.segmentCount(0.0D));
        assertEquals(1, RailgunRaycastService.segmentCount(1.0D));
        assertEquals(1, RailgunRaycastService.segmentCount(16.0D));
        assertEquals(2, RailgunRaycastService.segmentCount(16.0001D));
        assertEquals(4, RailgunRaycastService.segmentCount(64.0D));
        assertEquals(16, RailgunRaycastService.segmentCount(256.0D));
        assertEquals(0, RailgunRaycastService.segmentCount(Double.NaN));
    }
}
