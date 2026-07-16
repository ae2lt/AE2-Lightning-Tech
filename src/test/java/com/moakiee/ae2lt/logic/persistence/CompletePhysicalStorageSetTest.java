package com.moakiee.ae2lt.logic.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompletePhysicalStorageSetTest {
    @Test
    void preservesPhysicalShardOrder() {
        var resolved = CompletePhysicalStorageSet.resolve(
                List.of("third", "first", "second"),
                Map.of("first", 1, "second", 2, "third", 3)::get);

        assertEquals(List.of(3, 1, 2), resolved.orElseThrow());
    }

    @Test
    void missingShardNeverReturnsACompactedSubset() {
        var resolved = CompletePhysicalStorageSet.resolve(
                List.of("first", "unloaded", "third"),
                Map.of("first", 1, "third", 3)::get);

        assertTrue(resolved.isEmpty());
    }
}
