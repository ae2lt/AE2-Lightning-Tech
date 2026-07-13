package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TianshuPatternUploadRoutingTest {
    @Test
    void craftingTargetsUseEaepAssemblerIds() {
        assertTrue(TianshuPatternUploadRouting.isCraftingAssemblerId("ae2:molecular_assembler"));
        assertTrue(TianshuPatternUploadRouting.isCraftingAssemblerId(
                "extendedae:ex_molecular_assembler"));
        assertTrue(TianshuPatternUploadRouting.isCraftingAssemblerId(
                "extendedae:assembler_matrix_pattern"));
        assertTrue(TianshuPatternUploadRouting.isCraftingAssemblerId(
                "extendedae_plus:assembler_matrix_pattern_plus"));
    }

    @Test
    void craftingTargetsExcludeLookalikeProviders() {
        assertFalse(TianshuPatternUploadRouting.isCraftingAssemblerId("ae2:pattern_provider"));
        assertFalse(TianshuPatternUploadRouting.isCraftingAssemblerId("other:molecular_assembler"));
        assertFalse(TianshuPatternUploadRouting.isCraftingAssemblerId(null));
    }

    @Test
    void schedulerOnlyShowsPickerForProcessingFamily() {
        assertEquals(TianshuPatternUploadRouting.Route.CRAFTING_ASSEMBLER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.CRAFTING));
        assertEquals(TianshuPatternUploadRouting.Route.CRAFTING_ASSEMBLER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.STONECUTTING));
        assertEquals(TianshuPatternUploadRouting.Route.CRAFTING_ASSEMBLER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.SMITHING_TABLE));
        assertEquals(TianshuPatternUploadRouting.Route.PROCESSING_PROVIDER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.PROCESSING));
        assertEquals(TianshuPatternUploadRouting.Route.PROCESSING_PROVIDER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.ADVANCED));
        assertEquals(TianshuPatternUploadRouting.Route.PROCESSING_PROVIDER,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.OVERLOAD));
        assertEquals(TianshuPatternUploadRouting.Route.CLOSED_LOOP_STORAGE,
                TianshuPatternUploadRouting.forEncodingMode(TianshuEncodingMode.CLOSED_LOOP));
    }
}
