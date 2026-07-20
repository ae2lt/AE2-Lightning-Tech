package com.moakiee.ae2lt.celestweave.module;

import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public enum StepHeightOption {
    VANILLA("0.6", 0.6D),
    ONE("1", 1.0D),
    ONE_AND_HALF("1.5", 1.5D),
    TWO("2", 2.0D),
    THREE("3", 3.0D);

    private final String label;
    private final double height;

    StepHeightOption(String label, double height) {
        this.label = label;
        this.height = height;
    }

    public String label() {
        return label;
    }

    public double height() {
        return height;
    }

    public StringTag toTag() {
        return StringTag.valueOf(name());
    }

    public static StepHeightOption fromTag(Tag tag) {
        if (tag instanceof StringTag stringTag) {
            String id = stringTag.getAsString();
            for (StepHeightOption option : values()) {
                if (option.name().equalsIgnoreCase(id) || option.label.equalsIgnoreCase(id)) {
                    return option;
                }
            }
        }
        return ONE;
    }
}
