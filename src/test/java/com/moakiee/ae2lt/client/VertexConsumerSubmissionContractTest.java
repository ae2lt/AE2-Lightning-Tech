package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertexConsumerSubmissionContractTest {
    @Test
    void fluentVertexCallsAreExplicitlyFinishedOnMinecraftOneTwenty() throws IOException {
        Path javaRoot = Path.of("src", "main", "java");
        List<String> mismatches = new ArrayList<>();

        try (var files = Files.walk(javaRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                int vertices = countOccurrences(source, ".vertex(");
                if (vertices == 0) {
                    continue;
                }

                int completedVertices = countOccurrences(source, ".endVertex()");
                if (vertices != completedVertices) {
                    mismatches.add(javaRoot.relativize(file) + ": "
                            + vertices + " vertex calls, " + completedVertices + " completions");
                }
            }
        }

        assertTrue(mismatches.isEmpty(),
                "Minecraft 1.20.1 requires each fluent vertex call to end with endVertex():\n"
                        + String.join("\n", mismatches));
    }

    private static int countOccurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
