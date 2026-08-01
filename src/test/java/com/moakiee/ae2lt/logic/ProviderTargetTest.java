package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

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
        int hashBeforeMutation = address.hashCode();

        mutable.set(20, 30, 40);

        assertEquals(new BlockPos(4, 5, 6), address.pos());
        assertEquals(hashBeforeMutation, address.hashCode());
        assertEquals(
                new ProviderTarget(
                        Level.OVERWORLD,
                        new BlockPos(4, 5, 6),
                        Direction.UP),
                address);
    }

    @Test
    void wirelessConnectionTagRoundTripKeepsAddress() {
        var original = new WirelessConnection(
                Level.NETHER, new BlockPos(-7, 64, 12), Direction.WEST);

        var restored = WirelessConnection.fromTag(original.toTag());

        assertEquals(original, restored);
        assertEquals(Level.NETHER, restored.dimension());
        assertEquals(new BlockPos(-7, 64, 12), restored.pos());
        assertEquals(Direction.WEST, restored.boundFace());
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
    void patternInternDefersLiveBucketEnumerationUntilCompaction() {
        var table = new WirelessOverflowPatternTable();
        var pattern = new EmptyPattern();
        var enumerations = new AtomicInteger();

        table.intern(pattern, () -> {
            enumerations.incrementAndGet();
            return List.of();
        });
        table.intern(pattern, () -> {
            enumerations.incrementAndGet();
            return List.of();
        });

        assertEquals(0, enumerations.get());
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

    private static final class EmptyPattern implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("third-party equality must not run");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("third-party hashCode must not run");
        }
    }
}
