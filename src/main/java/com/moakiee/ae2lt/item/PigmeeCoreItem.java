package com.moakiee.ae2lt.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Reusable catalyst obtained by dropping an anvil onto an adult pig.
 *
 * <p>The stack-sensitive remainder API is shared by vanilla crafting, AE2 pattern decoding and
 * Thunderbolt batch dispatch. Returning the exact input stack therefore lets one core serve an
 * entire batch without any Pigmee-specific crafting hook.
 */
public final class PigmeeCoreItem extends Item {
    public PigmeeCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack remainder = stack.copy();
        remainder.setCount(1);
        return remainder;
    }
}
