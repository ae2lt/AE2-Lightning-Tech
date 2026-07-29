package com.moakiee.ae2lt.client.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoreEffectShaderSourceContractTest {
    @Test
    void vertexInputsUseStableLocationsAcrossOpenGlDrivers() throws Exception {
        String shader = Files.readString(Path.of(
                "src/main/resources/assets/ae2lt/pinwheel/shaders/program/multiblock/core.vsh"));

        assertAll(
                () -> assertTrue(shader.contains("layout(location = 0) in vec3 Position;")),
                () -> assertTrue(shader.contains("layout(location = 1) in vec4 Color;")),
                () -> assertTrue(shader.contains("layout(location = 2) in vec3 Normal;")));
    }
}
