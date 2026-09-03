package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WirelessTianshuAe2wtlibContractTest {
    private static final Path MAIN = Path.of("src/main");

    // Actual host persistence is covered by TianshuTerminalStateGameTests in the Forge runtime.
    @Test
    void menuAndStyleExposeTheEntangledSingularity() throws Exception {
        String menu = readJava("menu/TianshuWirelessPatternEncodingTermMenu.java");
        String screen = readJava("client/TianshuWirelessPatternEncodingTermScreen.java");
        String style = Files.readString(MAIN.resolve(Path.of(
                "resources/assets/ae2lt/screens/wireless_tianshu_encoder.json")));
        String terminalStyle = Files.readString(MAIN.resolve(Path.of(
                "resources/assets/ae2lt/screens/terminals/tianshu_encoder.json")));
        String guidePage = Files.readString(MAIN.resolve(Path.of(
                "resources/assets/ae2lt/ae2guide/tianshu/pattern-encoding-terminal.md")));

        assertTrue(menu.contains("PlacableItemType.QE_SINGULARITY"));
        assertTrue(menu.contains("AE2wtlibSlotSemantics.SINGULARITY"));
        assertTrue(menu.contains("WTMenuHost.INV_SINGULARITY"));
        assertTrue(menu.contains("public IGridNode getNetworkNode()"));
        assertTrue(menu.contains("return wirelessHost.getActionableNode();"));
        assertTrue(screen.contains("new BackgroundPanel(style.getImage(\"singularityBackground\"))"));
        assertTrue(style.contains("ae2:screens/wtlib/universal_terminal_with_viewcells.json"));
        assertTrue(style.contains("terminals/tianshu_encoder.json"));
        assertTrue(terminalStyle.contains("ae2:screens/terminals/pattern_encoding_terminal.json"));
        assertFalse(terminalStyle.contains("\"helpTopic\""));
        assertTrue(guidePage.contains("- ae2lt:wireless_tianshu_pattern_encoding_terminal"));
    }

    @Test
    void frequencyAdaptersKeepNativeFallbackAndRemoteDrainHooks() throws Exception {
        String item = readJava("integration/ae2wtlib/TianshuWTItem.java");
        String integration = readJava("integration/ae2wtlib/Ae2wtlibIntegration.java");
        String link = readJava("integration/ae2wtlib/WirelessTerminalFrequencyLink.java");
        String mixin = readJava("mixin/ae2wtlib/WTMenuHostMixin.java");

        assertTrue(item.contains("checkUniversalPreconditions(ItemStack stack, Player player)"));
        assertTrue(item.contains("WirelessTerminalFrequencyLink.resolveRoute(player, stack)"));
        assertTrue(item.contains("super.checkUniversalPreconditions(stack, player)"));
        assertTrue(integration.contains("terminal()::tryOpen"));
        assertFalse(integration.contains("MenuOpener.open"));

        assertTrue(link.contains("WUTHandler.getUpgradeCardCount()"));
        assertTrue(link.contains("resolveAdvancedNode"));
        // Resolution and power semantics are executed in WirelessTerminalFrequencyLinkTest.
        assertTrue(mixin.contains("method = \"getActionableNode\""));
        assertTrue(mixin.contains("method = \"rangeCheck\""));
        assertTrue(mixin.contains("rangeCheck = false;"));
        assertTrue(mixin.contains("route.isNetworkPowered()"));
        assertTrue(mixin.contains("resolveRoute(self.getPlayer(), self.getUpgrades())"));
    }

    private static String readJava(String relativePath) throws Exception {
        return Files.readString(MAIN.resolve(Path.of("java/com/moakiee/ae2lt", relativePath)));
    }
}
