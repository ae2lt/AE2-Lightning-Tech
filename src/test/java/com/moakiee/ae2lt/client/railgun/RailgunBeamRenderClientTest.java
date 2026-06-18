package com.moakiee.ae2lt.client.railgun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class RailgunBeamRenderClientTest {

    @Test
    void localBeamUpdateUsesServerTrace() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunBeamRenderClient.java"));

        assertTrue(source.contains("prev.from = p.from();"));
        assertTrue(source.contains("prev.to = p.to();"));
        assertFalse(source.contains("do NOT touch from/to"));
    }

    @Test
    void localBeamRefreshDoesNotPerformClientEntityLock() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunBeamRenderClient.java"));
        String refreshLocalBeam = source.substring(source.indexOf("private static void refreshLocalBeam"));

        assertFalse(
                refreshLocalBeam.contains("ProjectileUtil.getEntityHitResult"),
                "Local beam rendering must not visually lock onto a client-only entity hit.");
        assertFalse(
                refreshLocalBeam.contains("lockedTargetPoint"),
                "Local beam endpoint must come from the server trace, not client-only entity locking.");
    }
}
