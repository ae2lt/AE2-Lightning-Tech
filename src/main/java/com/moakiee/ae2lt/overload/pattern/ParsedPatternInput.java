package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

/**
 * One parsed input slot from a plain pattern before overload rules are applied.
 */
public record ParsedPatternInput(int slotIndex, ItemStack stack) {
    public ParsedPatternInput {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0");
        }
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("input stack must not be empty");
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
