package com.moakiee.ae2lt.grid.wirelesslink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class WirelessConnectionOwnershipSourceContractTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");

    @Test
    void everyWirelessBridgeCreationPathUsesWirelessLinkOps() throws Exception {
        String frequencyBinding = source(
                "com/moakiee/ae2lt/grid/FrequencyBindingHelper.java");
        String registry = source(
                "com/moakiee/ae2lt/grid/wirelesslink/WirelessLinkRegistry.java");

        assertEquals(1, occurrences(frequencyBinding, "WirelessLinkOps.createVirtualConnection("));
        assertEquals(2, occurrences(registry, "WirelessLinkOps.createVirtualConnection("));
        assertFalse(frequencyBinding.contains("GridConnection.create("));
        assertFalse(frequencyBinding.contains("GridHelper.createConnection("));
        assertFalse(registry.contains("GridConnection.create("));
        assertFalse(registry.contains("GridHelper.createConnection("));
    }

    @Test
    void ae2CoreConnectionCreationIsNotGloballyRewritten() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN_JAVA.resolve("com/moakiee/ae2lt/mixin"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String mixin = Files.readString(file);
                assertFalse(mixin.contains("targets = \"appeng.me.GridConnection\""), file.toString());
                assertFalse(mixin.contains("targets = \"appeng.api.networking.GridHelper\""), file.toString());
            }
        }

        String ops = source("com/moakiee/ae2lt/grid/wirelesslink/WirelessLinkOps.java");
        assertTrue(ops.contains("catch (IllegalStateException duplicateCandidate)"));
        assertTrue(ops.contains("throw duplicateCandidate;"));
        assertTrue(ops.contains("isDuplicateConnectionFailure(duplicateCandidate)"));
        assertFalse(ops.contains("!isWirelessBridge(connection)"));
        assertFalse(ops.contains("A virtual connection owned by another system already exists"));
        assertEquals(2, occurrences(ops, "trackWirelessBridge(connection);"));
        assertEquals(1, occurrences(ops, "GridConnection.create("));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(MAIN_JAVA.resolve(relativePath));
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
