package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PigmeeMolecularAssemblerCraftingContractTest {
    @Test
    void automaticCraftingUsesTheAe2EventWithoutAPlayerItemCallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/PigmeeMolecularAssemblerBlockEntity.java"));
        String tickingRequest = methodBody(source,
                "public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall)");

        int assemble = tickingRequest.indexOf("activePattern.assemble(craftingGrid, level)");
        int craftingEvent = tickingRequest.indexOf("CraftingEvent.fireAutoCraftingEvent(");
        int remainders = tickingRequest.indexOf("activePattern.getRemainingItems(craftingGrid)");
        int pushOutput = tickingRequest.indexOf("pushOutput(output.copy())");

        assertTrue(assemble >= 0, "The active pattern must still produce the crafting output");
        assertTrue(craftingEvent > assemble, "The AE2 auto-crafting event must follow assembly");
        assertTrue(remainders > craftingEvent, "Remainders must be calculated after the crafting event");
        assertTrue(pushOutput > remainders, "The crafted output must be pushed after remainder calculation");
        assertFalse(tickingRequest.contains(".onCraftedBy("),
                "Player-style crafting callbacks are invalid for this playerless assembler");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method: " + signature);
        int bodyStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("Unterminated method: " + signature);
    }
}
