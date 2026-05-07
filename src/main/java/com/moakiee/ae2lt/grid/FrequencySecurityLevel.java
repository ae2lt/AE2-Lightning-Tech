package com.moakiee.ae2lt.grid;

import javax.annotation.Nonnull;

public enum FrequencySecurityLevel {
    PUBLIC,
    ENCRYPTED,
    PRIVATE;

    public static final FrequencySecurityLevel[] VALUES = values();

    @Nonnull
    public static FrequencySecurityLevel fromId(byte id) {
        if (id < 0 || id >= VALUES.length) return PUBLIC;
        return VALUES[id];
    }

    public byte getId() {
        return (byte) ordinal();
    }
}
