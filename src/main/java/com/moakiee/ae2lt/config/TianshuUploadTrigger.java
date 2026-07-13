package com.moakiee.ae2lt.config;

/** Modifier condition used to enter the upload flow after encoding a pattern. */
public enum TianshuUploadTrigger {
    NO_SHIFT,
    SHIFT,
    CTRL,
    ALT,
    MANUAL_ONLY;

    public TianshuUploadTrigger next() {
        var values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
