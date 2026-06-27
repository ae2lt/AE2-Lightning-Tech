package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class ArmorModuleTextureContractTest {

    private static final List<String> MODULES = List.of(
            "module_night_vision",
            "module_water_breathing",
            "module_reach_extension",
            "module_matrix_shield",
            "module_phase_shield",
            "module_reflect",
            "module_undying",
            "module_dash",
            "module_creative_flight",
            "module_saturation",
            "module_dig_affinity",
            "module_phase_flight",
            "module_purification");

    @Test
    void armorModulesUseDedicatedItemTextures() throws Exception {
        for (String module : MODULES) {
            Path modelPath = Path.of("src/main/resources/assets/ae2lt/models/item", module + ".json");
            String model = Files.readString(modelPath);

            assertTrue(
                    model.contains("\"layer0\": \"ae2lt:item/" + module + "\""),
                    module + " should point to its dedicated item texture");
        }
    }

    @Test
    void armorModuleTextureFilesExist() {
        for (String module : MODULES) {
            Path texturePath = Path.of("src/main/resources/assets/ae2lt/textures/item", module + ".png");

            assertTrue(Files.isRegularFile(texturePath), module + " texture should exist");
        }
    }

    @Test
    void celestweaveArmorDoesNotDeclareRenderedArmorLayers() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorMaterials.java"));

        assertFalse(
                source.contains("new ArmorMaterial.Layer"),
                "Celestweave should remain equipable without rendering a worn armor model");
    }
}
