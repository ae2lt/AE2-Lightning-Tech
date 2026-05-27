package com.moakiee.ae2lt.overload.armor.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class FlightSpeedOptionTest {

    @Test
    void speedChoicesAreOneTwoAndFourTimesVanillaFlight() {
        assertEquals("1x", FlightSpeedOption.ONE.label());
        assertEquals("2x", FlightSpeedOption.TWO.label());
        assertEquals("4x", FlightSpeedOption.FOUR.label());
        assertEquals(0.05F, FlightSpeedOption.ONE.flyingSpeed(), 0.0001F);
        assertEquals(0.10F, FlightSpeedOption.TWO.flyingSpeed(), 0.0001F);
        assertEquals(0.20F, FlightSpeedOption.FOUR.flyingSpeed(), 0.0001F);
    }

    @Test
    void unknownStoredValueFallsBackToOneTimesSpeed() {
        assertEquals(FlightSpeedOption.ONE, FlightSpeedOption.fromId("bad"));
    }

    @Test
    void flightModulesExposeAndUseSpeedMultiplierConfig() throws Exception {
        String creativeFlight = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/FlightSubmodule.java"));
        String phaseFlight = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/PhaseFlightSubmodule.java"));

        assertTrue(creativeFlight.contains("FlightSpeedOption.CONFIG_KEY"));
        assertTrue(creativeFlight.contains("flightSpeed(armor)"));
        assertTrue(phaseFlight.contains("FlightSpeedOption.CONFIG_KEY"));
        assertTrue(phaseFlight.contains("phaseSpeed(armor)"));
    }

    @Test
    void creativeFlightRestoresPreviousFlyingSpeedWhenDisabled() throws Exception {
        String creativeFlight = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/FlightSubmodule.java"));

        assertTrue(creativeFlight.contains("TAG_PREVIOUS_SPEED"));
        assertTrue(creativeFlight.contains("restoreStoredAbilities"));
    }
}
