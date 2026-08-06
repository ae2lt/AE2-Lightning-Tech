package com.moakiee.ae2lt.overload.pattern;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.model.CompareKey;
import com.moakiee.ae2lt.overload.model.CompareKeys;
import com.moakiee.ae2lt.overload.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.model.MatchMode;

/**
 * Runtime definition object for one overload pattern.
 * <p>
 * This class intentionally stays one layer above AE2's concrete pattern
 * interfaces for now. It describes:
 * <ul>
 *   <li>which inputs and outputs exist</li>
 *   <li>how each input/output slot should be matched</li>
 *   <li>which outputs are primary vs non-primary</li>
 * </ul>
 * Later provider and CPU integration can adapt this object into the exact AE2
 * execution hooks they need.
 */
public final class OverloadPatternDetails implements OverloadedProviderOnlyPatternDetails {
    private final SourcePatternSnapshot sourcePattern;
    private final EncodedOverloadPattern encodedPattern;
    private final List<InputSlot> inputs;
    private final List<OutputSlot> outputs;

    public OverloadPatternDetails(ParsedPatternDefinition parsedPattern, EncodedOverloadPattern encodedPattern) {
        Objects.requireNonNull(parsedPattern, "parsedPattern");
        this.sourcePattern = parsedPattern.sourcePattern();
        this.encodedPattern = Objects.requireNonNull(encodedPattern, "encodedPattern");
        this.inputs = parsedPattern.inputs().stream()
                .map(input -> toInputSlot(input, encodedPattern.inputModeOrDefault(input.slotIndex())))
                .toList();
        this.outputs = parsedPattern.outputs().stream()
                .map(output -> toOutputSlot(output, encodedPattern.outputModeOrDefault(output.slotIndex())))
                .toList();
    }

    @Override
    public PatternExecutionHostKind requiredHostKind() {
        return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
    }

    @Override
    public String overloadPatternIdentity() {
        return sourcePattern.itemId() + "|inputs=" + encodedPattern.inputSlots().toString()
                + "|outputs=" + encodedPattern.outputSlots().toString();
    }

    @Override
    public OverloadPatternDetails overloadPatternDetailsView() {
        return this;
    }

    public SourcePatternSnapshot sourcePattern() {
        return sourcePattern;
    }

    public EncodedOverloadPattern encodedPattern() {
        return encodedPattern;
    }

    public List<InputSlot> inputs() {
        return inputs;
    }

    public List<OutputSlot> outputs() {
        return outputs;
    }

    public List<OutputSlot> primaryOutputs() {
        return outputs.stream()
                .filter(OutputSlot::primaryOutput)
                .collect(Collectors.toUnmodifiableList());
    }

    public List<OutputSlot> nonPrimaryOutputs() {
        return outputs.stream()
                .filter(output -> !output.primaryOutput())
                .collect(Collectors.toUnmodifiableList());
    }

    public MatchMode inputMode(int slotIndex) {
        return encodedPattern.inputModeOrDefault(slotIndex);
    }

    public MatchMode outputMode(int slotIndex) {
        return encodedPattern.outputModeOrDefault(slotIndex);
    }

    private static InputSlot toInputSlot(ParsedPatternInput input, MatchMode matchMode) {
        var template = normalizedCopy(input.stack());
        return new InputSlot(
                input.slotIndex(),
                template,
                input.amountPerCraft(),
                matchMode,
                CompareKeys.fromStack(template, matchMode));
    }

    private static OutputSlot toOutputSlot(ParsedPatternOutput output, MatchMode matchMode) {
        var template = normalizedCopy(output.stack());
        return new OutputSlot(
                output.slotIndex(),
                template,
                output.amountPerCraft(),
                matchMode,
                output.primaryOutput(),
                CompareKeys.fromStack(template, matchMode));
    }

    private static ItemStack normalizedCopy(ItemStack stack) {
        var copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    /**
     * One runtime input slot with its slot-local compare semantics.
     */
    public record InputSlot(
            int slotIndex,
            ItemStack template,
            int amountPerCraft,
            MatchMode matchMode,
            CompareKey compareKey
    ) {
        public InputSlot {
            Objects.requireNonNull(template, "template");
            if (amountPerCraft <= 0) {
                throw new IllegalArgumentException("amountPerCraft must be > 0");
            }
            Objects.requireNonNull(matchMode, "matchMode");
            Objects.requireNonNull(compareKey, "compareKey");
            template = normalizedCopy(template);
        }

        @Override
        public ItemStack template() {
            return template.copy();
        }
    }

    /**
     * One runtime output slot with its slot-local compare semantics and
     * primary/non-primary classification.
     * <p>
     * Non-primary outputs are still first-class outputs and are intentionally
     * retained for future CPU waiting/claiming logic.
     */
    public record OutputSlot(
            int slotIndex,
            ItemStack template,
            int amountPerCraft,
            MatchMode matchMode,
            boolean primaryOutput,
            CompareKey compareKey
    ) {
        public OutputSlot {
            Objects.requireNonNull(template, "template");
            if (amountPerCraft <= 0) {
                throw new IllegalArgumentException("amountPerCraft must be > 0");
            }
            Objects.requireNonNull(matchMode, "matchMode");
            Objects.requireNonNull(compareKey, "compareKey");
            template = normalizedCopy(template);
        }

        @Override
        public ItemStack template() {
            return template.copy();
        }
    }
}
