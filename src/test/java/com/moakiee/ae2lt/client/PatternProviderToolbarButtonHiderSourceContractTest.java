package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PatternProviderToolbarButtonHiderSourceContractTest {
    @Test
    void hidesExtendedAePlus1201SmartFeatureButtons() throws Exception {
        String hider = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/api/client/PatternProviderToolbarButtonHider.java"));
        String screen = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/OverloadedPatternProviderScreen.java"));

        assertTrue(hider.contains("com.extendedae_plus.util.GuiUtil$1"));
        assertTrue(hider.contains(
                "registerHiddenButtonClassName(EXTENDED_AE_PLUS_SMART_FEATURE_BUTTON)"));
        assertTrue(screen.contains("removeHiddenToolbarButtons();"));
    }
}
