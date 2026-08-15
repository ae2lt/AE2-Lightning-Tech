package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MultiblockInventoryDropContractTest {
    @Test
    void wrenchRemovalUsesNormalDropsAndLetsOnRemoveOwnInternalContents() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/event/MultiblockWrenchHandler.java"));

        assertAppearsBefore(source, "Block.getDrops(", "level.removeBlock(");
        assertFalse(source.contains("addAdditionalDrops("));
        assertFalse(source.contains("clearContent("));
    }

    @Test
    void internalInventoriesAreClearedBeforeTheirItemsEnterTheWorld() throws Exception {
        assertClearsBeforeDropping("MatrixPatternStorageBlockEntity.java", "inventory.clear()");
        assertClearsBeforeDropping("TianshuPatternStorageBlockEntity.java", "patterns.clear()");
        assertClearsBeforeDropping("TianshuSeedStorageBlockEntity.java", "cells.clear()");
    }

    private static void assertClearsBeforeDropping(String fileName, String clearCall)
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/" + fileName));
        assertAppearsBefore(source, clearCall, "NativeStackDropHelper.popResource(");
    }

    private static void assertAppearsBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, () -> "Missing source fragment: " + first);
        assertTrue(secondIndex >= 0, () -> "Missing source fragment: " + second);
        assertTrue(firstIndex < secondIndex, () -> first + " must appear before " + second);
    }
}
