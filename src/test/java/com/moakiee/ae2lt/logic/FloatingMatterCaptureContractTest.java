package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FloatingMatterCaptureContractTest {

    @Test
    void captureUsesTheActuallyImpactedFrontFace() throws Exception {
        String capture = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/FloatingMatterCapture.java"));
        String bulletMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ShulkerBulletMixin.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(capture.contains("host.getPart(hit.getDirection())"));
        assertFalse(capture.contains("getDirection().getOpposite()"));
        assertTrue(bulletMixin.contains("method = \"onHitBlock\""));
        assertFalse(bulletMixin.contains("onEntityCollision"));
        assertTrue(mixinConfig.contains("\"ShulkerBulletMixin\""));
        assertFalse(mixinConfig.contains("\"AnnihilationPlaneShulkerMixin\""));
    }
}
