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
}
