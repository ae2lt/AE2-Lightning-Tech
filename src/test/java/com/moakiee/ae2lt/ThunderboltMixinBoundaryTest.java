package com.moakiee.ae2lt;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ThunderboltMixinBoundaryTest {
    @Test
    void ae2ltDoesNotDependOnThunderboltMixinImplementationClasses() throws Exception {
        var mainSources = Path.of("src/main/java");
        try (var files = Files.walk(mainSources)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var source = Files.readString(file);
                assertFalse(
                        source.contains("com.moakiee.thunderbolt.mixin"),
                        () -> "AE2LT must own its accessors instead of importing Thunderbolt mixins: "
                                + file);
            }
        }
    }
}
