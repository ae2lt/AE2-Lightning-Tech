package com.moakiee.ae2lt.item;

final class FrequencyCardDisplayName {
    private FrequencyCardDisplayName() {
    }

    static String displayName(int frequencyId, String frequencyName) {
        if (frequencyName != null && !frequencyName.isBlank()) {
            return frequencyName;
        }
        return "#" + frequencyId;
    }
}
