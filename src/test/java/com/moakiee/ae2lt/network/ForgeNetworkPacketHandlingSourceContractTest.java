package com.moakiee.ae2lt.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ForgeNetworkPacketHandlingSourceContractTest {
    @Test
    void everyForgeSimpleChannelHandlerMarksItsPayloadHandled() throws Exception {
        Path networkSources = Path.of("src/main/java/com/moakiee/ae2lt/network");
        List<Path> handlers;
        try (var files = Files.walk(networkSources)) {
            handlers = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("static void handle");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }

        assertFalse(handlers.isEmpty());
        for (Path handler : handlers) {
            String source = Files.readString(handler);
            assertTrue(source.contains("setPacketHandled(true)"),
                    () -> handler + " must acknowledge its Forge SimpleChannel payload");
        }
    }
}
