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
    void buildAndMetadataDeclareVeilAsOptional() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        String metadata = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));

        assertAll(
                () -> assertTrue(build.contains(
                        "compileOnly(fg.deobf(\"foundry.veil:Veil-forge-${minecraft_version}")),
                () -> assertTrue(build.contains("${veil_version}:slim")),
                () -> assertFalse(build.contains("runtimeOnly fg.deobf(\"foundry.veil")),
                () -> assertFalse(build.contains("implementation fg.deobf(\"foundry.veil")),
                () -> assertTrue(metadata.contains("modId = \"veil\"")),
                () -> assertTrue(metadata.contains("mandatory = false")),
                () -> assertTrue(metadata.contains("side = \"CLIENT\"")),
                () -> assertTrue(Files.exists(Path.of(
                        "src/main/resources/assets/ae2lt/pinwheel/shaders/program/multiblock/core.vsh"))));
    }

    @Test
    void incompatibleVeilCanFallBackAfterStartup() throws Exception {
        String backend = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectBackend.java"));
        String renderTypes = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectRenderTypes.java"));
        String nativeShaders = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectShaders.java"));

        assertAll(
                () -> assertTrue(backend.contains("detectCompatibleVeil()")),
                () -> assertTrue(backend.contains("disableVeil(Throwable cause)")),
                () -> assertTrue(backend.contains("new DefaultArtifactVersion(\"1.0.0\")")),
                () -> assertTrue(backend.contains("new DefaultArtifactVersion(\"2.0.0\")")),
                () -> assertTrue(renderTypes.contains("RuntimeException | LinkageError")),
                () -> assertTrue(renderTypes.contains("CoreEffectBackend.disableVeil(exception)")),
                () -> assertTrue(nativeShaders.contains(
                        "registerShader(event, TIANSHU_SHADER, TIANSHU);")),
                () -> assertTrue(nativeShaders.contains(
                        "registerShader(event, MATRIX_SHADER, MATRIX);")));
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
    void activeShaderPacksUseAKnownVanillaShaderFallback() throws Exception {
        String backend = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectBackend.java"));
        String renderTypes = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectRenderTypes.java"));
        String geometry = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectGeometry.java"));

        assertAll(
                () -> assertTrue(backend.contains("modList.isLoaded(\"iris\")")),
                () -> assertTrue(backend.contains("modList.isLoaded(\"oculus\")")),
                () -> assertTrue(backend.contains("net.irisshaders.iris.api.v0.IrisApi")),
                () -> assertTrue(backend.contains("net.coderbot.iris.api.v0.IrisApi")),
                () -> assertTrue(backend.contains("isShaderPackInUse")),
                () -> assertFalse(backend.contains("import net.irisshaders")),
                () -> assertTrue(renderTypes.contains("POSITION_COLOR_SHADER")),
                () -> assertTrue(renderTypes.contains("SHADER_PACK_FALLBACK")),
                () -> assertTrue(renderTypes.contains("shaderPackActive ?")),
                () -> assertTrue(geometry.contains("CoreEffectBackend.useShaderPackFallback()")));
    }

    @Test
    void veilShadersUseTheOneTwentyTimeUniformAtNativeSpeed() throws Exception {
        Path veilDirectory = Path.of(
                "src/main/resources/assets/ae2lt/pinwheel/shaders/program/multiblock");
        for (String file : List.of("core.vsh", "matrix_core.fsh", "tianshu_core.fsh")) {
            String source = Files.readString(veilDirectory.resolve(file));
            assertAll(
                    () -> assertTrue(source.contains("uniform float GameTime;")),
                    () -> assertTrue(source.contains(
                            "#define VeilRenderTime (GameTime * 1200.0)")),
                    () -> assertFalse(source.contains("uniform float VeilRenderTime;")));
        }
    }

    @Test
    void nativeTimeIsUpdatedBeforeDirtyUniformsAreUploaded() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/core/CoreEffectShaders.java"));
        int update = source.indexOf("effectTime.set(");
        int upload = source.indexOf("super.apply();", update);

        assertTrue(update >= 0 && upload > update);
    }
}
