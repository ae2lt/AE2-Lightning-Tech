package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuMaintainableViewSourceContractTest {
    @Test
    void maintainableViewFiltersByConfiguredRulesAndInvalidatesAe2sIncrementalCache() throws Exception {
        var menu = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/menu/TianshuPatternEncodingTermMenu.java"));
        var screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuPatternEncodingTermScreen.java"));
        var mixins = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(menu.contains("maintenance.repository().get(key) != null"));
        assertTrue(menu.contains("invalidateVisibleNetworkContents();\n        broadcastChanges();"));
        assertTrue(menu.contains("ae2lt$getUpdateHelper()"));
        assertTrue(menu.contains("updateHelper.clear()"));
        assertTrue(menu.contains("storage.getAvailableStacks().keySet().forEach(updateHelper::addChange)"));
        assertTrue(screen.contains("summary != null && summary.ruleConfigured()"));
        assertTrue(screen.contains("craftable-only entries never leak into this view"));
        assertTrue(mixins.contains("\"MEStorageMenuAccessor\""));
    }
}
