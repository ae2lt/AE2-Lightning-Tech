package com.moakiee.ae2lt.overload.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

import com.moakiee.ae2lt.overload.model.MatchMode;

/**
 * AE2-facing runtime implementation of an overload pattern.
 * <p>
 * It preserves the original pattern's execution behavior while overriding input
 * matching semantics per slot for planning and crafting extraction.
 */
public final class Ae2OverloadPatternDetails implements IPatternDetails, OverloadedProviderOnlyPatternDetails {
    private final AEItemKey definition;
    private final OverloadPatternDetails overloadDetails;
    private final IPatternDetails sourceDetails;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    public Ae2OverloadPatternDetails(AEItemKey definition,
                                     OverloadPatternDetails overloadDetails,
                                     IPatternDetails sourceDetails) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.overloadDetails = Objects.requireNonNull(overloadDetails, "overloadDetails");
        this.sourceDetails = Objects.requireNonNull(sourceDetails, "sourceDetails");

        var sourceInputs = sourceDetails.getInputs();
        this.inputs = new IInput[sourceInputs.length];
        for (int slot = 0; slot < sourceInputs.length; slot++) {
            this.inputs[slot] = wrapInput(sourceInputs[slot], overloadDetails.inputMode(slot));
        }
        this.outputs = List.copyOf(sourceDetails.getOutputs());
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs.clone();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return sourceDetails.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        if (sourceDetails instanceof AEProcessingPattern processingPattern) {
            pushProcessingInputsToExternalInventory(processingPattern, inputHolder, inputSink);
            return;
        }

