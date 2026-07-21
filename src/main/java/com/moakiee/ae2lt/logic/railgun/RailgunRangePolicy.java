package com.moakiee.ae2lt.logic.railgun;

import java.util.List;

import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

/** Resolves the bounded travel-range multiplier contributed by installed modules. */
public final class RailgunRangePolicy {

    private RailgunRangePolicy() {}

    public static double effectiveRange(double baseRange, List<DeviceCapability> capabilities) {
        double multiplier = 1.0D;
        if (capabilities != null) {
            for (var capability : capabilities) {
                if (!(capability instanceof DeviceCapability.RangeMultiplier range)) {
                    continue;
                }
                multiplier = Math.min(
                        RailgunDefaults.MAX_RANGE_MULTIPLIER,
                        multiplier * range.factor());
            }
        }
        return Math.max(0.0D, baseRange) * multiplier;
    }
}
