package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ToolbarButtonSourceContractTest {
    @Test
    void customToolbarIconsReuseAe2sNativeIconButtonFootprint() throws Exception {
        assertUsesNativeIconButton("TextureToggleButton.java");
        assertUsesNativeIconButton("TeslaCoilModeButton.java");
        assertUsesNativeIconButton("ProviderBlockingModeButton.java");
    }

    private static void assertUsesNativeIconButton(String fileName) throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client", fileName));

        assertTrue(source.contains("extends IconButton"));
        assertTrue(source.contains("super.renderWidget("));
        assertFalse(source.contains("dest(getX() - 1"));
        assertFalse(source.contains("18, 20"));
        assertFalse(source.contains("isHovered() ? 1 : 0"));
    }
}