        IPatternDetails.super.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        var tooltip = sourceDetails.getTooltip(level, flags);
        tooltip.addProperty(net.minecraft.network.chat.Component.translatable(
                "tooltip.ae2lt.overload_pattern.provider_only"));
        return tooltip;
    }

    @Override
    public PatternExecutionHostKind requiredHostKind() {
        return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
    }

    @Override
    public String overloadPatternIdentity() {
        return overloadDetails.overloadPatternIdentity();
    }

    @Override
    public OverloadPatternDetails overloadPatternDetailsView() {
        return overloadDetails;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Ae2OverloadPatternDetails other && definition.equals(other.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }


    private static final class OverloadInput implements IInput {
        private final IInput sourceInput;
        private final MatchMode matchMode;
        private final GenericStack[] possibleInputs;
        private final List<AEItemKey> itemKeys;

        private OverloadInput(IInput sourceInput, MatchMode matchMode) {
            this.sourceInput = sourceInput;
            this.matchMode = matchMode;
            this.possibleInputs = sourceInput.getPossibleInputs();
            this.itemKeys = collectItemKeys(possibleInputs);
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return sourceInput.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return switch (matchMode) {
                case STRICT -> sourceInput.isValid(input, level);
                case ID_ONLY -> matchesItemId(input);
            };
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            var direct = sourceInput.getRemainingKey(template);
            if (direct != null || matchMode == MatchMode.STRICT) {
                return direct;
            }

            if (template instanceof AEItemKey itemKey) {
                for (var possible : itemKeys) {
                    if (possible.getItem() == itemKey.getItem()) {
                        var remaining = sourceInput.getRemainingKey(possible);
                        if (remaining != null) {
                            return remaining;
                        }
                    }
                }
            }
            return null;
        }

        private boolean matchesItemId(AEKey input) {
            if (!(input instanceof AEItemKey itemKey)) {
                return false;
            }
            for (var possible : itemKeys) {
                if (possible.getItem() == itemKey.getItem()) {
                    return true;
                }
            }
            return false;
        }

        private static List<AEItemKey> collectItemKeys(GenericStack[] possibleInputs) {
            var result = new ArrayList<AEItemKey>(possibleInputs.length);
            for (var possible : possibleInputs) {
                if (possible.what() instanceof AEItemKey itemKey) {
                    result.add(itemKey);
                }
            }
            if (result.isEmpty()) {
                throw new IllegalArgumentException("overload patterns currently only support item inputs");
            }
            return List.copyOf(result);
        }
    }

    private void pushProcessingInputsToExternalInventory(
            AEProcessingPattern processingPattern,
            KeyCounter[] inputHolder,
            PatternInputSink inputSink
    ) {
        var availableInputs = new KeyCounter();
        for (var counter : inputHolder) {
            availableInputs.addAll(counter);
        }

        for (var sparseInput : processingPattern.getSparseInputs()) {
            if (sparseInput == null) {
                continue;
            }

            var expectedKey = sparseInput.what();
            var requiredAmount = sparseInput.amount();
            var matchMode = resolveMatchMode(expectedKey);

            if (matchMode == MatchMode.ID_ONLY && expectedKey instanceof AEItemKey expectedItemKey) {
                pushIdOnlyInput(expectedItemKey, requiredAmount, availableInputs, inputSink);
            } else {
                pushStrictInput(expectedKey, requiredAmount, availableInputs, inputSink);
            }
        }
    }

    private MatchMode resolveMatchMode(AEKey expectedKey) {
        var sourceInputs = sourceDetails.getInputs();
        for (int slot = 0; slot < sourceInputs.length; slot++) {
            for (var possibleInput : sourceInputs[slot].getPossibleInputs()) {
                if (possibleInput.what().equals(expectedKey)) {
                    return overloadDetails.inputMode(slot);
                }
            }
        }
        return MatchMode.STRICT;
    }

    private static IInput wrapInput(IInput sourceInput, MatchMode matchMode) {
        if (matchMode != MatchMode.ID_ONLY || !hasItemVariants(sourceInput.getPossibleInputs())) {
            return sourceInput;
        }
        return new OverloadInput(sourceInput, matchMode);
    }

    private static boolean hasItemVariants(GenericStack[] possibleInputs) {
        for (var possibleInput : possibleInputs) {
            if (possibleInput.what() instanceof AEItemKey) {
                return true;
            }
        }
        return false;
    }

    private static void pushStrictInput(
            AEKey expectedKey,
            long requiredAmount,
            KeyCounter availableInputs,
            PatternInputSink inputSink
    ) {
        long available = availableInputs.get(expectedKey);
        if (available < requiredAmount) {
            throw new RuntimeException("Expected at least %d of %s when pushing pattern, but only %d available"
                    .formatted(requiredAmount, expectedKey, available));
        }

        inputSink.pushInput(expectedKey, requiredAmount);
        availableInputs.remove(expectedKey, requiredAmount);
    }

    private static void pushIdOnlyInput(
            AEItemKey expectedItemKey,
            long requiredAmount,
            KeyCounter availableInputs,
            PatternInputSink inputSink
    ) {
        long remaining = requiredAmount;

        remaining -= pushMatchingKey(expectedItemKey, remaining, availableInputs, inputSink);

        if (remaining > 0) {
            for (var availableKey : snapshotKeys(availableInputs)) {
                if (remaining <= 0) {
                    break;
                }
                if (availableKey.equals(expectedItemKey)) {
                    continue;
                }
                if (!(availableKey instanceof AEItemKey availableItemKey)) {
                    continue;
                }
                if (availableItemKey.getItem() != expectedItemKey.getItem()) {
                    continue;
                }

                remaining -= pushMatchingKey(availableKey, remaining, availableInputs, inputSink);
            }
        }

        if (remaining > 0) {
            throw new RuntimeException("Expected at least %d of %s by item id when pushing pattern, but only %d available"
                    .formatted(requiredAmount, expectedItemKey, requiredAmount - remaining));
        }
    }

    private static long pushMatchingKey(
            AEKey key,
            long requiredAmount,
            KeyCounter availableInputs,
            PatternInputSink inputSink
    ) {
        long available = availableInputs.get(key);
        if (available <= 0 || requiredAmount <= 0) {
            return 0;
        }

        long toPush = Math.min(available, requiredAmount);
        inputSink.pushInput(key, toPush);
        availableInputs.remove(key, toPush);
        return toPush;
    }

    private static List<AEKey> snapshotKeys(KeyCounter counter) {
        var keys = new ArrayList<AEKey>();
        for (var entry : counter) {
            if (entry.getLongValue() > 0) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }
}
