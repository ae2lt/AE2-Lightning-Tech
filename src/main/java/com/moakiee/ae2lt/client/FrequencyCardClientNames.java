package com.moakiee.ae2lt.client;

import org.jetbrains.annotations.Nullable;

public final class FrequencyCardClientNames {
    private FrequencyCardClientNames() {
    }

    @Nullable
    public static String frequencyName(int frequencyId) {
        var frequency = ClientFrequencyCache.getFrequency(frequencyId);
        return frequency == null ? null : frequency.name();
    }
}
