package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.parts.encoding.EncodingMode;
import org.jetbrains.annotations.Nullable;

public enum TianshuEncodingMode {
    CRAFTING(EncodingMode.CRAFTING),
    PROCESSING(EncodingMode.PROCESSING),
    STONECUTTING(EncodingMode.STONECUTTING),
    SMITHING_TABLE(EncodingMode.SMITHING_TABLE),
    CLOSED_LOOP(null);

    @Nullable
    private final EncodingMode ae2Mode;

    TianshuEncodingMode(@Nullable EncodingMode ae2Mode) {
        this.ae2Mode = ae2Mode;
    }

    public boolean isAe2Mode() { return ae2Mode != null; }
    @Nullable public EncodingMode ae2Mode() { return ae2Mode; }

    public static TianshuEncodingMode fromAe2(EncodingMode mode) {
        if (mode == null) return CRAFTING;
        return switch (mode) {
            case CRAFTING -> CRAFTING;
            case PROCESSING -> PROCESSING;
            case STONECUTTING -> STONECUTTING;
            case SMITHING_TABLE -> SMITHING_TABLE;
        };
    }
}
