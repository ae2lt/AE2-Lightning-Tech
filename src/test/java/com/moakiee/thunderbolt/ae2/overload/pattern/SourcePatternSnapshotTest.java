package com.moakiee.thunderbolt.ae2.overload.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SourcePatternSnapshotTest {
    @Test
    void fingerprintIncludesRecipeContentButIgnoresCompoundInsertionOrder() {
        var first = new CompoundTag();
        first.putString("output", "test:b");
        first.putInt("amount", 2);
        var firstInputs = new ListTag();
        var firstInput = new CompoundTag();
        firstInput.putString("id", "test:a");
        firstInput.putInt("count", 1);
        firstInputs.add(firstInput);
        first.put("inputs", firstInputs);

        var reordered = new CompoundTag();
        var reorderedInputs = new ListTag();
        var reorderedInput = new CompoundTag();
        reorderedInput.putInt("count", 1);
        reorderedInput.putString("id", "test:a");
        reorderedInputs.add(reorderedInput);
        reordered.put("inputs", reorderedInputs);
        reordered.putInt("amount", 2);
        reordered.putString("output", "test:b");

        var changed = reordered.copy();
        changed.putString("output", "test:c");
        var itemId = ResourceLocation.fromNamespaceAndPath("test", "pattern");

        assertEquals(
                new SourcePatternSnapshot(itemId, first, null).fingerprint(),
                new SourcePatternSnapshot(itemId, reordered, null).fingerprint());
        assertNotEquals(
                new SourcePatternSnapshot(itemId, first, null).fingerprint(),
                new SourcePatternSnapshot(itemId, changed, null).fingerprint());
    }

    @Test
    void fingerprintIgnoresOnlyTheEncodedPatternStackCount() {
        var one = new CompoundTag();
        one.putString("id", "test:pattern");
        one.putInt("count", 1);
        var recipe = new CompoundTag();
        recipe.putInt("ingredient_count", 3);
        one.put("components", recipe);

        var sixtyFour = one.copy();
        sixtyFour.putInt("count", 64);
        var changedRecipe = one.copy();
        changedRecipe.getCompound("components").putInt("ingredient_count", 4);
        var itemId = ResourceLocation.fromNamespaceAndPath("test", "pattern");

        assertEquals(
                new SourcePatternSnapshot(itemId, one, null).fingerprint(),
                new SourcePatternSnapshot(itemId, sixtyFour, null).fingerprint());
        assertNotEquals(
                new SourcePatternSnapshot(itemId, one, null).fingerprint(),
                new SourcePatternSnapshot(itemId, changedRecipe, null).fingerprint(),
                "recipe-internal counts must remain part of the identity");
    }
}
