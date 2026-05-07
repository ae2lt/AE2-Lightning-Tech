package com.moakiee.ae2lt.overload.pattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/**
 * Host-neutral parse result of a plain pattern item.
 * <p>
 * This is the input to overload conversion. It is intentionally simpler than
 * AE2 runtime interfaces and only carries the information needed to derive an
 * overload pattern item or overload runtime definition.
 */
public final class ParsedPatternDefinition {
    private final SourcePatternSnapshot sourcePattern;
    private final List<ParsedPatternInput> inputs;
    private final List<ParsedPatternOutput> outputs;

    public ParsedPatternDefinition(
            SourcePatternSnapshot sourcePattern,
            Collection<ParsedPatternInput> inputs,
            Collection<ParsedPatternOutput> outputs
    ) {
        this.sourcePattern = Objects.requireNonNull(sourcePattern, "sourcePattern");
        this.inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        this.outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
    }

    public static Builder builder(ItemStack sourcePatternStack, HolderLookup.Provider registries) {
        return new Builder(SourcePatternSnapshot.fromItemStack(sourcePatternStack, registries));
    }

    public SourcePatternSnapshot sourcePattern() {
        return sourcePattern;
    }

    public List<ParsedPatternInput> inputs() {
        return inputs;
    }

    public List<ParsedPatternOutput> outputs() {
        return outputs;
    }

    public int inputCount() {
        return inputs.size();
    }

    public int outputCount() {
        return outputs.size();
    }

    public static final class Builder {
        private final SourcePatternSnapshot sourcePattern;
        private final List<ParsedPatternInput> inputs = new ArrayList<>();
        private final List<ParsedPatternOutput> outputs = new ArrayList<>();

        private Builder(SourcePatternSnapshot sourcePattern) {
            this.sourcePattern = sourcePattern;
        }

        public Builder input(int slotIndex, ItemStack stack) {
            inputs.add(new ParsedPatternInput(slotIndex, stack));
            return this;
        }

        public Builder output(int slotIndex, ItemStack stack, boolean primaryOutput) {
            outputs.add(new ParsedPatternOutput(slotIndex, stack, primaryOutput));
            return this;
        }

        public ParsedPatternDefinition build() {
            return new ParsedPatternDefinition(sourcePattern, inputs, outputs);
        }
    }
}
