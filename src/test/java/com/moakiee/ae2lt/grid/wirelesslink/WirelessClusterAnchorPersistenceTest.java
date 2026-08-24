package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WirelessClusterAnchorPersistenceTest {
    @Test
    void cableCenterAnchorRoundTripsWithEmptySide() {
        var link = WirelessLink.createPart(
                UUID.randomUUID(),
                42,
                "minecraft:overworld",
                1234L,
                "",
                "ae2:cable_bus",
                "ae2:cable_bus",
                "ae2:fluix_glass_cable",
                "appeng.parts.networking.CablePart",
                UUID.randomUUID(),
                99L);

        var restored = WirelessLink.fromPersistentSnapshot(link.toPersistentSnapshot());

        assertTrue(restored.isPresent());
        assertEquals(link, restored.orElseThrow());
        assertEquals("", restored.orElseThrow().sideName());
    }

    @Test
    void idIndexUpdatesStateWithoutDuplicatingRestoreOrder() {
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

        assertEquals(1, index.values().size());
        assertEquals(1, index.nextBatch(64).size());
        assertEquals(WirelessLinkState.CONNECTED, index.get(link.linkId()).state());
    }

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

    @Test
    void clusterFrequencyConflictSurvivesSaveAndReload() {
        var conflicted = WirelessLink.createDevice(
                        UUID.randomUUID(),
                        7,
                        "minecraft:overworld",
                        88L,
                        "ae2:interface",
                        "ae2:interface",
                        UUID.randomUUID(),
                        1L)
                .withState(WirelessLinkState.CLUSTER_FREQUENCY_CONFLICT, 2L);

        var restored = WirelessLink.fromPersistentSnapshot(conflicted.toPersistentSnapshot());

        assertTrue(restored.isPresent());
        assertEquals(WirelessLinkState.CLUSTER_FREQUENCY_CONFLICT, restored.orElseThrow().state());
    }
}
