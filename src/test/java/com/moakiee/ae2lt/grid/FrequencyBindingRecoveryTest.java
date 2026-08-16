package com.moakiee.ae2lt.grid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrequencyBindingRecoveryTest {
    @Test
    void directVirtualConnectionIsEffective() {
        assertTrue(FrequencyBindingHelper.hasEffectiveConnection(true, false, false));
    }

    @Test
    void redundantClusterEntranceIsEffectiveWhenChannelIsReady() {
        assertTrue(FrequencyBindingHelper.hasEffectiveConnection(false, true, true));
    }

    @Test
    void sameGridWithoutAnAssignedChannelIsNotEffective() {
        assertFalse(FrequencyBindingHelper.hasEffectiveConnection(false, true, false));
    }

    @Test
    void missingRuntimeConnectionNeedsRecovery() {
        assertFalse(FrequencyBindingHelper.hasEffectiveConnection(false, false, false));
    }
}
