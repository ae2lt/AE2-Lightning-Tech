package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CelestweaveHeadRenderSourceContractTest {
    @Test
    void bothRealAndProjectedHeadItemsSuppressTheArmorModel() throws Exception {
        String oculus = read("src/main/java/com/moakiee/ae2lt/item/CelestweaveOculusItem.java");
        String projection = read("src/main/java/com/moakiee/ae2lt/item/PhaseLockProjectionItem.java");
        String extensions = read(
                "src/main/java/com/moakiee/ae2lt/client/CelestweaveHeadRenderExtensions.java");

        assertTrue(oculus.contains("CelestweaveHeadRenderExtensions.INSTANCE"));
        assertTrue(projection.contains("equipmentSlot == EquipmentSlot.HEAD"));
        assertTrue(projection.contains("CelestweaveHeadRenderExtensions.INSTANCE"));
        assertTrue(extensions.contains("original.setAllVisible(false)"));
        assertTrue(extensions.contains("return original"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
