package com.moakiee.ae2lt.overload.runtime.pattern;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;

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
    // Lazily computed from immutable state; benign race (String is safely publishable).
    private String cachedIdentity;

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
        var cached = cachedIdentity;
        if (cached != null) {
            return cached;
        }
        var computed = sourcePattern.itemId() + "#" + sourcePattern.fingerprint()
                + "|inputs=" + encodedPattern.inputSlots().toString()
                + "|outputs=" + encodedPattern.outputSlots().toString();
        cachedIdentity = computed;
        return computed;
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
        return new InputSlot(
                input.slotIndex(),
                wipeIfIdOnly(normalizedCopy(input.stack()), matchMode),
                input.amountPerCraft(),
                matchMode);
    }

    private static OutputSlot toOutputSlot(ParsedPatternOutput output, MatchMode matchMode) {
        return new OutputSlot(
                output.slotIndex(),
                wipeIfIdOnly(normalizedCopy(output.stack()), matchMode),
                output.amountPerCraft(),
                matchMode,
                output.primaryOutput());
    }

    private static ItemStack normalizedCopy(ItemStack stack) {
        var copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    /**
     * An ID_ONLY slot compares by item id only, so its template erases the captured components
     * ("仅id ⇒ 抹除NBT"). This keeps every metadata consumer — tooltips, CPU pending-output
     * registration, mirror accounting — consistent with the wiped AE2-facing keys exposed by
     * {@code Ae2OverloadPatternDetails}.
     */
    private static ItemStack wipeIfIdOnly(ItemStack stack, MatchMode matchMode) {
        var tag = stack.getTag();
        if (!matchMode.ignoresComponents() || tag == null || tag.isEmpty()) {
            return stack;
        }
        return new ItemStack(stack.getItem(), stack.getCount());
    }

    /**
     * One runtime input slot with its slot-local compare semantics.
     */
    public record InputSlot(
            int slotIndex,
            ItemStack template,
            int amountPerCraft,
            MatchMode matchMode
    ) {
        public InputSlot {
            Objects.requireNonNull(template, "template");
            if (amountPerCraft <= 0) {
                throw new IllegalArgumentException("amountPerCraft must be > 0");
            }
            Objects.requireNonNull(matchMode, "matchMode");
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
            boolean primaryOutput
    ) {
        public OutputSlot {
            Objects.requireNonNull(template, "template");
            if (amountPerCraft <= 0) {
                throw new IllegalArgumentException("amountPerCraft must be > 0");
            }
            Objects.requireNonNull(matchMode, "matchMode");
            template = normalizedCopy(template);
        }

        @Override
        public ItemStack template() {
            return template.copy();
        }
    }
}
