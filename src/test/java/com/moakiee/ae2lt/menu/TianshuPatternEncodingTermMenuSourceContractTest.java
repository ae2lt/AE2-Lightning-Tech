package com.moakiee.ae2lt.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuPatternEncodingTermMenuSourceContractTest {
    @Test
    void ae2EncodingCommitsTheCheckedCandidateAndConsumesTheStagedBlankPattern() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        int encodeStart = menu.indexOf("public void encode()");
        int candidate = menu.indexOf("var candidate = previewAe2EncodingCandidate()", encodeStart);
        int duplicateCheck = menu.indexOf(
                "shouldInterceptDuplicateEncoding(candidate, interceptDuplicates)", candidate);
        int commit = menu.indexOf("commitAe2EncodingCandidate(candidate)", duplicateCheck);
        int commitMethod = menu.indexOf("private void commitAe2EncodingCandidate(", commit);
        int stage = menu.indexOf("stageNetworkBlankPattern()", commitMethod);
        int write = menu.indexOf("encodedInventory.setItemDirect(0, candidate)", stage);
        int returnUnused = menu.indexOf("returnStagedBlankPatternToNetwork()", write);

        assertTrue(encodeStart >= 0);
        assertTrue(candidate > encodeStart);
        assertTrue(duplicateCheck > candidate);
        assertTrue(commit > duplicateCheck);
        assertTrue(commitMethod > commit);
        assertTrue(stage > commitMethod);
        assertTrue(write > stage);
        assertTrue(returnUnused > write);
        assertFalse(menu.substring(candidate, commitMethod).contains("super.encode()"));
        assertTrue(menu.contains("getEncodedPatternInv()"));
        assertTrue(menu.contains("encodedInventory.setItemDirect(0, AEItems.BLANK_PATTERN.stack"));
    }

    @Test
    void blankStagingReportsMissingPatternsAndInsufficientPower()
            throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));

        assertTrue(menu.contains("storage.extract("));
        assertTrue(menu.contains(
                "actionSource, Actionable.SIMULATE"));
        assertTrue(menu.contains("ae2lt.tianshu.encode.missing_blank"));
        assertTrue(menu.contains("ae2lt.tianshu.encode.insufficient_power"));
        assertTrue(menu.contains("ae2lt.tianshu.encode.extraction_failed"));
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
    void processingConversionIgnoresAe2sIntermediateVanillaPatternBroadcast() throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));

        assertTrue(menu.contains("if (!ae2EncodingInProgress) refreshDerivedConfiguration();"));
        int encodeStart = menu.indexOf("private void encodeServerWithOptions(");
        int guardOn = menu.indexOf("ae2EncodingInProgress = true;", encodeStart);
        int commit = menu.indexOf("commitAe2EncodingCandidate(candidate);", guardOn);
        int guardOff = menu.indexOf("ae2EncodingInProgress = false;", commit);
        int finalBroadcast = menu.indexOf("broadcastChanges();", guardOff);

        assertTrue(encodeStart >= 0);
        assertTrue(guardOn > encodeStart);
        assertTrue(commit > guardOn);
        assertTrue(guardOff > commit);
        assertTrue(finalBroadcast > guardOff);
        assertFalse(menu.substring(guardOn, guardOff).contains("applyConfiguredProcessingConversion()"));
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
        assertTrue(screen.contains("observeEncodingAck()"));
        assertTrue(screen.contains("observedEncodingAck != menu.triggeredUploadAck"));
        assertTrue(screen.contains("TianshuRecipeTransferContext.acceptEncodedPattern(menu, current)"));
        assertTrue(screen.contains("TianshuRecipeTransferContext.isEncodingResultReady("));
        assertFalse(screen.contains("TianshuRecipeTransferContext.clear(menu)"));
        assertFalse(screen.contains("retainAfterEncodedSlotChange("));
        assertTrue(menu.contains("TianshuRecipeTransferContext.beginEncoding("));
        assertTrue(menu.contains("public void clear()"));
        assertTrue(menu.contains("TianshuRecipeTransferContext.clear(this)"));
        assertFalse(menu.contains("pendingEncodedSourceChange"));
        assertFalse(menu.contains("expectedUploadedPattern"));
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
    void closedLoopComputedResultsUseBoundedPagesInsteadOfHundredsOfMenuSlots()
            throws Exception {
        String menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuClosedLoopPatternConfigScreen.java"));
        String network = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/NetworkInit.java"));

        assertFalse(menu.contains("closedLoopExternalInputInventory"));
        assertFalse(menu.contains("closedLoopSeedInventory"));
        assertFalse(menu.contains("getClosedLoopExternalInputSlots"));
        assertFalse(menu.contains("getClosedLoopSeedSlots"));
        assertTrue(menu.contains("ClosedLoopResultPage.from("));
        assertTrue(menu.contains("sendClosedLoopResultPage("));
        assertTrue(config.contains("requestVisibleResultPage()"));
        assertTrue(config.contains("drawResultRows("));
        assertTrue(network.contains("RequestClosedLoopResultPagePacket.TYPE"));
        assertTrue(network.contains("ClosedLoopResultPagePacket.TYPE"));
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
