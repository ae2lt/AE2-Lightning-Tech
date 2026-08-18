package com.moakiee.ae2lt.api.patternprovider;

import java.util.function.IntSupplier;

/**
 * Runtime policy exposed by AE2LT for compatible wireless providers.
 *
 * <p>A distance of {@code 0} means unlimited, matching AE2LT's configuration
 * semantics. The supplier form allows config reloads without rebuilding block
 * entities or exposing AE2LT's internal configuration classes.
 */
public final class WirelessPatternProviderPolicy {
    private static volatile IntSupplier maxDistanceSupplier = () -> 0;

    private WirelessPatternProviderPolicy() {
    }

    public static void setMaxDistanceSupplier(IntSupplier supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("supplier must not be null");
        }
        maxDistanceSupplier = supplier;
    }

    public static int maxDistance() {
        return Math.max(0, maxDistanceSupplier.getAsInt());
    }
}
