package com.moakiee.ae2lt.celestweave.module;

import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public enum FlightSpeedOption {
    ONE("1x", 1.0F),
    TWO("2x", 2.0F),
    FOUR("4x", 4.0F);

    public static final String CONFIG_KEY = "speed_multiplier";
    public static final float VANILLA_FLYING_SPEED = 0.05F;

    private final String label;
    private final float multiplier;

    FlightSpeedOption(String label, float multiplier) {
        this.label = label;
        this.multiplier = multiplier;
    }

    public String id() {
        return name();
    }

    public String label() {
        return label;
    }

    public float multiplier() {
        return multiplier;
    }

    public float flyingSpeed() {
        return VANILLA_FLYING_SPEED * multiplier;
    }

    public StringTag toTag() {
        return StringTag.valueOf(id());
    }

    public static FlightSpeedOption fromTag(Tag tag) {
        return tag instanceof StringTag stringTag
                ? fromId(stringTag.getAsString())
                : ONE;
    }

    public static FlightSpeedOption fromId(String id) {
        if (id != null) {
            for (FlightSpeedOption option : values()) {
                if (option.id().equalsIgnoreCase(id) || option.label.equalsIgnoreCase(id)) {
                    return option;
                }
            }
        }
        return ONE;
    }
}
