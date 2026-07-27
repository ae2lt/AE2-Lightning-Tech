package com.moakiee.ae2lt.logic.tianshu.terminal;

/** Persistent conversions applied to processing-pattern encoding. */
public enum ProcessingPatternEncodingType {
    NORMAL(false, false),
    ADVANCED(true, false),
    OVERLOAD(false, true),
    ADVANCED_OVERLOAD(true, true);

    private final boolean advanced;
    private final boolean overload;

    ProcessingPatternEncodingType(boolean advanced, boolean overload) {
        this.advanced = advanced;
        this.overload = overload;
    }

    public boolean hasAdvanced() {
        return advanced;
    }

    public boolean hasOverload() {
        return overload;
    }

    public boolean includes(ProcessingPatternEncodingType capability) {
        return (!capability.advanced || advanced) && (!capability.overload || overload);
    }

    public static ProcessingPatternEncodingType fromConfigs(
            AdvancedConfig advancedConfig, OverloadConfig overloadConfig) {
        if (advancedConfig != null) {
            return overloadConfig != null ? ADVANCED_OVERLOAD : ADVANCED;
        }
        return overloadConfig != null ? OVERLOAD : NORMAL;
    }

    /** Per-input target sides for advanced patterns; 0 = any side, 1..6 = Direction ordinal + 1. */
    public record AdvancedConfig(int[] directions) {
        public AdvancedConfig {
            directions = directions == null ? new int[0] : directions.clone();
        }

        @Override
        public int[] directions() {
            return directions.clone();
        }

        public int direction(int slot) {
            return directions != null && slot >= 0 && slot < directions.length
                    ? Math.max(0, Math.min(6, directions[slot])) : 0;
        }
    }

    /** Draft slots (by index) that match by id only; all other slots stay strict. */
    public record OverloadConfig(int[] inputIdOnly, int[] outputIdOnly) {
        public OverloadConfig {
            inputIdOnly = inputIdOnly == null ? new int[0] : inputIdOnly.clone();
            outputIdOnly = outputIdOnly == null ? new int[0] : outputIdOnly.clone();
        }

        @Override
        public int[] inputIdOnly() {
            return inputIdOnly.clone();
        }

        @Override
        public int[] outputIdOnly() {
            return outputIdOnly.clone();
        }

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
