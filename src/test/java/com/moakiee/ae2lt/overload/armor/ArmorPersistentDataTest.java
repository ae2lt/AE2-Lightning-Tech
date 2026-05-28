package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorPersistentDataTest {

    @Test
    void persistentFacadeKeepsLegacyCustomDataKeysAndMethods() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/state/ArmorPersistentData.java"));

        assertTrue(source.contains("\"OverloadArmor\""));
        assertTrue(source.contains("\"ArmorId\""));
        assertTrue(source.contains("\"InstalledSubmodules\""));
        assertTrue(source.contains("\"FeatureToggles\""));
        assertTrue(source.contains("\"SubmoduleData\""));
        assertTrue(source.contains("ensureArmorId"));
        assertTrue(source.contains("loadModuleStacks"));
        assertTrue(source.contains("saveModuleStacks"));
        assertTrue(source.contains("getToggle"));
        assertTrue(source.contains("setToggle"));
    }
}
