package com.moakiee.ae2lt.network;

final class FrequencyCardBindingMessage {
    private FrequencyCardBindingMessage() {
    }

    static String displayName(int frequencyId, String frequencyName) {
        if (frequencyName != null && !frequencyName.isBlank()) {
            return frequencyName;
        }
        return "#" + frequencyId;
    }
}
