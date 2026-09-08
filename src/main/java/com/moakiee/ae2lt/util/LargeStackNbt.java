package com.moakiee.ae2lt.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Item identity/full NBT plus an independent count, with legacy ItemStack support. */
public final class LargeStackNbt {
    private static final String COUNT = "ae2ltCount";

    private LargeStackNbt() {
    }

    public static CompoundTag save(ItemStack stack) {
        CompoundTag tag = stack.copyWithCount(1).save(new CompoundTag());
        tag.putInt(COUNT, stack.getCount());
        return tag;
    }

    public static ItemStack load(CompoundTag tag) {
        ItemStack stack = ItemStack.of(tag);
        if (tag.contains(COUNT, Tag.TAG_INT) && !stack.isEmpty()) {
            int count = tag.getInt(COUNT);
            if (count <= 0) {
                return ItemStack.EMPTY;
            }
            stack.setCount(count);
        }
        return stack;
    }
}
