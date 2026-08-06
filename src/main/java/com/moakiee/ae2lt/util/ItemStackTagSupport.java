package com.moakiee.ae2lt.util;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Small helpers for 1.20.1-style ItemStack NBT access.
 */
public final class ItemStackTagSupport {
    private ItemStackTagSupport() {
    }

    public static CompoundTag getTagCopy(ItemStack stack) {
        var tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag.copy();
    }

    public static void updateTag(ItemStack stack, Consumer<CompoundTag> updater) {
        var tag = stack.getOrCreateTag();
        updater.accept(tag);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.setTag(null);
        } else {
            stack.setTag(tag.copy());
        }
    }
}
