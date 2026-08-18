package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WirelessConnectorRendererContractTest {
    private static final Path RENDERER = Path.of(
            "src", "main", "java", "com", "moakiee", "ae2lt", "client",
            "WirelessConnectorRenderer.java");
    @Test
    void forgeOverlayUsesTheCameraRelativeTranslucentStageContract() throws IOException {
        String source = Files.readString(RENDERER);

        assertTrue(source.contains("Stage.AFTER_TRANSLUCENT_BLOCKS"));
        assertTrue(source.contains("event.getCamera().getPosition()"));
        assertTrue(source.contains("pos.getX() - cam.x"));
        assertTrue(source.contains("from.getX() + 0.5 - cam.x"));
        assertTrue(source.contains("buffer.getBuffer(OverlayRenderType.getBlockHilightLine())"));
        assertTrue(source.contains("conn.boundFace(), COLOR_CONNECTED"));

        assertFalse(source.contains("Stage.AFTER_LEVEL"));
        assertFalse(source.contains("scratchRotation"));
        assertFalse(source.contains("poseStack.mulPose"));
        assertFalse(source.contains("getLineSeeThrough"));
        assertEquals(countOccurrences(source, "vc.vertex("), countOccurrences(source, ".endVertex()"));
    }

    @Test
    void hostCubeRendersInTheSameGlobalPassAsConnections() throws IOException {
        String source = Files.readString(RENDERER);

        assertTrue(source.contains("renderInnerCube(poseStack, buffer, cam, hostPos"));
        assertTrue(source.contains("pos.getX() - cam.x"));
        assertTrue(source.contains("buffer.endBatch(Ae2ltRenderTypes.getFaceSeeThrough())"));
    }

    private static int countOccurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
