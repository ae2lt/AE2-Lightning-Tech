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
}
