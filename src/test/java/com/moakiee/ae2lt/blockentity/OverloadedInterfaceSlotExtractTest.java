package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

class OverloadedInterfaceSlotExtractTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Minimal handler: one slot, optional per-call cap, counts extract calls. */
    private static final class CappedSlot implements IItemHandler {
        ItemStack stack;
        final int perCallCap;
        int extractCalls;
        int maximumCalls = Integer.MAX_VALUE;

        CappedSlot(ItemStack stack, int perCallCap) {
            this.stack = stack;
            this.perCallCap = perCallCap;
        }

        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return stack; }
        @Override public ItemStack insertItem(int slot, ItemStack s, boolean simulate) { return s; }
        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack s) { return true; }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            extractCalls++;
            if (extractCalls > maximumCalls) return ItemStack.EMPTY;
            int take = Math.min(Math.min(amount, perCallCap), stack.getCount());
            if (take <= 0) return ItemStack.EMPTY;
            var out = stack.copyWithCount(take);
            if (!simulate) stack = stack.getCount() == take ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - take);
            return out;
        }
    }

    private record Sunk(AEKey key, long amount) {}

    @Test
    void commonCaseCostsOneExtractCall() {
        var handler = new CappedSlot(new ItemStack(Items.STONE, 40), 64);
        var sunk = new ArrayList<Sunk>();
        long got = OverloadedInterfaceBlockEntity.extractSlotForKey(
                handler, 0, AEItemKey.of(Items.STONE), 40, (k, a) -> sunk.add(new Sunk(k, a)));
        assertEquals(40, got);
        assertEquals(1, handler.extractCalls, "a drained slot must not be probed again");
        assertEquals(List.of(new Sunk(AEItemKey.of(Items.STONE), 40L)), sunk);
        assertTrue(handler.stack.isEmpty());
    }

    @Test
    void perCallStackCapIsDrainedLikeTheFacade() {
        var handler = new CappedSlot(new ItemStack(Items.STONE, 200), 64);
        long total = 0;
        var sunk = new ArrayList<Sunk>();
        total = OverloadedInterfaceBlockEntity.extractSlotForKey(
                handler, 0, AEItemKey.of(Items.STONE), 200, (k, a) -> sunk.add(new Sunk(k, a)));
        assertEquals(200, total);
        assertEquals(4, handler.extractCalls);
        assertEquals(200, sunk.stream().mapToLong(Sunk::amount).sum());
        assertTrue(handler.stack.isEmpty());
    }

    @Test
    void perCallCapBelowMaxStackSizeDoesNotLeaveExtractableItemsBehind() {
        var handler = new CappedSlot(new ItemStack(Items.STONE, 200), 16);
        var sunk = new ArrayList<Sunk>();
        long got = OverloadedInterfaceBlockEntity.extractSlotForKey(
                handler, 0, AEItemKey.of(Items.STONE), 200,
                (k, a) -> sunk.add(new Sunk(k, a)));
        assertEquals(200, got);
        assertEquals(200, sunk.stream().mapToLong(Sunk::amount).sum());
        assertTrue(handler.stack.isEmpty());
    }

    @Test
    void partialExtractionStopsWhenTheHandlerRefusesFurtherWork() {
        var handler = new CappedSlot(new ItemStack(Items.STONE, 200), 16);
        handler.maximumCalls = 1;
        var sunk = new ArrayList<Sunk>();
        long got = OverloadedInterfaceBlockEntity.extractSlotForKey(
                handler, 0, AEItemKey.of(Items.STONE), 200,
                (k, a) -> sunk.add(new Sunk(k, a)));
        assertEquals(16, got);
        assertEquals(184, handler.stack.getCount());
        assertEquals(200, handler.stack.getCount() + sunk.stream().mapToLong(Sunk::amount).sum());
        assertEquals(2, handler.extractCalls);
    }

    @Test
    void changedSlotIsNotDrainedUsingThePreviousKeysFilterDecision() {
        var handler = new CappedSlot(new ItemStack(Items.STONE, 200), 64);
        var sunk = new ArrayList<Sunk>();
        long got = OverloadedInterfaceBlockEntity.extractSlotForKey(
                handler, 0, AEItemKey.of(Items.STONE), 200, (k, a) -> {
                    sunk.add(new Sunk(k, a));
                    handler.stack = new ItemStack(Items.DIRT, 100);
                });
        assertEquals(64, got);
        assertEquals(List.of(new Sunk(AEItemKey.of(Items.STONE), 64L)), sunk);
        assertEquals(100, handler.stack.getCount());
        assertEquals(1, handler.extractCalls);
    }

    @Test
    void budgetBelowSlotCountLeavesRemainderInSlot() {
        var handler = new CappedSlot(new ItemStack(Items.STONE, 40), 64);
        long got = OverloadedInterfaceBlockEntity.extractSlotForKey(
                handler, 0, AEItemKey.of(Items.STONE), 10, (k, a) -> { });
        assertEquals(10, got);
        assertEquals(30, handler.stack.getCount());
    }

    @Test
    void mismatchedStackIsBufferedUnderItsOwnKey() {
        // Handler hands back a different item than observed; nothing may vanish.
        var handler = new CappedSlot(new ItemStack(Items.DIRT, 5), 64);
        var sunk = new ArrayList<Sunk>();
        long got = OverloadedInterfaceBlockEntity.extractSlotForKey(
                handler, 0, AEItemKey.of(Items.STONE), 5, (k, a) -> sunk.add(new Sunk(k, a)));
        assertEquals(5, got);
        assertEquals(List.of(new Sunk(AEItemKey.of(Items.DIRT), 5L)), sunk);
    }
}
