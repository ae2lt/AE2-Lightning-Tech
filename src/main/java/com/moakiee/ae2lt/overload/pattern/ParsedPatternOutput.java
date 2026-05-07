package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

/**
 * One parsed output slot from a plain pattern before overload rules are applied.
 * <p>
 * {@code primaryOutput} identifies outputs that should be treated as the main
 * result. Non-primary outputs remain first-class outputs and may represent
 * secondary or intermediate products for later CPU matching logic.
 */
public record ParsedPatternOutput(int slotIndex, ItemStack stack, boolean primaryOutput) {
    public ParsedPatternOutput {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("output stack must not be empty");
        }
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    public int amountPerCraft() {
        return stack.getCount();
    }
}
