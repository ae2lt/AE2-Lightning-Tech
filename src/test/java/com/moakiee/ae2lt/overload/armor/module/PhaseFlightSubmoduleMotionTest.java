package com.moakiee.ae2lt.overload.armor.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class PhaseFlightSubmoduleMotionTest {

    @Test
    void phaseMotionUsesSprintingStateForCtrlAcceleration() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/module/PhaseFlightSubmodule.java"));

        assertTrue(
                source.contains("player.isSprinting()"),
                "Phase flight motion must account for sprinting so Ctrl acceleration affects phased movement.");
    }
}
