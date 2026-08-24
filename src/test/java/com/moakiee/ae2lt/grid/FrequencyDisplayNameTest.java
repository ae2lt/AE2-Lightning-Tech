package com.moakiee.ae2lt.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrequencyDisplayNameTest {
    @Test
    void configuredNameTakesPriorityOverNumericId() {
        assertEquals("主网络", FrequencyDisplayName.of(42, "主网络"));
    }

    @Test
    void missingNameFallsBackToUnambiguousId() {
        assertEquals("#42", FrequencyDisplayName.of(42, ""));
        assertEquals("#42", FrequencyDisplayName.of(42, null));
    }
}
