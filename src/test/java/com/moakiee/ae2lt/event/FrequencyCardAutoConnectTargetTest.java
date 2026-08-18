package com.moakiee.ae2lt.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrequencyCardAutoConnectTargetTest {
    @Test
    void usesResolvedPlacementPositionAndSide() {
        var target = FrequencyCardAutoConnectTarget.fromPartPlacement(
                new FrequencyCardAutoConnectTarget.GridPos(1, 2, 3),
                "north",
                new FrequencyCardAutoConnectTarget.GridPos(4, 5, 6),
                "south");

        assertEquals(new FrequencyCardAutoConnectTarget.GridPos(4, 5, 6), target.pos());
        assertEquals("south", target.sideName());
    }

    @Test
    void cablePartsAreStoredInTheCenterSlot() {
        assertEquals("", FrequencyCardAutoConnectTarget.placedPartStorageSideName("north", true));
        assertEquals("north", FrequencyCardAutoConnectTarget.placedPartStorageSideName("north", false));
    }
}
