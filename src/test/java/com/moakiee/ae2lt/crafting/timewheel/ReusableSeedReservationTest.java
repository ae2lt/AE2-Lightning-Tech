package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReusableSeedReservationTest {
    @Test
    void dedicatedTaskSubtractsOnlyItsOwnPositivePoolFromTheAggregate() {
        assertEquals(2, ReusableSeedReservation.reservedForTask(2, 1, false));
        assertEquals(1, ReusableSeedReservation.reservedForTask(2, 1, true));
        assertEquals(1, ReusableSeedReservation.reservedForTask(1, -1, true));
        assertEquals(0, ReusableSeedReservation.reservedForTask(1, 1, true));
    }
}
