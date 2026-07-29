package com.moakiee.ae2lt.client.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreEffectShaderSourceContractTest {
    @Test
    void vertexInputsUseTheNativeShaderInstanceContract() throws Exception {
        String shader = Files.readString(Path.of(
                "src/main/resources/assets/ae2lt/shaders/core/multiblock/core.vsh"));

        assertAll(
                () -> assertTrue(shader.startsWith("#version 150")),
                () -> assertTrue(shader.contains("in vec3 Position;")),
                () -> assertTrue(shader.contains("in vec4 Color;")),
                () -> assertTrue(shader.contains("in vec3 Normal;")),
                () -> assertTrue(shader.contains("uniform float EffectTime;")),
                () -> assertFalse(shader.contains("layout(location")),
                () -> assertFalse(shader.contains("VeilRenderTime")));
    }

    @Test
    void shaderDefinitionsUseNamespacedNativeResources() throws Exception {
        Path shaderDirectory = Path.of(
                "src/main/resources/assets/ae2lt/shaders/core/multiblock");
        String matrix = Files.readString(shaderDirectory.resolve("matrix_core.json"));
        String tianshu = Files.readString(shaderDirectory.resolve("tianshu_core.json"));

        assertAll(
                () -> assertTrue(matrix.contains("\"vertex\": \"ae2lt:multiblock/core\"")),
                () -> assertTrue(matrix.contains("\"fragment\": \"ae2lt:multiblock/matrix_core\"")),
                () -> assertTrue(matrix.contains("\"name\": \"EffectTime\"")),
                () -> assertTrue(tianshu.contains("\"vertex\": \"ae2lt:multiblock/core\"")),
                () -> assertTrue(tianshu.contains("\"fragment\": \"ae2lt:multiblock/tianshu_core\"")),
                () -> assertTrue(tianshu.contains("\"name\": \"EffectTime\"")));
    }

    @Test
    void buildAndMetadataDeclareVeilAsOptional() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        String metadata = Files.readString(Path.of(
                "src/main/templates/META-INF/neoforge.mods.toml"));

        assertAll(
                () -> assertTrue(build.contains("compileOnly(\"foundry.veil:veil-neoforge-")),
                () -> assertTrue(build.contains("ae2ltEnableVeilDevRuntime")),
                () -> assertFalse(build.contains("implementation(\"foundry.veil")),
                () -> assertTrue(metadata.contains("modId = \"veil\"")),
                () -> assertTrue(metadata.contains("type = \"optional\"")),
                () -> assertTrue(Files.exists(Path.of(
                        "src/main/resources/assets/ae2lt/pinwheel/shaders/program/multiblock/core.vsh"))));
    }

    @Test
    void optionalVeilClassesAreIsolatedFromTheNativeBackend() throws Exception {
        Path javaRoot = Path.of("src/main/java");
        List<Path> veilReferences;
        try (var files = Files.walk(javaRoot)) {
            veilReferences = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("foundry.veil");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(javaRoot::relativize)
                    .toList();
        }

        assertEquals(
                List.of(Path.of(
                        "com/moakiee/ae2lt/client/core/veil/VeilCoreEffectShaders.java")),
                veilReferences);
    }

    @Test
    void nativeAndVeilShadersKeepTheSameVisualLogic() throws Exception {
        Path nativeDirectory = Path.of(
                "src/main/resources/assets/ae2lt/shaders/core/multiblock");
        Path veilDirectory = Path.of(
                "src/main/resources/assets/ae2lt/pinwheel/shaders/program/multiblock");

        assertAll(
                () -> assertEquals(
                        Files.readString(nativeDirectory.resolve("core.vsh")).stripTrailing(),
                        normalizeVeilShader(veilDirectory.resolve("core.vsh"))),
                () -> assertEquals(
                        Files.readString(nativeDirectory.resolve("matrix_core.fsh")).stripTrailing(),
                        normalizeVeilShader(veilDirectory.resolve("matrix_core.fsh"))),
                () -> assertEquals(
                        Files.readString(nativeDirectory.resolve("tianshu_core.fsh")).stripTrailing(),
                        normalizeVeilShader(veilDirectory.resolve("tianshu_core.fsh"))));
    }

    private static String normalizeVeilShader(Path path) throws IOException {
        String shader = Files.readString(path)
                .replaceAll("layout\\(location = \\d+\\) in", "in")
                .replace("VeilRenderTime", "EffectTime")
                .stripTrailing();
        return "#version 150\n\n" + shader;
    }
}
