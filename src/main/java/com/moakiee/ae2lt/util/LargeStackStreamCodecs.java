package com.moakiee.ae2lt.util;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * FriendlyByteBuf's vanilla ItemStack codec stores the count as a byte in
 * 1.20.1, so large machine stacks need their count encoded separately.
 */
public final class LargeStackStreamCodecs {
    private LargeStackStreamCodecs() {
    }

    public static void writeItemStack(FriendlyByteBuf buffer, ItemStack stack) {
        if (stack.isEmpty()) {
            buffer.writeItemStack(ItemStack.EMPTY, true);
            buffer.writeVarInt(0);
            return;
        }

        buffer.writeItemStack(stack.copyWithCount(1), true);
        writeStackCount(buffer, stack.getCount());
    }

    public static ItemStack readItemStack(FriendlyByteBuf buffer) {
        ItemStack stack = buffer.readItem();
        int count = readStackCount(buffer);
        if (stack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }

        return stack.copyWithCount(count);
    }

    static void writeStackCount(FriendlyByteBuf buffer, int count) {
        buffer.writeVarInt(Math.max(0, count));
    }

    static int readStackCount(FriendlyByteBuf buffer) {
        return buffer.readVarInt();
    }
}
