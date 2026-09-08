package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEKeyType;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ConnectionState;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ImportBackpressureWaiters;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IoDirection;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IoScheduledEntry;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.WirelessConnection;

class OverloadedInterfaceBackpressureWaitersTest {
    @Test
    void unlockingOneTypeResumesItOnceAndPreservesOtherSchedulingState() {
        var items = AEKeyType.items();
        var fluids = AEKeyType.fluids();
        var waiting = new ImportBackpressureWaiters();
        var itemEntry = entry(items, 1);
        var fluidEntry = entry(fluids, 1);
        var cd = itemEntry.state.cdFor(items, IoDirection.IMPORT);
        cd.onSuccess(10, IOSpeedMode.FAST);
        waiting.park(itemEntry);
        waiting.park(fluidEntry);
        var locks = new IdentityHashMap<AEKeyType, Long>();
        locks.put(items, 100L);
        locks.put(fluids, 200L);
        var due = new ArrayList<IoScheduledEntry>();

        waiting.resumeReady(locks, 20, due);
        assertTrue(due.isEmpty());
        locks.remove(items);
        waiting.resumeReady(locks, 21, due);
        assertEquals(List.of(itemEntry), due);
        assertEquals(11, cd.cooldownUntil(), "resuming must not reset the learned cooldown");

        due.clear();
        waiting.resumeReady(locks, 100, due);
        assertTrue(due.isEmpty(), "the resumed item entry must not survive in another queue");
        waiting.resumeReady(locks, 200, due);
        assertEquals(List.of(fluidEntry), due);
    }

    @Test
    void extendedRejectionUsesTheCurrentLockAndRebuildDropsOldWaiters() {
        var type = AEKeyType.items();
        var locks = new IdentityHashMap<AEKeyType, Long>();
        locks.put(type, 20L);
        var waiting = new ImportBackpressureWaiters();
        waiting.park(entry(type, 1));
        locks.put(type, 40L);
        var due = new ArrayList<IoScheduledEntry>();
        waiting.resumeReady(locks, 20, due);
        assertTrue(due.isEmpty());

        waiting.clear();
        var rebuilt = entry(type, 2);
        waiting.park(rebuilt);
        locks.clear();
        waiting.resumeReady(locks, 21, due);
        assertEquals(List.of(rebuilt), due);
    }

    @Test
    void blockedPollingScalesWithKeyTypesInsteadOfConnectionCount() {
        var type = AEKeyType.items();
        var waiting = new ImportBackpressureWaiters();
        for (int index = 0; index < 1024; index++) waiting.park(entry(type, 1));
        var locks = new CountingLocks();
        locks.put(type, 1000L);
        var due = new ArrayList<IoScheduledEntry>();
        for (long tick = 0; tick < 200; tick++) waiting.resumeReady(locks, tick, due);
        assertTrue(due.isEmpty());
        assertEquals(200, locks.reads, "1024 blocked sources must share one type-level check");
        locks.clear();
        waiting.resumeReady(locks, 200, due);
        assertEquals(1024, due.size(), "every source must resume after capacity returns");
        due.clear();
        waiting.resumeReady(locks, 201, due);
        assertTrue(due.isEmpty());
    }

    private static IoScheduledEntry entry(AEKeyType type, int generation) {
        return new IoScheduledEntry(new WirelessConnection(Level.OVERWORLD, BlockPos.ZERO, Direction.UP),
                new ConnectionState(), type, IoDirection.IMPORT, generation);
    }

    private static final class CountingLocks extends IdentityHashMap<AEKeyType, Long> {
        int reads;

        @Override
        public Long getOrDefault(Object key, Long defaultValue) {
            reads++;
            return super.getOrDefault(key, defaultValue);
        }
    }
}
