package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;

/** Real AE2 facade / NeoForge handler calls; no simulated server MSPT claims. */
class OverloadedInterfaceStorageCostTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exportStockCalibrationReadsEachSlotOnceAndExcludesOutputOnlySlots() {
        var handler = new CountingHandler(64) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) { return slot < 32; }
        };
        fill(handler, 8);
        handler.reads = 0;
        var stock = new KeyCounter();
        OverloadedInterfaceBlockEntity.observeInsertableStock(handler,
                new OverloadedInterfaceBlockEntity.ImportSlotKeyCache(), stock);
        assertEquals(64, handler.reads);
        assertEquals(8, stock.size());
        for (var entry : stock) assertEquals(4 * 64, entry.getLongValue());
    }

    @Test
    void outputSlotBudgetBatchesWithoutCountingUnrelatedEmptySlots() {
        var cache = new OverloadedInterfaceBlockEntity.ImportSlotKeyCache();
        cache.prepareSlots(27);
        cache.prepareBudgets(27);
        int due = 0, stock = 0, visits = 0;
        for (int tick = 0; tick < 1000; tick++) {
            assertTrue(stock + 10 <= 64, "output buffer blocked production");
            stock += 10;
            if (tick < due) continue;
            cache.keyForSlot(0, new ItemStack(Items.STONE, stock));
            due = tick + cache.drained(0, tick, stock, stock, 64);
            stock = 0;
            if (tick >= 100) visits++;
        }
        assertEquals(300, visits);
        cache.keyForSlot(0, new ItemStack(Items.DIRT, 10));
        assertEquals(1, cache.drained(0, 1000, 10, 10, 64), "replacement key inherited a stale rate");
        cache.forgetDrain(0);
        assertEquals(1, cache.drained(0, 1020, 10, 10, 64), "a pause was treated as slow production");
    }

    @Test
    void wrapperRefreshesAreSpreadWithoutSkippingAnyInventoryTransfer() {
        var key = AEItemKey.of(Items.STONE);
        var states = new ArrayList<OverloadedInterfaceBlockEntity.ConnectionState>();
        var handlers = new ArrayList<ItemStackHandler>();
        var creations = new AtomicInteger();
        for (int i = 0; i < 512; i++) {
            var handler = new ItemStackHandler(1);
            var state = new OverloadedInterfaceBlockEntity.ConnectionState();
            state.storageStrategies = Map.of(key.getType(), (extractable, listener) -> {
                creations.incrementAndGet();
                return ExternalStorageFacade.of(handler);
            });
            handlers.add(handler);
            states.add(state);
        }
        int peak = 0, steadyCreations = 0;
        for (int tick = 0; tick < 80; tick++) {
            int before = creations.get();
            // Run the same periodic discovery slices as the block entity, before
            // due transfers. First discovery is immediate for all connections.
            if (tick > 0) {
                int start = tick % 20 * states.size() / 20;
                int end = (tick % 20 + 1) * states.size() / 20;
                for (int i = start; i < end; i++) states.get(i).storageWrappers = null;
            }
            for (int i = 0; i < states.size(); i++) {
                handlers.get(i).setStackInSlot(0, new ItemStack(Items.STONE, 64));
                var wrappers = states.get(i).refreshWrappers(tick);
                var wrapper = wrappers.get(key.getType());
                assertEquals(64, wrapper.extract(key, 64, Actionable.MODULATE, IActionSource.empty()));
                assertTrue(handlers.get(i).getStackInSlot(0).isEmpty());
            }
            if (tick >= 20) {
                int refreshed = creations.get() - before;
                peak = Math.max(peak, refreshed);
                steadyCreations += refreshed;
            }
        }
        assertEquals(3 * 512, steadyCreations);
        assertTrue(peak <= 26, "periodic wrapper creations bunched into a tick: " + peak);
        System.out.printf("interface-wrapper-cost targets=512 steadyTicks=60 creations=%d peak/tick=%d transferred=%d%n",
                steadyCreations, peak, 512L * 80 * 64);
    }

    @Test
    void absentWrappersAreCachedAndRediscoveredWithinTwentyTicks() {
        var key = AEItemKey.of(Items.STONE);
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        var attempts = new AtomicInteger();
        var available = new java.util.concurrent.atomic.AtomicBoolean();
        state.storageStrategies = Map.of(key.getType(), (extractable, listener) -> {
            attempts.incrementAndGet();
            return available.get() ? ExternalStorageFacade.of(new ItemStackHandler(1)) : null;
        });
        assertTrue(state.refreshWrappers(0).isEmpty());
        available.set(true);
        for (int tick = 1; tick < 20; tick++) assertTrue(state.refreshWrappers(tick).isEmpty());
        assertEquals(1, attempts.get(), "an absent capability must not recreate wrappers every tick");
        assertFalse(state.refreshWrappers(20).isEmpty());
        assertEquals(2, attempts.get());
        state.refreshWrappers(5);
        assertEquals(3, attempts.get(), "clock rollback must refresh immediately");
        state.storageWrappers = null;
        state.refreshWrappers(6);
        assertEquals(4, attempts.get(), "physical invalidation must bypass the periodic deadline");
    }

    @Test
    void measuresActualSlotReadsForEnumeratedAndExactKeyImports() {
        for (int slots : new int[] {16, 64, 256}) {
            for (int kinds : new int[] {1, Math.min(64, slots)}) {
                var handler = new CountingHandler(slots);
                fill(handler, kinds);
                var wrapper = ExternalStorageFacade.of(handler);
                handler.reads = 0;
                var available = new KeyCounter();
                wrapper.getAvailableStacks(available);
                long enumerationReads = handler.reads;
                long moved = 0;
                for (var stack : available) {
                    moved += wrapper.extract(stack.getKey(), stack.getLongValue(),
                            Actionable.MODULATE, IActionSource.empty());
                }
                assertEquals(slots * 64L, moved);
                long fullReads = handler.reads;
                fill(handler, kinds);
                var key = AEItemKey.of(handler.getStackInSlot(0));
                handler.reads = 0;
                long exactAvailable = wrapper.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                assertEquals((slots / kinds) * 64L, exactAvailable);
                assertEquals(exactAvailable, wrapper.extract(key, exactAvailable, Actionable.MODULATE, IActionSource.empty()));
                System.out.printf("interface-storage-cost slots=%d kinds=%d enumerateReads=%d importAllReads=%d exactOneKeyReads=%d%n",
                        slots, kinds, enumerationReads, fullReads, handler.reads);
            }
        }
    }

    private static void fill(ItemStackHandler handler, int kinds) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var stack = new ItemStack(Items.STONE, 64);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("kind-" + slot % kinds));
            handler.setStackInSlot(slot, stack);
        }
    }

    private static class CountingHandler extends ItemStackHandler {
        long reads;
        CountingHandler(int slots) { super(slots); }
        @Override
        public ItemStack getStackInSlot(int slot) {
            reads++;
            return super.getStackInSlot(slot);
        }
    }
}
