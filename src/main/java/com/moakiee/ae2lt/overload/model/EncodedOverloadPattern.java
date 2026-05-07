package com.moakiee.ae2lt.overload.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable overload-specific configuration stored alongside a pattern.
 * <p>
 * The actual encoded recipe payload still belongs to AE2's pattern item. This
 * object only carries overload matching metadata for each logical input/output
 * slot and can be attached to a future item component or custom data payload.
 */
public final class EncodedOverloadPattern {
    private static final EncodedOverloadPattern EMPTY = new EncodedOverloadPattern(List.of(), List.of());

    private final Map<Integer, OverloadPatternSlot> inputSlots;
    private final Map<Integer, OverloadPatternSlot> outputSlots;

    public EncodedOverloadPattern(
            Collection<OverloadPatternSlot> inputSlots,
            Collection<OverloadPatternSlot> outputSlots
    ) {
        this.inputSlots = freezeByIndex(inputSlots, OverloadPatternSlot.Side.INPUT);
        this.outputSlots = freezeByIndex(outputSlots, OverloadPatternSlot.Side.OUTPUT);
    }

    public static EncodedOverloadPattern empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Collection<OverloadPatternSlot> inputSlots() {
        return inputSlots.values();
    }

    public Collection<OverloadPatternSlot> outputSlots() {
        return outputSlots.values();
    }

    public Optional<OverloadPatternSlot> inputSlot(int slotIndex) {
        return Optional.ofNullable(inputSlots.get(slotIndex));
    }

    public Optional<OverloadPatternSlot> outputSlot(int slotIndex) {
        return Optional.ofNullable(outputSlots.get(slotIndex));
    }

    /**
     * Unspecified slots default to STRICT to keep overload behavior opt-in.
     */
    public MatchMode inputModeOrDefault(int slotIndex) {
        return inputSlot(slotIndex)
                .map(OverloadPatternSlot::matchMode)
                .orElse(MatchMode.STRICT);
    }

    /**
     * Unspecified slots default to STRICT to keep overload behavior opt-in.
     */
    public MatchMode outputModeOrDefault(int slotIndex) {
        return outputSlot(slotIndex)
                .map(OverloadPatternSlot::matchMode)
                .orElse(MatchMode.STRICT);
    }

    public boolean isEmpty() {
        return inputSlots.isEmpty() && outputSlots.isEmpty();
    }

    private static Map<Integer, OverloadPatternSlot> freezeByIndex(
            Collection<OverloadPatternSlot> slots,
            OverloadPatternSlot.Side expectedSide
    ) {
        Objects.requireNonNull(slots, "slots");

        var sorted = new TreeMap<Integer, OverloadPatternSlot>();
        for (var slot : slots) {
            Objects.requireNonNull(slot, "slot");
            if (slot.side() != expectedSide) {
                throw new IllegalArgumentException("slot side mismatch: expected " + expectedSide);
            }
            var previous = sorted.put(slot.slotIndex(), slot);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate slot index: " + slot.slotIndex());
            }
        }
        return Map.copyOf(sorted);
    }

    public static final class Builder {
        private final Map<Integer, OverloadPatternSlot> inputSlots = new TreeMap<>();
        private final Map<Integer, OverloadPatternSlot> outputSlots = new TreeMap<>();

        private Builder() {
        }

        public Builder input(int slotIndex, MatchMode matchMode) {
            var slot = OverloadPatternSlot.input(slotIndex, matchMode);
            putUnique(inputSlots, slot);
            return this;
        }

        public Builder output(int slotIndex, MatchMode matchMode) {
            var slot = OverloadPatternSlot.output(slotIndex, matchMode);
            putUnique(outputSlots, slot);
            return this;
        }

        public EncodedOverloadPattern build() {
            return new EncodedOverloadPattern(inputSlots.values(), outputSlots.values());
        }

        private static void putUnique(Map<Integer, OverloadPatternSlot> target, OverloadPatternSlot slot) {
            var previous = target.put(slot.slotIndex(), slot);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate slot index: " + slot.slotIndex());
            }
        }
    }
}
