package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MatrixPortBatchRenderingContractTest {
    @Test
    void renderTraversalIsLimitedToTheVisiblePatternWindow() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/MatrixPortScreen.java"));

        assertTrue(screen.contains("visiblePatternSlots ="));
        assertTrue(screen.contains("menu.slots.addAll(visibleMenuSlots)"));
        assertTrue(screen.contains("menu.slots.addAll(allMenuSlots)"));
        assertTrue(screen.contains("finally {"));
        assertFalse(screen.contains("renderBackground(graphics, mouseX, mouseY, partialTick);"));
    }

    @Test
    void scrollingAndSearchChangeDetectionDoNotScanEveryPatternEachTick() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/MatrixPortScreen.java"));
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/MatrixPortMenu.java"));

        assertTrue(screen.contains("for (var slot : visiblePatternSlots)"));
        assertTrue(screen.contains("menu.getPatternContentRevision()"));
        assertFalse(screen.contains("contentFingerprint()"));
        assertTrue(menu.contains("patternContentRevision++"));
        assertTrue(menu.contains("public void setItem(int slotId, int stateId, ItemStack stack)"));
        assertTrue(menu.contains("public void initializeContents("));
    }
}
