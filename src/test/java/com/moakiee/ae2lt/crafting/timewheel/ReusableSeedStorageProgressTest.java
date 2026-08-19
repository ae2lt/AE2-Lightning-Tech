package com.moakiee.ae2lt.crafting.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReusableSeedStorageProgressTest {
    @Test
    void noDiskAndFullDiskCannotTakeTheReturnedSeed() {
        assertEquals(0, ReusableSeedStorageProgress.transferable(1, 1, 0));
    }

    @Test
    void insertedDiskAllowsTheCpuHeldSeedToReturn() {
        assertEquals(1, ReusableSeedStorageProgress.transferable(1, 1, 1));
    }

    @Test
    void partialSpaceMakesBoundedProgressWithoutLosingTheRemainder() {
        assertEquals(3, ReusableSeedStorageProgress.transferable(8, 8, 3));
        assertEquals(2, ReusableSeedStorageProgress.transferable(8, 2, 20));
    }
}
