package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

class OverloadedInterfaceSlotKeyCacheTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void countChangesAndEmptyRefillsReuseTheImmutableKey() {
        var cache = new OverloadedInterfaceBlockEntity.ImportSlotKeyCache();
        cache.prepareSlots(27);
        var stack = new ItemStack(Items.STONE, 64);
        var first = cache.keyForSlot(0, stack);
        stack.setCount(7);
        assertSame(first, cache.keyForSlot(0, stack));
        assertSame(first, cache.keyForSlot(0, stack.copy()));
        assertNull(cache.keyForSlot(0, ItemStack.EMPTY));
        cache.prepareSlots(27);
        assertSame(first, cache.keyForSlot(0, new ItemStack(Items.STONE, 32)));
        assertEquals(64, first.getReadOnlyStack().getCount(), "live count changes must not mutate the snapshot");
    }

    @Test
    void inPlaceComponentChangesAndItemReplacementInvalidateTheKey() {
        var cache = new OverloadedInterfaceBlockEntity.ImportSlotKeyCache();
        cache.prepareSlots(1);
        var stack = new ItemStack(Items.STONE);
        var plain = cache.keyForSlot(0, stack);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        var named = cache.keyForSlot(0, stack);
        assertNotSame(plain, named);
        assertFalse(plain.matches(stack));
        assertTrue(named.matches(stack));
        stack.remove(DataComponents.CUSTOM_NAME);
        var reset = cache.keyForSlot(0, stack);
        assertNotSame(named, reset);
        assertEquals(plain, reset);
        var dirt = cache.keyForSlot(0, new ItemStack(Items.DIRT));
        assertNotEquals(reset, dirt);
        assertTrue(dirt.matches(new ItemStack(Items.DIRT)));
    }

    @Test
    void slotResizeAndResetReleaseKeysAndHugeHandlersUseAnUncachedTail() {
        var cache = new OverloadedInterfaceBlockEntity.ImportSlotKeyCache();
        var stack = new ItemStack(Items.STONE);
        cache.prepareSlots(27);
        var first = cache.keyForSlot(0, stack);
        cache.prepareSlots(1);
        var resized = cache.keyForSlot(0, stack);
        assertNotSame(first, resized);
        cache.clear();
        cache.prepareSlots(1);
        assertNotSame(resized, cache.keyForSlot(0, stack));
        cache.prepareSlots(100_000);
        var tail = cache.keyForSlot(99_999, stack);
        var tailAgain = cache.keyForSlot(99_999, stack);
        assertNotSame(tail, tailAgain);
        assertEquals(tail, tailAgain);
    }
}
