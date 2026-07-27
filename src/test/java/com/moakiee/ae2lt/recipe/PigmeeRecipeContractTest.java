package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class PigmeeRecipeContractTest {
    @Test
    void surroundsTheReusableCoreWithEightPinkWool() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/ae2lt/recipe/pigmee_fumo.json"))).getAsJsonObject();

        var pattern = json.getAsJsonArray("pattern");
        assertEquals("aaa", pattern.get(0).getAsString());
        assertEquals("aca", pattern.get(1).getAsString());
        assertEquals("aaa", pattern.get(2).getAsString());
        assertEquals(
                "minecraft:pink_wool",
                json.getAsJsonObject("key").getAsJsonObject("a").get("item").getAsString());
        assertEquals(
                "ae2lt:pigmee_core",
                json.getAsJsonObject("key").getAsJsonObject("c").get("item").getAsString());
        assertEquals(1, json.getAsJsonObject("result").get("count").getAsInt());
    }
}
