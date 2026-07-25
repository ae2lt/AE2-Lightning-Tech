package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.helpers.patternprovider.PatternContainer;
import com.moakiee.ae2lt.blockentity.MatrixPatternStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuPatternStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuClosedLoopPatternVisibilityContractTest {
    @Test
    void physicalWarehousesAreIndependentPatternContainersAndPortsAreNot() throws Exception {
        var terminalPart = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/part/TianshuPatternEncodingTerminalPart.java"));
        var tianshuPort = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/TianshuSupercomputerPortBlockEntity.java"));
        var tianshuStorage = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/TianshuPatternStorageBlockEntity.java"));
        var matrixPort = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPortBlockEntity.java"));
        var matrixStorage = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPatternStorageBlockEntity.java"));

        assertFalse(terminalPart.contains("TianshuPatternTerminalStorage"));
        assertFalse(terminalPart.contains("MEStorage getInventory()"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/terminal/"
                        + "TianshuPatternTerminalStorage.java")));

        assertFalse(PatternContainer.class.isAssignableFrom(
                TianshuSupercomputerPortBlockEntity.class));
        assertTrue(PatternContainer.class.isAssignableFrom(
                TianshuPatternStorageBlockEntity.class));
        assertFalse(PatternContainer.class.isAssignableFrom(MatrixPortBlockEntity.class));
        assertTrue(PatternContainer.class.isAssignableFrom(
                MatrixPatternStorageBlockEntity.class));

        assertFalse(tianshuPort.contains("PatternContainer"));
        assertFalse(tianshuPort.contains("getTerminalPatternInventory()"));
        assertTrue(tianshuStorage.contains("implements PatternContainer"));
        assertTrue(tianshuStorage.contains("getTerminalPatternInventory()"));
        assertTrue(tianshuStorage.contains("new InternalPatternContainerLink"));

        assertFalse(matrixPort.contains("implements IBatchCraftingProvider, PatternContainer"));
        assertTrue(matrixStorage.contains("implements MatrixPatternCore, PatternContainer"));
        assertTrue(matrixStorage.contains("getTerminalPatternInventory()"));
        assertTrue(matrixStorage.contains("new InternalPatternContainerLink"));
        assertTrue(matrixStorage.contains(
                "AEItemKey.of(ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get())"));
    }
}
