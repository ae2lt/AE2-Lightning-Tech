package com.moakiee.ae2lt.overload.model;

import java.util.Objects;

/**
 * Describes the overload matching rule for one logical pattern slot.
 * <p>
 * This model deliberately does not store the actual item contents of the slot.
 * It only captures which side the slot belongs to and how that slot should be
 * compared later.
 */
public record OverloadPatternSlot(
        Side side,
        int slotIndex,
        MatchMode matchMode
) {
    public OverloadPatternSlot {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(matchMode, "matchMode");
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
    }

    public static OverloadPatternSlot input(int slotIndex, MatchMode matchMode) {
        return new OverloadPatternSlot(Side.INPUT, slotIndex, matchMode);
    }

    public static OverloadPatternSlot output(int slotIndex, MatchMode matchMode) {
        return new OverloadPatternSlot(Side.OUTPUT, slotIndex, matchMode);
    }

    public boolean isInput() {
        return side == Side.INPUT;
    }

    public boolean isOutput() {
        return side == Side.OUTPUT;
    }

    public enum Side {
        INPUT,
        OUTPUT
    }
}
