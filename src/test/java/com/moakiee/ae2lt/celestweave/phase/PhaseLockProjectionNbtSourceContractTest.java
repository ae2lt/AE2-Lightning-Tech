package com.moakiee.ae2lt.celestweave.phase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PhaseLockProjectionNbtSourceContractTest {
    @Test
    void enchantmentSnapshotUsesVanilla1201ShortLevelTags() throws Exception {
        String synchronizer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/phase/PhaseLockProjectionSynchronizer.java"));

        assertTrue(synchronizer.contains("entryTag.putShort(\"lvl\""));
        assertFalse(synchronizer.contains("entryTag.putInt(\"lvl\""));
        assertTrue(synchronizer.contains("normalizedEnchantments.isEmpty()"));
        assertTrue(synchronizer.contains("entries.sort(Comparator.comparing("));
    }
}
