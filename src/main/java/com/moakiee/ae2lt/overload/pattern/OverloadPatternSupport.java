package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/**
 * Shared conversions between AE2 pattern details and overload-specific model
 * types.
 */
public final class OverloadPatternSupport {
    private OverloadPatternSupport() {
    }

    public static ParsedPatternDefinition toParsedDefinition(ItemStack sourcePatternStack,
                                                             IPatternDetails sourceDetails,
                                                             HolderLookup.Provider registries) {
        Objects.requireNonNull(sourcePatternStack, "sourcePatternStack");
        Objects.requireNonNull(sourceDetails, "sourceDetails");
        Objects.requireNonNull(registries, "registries");

        var builder = ParsedPatternDefinition.builder(sourcePatternStack, registries);

        var inputs = sourceDetails.getInputs();
        for (int slot = 0; slot < inputs.length; slot++) {
            var inputTemplate = firstItemTemplate(inputs[slot].getPossibleInputs());
            if (!inputTemplate.isEmpty()) {
                builder.input(slot, inputTemplate);
            }
        }

        var outputs = sourceDetails.getOutputs();
        boolean primaryOutputAssigned = false;
        for (int slot = 0; slot < outputs.size(); slot++) {
            var outputStack = toItemStack(outputs.get(slot));
            if (outputStack.isEmpty()) {
                continue;
            }

            builder.output(slot, outputStack, !primaryOutputAssigned);
            primaryOutputAssigned = true;
        }

        return builder.build();
    }

    public static ItemStack toItemStack(GenericStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return ItemStack.EMPTY;
        }
        return itemKey.toStack((int) stack.amount());
    }

    private static ItemStack firstItemTemplate(GenericStack[] possibleInputs) {
        for (var possible : possibleInputs) {
            if (possible.what() instanceof AEItemKey itemKey) {
                return itemKey.toStack((int) possible.amount());
            }
        }
        return ItemStack.EMPTY;
    }
}
