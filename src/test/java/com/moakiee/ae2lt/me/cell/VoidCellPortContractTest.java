package com.moakiee.ae2lt.me.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class VoidCellPortContractTest {
    private static final Path ROOT = Path.of("src", "main");

    @Test
    void insertionUsesGenericAeKeySemanticsWithoutTypeGates() throws IOException {
        String source = Files.readString(ROOT.resolve(Path.of(
                "java", "com", "moakiee", "ae2lt", "me", "cell", "VoidCellInventory.java")));
        String insert = source.substring(
                source.indexOf("public long insert("),
                source.indexOf("public long extract("));

        assertTrue(insert.contains("partitionList.matchesFilter(what, partitionMode)"));
        assertTrue(insert.contains("what.getAmountPerUnit()"));
        assertTrue(insert.contains("return amount;"));
        assertFalse(insert.contains("instanceof"));
        assertFalse(insert.contains("AEItemKey"));
        assertFalse(insert.contains("AEFluidKey"));
        assertFalse(insert.contains("LightningKey"));
        assertFalse(insert.contains("ChemBlacklist"));
    }

    @Test
    void workbenchConfigurationAcceptsEveryRegisteredKeyType() throws IOException {
        String source = Files.readString(ROOT.resolve(Path.of(
                "java", "com", "moakiee", "ae2lt", "item", "VoidStorageCellItem.java")));

        assertTrue(source.contains("return CellConfig.create(stack);"));
        assertFalse(source.contains("CellConfig.create(key ->"));
    }

    @Test
    void recipeMatchesTheUpstream121VoidCellRecipe() throws IOException {
        var recipe = JsonParser.parseString(Files.readString(ROOT.resolve(Path.of(
                "resources", "data", "ae2lt", "recipes", "void_cell.json"))))
                .getAsJsonObject();
        var pattern = recipe.getAsJsonArray("pattern");

        assertEquals("CEC", pattern.get(0).getAsString());
        assertEquals("KXK", pattern.get(1).getAsString());
        assertEquals("III", pattern.get(2).getAsString());

        var key = recipe.getAsJsonObject("key");
        assertEquals(Set.of("C", "E", "K", "X", "I"), key.keySet());
        assertEquals("ae2:condenser", key.getAsJsonObject("E").get("item").getAsString());
        assertEquals("ae2:void_card", key.getAsJsonObject("K").get("item").getAsString());
        assertEquals("ae2:cell_component_16k", key.getAsJsonObject("X").get("item").getAsString());
        assertEquals("ae2lt:void_cell",
                recipe.getAsJsonObject("result").get("item").getAsString());
    }

    @Test
    void guideEntryTracksTheUpstream121PageInsteadOfAddingNewRules() throws IOException {
        String guide = Files.readString(ROOT.resolve(Path.of(
                "resources", "assets", "ae2lt", "ae2guide", "materials", "void-cell.md")));

        assertTrue(guide.contains("A pocket condenser in the drive."));
        assertTrue(guide.contains("needs to be partitioned"));
        assertTrue(guide.contains("matches its filter or condense them"));
        assertTrue(guide.contains("Right click it to open configuration GUI."));
        assertFalse(guide.contains("amountPerUnit"));
    }

    @Test
    void portUsesDedicated120NbtAndCarriesUpstreamNotice() throws IOException {
        String data = Files.readString(ROOT.resolve(Path.of(
                "java", "com", "moakiee", "ae2lt", "me", "cell", "VoidCellData.java")));
        String notices = Files.readString(Path.of("THIRD_PARTY_NOTICES.md"));

        assertTrue(data.contains("\"ae2lt:void_cell\""));
        assertTrue(data.contains("TAG_VERSION"));
        assertFalse(data.contains("ModDataComponents"));
        assertTrue(notices.contains("## ExtendedAE — ME Void Cell"));
        assertTrue(notices.contains("90005ee29839fb9fa83bbe6544919c722f8b0dc6"));
        assertTrue(notices.contains("GNU Lesser General Public License version 3"));
    }
}
