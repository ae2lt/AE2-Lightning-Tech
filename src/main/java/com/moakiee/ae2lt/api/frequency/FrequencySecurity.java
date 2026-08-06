package com.moakiee.ae2lt.api.frequency;

/**
 * Security level of a wireless frequency. Mirrors the internal enum so addon
 * authors do not need to import non-api classes.
 */
public enum FrequencySecurity {
    PUBLIC,
    ENCRYPTED,
    PRIVATE
}
