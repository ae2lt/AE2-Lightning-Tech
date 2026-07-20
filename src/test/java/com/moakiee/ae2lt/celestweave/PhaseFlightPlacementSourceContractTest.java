package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PhaseFlightPlacementSourceContractTest {
    @Test
    void phasePlacementExcludesOnlyThePlacingPlayer() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/BlockItemPhasePlacementMixin.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(mixinConfig.contains("BlockItemPhasePlacementMixin"));
        assertTrue(mixin.contains("PhaseFlightMovementGuard.isPhaseFlightActive(player)"));
        assertTrue(mixin.contains("this.mustSurvive() && !state.canSurvive(level, pos)"));
        assertTrue(mixin.contains("level.isUnobstructed("));
        assertTrue(mixin.contains("player,"));
        assertFalse(mixin.contains("CollisionContext.empty()"));
        assertFalse(mixin.contains("cir.setReturnValue(true)"));
    }
}
