package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ReviewFollowupSourceContractTest {
    @Test
    void mekanismLaserHookTargetsTheUniqueDissipationMutation() throws IOException {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/mekanism/TileEntityBasicLaserMixin.java"));
        String integration = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/mekanism/MekanismArmorIntegration.java"));

        assertTrue(mixin.contains("FloatingLong;timesEqual"));
        assertTrue(mixin.contains("energyBeforeDissipation.subtract(retainedEnergy)"));
        assertFalse(mixin.contains("@Local(ordinal"));
        assertTrue(integration.contains("laserDissipation.get() != FULL_LASER_DISSIPATION"));
        assertTrue(integration.contains("convertedEnergy.greaterThan(MAX_SIGNED_LONG)"));
    }

    @Test
    void ae2wtlibVerificationOnlyRequiresTheOwnedTerminal() throws IOException {
        String integration = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/ae2wtlib/Ae2wtlibIntegration.java"));

        assertTrue(integration.contains("WUTHandler.wirelessTerminals.get(TIANSHU_TERMINAL_NAME)"));
        assertTrue(integration.contains("WUTHandler.terminalNames.indexOf(TIANSHU_TERMINAL_NAME)"));
        assertFalse(integration.contains("\"pattern_access\""));
        assertFalse(integration.contains("\"pattern_encoding\""));
    }

    @Test
    void wirelessTianshuUsesItsOwnNameAndKeepsTheUniversalTerminalCycle() throws IOException {
        String integration = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/ae2wtlib/Ae2wtlibIntegration.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/TianshuWirelessPatternEncodingTermScreen.java"));
        String menuHost = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/ae2wtlib/TianshuWTMenuHost.java"));

        assertTrue(integration.contains(
                "item.ae2lt.wireless_tianshu_pattern_encoding_terminal"));
        assertTrue(integration.contains("TIANSHU_TERMINAL_DESCRIPTION_ID);"));
        assertFalse(integration.contains("terminal());"));
        assertTrue(screen.contains("implements IUniversalTerminalCapable"));
        assertTrue(screen.contains("if (menu.isWUT())"));
        assertTrue(screen.contains("new CycleTerminalButton(ignored -> cycleTerminal())"));
        assertTrue(menuHost.contains("getItemStack().getItem() instanceof ItemWUT"));
    }
}
