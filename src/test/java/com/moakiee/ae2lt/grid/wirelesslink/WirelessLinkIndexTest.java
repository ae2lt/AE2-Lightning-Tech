package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WirelessLinkIndexTest {
    @Test
    void positionIndexTracksStateUpdatesAndRemovals() {
        var link = WirelessLink.createDevice(
                UUID.randomUUID(),
                7,
                "minecraft:overworld",
                88L,
                "ae2:interface",
                "ae2:interface",
                UUID.randomUUID(),
                1L);
        var index = new WirelessLinkIndex();

        index.put(link);
        index.put(link.withState(WirelessLinkState.CONNECTED, 2L));

        assertEquals(1, index.findAllAt("minecraft:overworld", 88L).size());
        assertEquals(
                WirelessLinkState.CONNECTED,
                index.findAllAt("minecraft:overworld", 88L).iterator().next().state());
        assertTrue(index.findAllAt("minecraft:the_nether", 88L).isEmpty());

        index.remove(link.linkId());

        assertTrue(index.findAllAt("minecraft:overworld", 88L).isEmpty());
    }
}
