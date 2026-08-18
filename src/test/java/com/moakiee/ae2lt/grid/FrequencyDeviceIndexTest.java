package com.moakiee.ae2lt.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FrequencyDeviceIndexTest {
    @Test
    void preservesRegistrationOrder() {
        var index = new FrequencyDeviceIndex<String>();

        index.put(7, "minecraft:overworld", 10L, "first");
        index.put(7, "minecraft:overworld", 20L, "second");

        assertEquals(List.of("first", "second"), index.get(7));
    }

    @Test
    void replacesDeviceAtSameDimensionAndPositionWithoutReordering() {
        var index = new FrequencyDeviceIndex<String>();

        index.put(7, "minecraft:overworld", 10L, "old");
        index.put(7, "minecraft:overworld", 20L, "second");

        assertTrue(index.put(7, "minecraft:overworld", 10L, "updated"));
        assertFalse(index.put(7, "minecraft:overworld", 10L, "updated"));
        assertEquals(List.of("updated", "second"), index.get(7));
    }

    @Test
    void isolatesDimensionsAndFrequencies() {
        var index = new FrequencyDeviceIndex<String>();

        index.put(7, "minecraft:overworld", 10L, "overworld");
        index.put(7, "minecraft:the_nether", 10L, "nether");
        index.put(8, "minecraft:overworld", 10L, "other-frequency");

        assertTrue(index.remove(7, "minecraft:overworld", 10L));
        assertEquals(List.of("nether"), index.get(7));
        assertEquals(List.of("other-frequency"), index.get(8));
    }
}
