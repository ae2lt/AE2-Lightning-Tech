package com.moakiee.ae2lt.overload.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;

import appeng.menu.guisync.PacketWritable;

import com.moakiee.ae2lt.overload.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.model.MatchMode;

/**
 * Editable GUI state for one overload pattern conversion session.
 * <p>
 * This is purely a configuration model: it does not execute crafting logic.
 * It only tracks which per-slot modes are currently selected by the player.
 */
public record OverloadPatternEditState(
        boolean hasSourcePattern,
        boolean sourceWasOverloadPattern,
        int inputCount,
        int outputCount,
        List<ConfiguredSlot> inputSlots,
        List<ConfiguredSlot> outputSlots,
        boolean canEncode
) implements PacketWritable {

    public OverloadPatternEditState {
        if (inputCount < 0 || outputCount < 0) {
            throw new IllegalArgumentException("slot counts must be >= 0");
        }
        inputSlots = List.copyOf(Objects.requireNonNull(inputSlots, "inputSlots"));
        outputSlots = List.copyOf(Objects.requireNonNull(outputSlots, "outputSlots"));
    }

    public OverloadPatternEditState(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readSlots(buffer),
                readSlots(buffer),
                buffer.readBoolean());
    }

    public static OverloadPatternEditState empty() {
        return new OverloadPatternEditState(false, false, 0, 0, List.of(), List.of(), false);
    }

    public static OverloadPatternEditState fromPattern(
            ParsedPatternDefinition parsedPattern,
            EncodedOverloadPattern encodedPattern,
            boolean sourceWasOverloadPattern
    ) {
        Objects.requireNonNull(parsedPattern, "parsedPattern");
        Objects.requireNonNull(encodedPattern, "encodedPattern");

        var inputs = new ArrayList<ConfiguredSlot>(parsedPattern.inputCount());
        for (var input : parsedPattern.inputs()) {
            inputs.add(new ConfiguredSlot(
                    input.slotIndex(),
                    encodedPattern.inputModeOrDefault(input.slotIndex()),
                    false));
        }

        var outputs = new ArrayList<ConfiguredSlot>(parsedPattern.outputCount());
        for (var output : parsedPattern.outputs()) {
            outputs.add(new ConfiguredSlot(
                    output.slotIndex(),
                    encodedPattern.outputModeOrDefault(output.slotIndex()),
                    output.primaryOutput()));
        }

        return new OverloadPatternEditState(
                true,
                sourceWasOverloadPattern,
                inputs.size(),
                outputs.size(),
                inputs,
                outputs,
                true);
    }

    public MatchMode inputMode(int slotIndex) {
        return inputSlots.stream()
                .filter(slot -> slot.slotIndex() == slotIndex)
                .findFirst()
                .map(ConfiguredSlot::matchMode)
                .orElse(MatchMode.STRICT);
    }

    public MatchMode outputMode(int slotIndex) {
        return outputSlots.stream()
                .filter(slot -> slot.slotIndex() == slotIndex)
                .findFirst()
                .map(ConfiguredSlot::matchMode)
                .orElse(MatchMode.STRICT);
    }

    public OverloadPatternEditState toggleInputMode(int slotIndex) {
        return new OverloadPatternEditState(
                hasSourcePattern,
                sourceWasOverloadPattern,
                inputCount,
                outputCount,
                toggle(inputSlots, slotIndex),
                outputSlots,
                canEncode);
    }

    public OverloadPatternEditState toggleOutputMode(int slotIndex) {
        return new OverloadPatternEditState(
                hasSourcePattern,
                sourceWasOverloadPattern,
                inputCount,
                outputCount,
                inputSlots,
                toggle(outputSlots, slotIndex),
                canEncode);
    }

    public EncodedOverloadPattern toEncodedPattern() {
        var builder = EncodedOverloadPattern.builder();
        for (var slot : inputSlots) {
            builder.input(slot.slotIndex(), slot.matchMode());
        }
        for (var slot : outputSlots) {
            builder.output(slot.slotIndex(), slot.matchMode());
        }
        return builder.build();
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(hasSourcePattern);
        buffer.writeBoolean(sourceWasOverloadPattern);
        buffer.writeVarInt(inputCount);
        buffer.writeVarInt(outputCount);
        writeSlots(buffer, inputSlots);
        writeSlots(buffer, outputSlots);
        buffer.writeBoolean(canEncode);
    }

    private static List<ConfiguredSlot> toggle(List<ConfiguredSlot> slots, int slotIndex) {
        var updated = new ArrayList<ConfiguredSlot>(slots.size());
        for (var slot : slots) {
            if (slot.slotIndex() == slotIndex) {
                updated.add(slot.withMatchMode(nextMode(slot.matchMode())));
            } else {
                updated.add(slot);
            }
        }
        return List.copyOf(updated);
    }

    private static MatchMode nextMode(MatchMode matchMode) {
        return matchMode == MatchMode.STRICT ? MatchMode.ID_ONLY : MatchMode.STRICT;
    }

    private static void writeSlots(RegistryFriendlyByteBuf buffer, List<ConfiguredSlot> slots) {
        buffer.writeVarInt(slots.size());
        for (var slot : slots) {
            buffer.writeVarInt(slot.slotIndex());
            buffer.writeEnum(slot.matchMode());
            buffer.writeBoolean(slot.primaryOutput());
        }
    }

    private static List<ConfiguredSlot> readSlots(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        var slots = new ArrayList<ConfiguredSlot>(size);
        for (int i = 0; i < size; i++) {
            slots.add(new ConfiguredSlot(
                    buffer.readVarInt(),
                    buffer.readEnum(MatchMode.class),
                    buffer.readBoolean()));
        }
        return List.copyOf(slots);
    }

    /**
     * Slot-local edit entry shown in the encoder UI.
     */
    public record ConfiguredSlot(int slotIndex, MatchMode matchMode, boolean primaryOutput) {
        public ConfiguredSlot {
            Objects.requireNonNull(matchMode, "matchMode");
        }

        public ConfiguredSlot withMatchMode(MatchMode newMode) {
            return new ConfiguredSlot(slotIndex, newMode, primaryOutput);
        }
    }
}
