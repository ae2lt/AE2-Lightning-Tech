package com.moakiee.ae2lt.logic.tianshu.terminal;

/** One-shot conversion applied to the next processing-pattern encode. */
public enum ProcessingPatternEncodingType {
    NORMAL,
    ADVANCED,
    OVERLOAD;

    /** Per-input target sides for advanced patterns; 0 = any side, 1..6 = Direction ordinal + 1. */
    public record AdvancedConfig(int[] directions) {
        public int direction(int slot) {
            return directions != null && slot >= 0 && slot < directions.length
                    ? Math.max(0, Math.min(6, directions[slot])) : 0;
        }
    }

    /** Draft slots (by index) that match by id only; all other slots stay strict. */
    public record OverloadConfig(int[] inputIdOnly, int[] outputIdOnly) {
        public boolean isInputIdOnly(int slot) {
            return contains(inputIdOnly, slot);
        }

        public boolean isOutputIdOnly(int slot) {
            return contains(outputIdOnly, slot);
        }

        private static boolean contains(int[] slots, int slot) {
            if (slots == null) return false;
            for (int candidate : slots) {
                if (candidate == slot) return true;
            }
            return false;
        }
    }
}
