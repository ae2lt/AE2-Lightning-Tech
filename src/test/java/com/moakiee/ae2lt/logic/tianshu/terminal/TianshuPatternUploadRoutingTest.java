package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TianshuPatternUploadRoutingTest {
    @Test
    void craftingTargetsIncludeAe2AndAddonAssemblers() {
        assertTrue(TianshuPatternUploadRouting.isCraftingAssemblerPath("molecular_assembler"));
        assertTrue(TianshuPatternUploadRouting.isCraftingAssemblerPath("ex_molecular_assembler"));
        assertTrue(TianshuPatternUploadRouting.isCraftingAssemblerPath("assembler_matrix_pattern"));
        assertTrue(TianshuPatternUploadRouting.isCraftingAssemblerPath("assembler_matrix_pattern_plus"));
    }

    @Test
    void craftingTargetsExcludeOrdinaryPatternProviders() {
        assertFalse(TianshuPatternUploadRouting.isCraftingAssemblerPath("pattern_provider"));
        assertFalse(TianshuPatternUploadRouting.isCraftingAssemblerPath("extended_pattern_provider"));
        assertFalse(TianshuPatternUploadRouting.isCraftingAssemblerPath(null));
    }
}
