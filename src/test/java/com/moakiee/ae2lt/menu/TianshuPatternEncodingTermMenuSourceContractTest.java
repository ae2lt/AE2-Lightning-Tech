package com.moakiee.ae2lt.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuPatternEncodingTermMenuSourceContractTest {
    @Test
    void ae2EncodingCanReadAndConsumeTheStagedBlankPattern() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        int encodeStart = menu.indexOf("public void encode()");
        int stage = menu.indexOf("stageNetworkBlankPattern()", encodeStart);
        int encode = menu.indexOf("super.encode()", encodeStart);
        int returnUnused = menu.indexOf("returnStagedBlankPatternToNetwork()", encode);

        assertTrue(encodeStart >= 0);
        assertTrue(stage > encodeStart);
        assertTrue(encode > stage);
        assertTrue(returnUnused > encode);
        assertTrue(menu.contains("getEncodedPatternInv()"));
        assertTrue(menu.contains("encodedInventory.setItemDirect(0, AEItems.BLANK_PATTERN.stack"));
    }

    @Test
    void terminalUsesTheInheritedNetworkInventoryDuringAe2Synchronization() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String host = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/terminal/TianshuPatternTerminalHost.java"));
        String part = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/part/TianshuPatternEncodingTerminalPart.java"));

        assertTrue(menu.contains("broadcastParentChanges();"));
        assertFalse(menu.contains("runWithMenuInventory"));
        assertFalse(host.contains("runWithMenuInventory"));
        assertFalse(part.contains("MEStorage getInventory()"));
        assertFalse(part.contains("synchronizingMenuInventory"));
    }

    @Test
    void processingPatternConfigurationPersistsAndTracksTheCurrentDraft() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String host = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/terminal/TianshuPatternTerminalHost.java"));
        String part = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/part/TianshuPatternEncodingTerminalPart.java"));

        assertTrue(menu.contains("restoreProcessingDraft("));
        assertTrue(menu.contains("persistProcessingDraft()"));
        assertTrue(menu.contains("refreshProcessingDraftBinding()"));
        assertTrue(host.contains("getProcessingPatternTerminalDraft()"));
        assertTrue(part.contains("TAG_PROCESSING_DRAFT"));
        assertTrue(part.contains("ProcessingPatternTerminalDraft.read("));
    }

    @Test
    void closedLoopAuthoringUsesLeftMemberMarksAndTheRightPrimaryOutputMark() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));

        assertTrue(menu.contains("new ClosedLoopMemberSlot(closedLoopMemberInventory"));
        assertTrue(menu.contains("new ClosedLoopOutputSlot(closedLoopOutputInventory"));
        assertTrue(menu.contains("var markedOutput = getMarkedClosedLoopPrimaryOutput()"));
        assertTrue(menu.contains("refreshClosedLoops(markedOutput.what())"));
        assertFalse(menu.contains("refreshClosedLoops(source)"));
        assertFalse(menu.contains("PatternDetailsHelper.decodePattern(source"));
    }

    @Test
    void closedLoopEncodingCanConsumeANetworkBlankWithoutAnOldSourcePattern() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        int derived = menu.indexOf("var result = encodeDerivedPattern()");
        int stage = menu.indexOf("stageNetworkBlankPattern()", derived);
        int write = menu.indexOf("encodedInventory.setItemDirect(0, result)", stage);

        assertTrue(derived >= 0);
        assertTrue(stage > derived);
        assertTrue(write > stage);
        assertTrue(menu.contains(
                "if (tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return ItemStack.EMPTY"));
    }
}
