package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ToolbarButtonSourceContractTest {
    private static final Path CLIENT_SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/client");
    private static final Pattern NULL_ICON = Pattern.compile(
            "protected\\s+Icon\\s+getIcon\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+null\\s*;\\s*}",
            Pattern.DOTALL);

    @Test
    void customToolbarIconsReuseAe2sNativeIconButtonFootprint() throws Exception {
        assertUsesNativeIconButton("TextureToggleButton.java");
        assertUsesNativeIconButton("TeslaCoilModeButton.java");
        assertUsesNativeIconButton("ProviderBlockingModeButton.java");
    }

    @Test
    void iconButtonsNeverReturnANullBaseIcon() throws Exception {
        try (var paths = Files.walk(CLIENT_SOURCE)) {
            for (var path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(NULL_ICON.matcher(source).find(),
                        () -> path + " returns null from IconButton#getIcon(), which crashes AE2 1.20.1 rendering");
            }
        }
    }

    private static void assertUsesNativeIconButton(String fileName) throws Exception {
        String source = Files.readString(CLIENT_SOURCE.resolve(fileName));

        assertTrue(source.contains("extends IconButton"));
        assertTrue(source.contains("super.renderWidget("));
        assertFalse(source.contains("dest(getX() - 1"));
        assertFalse(source.contains("18, 20"));
        assertFalse(source.contains("isHovered() ? 1 : 0"));
    }
}
