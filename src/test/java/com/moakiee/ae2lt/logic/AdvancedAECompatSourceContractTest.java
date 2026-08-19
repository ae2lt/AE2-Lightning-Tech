package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdvancedAECompatSourceContractTest {
    @Test
    void forge120EncoderReceivesItsArrayAndHashMapAbi() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/AdvancedAECompat.java"));

        assertTrue(source.contains("new HashMap<AEKey, Direction>()"));
        assertTrue(source.contains("inputs.toArray(GenericStack[]::new)"));
        assertTrue(source.contains("outputs.toArray(GenericStack[]::new)"));
        assertTrue(source.contains("GenericStack[].class, GenericStack[].class, HashMap.class"));
        assertTrue(source.contains("isLoaded() && ADV_ENCODE_METHOD != null"));
    }
}
