package com.moakiee.ae2lt.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RecipeConflictScannerTest {
    @Test
    void scalesOtherRecipeInputsBy8192() {
        assertTrue(RecipeConflictScanner.canCover(
                List.of(RecipeConflictScanner.INPUT_SCALE),
                List.of(RecipeConflictScanner.INPUT_SCALE),
                (supply, requirement) -> true));
        assertFalse(RecipeConflictScanner.canCover(
                List.of(RecipeConflictScanner.INPUT_SCALE),
                List.of(RecipeConflictScanner.INPUT_SCALE + 1),
                (supply, requirement) -> true));
    }

    @Test
    void doesNotSpendOneOverlappingSupplyTwice() {
        assertFalse(RecipeConflictScanner.canCover(
                List.of(8L),
                List.of(5L, 5L),
                (supply, requirement) -> true));
    }

    @Test
    void followsAlternativeCompatibilityEdges() {
        assertTrue(RecipeConflictScanner.canCover(
                List.of(1L, 1L),
                List.of(1L, 1L),
                (supply, requirement) -> supply != requirement));
    }
}

