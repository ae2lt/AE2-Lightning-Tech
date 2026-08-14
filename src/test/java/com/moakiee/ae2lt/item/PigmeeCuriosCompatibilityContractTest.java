package com.moakiee.ae2lt.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class PigmeeCuriosCompatibilityContractTest {
    private static final Path RESOURCE_ROOT = Path.of("src/main/resources/data");

    @Test
    void allThreePigmeesShareTheStandardCuriosHeadSlot() throws Exception {
        JsonObject tag = readJson("curios/tags/items/head.json");
        Set<String> values = tag.getAsJsonArray("values").asList().stream()
                .map(element -> element.getAsString())
                .collect(Collectors.toSet());

        assertFalse(tag.get("replace").getAsBoolean());
        assertEquals(Set.of(
                "ae2lt:pigmee_fumo",
                "ae2lt:hyperdimensional_pigmee_fumo",
                "ae2lt:creative_pigmee_fumo"), values);
    }

    @Test
    void ae2ltEnablesOneStandardHeadSlotForPlayers() throws Exception {
        JsonObject entities = readJson("ae2lt/curios/entities/player_pigmee_head.json");

        assertEquals(1, entities.getAsJsonArray("entities").size());
        assertEquals("player", entities.getAsJsonArray("entities").get(0).getAsString());
        assertEquals(1, entities.getAsJsonArray("slots").size());
        assertEquals("head", entities.getAsJsonArray("slots").get(0).getAsString());
        assertTrue(Files.exists(RESOURCE_ROOT.resolve("ae2lt/curios/slots/overloaded_frequency_card.json")),
                "Pigmee support must not replace the existing dedicated frequency-card slot");
    }

    @Test
    void clientRenderingIsRegisteredOnlyBehindTheOptionalCuriosBoundary() throws Exception {
        String clientSetup = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/LightningKeyClientInit.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/PigmeeCuriosClientBridge.java"));

        assertTrue(clientSetup.contains("ModList.get().isLoaded(\"curios\")"));
        assertTrue(clientSetup.contains("PigmeeCuriosClientBridge.registerRenderers()"));
        assertEquals(3, countOccurrences(bridge, "CuriosRendererRegistry.register("));
        assertTrue(bridge.contains("ModFumos.PIGMEE_FUMO_ITEM.get()"));
        assertTrue(bridge.contains("ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get()"));
        assertTrue(bridge.contains("ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get()"));
        assertTrue(bridge.contains("ItemDisplayContext.HEAD"));
        assertTrue(bridge.contains("CustomHeadLayer.translateToHead"));
    }

    private static JsonObject readJson(String relativePath) throws Exception {
        return JsonParser.parseString(Files.readString(RESOURCE_ROOT.resolve(relativePath))).getAsJsonObject();
    }

    private static int countOccurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
