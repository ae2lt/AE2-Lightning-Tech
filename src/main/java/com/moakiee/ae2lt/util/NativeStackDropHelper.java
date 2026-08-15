package com.moakiee.ae2lt.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Creates world drops without exposing oversized stacks to vanilla collectors. */
public final class NativeStackDropHelper {
    private NativeStackDropHelper() {
    }

    public static void popResource(Level level, BlockPos pos, ItemStack stack) {
        for (ItemStack nativeStack : splitForDrop(stack)) {
            Block.popResource(level, pos, nativeStack);
        }
    }

    public static void addDrops(List<ItemStack> drops, ItemStack stack) {
        drops.addAll(splitForDrop(stack));
    }

    static List<ItemStack> splitForDrop(ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }

        ItemStack remaining = stack.copy();
        int nativeLimit = Math.max(1, remaining.getMaxStackSize());
        List<ItemStack> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            result.add(remaining.split(Math.min(nativeLimit, remaining.getCount())));
        }
        return result;
    }
}
