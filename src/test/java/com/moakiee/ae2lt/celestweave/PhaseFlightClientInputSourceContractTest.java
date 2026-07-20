package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PhaseFlightClientInputSourceContractTest {
    @Test
    void verticalFlightInputIsAuthorizedAtItsExactVanillaMutation() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/client/LocalPlayerPhaseMovementMixin.java"));
        assertTrue(mixin.contains("method = \"aiStep\""));
        assertTrue(mixin.contains("LocalPlayer;setDeltaMovement"));
        assertTrue(mixin.contains("PhaseFlightMovementGuard.runAsSelfMovement("));
        assertFalse(mixin.contains("@Inject(method = \"aiStep\", at = @At(\"HEAD\"))"));

        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));
        assertTrue(mixinConfig.contains("client.LocalPlayerPhaseMovementMixin"));
    }

    @Test
    void deviceHubShowsThreePhaseFlightOptionsAndScrollsToTheFourth() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/hub/DeviceHubScreen.java"));
        assertTrue(screen.contains("CONFIG_VISIBLE_ROWS = 3"));
        assertTrue(screen.contains("Math.min(count, CONFIG_VISIBLE_ROWS)"));
        assertTrue(screen.contains("renderConfigScrollBar"));
        assertTrue(screen.contains("configScrollOffset"));
        assertTrue(screen.contains("ACTION_CYCLE_MODULE_CONFIG, configIndex"));
        assertFalse(screen.contains("Math.min(count, 2)"));
    }
}
