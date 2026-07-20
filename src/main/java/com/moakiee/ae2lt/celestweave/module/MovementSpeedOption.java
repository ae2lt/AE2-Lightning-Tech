package com.moakiee.ae2lt.celestweave.module;

import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public enum MovementSpeedOption {
    HALF("0.5", 0.5D),
    ONE("1", 1.0D),
    ONE_AND_HALF("1.5", 1.5D),
    TWO("2", 2.0D),
    THREE("3", 3.0D),
    FOUR("4", 4.0D);

    private final String label;
    private final double multiplier;

    MovementSpeedOption(String label, double multiplier) {
        this.label = label;
        this.multiplier = multiplier;
    }

    public String label() {
        return label;
    }

    public double multiplier() {
        return multiplier;
    }

    public StringTag toTag() {
        return StringTag.valueOf(name());
    }

    public static MovementSpeedOption fromTag(Tag tag) {
        if (tag instanceof StringTag stringTag) {
            String id = stringTag.getAsString();
            for (MovementSpeedOption option : values()) {
                if (option.name().equalsIgnoreCase(id) || option.label.equalsIgnoreCase(id)) {
                    return option;
                }
            }
        }
        return ONE;
    }
}
