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
    void patternInternUsesEqualityOncePerEquivalentExecutionObject() {
        var table = new WirelessOverflowPatternTable();
        var equalityCalls = new AtomicInteger();
        var throwOnEquality = new boolean[1];
        var first = new LogicalPattern(
                "same", equalityCalls, throwOnEquality);
        var converted = new LogicalPattern(
                "same", equalityCalls, throwOnEquality);

        short firstId = table.intern(first, List::of);
        short convertedId = table.intern(converted, List::of);

        assertEquals(firstId, convertedId);
        assertTrue(equalityCalls.get() > 0);

        int coldEqualityCalls = equalityCalls.get();
        throwOnEquality[0] = true;
        assertEquals(convertedId, table.intern(converted, List::of));
        assertEquals(coldEqualityCalls, equalityCalls.get());
    }

    @Test
    void fullAcceptanceUsesExponentialChunksWithoutExceedingRequest() {
        var chunks = new ArrayList<Integer>();
        var result = target.pushPattern(
                new EmptyPattern(),
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
                new EmptyPattern(),
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
                new EmptyPattern(),
                10L,
                true,
                () -> false,
                copies -> copies >= 2
                        ? ProviderTarget.BatchChunk.GLOBAL_ABORT
                        : new ProviderTarget.BatchChunk(copies, true, false));

        assertEquals(2L, result.ownedCopies());
        assertTrue(result.globalAbort());
    }

    @Test
    void nextCallStartsFromLastFullyInsertedChunk() {
        var pattern = new EmptyPattern();
        var initialChunks = new ArrayList<Integer>();

        var initial = target.pushPattern(
                pattern,
                8L,
                true,
                () -> false,
                copies -> {
                    initialChunks.add(copies);
                    return new ProviderTarget.BatchChunk(copies, true, false);
                });

        assertEquals(8L, initial.ownedCopies());
        assertEquals(List.of(1, 1, 2, 4), initialChunks);

        var reusedChunks = new ArrayList<Integer>();
        var reused = target.pushPattern(
                pattern,
                32L,
                true,
                () -> false,
                copies -> {
                    reusedChunks.add(copies);
                    return copies <= 4
                            ? new ProviderTarget.BatchChunk(copies, true, false)
                            : ProviderTarget.BatchChunk.REJECTED;
                });

        assertEquals(8L, reused.ownedCopies());
        assertEquals(List.of(4, 4, 8), reusedChunks);
    }

    @Test
    void rejectedStartingChunkBacksOffUntilFirstSuccess() {
        var pattern = new EmptyPattern();
        target.pushPattern(
                pattern,
                16L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));

        var recoveryChunks = new ArrayList<Integer>();
        var recovery = target.pushPattern(
                pattern,
                32L,
                true,
                () -> false,
                copies -> {
                    recoveryChunks.add(copies);
                    return copies <= 2
                            ? new ProviderTarget.BatchChunk(copies, true, false)
                            : ProviderTarget.BatchChunk.REJECTED;
                });

        assertEquals(2L, recovery.ownedCopies());
        assertEquals(List.of(8, 4, 2), recoveryChunks);

        var nextChunks = new ArrayList<Integer>();
        target.pushPattern(
                pattern,
                8L,
                true,
                () -> false,
                copies -> {
                    nextChunks.add(copies);
                    return copies <= 2
                            ? new ProviderTarget.BatchChunk(copies, true, false)
                            : ProviderTarget.BatchChunk.REJECTED;
                });

        assertEquals(List.of(2, 2, 4), nextChunks);
    }

    @Test
    void overflowKeepsLastChunkThatAvoidedSendList() {
        var pattern = new EmptyPattern();
        target.pushPattern(
                pattern,
                8L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));

        var overflowChunks = new ArrayList<Integer>();
        var overflow = target.pushPattern(
                pattern,
                32L,
                true,
                () -> false,
                copies -> {
                    overflowChunks.add(copies);
                    return overflowChunks.size() == 1
                            ? new ProviderTarget.BatchChunk(copies, true, false)
                            : new ProviderTarget.BatchChunk(copies, false, false);
                });

        assertEquals(8L, overflow.ownedCopies());
        assertEquals(List.of(4, 4), overflowChunks);

        var nextChunks = new ArrayList<Integer>();
        target.pushPattern(
                pattern,
                4L,
                true,
                () -> false,
                copies -> {
                    nextChunks.add(copies);
                    return new ProviderTarget.BatchChunk(copies, true, false);
                });

        assertEquals(List.of(4), nextChunks);
    }

    @Test
    void overflowOnStartingChunkDegradesNextAttempt() {
        var pattern = new EmptyPattern();
        target.pushPattern(
                pattern,
                8L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));

        target.pushPattern(
                pattern,
                4L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, false, false));

        var nextChunks = new ArrayList<Integer>();
        target.pushPattern(
                pattern,
                2L,
                true,
                () -> false,
                copies -> {
                    nextChunks.add(copies);
                    return new ProviderTarget.BatchChunk(copies, true, false);
                });

        assertEquals(List.of(2), nextChunks);
    }

    @Test
    void batchHistoryIsIsolatedByCanonicalPatternIdentity() {
        var firstPattern = new EmptyPattern();
        var secondPattern = new EmptyPattern();
        target.pushPattern(
                firstPattern,
                8L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));

        var chunks = new ArrayList<Integer>();
        target.pushPattern(
                secondPattern,
                2L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(copies, true, false);
                });

        assertEquals(List.of(1, 1), chunks);
    }

    @Test
    void requestLimitedTailDoesNotShrinkRememberedChunk() {
        var pattern = new EmptyPattern();
        target.pushPattern(
                pattern,
                8L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));

        target.pushPattern(
                pattern,
                1L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));

        var chunks = new ArrayList<Integer>();
        target.pushPattern(
                pattern,
                4L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(copies, true, false);
                });

        assertEquals(List.of(4), chunks);
    }

    @Test
    void globalAbortDoesNotChangeRememberedChunk() {
        var pattern = new EmptyPattern();
        target.pushPattern(
                pattern,
                8L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(copies, true, false));

        target.pushPattern(
                pattern,
                32L,
                true,
                () -> false,
                ignored -> ProviderTarget.BatchChunk.GLOBAL_ABORT);

        var chunks = new ArrayList<Integer>();
        target.pushPattern(
                pattern,
                4L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(copies, true, false);
                });

        assertEquals(List.of(4), chunks);
    }

    @Test
    void persistedNormalHistoryRestoresTheProvenStartingChunk() {
        var originalPattern = new EmptyPattern();
        target.pushPattern(
                originalPattern,
                8L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var snapshot = target.adaptiveBatchSnapshots()
                .get(originalPattern);
        assertTrue(target.consumeAdaptiveBatchHistoryDirty());

        var restoredTarget = new ProviderTarget(
                Level.OVERWORLD, BlockPos.ZERO, Direction.NORTH);
        var restoredPattern = new EmptyPattern();
        restoredTarget.restoreAdaptiveBatchSnapshot(
                restoredPattern, snapshot);

        var chunks = new ArrayList<Integer>();
        restoredTarget.pushPattern(
                restoredPattern,
                16L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(List.of(4, 4, 8), chunks);
    }

    @Test
    void wirelessGrowthProofRepeatsOnlyTheBaselineWithinEachTick() {
        var pattern = new EmptyPattern();
        var chunks = new ArrayList<Integer>();
        var firstTick = target.pushPatternStep(
                pattern,
                1_000L,
                0L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(1_000L, firstTick.ownedCopies());
        assertEquals(
                List.of(1, 1, 2, 4, 8, 16, 32, 64, 128, 256, 488),
                chunks);

        chunks.clear();
        var secondTick = target.pushPatternStep(
                pattern,
                1_024L,
                1L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(1_024L, secondTick.ownedCopies());
        assertEquals(List.of(256, 256, 512), chunks);
    }

    @Test
    void persistedWirelessHistoryStillRequiresTwoFreshBaselines() {
        var originalPattern = new EmptyPattern();
        target.pushPatternStep(
                originalPattern,
                16L,
                20L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var snapshot = target.adaptiveBatchSnapshots()
                .get(originalPattern);
        var encoded = AdaptiveBatchStatePersistence.writeSnapshot(snapshot);
        var decoded = AdaptiveBatchStatePersistence.readSnapshot(encoded);
        assertEquals(snapshot, decoded);

        var restoredTarget = new ProviderTarget(
                Level.OVERWORLD, BlockPos.ZERO, Direction.NORTH);
        var restoredPattern = new EmptyPattern();
        restoredTarget.restoreAdaptiveBatchSnapshot(
                restoredPattern, decoded);

        var chunks = new ArrayList<Integer>();
        restoredTarget.pushPatternStep(
                restoredPattern,
                32L,
                21L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(List.of(8, 8, 16), chunks);
    }

    @Test
    void corruptedPersistedBatchHistoryFailsClosed() {
        var tag = new net.minecraft.nbt.CompoundTag();
        var step = new net.minecraft.nbt.CompoundTag();
        step.putInt("next_chunk", 0);
        tag.put("step", step);

        assertEquals(
                null,
                AdaptiveBatchStatePersistence.readSnapshot(tag));
    }

    @Test
    void wirelessSecondBaselineFailureRestartsTheProofNextTick() {
        var pattern = new EmptyPattern();
        var chunks = new ArrayList<Integer>();
        int[] firstTickCalls = {0};

        var firstTick = target.pushPatternStep(
                pattern,
                100L,
                0L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return firstTickCalls[0]++ == 0
                            ? new ProviderTarget.BatchChunk(
                                    copies, true, false)
                            : ProviderTarget.BatchChunk.REJECTED;
                });
        int nextTickStart = chunks.size();
        var secondTick = target.pushPatternStep(
                pattern,
                100L,
                1L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });
        assertEquals(1L, firstTick.ownedCopies());
        assertEquals(
                ProviderTarget.BaselineStatus.PREFIX_COMPLETE,
                firstTick.baselineStatus());
        assertEquals(100L, secondTick.ownedCopies());
        assertEquals(
                List.of(1, 1, 2, 4, 8, 16, 32, 36),
                chunks.subList(nextTickStart, chunks.size()));
    }

    @Test
    void wirelessBatchStepCapsGrowthAtProvenChunk() {
        var pattern = new EmptyPattern();
        target.pushPatternStep(
                pattern,
                16L,
                0L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var chunks = new ArrayList<Integer>();
        var rejected = target.pushPatternStep(
                pattern,
                1_000L,
                1L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return copies < 16
                            ? new ProviderTarget.BatchChunk(
                                    copies, true, false)
                            : ProviderTarget.BatchChunk.REJECTED;
                });
        assertEquals(16L, rejected.ownedCopies());
        assertEquals(List.of(8, 8, 16), chunks);

        var recovered = target.pushPatternStep(
                pattern,
                1_000L,
                2L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });
        assertEquals(16L, recovered.ownedCopies());

        target.pushPatternStep(
                pattern,
                1_000L,
                3L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });
        assertEquals(List.of(8, 8, 16, 8, 8, 8, 8), chunks);
    }

    @Test
    void wirelessBatchStepBacksOffWhenProvenChunkNoLongerFits() {
        var pattern = new EmptyPattern();
        target.pushPatternStep(
                pattern,
                16L,
                0L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var chunks = new ArrayList<Integer>();
        target.pushPatternStep(
                pattern,
                1_000L,
                1L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return ProviderTarget.BatchChunk.REJECTED;
                });
        target.pushPatternStep(
                pattern,
                1_000L,
                2L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });
        target.pushPatternStep(
                pattern,
                1_000L,
                3L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return ProviderTarget.BatchChunk.REJECTED;
                });
        target.pushPatternStep(
                pattern,
                1_000L,
                4L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(List.of(8, 4, 8, 4), chunks);
    }

    @Test
    void wirelessExploratoryRejectionKeepsProvenChunk() {
        var pattern = new EmptyPattern();
        target.pushPatternStep(
                pattern,
                16L,
                0L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var chunks = new ArrayList<Integer>();
        target.pushPatternStep(
                pattern,
                1_000L,
                1L,
                true,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return ProviderTarget.BatchChunk.REJECTED;
                });
        target.pushPatternStep(
                pattern,
                16L,
                2L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(List.of(8, 8, 8), chunks);
    }

    @Test
    void wirelessRejectedRequestLimitedTailKeepsRememberedChunk() {
        var pattern = new EmptyPattern();
        target.pushPatternStep(
                pattern,
                16L,
                0L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var chunks = new ArrayList<Integer>();
        target.pushPatternStep(
                pattern,
                3L,
                1L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return ProviderTarget.BatchChunk.REJECTED;
                });
        target.pushPatternStep(
                pattern,
                16L,
                2L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(List.of(3, 8, 8), chunks);
    }

    @Test
    void wirelessSuccessfulRequestLimitedTailKeepsRememberedChunk() {
        var pattern = new EmptyPattern();
        target.pushPatternStep(
                pattern,
                16L,
                0L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var chunks = new ArrayList<Integer>();
        target.pushPatternStep(
                pattern,
                3L,
                1L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });
        target.pushPatternStep(
                pattern,
                16L,
                2L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(List.of(3, 8, 8), chunks);
    }

    @Test
    void wirelessBatchStepHistoryExpiresAfterOneHundredIdleTicks() {
        var pattern = new EmptyPattern();
        target.pushPatternStep(
                pattern,
                16L,
                0L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var chunks = new ArrayList<Integer>();
        target.pushPatternStep(
                pattern,
                100L,
                100L,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(List.of(1, 1, 2, 4, 8, 16, 32, 36), chunks);
    }

    @Test
    void wirelessBatchStepKeepsHistoryAtTheHundredTickFallbackBoundary() {
        var pattern = new EmptyPattern();
        target.pushPatternStep(
                pattern,
                16L,
                0L,
                true,
                () -> false,
                copies -> new ProviderTarget.BatchChunk(
                        copies, true, false));

        var chunks = new ArrayList<Integer>();
        target.pushPatternStep(
                pattern,
                100L,
                100L,
                true,
                true,
                () -> false,
                copies -> {
                    chunks.add(copies);
                    return new ProviderTarget.BatchChunk(
                            copies, true, false);
                });

        assertEquals(List.of(8, 8, 16, 32, 36), chunks);
    }

    @Test
    void preDispatchReturnIsClaimedOncePerTargetAndTick() {
        var otherTarget = new ProviderTarget(
                Level.OVERWORLD, BlockPos.ZERO.relative(Direction.EAST), Direction.WEST);

        assertTrue(target.claimOutputReturnScan(42L));
        assertFalse(target.claimOutputReturnScan(42L));
        assertTrue(otherTarget.claimOutputReturnScan(42L));
        assertTrue(target.claimOutputReturnScan(43L));

        target.clearRuntimeState();

        assertTrue(target.claimOutputReturnScan(43L));
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
            return 31;
        }
    }

    private static final class LogicalPattern implements IPatternDetails {
        private final String id;
        private final AtomicInteger equalityCalls;
        private final boolean[] throwOnEquality;

        private LogicalPattern(
                String id,
                AtomicInteger equalityCalls,
                boolean[] throwOnEquality) {
            this.id = id;
            this.equalityCalls = equalityCalls;
            this.throwOnEquality = throwOnEquality;
        }

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
            equalityCalls.incrementAndGet();
            if (throwOnEquality[0]) {
                throw new AssertionError("identity cache must bypass equality");
            }
            return other instanceof LogicalPattern pattern && id.equals(pattern.id);
        }

        @Override
        public int hashCode() {
            return 31;
        }
    }
}
