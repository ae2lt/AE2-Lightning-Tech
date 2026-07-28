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
    void insertedCustomPatternsRestoreTheirOwnEditingStateInsteadOfReusingTheOldDraft()
            throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String advanced = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/AdvancedAECompat.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));

        int refresh = menu.indexOf("private void refreshDerivedConfiguration()");
        int reset = menu.indexOf("resetProcessingEncodingType();", refresh);
        int restore = menu.indexOf("restoreInsertedProcessingPattern(source)", refresh);
        assertTrue(refresh >= 0);
        assertTrue(reset > refresh);
        assertTrue(restore > reset);
        assertTrue(menu.contains("conversionService.restoreEditableState("));
        assertTrue(menu.contains("restoreOverloadConfig("));
        assertTrue(menu.contains("selectInsertedPatternMode(TianshuEncodingMode.PROCESSING)"));
        assertTrue(menu.contains("selectInsertedPatternMode(TianshuEncodingMode.CLOSED_LOOP)"));
        assertTrue(advanced.contains("restoreForEditing("));
        assertTrue(advanced.contains("current instanceof WrappedPatternDetails"));
        assertTrue(advanced.contains("advanced.getSparseInputs()"));
        assertTrue(advanced.contains("direction.ordinal() + 1"));
        assertTrue(screen.contains("observeEncodedPatternSource()"));
        assertTrue(screen.contains("TianshuRecipeTransferContext.clear(menu)"));
        assertTrue(screen.contains("menu.clearClientUploadSelectionState()"));
    }

    @Test
    void closedLoopAuthoringFixesTheFirstRightSlotAsPrimaryAndDisplaysByproducts() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuClosedLoopPatternConfigScreen.java"));
        String panel = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuClosedLoopEncodingPanel.java"));
        String layout = Files.readString(Path.of(
                "src/main/resources/assets/ae2/screens/terminals/"
                        + "tianshu_pattern_encoding_terminal.json"));

        assertTrue(menu.contains("new ClosedLoopMemberSlot(closedLoopMemberInventory"));
        assertTrue(menu.contains("new ClosedLoopOutputSlot(closedLoopOutputInventory"));
        assertTrue(menu.contains("slot.setIcon(Icon.BACKGROUND_PRIMARY_OUTPUT)"));
        assertTrue(menu.contains("new ClosedLoopReadonlySlot(closedLoopOutputInventory, i)"));
        assertTrue(menu.contains("var markedOutput = getMarkedClosedLoopPrimaryOutput()"));
        assertTrue(menu.contains("refreshClosedLoops(markedOutput.what())"));
        assertTrue(menu.contains("closedLoopOutputRoles[i] = i == 0 ? 1 : 2"));
        assertTrue(menu.contains("writeOutputCandidates(payload.netOutputs())"));
        assertFalse(menu.contains("setClosedLoopOutputRole"));
        assertFalse(config.contains("cycleOutputRole"));
        assertTrue(config.contains("outputRoles[visible].active = false"));
        assertTrue(menu.contains("registerClientAction(\"cycleClosedLoopOutput\""));
        assertTrue(menu.contains("rotated[i] = next.copy()"));
        assertTrue(menu.contains("snapshotClosedLoopOutputKeys()"));
        assertTrue(menu.contains("orderClosedLoopOutputs("));
        assertTrue(menu.contains("closedLoopOutputRoles[i] = i == 0 ? 1 : 2"));
        assertTrue(panel.contains("ActionItems.S_CYCLE_PROCESSING_OUTPUT"));
        assertTrue(panel.contains("menu.cycleClosedLoopOutput()"));
        assertTrue(panel.contains("menu.canCycleClosedLoopOutputs()"));
        assertTrue(layout.contains(
                "\"closedLoopCycleOutput\": { \"left\": 133, \"bottom\": 176"));
        assertFalse(menu.contains("refreshClosedLoops(source)"));
        int refreshClosedLoops = menu.indexOf("private void refreshClosedLoops(");
        int fillSelected = menu.indexOf(
                "private void fillClosedLoopDraftFromSelectedCandidate()", refreshClosedLoops);
        assertTrue(refreshClosedLoops >= 0 && fillSelected > refreshClosedLoops);
        assertFalse(menu.substring(refreshClosedLoops, fillSelected)
                .contains("PatternDetailsHelper.decodePattern(source"));
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

    @Test
    void globalReserveAdditionUsesAnAe2FakeSlotDiscoverableByJeiAndEmi() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuGlobalReserveScreen.java"));
        String layout = Files.readString(Path.of(
                "src/main/resources/assets/ae2/screens/tianshu_inventory_overview.json"));

        assertTrue(menu.contains("new FakeSlot(globalReserveMarkInventory, 0)"));
        assertTrue(menu.contains(
                "addSlot(globalReserveMarkSlot, Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK)"));
        assertTrue(layout.contains("\"AE2LT_TIANSHU_GLOBAL_RESERVE_MARK\""));
        assertTrue(screen.contains("GenericStack.fromItemStack(menu.getGlobalReserveMarkSlot().getItem())"));
        assertTrue(screen.contains("menu.sendGlobalReserve(key, value, ReservedStockMatchMode.EXACT)"));
        assertTrue(screen.contains("menu.getGlobalReserveMarkSlot().setFilterTo("));
    }
}
