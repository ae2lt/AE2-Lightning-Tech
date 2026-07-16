package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TianshuPatternUploadRoutingTest {
    @Test
    void nullModeIsInvalid() {
        assertEquals(TianshuPatternUploadRouting.Route.INVALID,
                TianshuPatternUploadRouting.forEncodingMode(null));
    }

    @Test
    void contextlessResultsAreNeverAcknowledged() {
        assertFalse(TianshuPatternUploadRouting.isValidEncodingResult(null, null));
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
