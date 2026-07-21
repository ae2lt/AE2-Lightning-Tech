package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.device.capability.DeviceCapability;

class RailgunRangePolicyTest {

    @Test
    void rangeModulesDoubleThenQuadrupleBaseRange() {
        var one = new DeviceCapability.RangeMultiplier(2.0D);

        assertEquals(64.0D, RailgunRangePolicy.effectiveRange(64.0D, List.of()));
        assertEquals(128.0D, RailgunRangePolicy.effectiveRange(64.0D, List.of(one)));
        assertEquals(256.0D, RailgunRangePolicy.effectiveRange(64.0D, List.of(one, one)));
    }

    @Test
    void malformedCapabilityListsCannotExceedFourTimesRange() {
        var one = new DeviceCapability.RangeMultiplier(2.0D);

        assertEquals(256.0D, RailgunRangePolicy.effectiveRange(64.0D, List.of(one, one, one)));
        assertEquals(64.0D, RailgunRangePolicy.effectiveRange(
                64.0D,
                List.of(new DeviceCapability.RangeMultiplier(Double.POSITIVE_INFINITY))));
        assertEquals(0.0D, RailgunRangePolicy.effectiveRange(-64.0D, List.of(one)));
    }
}
