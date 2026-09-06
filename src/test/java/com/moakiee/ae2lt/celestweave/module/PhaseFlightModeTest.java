package com.moakiee.ae2lt.celestweave.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

class PhaseFlightModeTest {
    @Test
    void legacyBooleanKeepsItsExactMeaning() {
        assertEquals(PhaseFlightMode.ALL, PhaseFlightMode.fromTag(ByteTag.valueOf(true)));
        assertEquals(PhaseFlightMode.OFF, PhaseFlightMode.fromTag(ByteTag.valueOf(false)));
    }

    @Test
    void missingAndUnknownValuesKeepTheLegacyEnabledDefault() {
        assertEquals(PhaseFlightMode.ALL, PhaseFlightMode.fromTag(null));
        assertEquals(PhaseFlightMode.ALL, PhaseFlightMode.fromTag(StringTag.valueOf("unknown")));
    }

    @Test
    void choicesHaveTheRequestedOrderAndStableSerializedIds() {
        assertEquals(
                List.of(
                        PhaseFlightMode.ALL,
                        PhaseFlightMode.CREATIVE_FLIGHT_ONLY,
                        PhaseFlightMode.OFF),
                List.of(PhaseFlightMode.values()));
        assertEquals(
                PhaseFlightMode.CREATIVE_FLIGHT_ONLY,
                PhaseFlightMode.fromTag(ByteTag.valueOf((byte) 2)));
        assertEquals(ByteTag.valueOf((byte) 1), PhaseFlightMode.ALL.toTag());
        assertEquals(ByteTag.valueOf((byte) 0), PhaseFlightMode.OFF.toTag());
    }

    @Test
    void creativeOnlyExcludesWingGliding() {
        assertTrue(PhaseFlightMode.ALL.allows(false, true));
        assertTrue(PhaseFlightMode.CREATIVE_FLIGHT_ONLY.allows(true, true));
        assertFalse(PhaseFlightMode.CREATIVE_FLIGHT_ONLY.allows(false, true));
        assertFalse(PhaseFlightMode.OFF.allows(true, true));
    }
}
