package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuMaintainableViewSourceContractTest {
    @Test
    void maintainableViewUsesNonDestructiveRulePartitionAndShowsStoredAmounts() throws Exception {
        var menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        var screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));

        assertTrue(screen.contains("refreshMaintenancePartitionIfNeeded()"));
        assertTrue(screen.contains("protected IPartitionList createPartitionList"));
        assertTrue(screen.contains("entry.ruleConfigured()"));
        assertTrue(screen.contains("menu.getConfigManager().putSetting(Settings.VIEW_MODE, ViewItems.ALL)"));
        assertTrue(screen.contains("Filters the visible view without deleting entries"));
        assertTrue(menu.contains("lastMaintenanceSummaryTick = Integer.MIN_VALUE"));
        assertFalse(menu.contains("MEStorageMenuAccessor"));
        assertFalse(screen.contains("craftable-only entries never leak into this view"));
    }

    @Test
    void maintenanceOverviewWaitsForRuleDataWithoutFlashingTheTerminal() throws Exception {
        var overview = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuGlobalReserveScreen.java"));

        assertTrue(overview.contains("private boolean awaitingRuleEditor"));
        assertTrue(overview.contains("requestRuleEditor(summary.key())"));
        assertTrue(overview.contains(
                "menu.getMaintenanceEditorRevision() != requestedRuleEditorRevision"));
        assertTrue(overview.contains("switchToScreen(new TianshuMaintenanceRuleScreen<>("));
        assertTrue(overview.contains("this, menu.getMaintenanceEditorData()"));
        assertFalse(overview.contains("getParent(), menu.getMaintenanceEditorData()"));
        assertFalse(overview.contains("getParent().requestMaintenanceEditorFor(summary.key())"));

        int clickHandler = overview.indexOf("public boolean mouseClicked");
        int rulesBranch = overview.indexOf("if (view == View.RULES)", clickHandler);
        int reservesBranch = overview.indexOf("} else {", rulesBranch);
        assertTrue(clickHandler >= 0 && rulesBranch > clickHandler && reservesBranch > rulesBranch);
        assertFalse(overview.substring(rulesBranch, reservesBranch).contains("returnToParent()"));
    }
}
