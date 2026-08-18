package com.moakiee.ae2lt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class NativeStackDropHelperTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void splitsOversizedStackWithoutMutatingStoredStack() {
        ItemStack oversized = new ItemStack(Items.DIAMOND, 130);

        List<ItemStack> drops = new ArrayList<>();
        NativeStackDropHelper.addDrops(drops, oversized);

        assertEquals(List.of(64, 64, 2), drops.stream().map(ItemStack::getCount).toList());
        assertEquals(130, drops.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(130, oversized.getCount());
    }

    @Test
    void usesItemSpecificNativeStackLimitAndPreservesTag() {
        ItemStack oversized = new ItemStack(Items.DIAMOND_PICKAXE, 3);
        oversized.getOrCreateTag().putString("Marker", "kept");

        List<ItemStack> drops = NativeStackDropHelper.splitForDrop(oversized);

        assertEquals(List.of(1, 1, 1), drops.stream().map(ItemStack::getCount).toList());
        assertTrue(drops.stream().allMatch(stack -> "kept".equals(stack.getOrCreateTag().getString("Marker"))));
        assertEquals(3, oversized.getCount());
    }

    @Test
    void ignoresEmptyStacks() {
        assertTrue(NativeStackDropHelper.splitForDrop(ItemStack.EMPTY).isEmpty());
    }
}
