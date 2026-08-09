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
}
