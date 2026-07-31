package com.moakiee.ae2lt.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class PatternProviderUpgradeItemTest {
    private static final Path RECIPE_ROOT = Path.of("src/main/resources/data/ae2lt/recipe");

    @Test
    void overloadedUpgradeAcceptsAllFourProviderBlocks() {
        for (var id : List.of(
                "ae2:pattern_provider",
                "extendedae:ex_pattern_provider",
                "advanced_ae:small_adv_pattern_provider",
                "advanced_ae:adv_pattern_provider")) {
            assertTrue(OverloadedPatternProviderUpgradeItem.supportsSource(ResourceLocation.parse(id)), id);
        }

        assertFalse(OverloadedPatternProviderUpgradeItem.supportsSource(
                ResourceLocation.fromNamespaceAndPath("ae2lt", "overloaded_pattern_provider")));
        assertFalse(OverloadedPatternProviderUpgradeItem.supportsSource(
                ResourceLocation.fromNamespaceAndPath("advanced_ae", "adv_pattern_provider_part")));
    }

    @Test
    void theTwoUpgradeTiersUseSeparateItemsAndRecipes() throws Exception {
        JsonObject overloaded = readRecipe("overloaded_pattern_provider_upgrade.json");
        JsonObject extended = readRecipe("extended_overloaded_pattern_provider_upgrade.json");

        assertEquals(
                "ae2lt:overloaded_pattern_provider",
                overloaded.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals(
                "ae2lt:overloaded_pattern_provider_upgrade",
                overloaded.getAsJsonObject("result").get("id").getAsString());
        assertEquals(
                "ae2lt:extended_overloaded_pattern_provider",
                extended.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals(
                "ae2lt:extended_overloaded_pattern_provider_upgrade",
                extended.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void optionalProviderSupportDoesNotLinkTheirClasses() throws Exception {
        String implementation = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/OverloadedPatternProviderUpgradeItem.java"));

        assertTrue(implementation.contains("instanceof PatternContainer"));
        assertFalse(implementation.contains("net.pedroksl"));
        assertFalse(implementation.contains("com.glodblock"));
    }

    private static JsonObject readRecipe(String filename) throws Exception {
        return JsonParser.parseString(Files.readString(RECIPE_ROOT.resolve(filename))).getAsJsonObject();
    }
}
