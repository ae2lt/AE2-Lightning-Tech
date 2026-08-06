package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.stacks.GenericStack;
import appeng.menu.guisync.PacketWritable;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType.AdvancedConfig;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType.OverloadConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent processing configuration bound to the terminal's current fake-slot draft.
 */
public record ProcessingPatternTerminalDraft(
        ProcessingPatternEncodingType type,
        List<GenericStack> inputs,
        List<GenericStack> outputs,
        @Nullable AdvancedConfig advancedConfig,
        @Nullable OverloadConfig overloadConfig) implements PacketWritable {
    // AE2's extended processing terminal inventory has 81 inputs and 27 outputs.
    // Keep the two protocol bounds distinct so a valid full input snapshot is accepted
    // without also permitting impossible output or configuration payload sizes.
    private static final int MAX_INPUT_SLOTS = 81;
    private static final int MAX_OUTPUT_SLOTS = 27;
    private static final String TAG_TYPE = "Type";
    private static final String TAG_INPUT_SIZE = "InputSize";
    private static final String TAG_OUTPUT_SIZE = "OutputSize";
    private static final String TAG_INPUTS = "Inputs";
    private static final String TAG_OUTPUTS = "Outputs";
    private static final String TAG_DIRECTIONS = "Directions";
    private static final String TAG_INPUT_ID_ONLY = "InputIdOnly";
    private static final String TAG_OUTPUT_ID_ONLY = "OutputIdOnly";
    private static final String TAG_SLOT = "Slot";

    private static final ProcessingPatternTerminalDraft EMPTY =
            new ProcessingPatternTerminalDraft(
                    ProcessingPatternEncodingType.NORMAL, List.of(), List.of(), null, null);

    public ProcessingPatternTerminalDraft {
        type = Objects.requireNonNull(type, "type");
        inputs = immutableNullableCopy(inputs, "inputs", MAX_INPUT_SLOTS);
        outputs = immutableNullableCopy(outputs, "outputs", MAX_OUTPUT_SLOTS);
        if (type != ProcessingPatternEncodingType.fromConfigs(advancedConfig, overloadConfig)) {
            throw new IllegalArgumentException("processing draft type does not match configuration");
        }
        if (advancedConfig != null) {
            var directions = advancedConfig.directions();
            if (directions.length > inputs.size()) {
                throw new IllegalArgumentException("too many advanced directions");
            }
            for (int direction : directions) {
                if (direction < 0 || direction > 6) {
                    throw new IllegalArgumentException("invalid advanced direction");
                }
            }
        }
        if (overloadConfig != null) {
            validateSlots(overloadConfig.inputIdOnly(), inputs.size());
            validateSlots(overloadConfig.outputIdOnly(), outputs.size());
        }
    }

    public ProcessingPatternTerminalDraft(RegistryFriendlyByteBuf data) {
        this(
                data.readEnum(ProcessingPatternEncodingType.class),
                readStacks(data, MAX_INPUT_SLOTS, "input size"),
                readStacks(data, MAX_OUTPUT_SLOTS, "output size"),
                readAdvancedConfig(data),
                readOverloadConfig(data));
    }

    public static ProcessingPatternTerminalDraft empty() {
        return EMPTY;
    }

    public static ProcessingPatternTerminalDraft advanced(
            List<GenericStack> inputs, List<GenericStack> outputs, AdvancedConfig config) {
        return new ProcessingPatternTerminalDraft(
                ProcessingPatternEncodingType.ADVANCED, inputs, outputs,
                Objects.requireNonNull(config, "config"), null);
    }

    public static ProcessingPatternTerminalDraft overload(
            List<GenericStack> inputs, List<GenericStack> outputs, OverloadConfig config) {
        return new ProcessingPatternTerminalDraft(
                ProcessingPatternEncodingType.OVERLOAD, inputs, outputs,
                null, Objects.requireNonNull(config, "config"));
    }

    public static ProcessingPatternTerminalDraft configured(
            List<GenericStack> inputs, List<GenericStack> outputs,
            @Nullable AdvancedConfig advancedConfig, @Nullable OverloadConfig overloadConfig) {
        return new ProcessingPatternTerminalDraft(
                ProcessingPatternEncodingType.fromConfigs(advancedConfig, overloadConfig),
                inputs, outputs, advancedConfig, overloadConfig);
    }

    public boolean matches(List<GenericStack> currentInputs, List<GenericStack> currentOutputs) {
        return sameStackKeys(inputs, currentInputs) && sameStackKeys(outputs, currentOutputs);
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString(TAG_TYPE, type.name());
        tag.putInt(TAG_INPUT_SIZE, inputs.size());
        tag.putInt(TAG_OUTPUT_SIZE, outputs.size());
        tag.put(TAG_INPUTS, writeStacks(inputs, registries));
        tag.put(TAG_OUTPUTS, writeStacks(outputs, registries));
        if (advancedConfig != null) {
            tag.putIntArray(TAG_DIRECTIONS, advancedConfig.directions());
        }
        if (overloadConfig != null) {
            tag.putIntArray(TAG_INPUT_ID_ONLY, overloadConfig.inputIdOnly());
            tag.putIntArray(TAG_OUTPUT_ID_ONLY, overloadConfig.outputIdOnly());
        }
        return tag;
    }

    @Nullable
    public static ProcessingPatternTerminalDraft read(
            CompoundTag tag, HolderLookup.Provider registries) {
        try {
            var type = ProcessingPatternEncodingType.valueOf(tag.getString(TAG_TYPE));
            if (type == ProcessingPatternEncodingType.NORMAL) return null;
            int inputSize = checkedSize(
                    tag.getInt(TAG_INPUT_SIZE), MAX_INPUT_SLOTS, "input size");
            int outputSize = checkedSize(
                    tag.getInt(TAG_OUTPUT_SIZE), MAX_OUTPUT_SLOTS, "output size");
            var inputs = readStacks(
                    tag.getList(TAG_INPUTS, Tag.TAG_COMPOUND), inputSize, registries);
            var outputs = readStacks(
                    tag.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND), outputSize, registries);
            var advanced = type.hasAdvanced()
                    ? new AdvancedConfig(tag.getIntArray(TAG_DIRECTIONS)) : null;
            var overload = type.hasOverload()
                    ? new OverloadConfig(
                            tag.getIntArray(TAG_INPUT_ID_ONLY),
                            tag.getIntArray(TAG_OUTPUT_ID_ONLY))
                    : null;
            return configured(inputs, outputs, advanced, overload);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean sameState(
            @Nullable ProcessingPatternTerminalDraft left,
            @Nullable ProcessingPatternTerminalDraft right) {
        if (left == right) return true;
        if (left == null || right == null
                || left.type != right.type
                || !left.inputs.equals(right.inputs)
                || !left.outputs.equals(right.outputs)) {
            return false;
        }
        return sameAdvanced(left.advancedConfig, right.advancedConfig)
                && sameOverload(left.overloadConfig, right.overloadConfig);
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeEnum(type);
        writeStacks(data, inputs);
        writeStacks(data, outputs);
        data.writeBoolean(advancedConfig != null);
        if (advancedConfig != null) writeIntArray(data, advancedConfig.directions());
        data.writeBoolean(overloadConfig != null);
        if (overloadConfig != null) {
            writeIntArray(data, overloadConfig.inputIdOnly());
            writeIntArray(data, overloadConfig.outputIdOnly());
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProcessingPatternTerminalDraft draft && sameState(this, draft);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, inputs, outputs);
        if (advancedConfig != null) {
            result = 31 * result + Arrays.hashCode(advancedConfig.directions());
        }
        if (overloadConfig != null) {
            result = 31 * result + Arrays.hashCode(overloadConfig.inputIdOnly());
            result = 31 * result + Arrays.hashCode(overloadConfig.outputIdOnly());
        }
        return result;
    }

    private static List<GenericStack> immutableNullableCopy(
            List<GenericStack> stacks, String name, int maxSize) {
        if (stacks == null || stacks.size() > maxSize) {
            throw new IllegalArgumentException("invalid processing draft " + name);
        }
        return Collections.unmodifiableList(new ArrayList<>(stacks));
    }

    private static boolean sameStackKeys(List<GenericStack> left, List<GenericStack> right) {
        if (right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            var leftStack = left.get(i);
            var rightStack = right.get(i);
            if (leftStack == null || rightStack == null) {
                if (leftStack != rightStack) return false;
            } else if (!leftStack.what().equals(rightStack.what())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameAdvanced(
            @Nullable AdvancedConfig left, @Nullable AdvancedConfig right) {
        return left == right || left != null && right != null
                && Arrays.equals(left.directions(), right.directions());
    }

    private static boolean sameOverload(
            @Nullable OverloadConfig left, @Nullable OverloadConfig right) {
        return left == right || left != null && right != null
                && Arrays.equals(left.inputIdOnly(), right.inputIdOnly())
                && Arrays.equals(left.outputIdOnly(), right.outputIdOnly());
    }

    private static void validateSlots(int[] slots, int slotCount) {
        var seen = new boolean[slotCount];
        for (int slot : slots) {
            if (slot < 0 || slot >= slotCount || seen[slot]) {
                throw new IllegalArgumentException("invalid overload slot");
            }
            seen[slot] = true;
        }
    }

    private static ListTag writeStacks(
            List<GenericStack> stacks, HolderLookup.Provider registries) {
        var result = new ListTag();
        for (int slot = 0; slot < stacks.size(); slot++) {
            var stack = stacks.get(slot);
            if (stack == null) continue;
            var entry = GenericStack.writeTag(registries, stack);
            entry.putInt(TAG_SLOT, slot);
            result.add(entry);
        }
        return result;
    }

    private static List<GenericStack> readStacks(
            ListTag entries, int size, HolderLookup.Provider registries) {
        var result = nullableStackList(size);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            int slot = entry.getInt(TAG_SLOT);
            if (slot >= 0 && slot < size) {
                result.set(slot, GenericStack.readTag(registries, entry));
            }
        }
        return result;
    }

    private static void writeStacks(RegistryFriendlyByteBuf data, List<GenericStack> stacks) {
        data.writeVarInt(stacks.size());
        for (var stack : stacks) GenericStack.writeBuffer(stack, data);
    }

    private static List<GenericStack> readStacks(
            RegistryFriendlyByteBuf data, int maxSize, String name) {
        int size = checkedSize(data.readVarInt(), maxSize, name);
        var result = new ArrayList<GenericStack>(size);
        for (int i = 0; i < size; i++) result.add(GenericStack.readBuffer(data));
        return result;
    }

    private static void writeIntArray(RegistryFriendlyByteBuf data, int[] values) {
        data.writeVarInt(values.length);
        for (int value : values) data.writeVarInt(value);
    }

    private static int[] readIntArray(
            RegistryFriendlyByteBuf data, int maxSize, String name) {
        int size = checkedSize(data.readVarInt(), maxSize, name);
        var result = new int[size];
        for (int i = 0; i < size; i++) result[i] = data.readVarInt();
        return result;
    }

    @Nullable
    private static AdvancedConfig readAdvancedConfig(RegistryFriendlyByteBuf data) {
        return data.readBoolean()
                ? new AdvancedConfig(readIntArray(
                        data, MAX_INPUT_SLOTS, "advanced direction count"))
                : null;
    }

    @Nullable
    private static OverloadConfig readOverloadConfig(RegistryFriendlyByteBuf data) {
        return data.readBoolean()
                ? new OverloadConfig(
                        readIntArray(data, MAX_INPUT_SLOTS, "overload input slot count"),
                        readIntArray(data, MAX_OUTPUT_SLOTS, "overload output slot count"))
                : null;
    }

    private static int checkedSize(int size, int maxSize, String name) {
        if (size < 0 || size > maxSize) {
            throw new IllegalArgumentException("invalid processing draft " + name);
        }
        return size;
    }

    private static ArrayList<GenericStack> nullableStackList(int size) {
        var result = new ArrayList<GenericStack>(size);
        for (int i = 0; i < size; i++) result.add(null);
        return result;
    }
}
