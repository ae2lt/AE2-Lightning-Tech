package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WirelessTianshuAe2wtlibContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    void hostRestoresAndPersistsAe2wtlibSegmentedInventories() throws Exception {
        String host = readJava("integration/ae2wtlib/TianshuWTMenuHost.java");
        String fallback = readJava(
                "logic/tianshu/terminal/TianshuWirelessPatternEncodingTermMenuHost.java");

        assertTrue(host.contains("readFromNbt();"));
        assertTrue(host.contains("super.readFromNbt();"));
        assertTrue(host.contains("super.saveChanges();"));
        assertTrue(host.contains("public void markForSave()"));
        assertTrue(host.contains("saveChanges();"));
        assertFalse(host.contains("new AppEngInternalInventory(null, 5)"));
        assertTrue(host.contains("getMainMenuIcon()"));

        assertTrue(fallback.contains("InternalInventoryHost"));
        assertTrue(fallback.contains("new AppEngInternalInventory(this, 5)"));
        assertTrue(fallback.contains("onChangeInventory(InternalInventory inventory, int slot)"));
    }

    @Test
    void menuAndStyleExposeTheEntangledSingularity() throws Exception {
        String menu = readJava("menu/TianshuWirelessPatternEncodingTermMenu.java");
        String screen = readJava("client/TianshuWirelessPatternEncodingTermScreen.java");
        String style = Files.readString(MAIN.resolve(Path.of(
                "resources/assets/ae2/screens/wireless_tianshu_pattern_encoding_terminal.json")));
        String terminalStyle = Files.readString(MAIN.resolve(Path.of(
                "resources/assets/ae2/screens/terminals/tianshu_pattern_encoding_terminal.json")));
        String guidePage = Files.readString(MAIN.resolve(Path.of(
                "resources/assets/ae2lt/ae2guide/tianshu/pattern-encoding-terminal.md")));

        assertTrue(menu.contains("PlacableItemType.QE_SINGULARITY"));
        assertTrue(menu.contains("AE2wtlibSlotSemantics.SINGULARITY"));
        assertTrue(menu.contains("WTMenuHost.INV_SINGULARITY"));
        assertTrue(menu.contains("public IGridNode getNetworkNode()"));
        assertTrue(menu.contains("return wirelessHost.getActionableNode();"));
        assertTrue(screen.contains("new BackgroundPanel(style.getImage(\"singularityBackground\"))"));
        assertTrue(style.contains("wtlib/universal_terminal_with_viewcells.json"));
        assertFalse(terminalStyle.contains("\"helpTopic\""));
        assertTrue(guidePage.contains("- ae2lt:wireless_tianshu_pattern_encoding_terminal"));
    }

    @Test
    void frequencyRouteControlsOpeningStorageAndRangeValidation() throws Exception {
        String item = readJava("integration/ae2wtlib/TianshuWTItem.java");
        String integration = readJava("integration/ae2wtlib/Ae2wtlibIntegration.java");
        String link = readJava("integration/ae2wtlib/WirelessTerminalFrequencyLink.java");
        String mixin = readJava("mixin/ae2wtlib/WTMenuHostMixin.java");

        assertTrue(item.contains("checkUniversalPreconditions(ItemStack stack, Player player)"));
        assertTrue(item.contains("WirelessTerminalFrequencyLink.resolve(player, stack)"));
        assertTrue(item.contains("super.checkUniversalPreconditions(stack, player)"));
        assertTrue(integration.contains("terminal()::tryOpen"));
        assertFalse(integration.contains("MenuOpener.open"));

        assertTrue(link.contains("WUTHandler.getUpgradeCardCount()"));
        assertTrue(link.contains("resolveAdvancedNode"));
        assertTrue(link.contains("node != null && node.isPowered()"));
        assertFalse(link.contains("node.getGrid()"));
        assertTrue(mixin.contains("method = \"getActionableNode\""));
        assertTrue(mixin.contains("method = \"rangeCheck\""));
        assertTrue(mixin.contains("rangeCheck = false;"));
        assertTrue(mixin.contains("WirelessTerminalFrequencyLink.isNetworkPowered(node)"));
    }

    private static String readJava(String relativePath) throws Exception {
        return Files.readString(MAIN.resolve(Path.of("java/com/moakiee/ae2lt", relativePath)));
    }
}
