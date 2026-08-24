package com.moakiee.ae2lt.grid;

import org.jetbrains.annotations.Nullable;

/** Shared fallback rules for displaying a configured wireless frequency name. */
public final class FrequencyDisplayName {
    private FrequencyDisplayName() {
    }

    public static String of(int frequencyId, @Nullable String configuredName) {
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName;
        }
        return "#" + frequencyId;
    }
}
