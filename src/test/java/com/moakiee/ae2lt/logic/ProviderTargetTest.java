package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;

class ProviderTargetTest {
    private final ProviderTarget target = new ProviderTarget(
            Level.OVERWORLD, BlockPos.ZERO, Direction.NORTH);

    @Test
    void addressIdentityIncludesFaceButMachineMatchDoesNot() {
        var north = new ProviderTarget(
                Level.OVERWORLD, new BlockPos(1, 2, 3), Direction.NORTH);
        var wirelessNorth = new WirelessConnection(
                Level.OVERWORLD, new BlockPos(1, 2, 3), Direction.NORTH);
        var south = new ProviderTarget(
                Level.OVERWORLD, new BlockPos(1, 2, 3), Direction.SOUTH);

        assertEquals(north, wirelessNorth);
        assertEquals(north.hashCode(), wirelessNorth.hashCode());
        assertNotEquals(north, south);
        assertTrue(north.sameTarget(Level.OVERWORLD, south.pos()));
    }

    @Test
    void addressCopiesMutableBlockPosition() {
        var mutable = new BlockPos.MutableBlockPos(4, 5, 6);
        var address = new ProviderTarget(
                Level.OVERWORLD, mutable, Direction.UP);

        mutable.set(20, 30, 40);

        assertEquals(new BlockPos(4, 5, 6), address.pos());
    }

    @Test
    void overflowQueueReusesOrphanConnectionRuntime() {
        var orphan = new WirelessConnection(
                Level.OVERWORLD, new BlockPos(7, 8, 9), Direction.WEST);
        var readded = new WirelessConnection(
                Level.OVERWORLD, new BlockPos(7, 8, 9), Direction.WEST);
        var queue = new WirelessOverflowQueue();
        var bucket = WirelessOverflowQueue.Bucket.fallback(
                (short) 0, List.of());

        queue.restoreBucket(orphan, bucket, 0L);

        assertNotSame(orphan, readded);
        assertSame(orphan, queue.adopt(readded));
        assertSame(bucket, queue.get(readded));

        orphan.clearRuntimeState();

        assertSame(bucket, queue.get(readded));
    }

    @Test
    void fullAcceptanceUsesExponentialChunksWithoutExceedingRequest() {
        var chunks = new ArrayList<Integer>();
        var result = target.pushPattern(
                100L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(copies, true, false);
                });

        assertEquals(100L, result.ownedCopies());
        assertFalse(result.globalAbort());
        assertEquals(List.of(1, 1, 2, 4, 8, 16, 32, 36), chunks);
    }

    @Test
    void rejectionEndsCurrentCallWithoutTailSearch() {
        var chunks = new ArrayList<Integer>();
        var result = target.pushPattern(
                100L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    if (copies > 8) {
                        return ProviderTarget.BatchChunk.REJECTED;
                    }
                    return new ProviderTarget.BatchChunk(copies, true, false);
                });

        assertEquals(16L, result.ownedCopies());
        assertEquals(List.of(1, 1, 2, 4, 8, 16), chunks);
    }

    @Test
    void globalAbortPreservesAlreadyOwnedCopies() {
        var result = target.pushPattern(
                10L,
                true,
                () -> false,
                copies -> copies >= 2
                        ? ProviderTarget.BatchChunk.GLOBAL_ABORT
                        : new ProviderTarget.BatchChunk(copies, true, false));

        assertEquals(2L, result.ownedCopies());
        assertTrue(result.globalAbort());
    }
}
