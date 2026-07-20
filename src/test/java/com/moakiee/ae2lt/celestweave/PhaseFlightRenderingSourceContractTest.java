package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PhaseFlightRenderingSourceContractTest {
    @Test
    void phaseFlightOnlyOverridesTheRendererSpectatorArgument() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/client/LevelRendererPhaseFlightMixin.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(mixinConfig.contains("client.LevelRendererPhaseFlightMixin"));
        assertTrue(mixin.contains("@ModifyArg("));
        assertTrue(mixin.contains("method = \"renderLevel\""));
        assertTrue(mixin.contains("LevelRenderer;setupRender("));
        assertTrue(mixin.contains("index = 3"));
        assertTrue(mixin.contains("isSpectator || PhaseFlightMovementGuard.isPhaseFlightActive"));
        assertFalse(mixin.contains("isSpectator()"));
        assertFalse(mixin.contains("GameType.SPECTATOR"));
    }
}
