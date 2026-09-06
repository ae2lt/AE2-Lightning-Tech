package com.moakiee.ae2lt.celestweave.module;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Selects which Phase Flight movement styles may pass through blocks. */
public enum PhaseFlightMode {
    ALL("all", 1),
    CREATIVE_FLIGHT_ONLY("creative_flight_only", 2),
    OFF("off", 0);

    private final String id;
    private final byte serializedValue;

    PhaseFlightMode(String id, int serializedValue) {
        this.id = id;
        this.serializedValue = (byte) serializedValue;
    }

    public String id() {
        return id;
    }

    public ByteTag toTag() {
        return ByteTag.valueOf(serializedValue);
    }

    public boolean allows(boolean creativeFlightActive, boolean anyFlightActive) {
        return switch (this) {
            case ALL -> anyFlightActive;
            case CREATIVE_FLIGHT_ONLY -> creativeFlightActive;
            case OFF -> false;
        };
    }

    public static PhaseFlightMode fromTag(Tag tag) {
        if (tag instanceof ByteTag byteTag) {
            return switch (byteTag.getAsByte()) {
                case 0 -> OFF;
                case 2 -> CREATIVE_FLIGHT_ONLY;
                default -> ALL;
            };
        }
        if (tag instanceof StringTag stringTag) {
            return fromId(stringTag.getAsString());
        }
        return ALL;
    }

    public static PhaseFlightMode fromId(String id) {
        if (id != null) {
            for (PhaseFlightMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id) || mode.name().equalsIgnoreCase(id)) {
                    return mode;
                }
            }
        }
        return ALL;
    }
}
