package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class OverloadedAmountMathTest {

    @Test
    void overlappingFuzzyConfigurationsDoNotDuplicatePhysicalStock() {
        var caps = new LinkedHashMap<String, Long>();
        var amounts = new LinkedHashMap<String, Long>();

        OverloadedAmountMath.mergeSharedExposure(caps, amounts, "variant", 64, 64);
        OverloadedAmountMath.mergeSharedExposure(caps, amounts, "variant", 64, 64);

        assertEquals(128L, caps.get("variant"));
        assertEquals(64L, amounts.get("variant"));
        assertEquals(64L, OverloadedAmountMath.capVisibleAmount(
                amounts.get("variant"), caps.get("variant")));
    }

    @Test
    void sharedCapacityAdditionSaturates() {
        assertEquals(
                Long.MAX_VALUE,
                OverloadedAmountMath.saturatingAdd(Long.MAX_VALUE - 4, 8));
    }
}
