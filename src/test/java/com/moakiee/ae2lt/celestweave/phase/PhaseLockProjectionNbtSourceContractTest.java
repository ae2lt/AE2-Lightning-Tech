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

    @Test
    void unchangedProjectionDoesNotReplaceMirroredComponentsEveryTick() throws Exception {
        String synchronizer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/phase/PhaseLockProjectionSynchronizer.java"));

        int noneBranch = synchronizer.indexOf(
                "if (direction == PhaseLockProjectionSyncRules.Direction.NONE)");
        int branchReturn = synchronizer.indexOf("return;", noneBranch);
        int firstReplacement = synchronizer.indexOf("replaceMirroredComponents(", noneBranch);
        assertTrue(noneBranch >= 0);
        assertTrue(branchReturn > noneBranch);
        assertTrue(firstReplacement > branchReturn);

        int curseMethod = synchronizer.indexOf("private static void ensureProjectionCurses");
        int setEnchantments = synchronizer.indexOf(
                "setEnchantments(projection, enchantments)", curseMethod);
        String curseBody = synchronizer.substring(curseMethod, setEnchantments);
        assertTrue(curseBody.contains("getOrDefault(projectionCurses.binding(), 0) == 1"));
        assertTrue(curseBody.contains("getOrDefault(projectionCurses.vanishing(), 0) == 1"));
        assertTrue(curseBody.contains("return;"));
    }
}
