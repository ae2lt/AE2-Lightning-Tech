package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.mojang.serialization.MapCodec;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;

/** Regression coverage for the production persistent-buffer and retry paths. */
class OverloadedInterfaceBufferPathTest {
    private static final TestKeyType ITEMS = new TestKeyType("items");
    private static final TestKeyType FLUIDS = new TestKeyType("fluids");

    @Test
    void rejectedPrefixDoesNotLockAnUntouchedTailOfTheSameKeyType() {
        var buffer = new LinkedHashMap<AEKey, Long>();
        TestKey tail = null;
        var rejected = new HashSet<AEKey>();
        for (int i = 0; i < 16_385; i++) {
            var key = new TestKey(ITEMS, "item-" + i);
            buffer.put(key, 1L);
            if (i < 16_384) {
                rejected.add(key);
            } else {
                tail = key;
            }
        }

        var locks = new IdentityHashMap<AEKeyType, Long>();
        var flushState = new OverloadedInterfaceBlockEntity.ImportBufferFlushState();
        flushState.rebuildFrom(buffer);
        var storage = new ProgrammableStorage(key -> rejected.contains(key));
        var first = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                flushState,
                storage, IActionSource.empty(), 0, () -> {});

        assertEquals(16_384, storage.attempts);
        assertFalse(locks.containsKey(ITEMS),
                "a bounded prefix cannot prove that the whole type is rejected");
        assertEquals(16_385, buffer.size());

        var second = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, first.lastFlushTick(), first.flushLimited(),
                first.remainingKeys(), flushState,
                storage, IActionSource.empty(), 5, () -> {});

        assertTrue(storage.inserted.getOrDefault(tail, 0L) > 0,
                "the accepted tail key must be reached after the prefix rotates");
        assertNull(buffer.get(tail));
        assertFalse(locks.containsKey(ITEMS));
        assertEquals(16_384, buffer.size());
        assertEquals(1L, storage.totalInserted(), "every accepted key is accounted once");
        assertEquals(16_384L, buffer.values().stream().mapToLong(Long::longValue).sum());
        assertEquals(second.lastFlushTick(), 5L);
    }

    @Test
    void partialInsertAndMixedTypesKeepPerKeyOwnershipAndOnlyLockFullyTestedTypes() {
        var aRejected = new TestKey(ITEMS, "reject");
        var aPartial = new TestKey(ITEMS, "partial");
        var bAccepted = new TestKey(FLUIDS, "accepted");
        var buffer = new LinkedHashMap<AEKey, Long>();
        buffer.put(aRejected, 4L);
        buffer.put(aPartial, 10L);
        buffer.put(bAccepted, 7L);

        var capacities = Map.of(aPartial, 2L, bAccepted, 7L);
        var storage = new ProgrammableStorage(key -> key.equals(aRejected), capacities);
        var locks = new IdentityHashMap<AEKeyType, Long>();
        var flushState = new OverloadedInterfaceBlockEntity.ImportBufferFlushState();
        flushState.rebuildFrom(buffer);
        var result = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                flushState,
                storage, IActionSource.empty(), 0, () -> {});

        assertEquals(4L, buffer.get(aRejected));
        assertEquals(8L, buffer.get(aPartial));
        assertNull(buffer.get(bAccepted));
        assertFalse(locks.containsKey(ITEMS),
                "a partial insert proves that this type is not fully rejected");
        assertFalse(locks.containsKey(FLUIDS));
        assertEquals(9L, storage.totalInserted());
        assertTrue(result.changed());
    }

    @Test
    void stoppedHighCardinalityBacklogDrainsWithoutDroppingKeys() {
        int keyCount = 49_152;
        var buffer = new LinkedHashMap<AEKey, Long>(keyCount);
        for (int i = 0; i < keyCount; i++) {
            buffer.put(new TestKey(ITEMS, "drain-" + i), 1L);
        }

        var storage = new ProgrammableStorage(ignored -> false);
        var locks = new IdentityHashMap<AEKeyType, Long>();
        var flushState = new OverloadedInterfaceBlockEntity.ImportBufferFlushState();
        flushState.rebuildFrom(buffer);
        var first = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                flushState,
                storage, IActionSource.empty(), 0, () -> {});

        assertEquals(16_384, storage.attempts,
                "the initial flush must honor the configured first slice");
        assertEquals(16_384, first.visitedKeys(),
                "the rejected slice must not inspect the untouched tail");
        assertEquals(32_768, buffer.size());
        assertTrue(first.flushLimited());

        int attemptsAfterFirst = storage.attempts;
        var second = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, first.lastFlushTick(), first.flushLimited(),
                first.remainingKeys(), flushState,
                storage, IActionSource.empty(), 1, () -> {});

        assertEquals(32_768, storage.attempts - attemptsAfterFirst,
                "the current stopped-backlog path expands the next drain slice; "
                        + "keep this cost visible when evaluating a bounded replacement");
        assertEquals(32_768, second.visitedKeys(),
                "an all-accepting stopped backlog may still use the fast drain path");
        assertTrue(buffer.isEmpty());
        assertFalse(second.flushLimited());
        assertEquals(keyCount, storage.totalInserted());
        assertTrue(locks.isEmpty());
    }

    @Test
    void aRejectedSliceDoesNotScanTheWholeRemainingBacklog() {
        int keyCount = 49_152;
        var partial = new TestKey(ITEMS, "partial-first");
        var buffer = new LinkedHashMap<AEKey, Long>(keyCount);
        buffer.put(partial, 2L);
        for (int i = 1; i < keyCount; i++) {
            buffer.put(new TestKey(ITEMS, "rejected-" + i), 1L);
        }

        var capacities = Map.of(partial, 1L);
        var storage = new ProgrammableStorage(key -> !key.equals(partial), capacities);
        var locks = new IdentityHashMap<AEKeyType, Long>();
        var flushState = new OverloadedInterfaceBlockEntity.ImportBufferFlushState();
        flushState.rebuildFrom(buffer);

        var first = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                flushState, storage, IActionSource.empty(), 0, () -> {});
        assertEquals(16_384, first.visitedKeys());
        assertTrue(first.flushLimited(),
                "partial progress plus a rejected key must retain bounded continuation state");

        int attemptsAfterFirst = storage.attempts;
        var second = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, first.lastFlushTick(), first.flushLimited(),
                first.remainingKeys(), flushState,
                storage, IActionSource.empty(), 1, () -> {});
        assertEquals(16_384, second.visitedKeys(),
                "a rejected continuation must not rescan all 49,152 pending keys");
        assertEquals(16_384, storage.attempts - attemptsAfterFirst);
        assertEquals(49_152L,
                buffer.values().stream().mapToLong(Long::longValue).sum(),
                "partial ownership must remain in the buffer without duplication");
        assertFalse(locks.containsKey(ITEMS),
                "the partial key remains evidence against a type-wide rejection lock");
    }

    @Test
    void aRejectedTypeRecoversAndItsLockIsRemovedAfterARealSuccessfulFlush() {
        var key = new TestKey(ITEMS, "recovery");
        var buffer = new LinkedHashMap<AEKey, Long>();
        buffer.put(key, 6L);
        var locks = new IdentityHashMap<AEKeyType, Long>();
        var flushState = new OverloadedInterfaceBlockEntity.ImportBufferFlushState();
        flushState.rebuildFrom(buffer);
        var unavailable = new ProgrammableStorage(ignored -> true);

        var first = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                flushState,
                unavailable, IActionSource.empty(), 0, () -> {});
        assertEquals(20L, locks.get(ITEMS));
        assertEquals(6L, buffer.get(key));

        unavailable.rejection = ignored -> false;
        var second = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, first.lastFlushTick(), first.flushLimited(),
                first.remainingKeys(), flushState,
                unavailable, IActionSource.empty(), 5, () -> {});

        assertTrue(second.changed());
        assertFalse(locks.containsKey(ITEMS));
        assertTrue(buffer.isEmpty());
        assertEquals(6L, unavailable.totalInserted());
    }

    @Test
    void allRejectedHighCardinalityLocksAcrossSlicesWithContinuousInputAndRecovers() {
        int initialKeys = 16_385;
        var buffer = new LinkedHashMap<AEKey, Long>(initialKeys + 4);
        for (int i = 0; i < initialKeys; i++) {
            buffer.put(new TestKey(ITEMS, "rejecting-" + i), 1L);
        }

        var locks = new IdentityHashMap<AEKeyType, Long>();
        var flushState = new OverloadedInterfaceBlockEntity.ImportBufferFlushState();
        flushState.rebuildFrom(buffer);
        var storage = new ProgrammableStorage(ignored -> true);

        var result = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, Long.MIN_VALUE, false, 0,
                flushState, storage, IActionSource.empty(), 0, () -> {});
        assertEquals(16_384, result.visitedKeys());
        assertFalse(locks.containsKey(ITEMS));

        for (int round = 1; round <= 2; round++) {
            var newKey = new TestKey(ITEMS, "continuous-" + round);
            buffer.put(newKey, 1L);
            flushState.onBuffered(newKey, true);

            result = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                    buffer, locks, result.lastFlushTick(), result.flushLimited(),
                    result.remainingKeys(), flushState,
                    storage, IActionSource.empty(), round * 5L, () -> {});
            assertEquals(16_384, result.visitedKeys(),
                    "cross-slice rejection must remain bounded by the remote-attempt slice");
        }

        assertTrue(locks.containsKey(ITEMS),
                "a completed all-rejected pass must establish type backpressure");
        assertEquals(16_387, buffer.size());

        storage.rejection = ignored -> false;
        result = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, result.lastFlushTick(), result.flushLimited(),
                result.remainingKeys(), flushState,
                storage, IActionSource.empty(), 15, () -> {});
        assertFalse(locks.containsKey(ITEMS),
                "a real successful flush must release the type lock");
        assertTrue(result.visitedKeys() <= 16_384);

        result = OverloadedInterfaceBlockEntity.flushImportBufferEntries(
                buffer, locks, result.lastFlushTick(), result.flushLimited(),
                result.remainingKeys(), flushState,
                storage, IActionSource.empty(), 16, () -> {});
        assertTrue(buffer.isEmpty(), "recovery must drain the complete retained backlog");
        assertEquals(16_387L, storage.totalInserted());
    }

    @Test
    void programmableStorageKeepsInfiniteAndFiniteCapacitySemantics() {
        var infinite = new TestKey(ITEMS, "infinite");
        var finite = new TestKey(ITEMS, "finite");
        var storage = new ProgrammableStorage(ignored -> false, Map.of(finite, 5L));

        assertEquals(4L, storage.insert(infinite, 4L, Actionable.SIMULATE, IActionSource.empty()));
        assertEquals(0L, storage.totalInserted(), "SIMULATE must not record an insertion");
        assertEquals(Long.MAX_VALUE, storage.remainingCapacity(infinite));

        assertEquals(4L, storage.insert(infinite, 4L, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(6L, storage.insert(infinite, 6L, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(10L, storage.inserted.get(infinite));
        assertEquals(Long.MAX_VALUE, storage.remainingCapacity(infinite));

        assertEquals(5L, storage.insert(finite, 9L, Actionable.SIMULATE, IActionSource.empty()));
        assertEquals(5L, storage.remainingCapacity(finite),
                "SIMULATE must not consume finite capacity");
        assertEquals(5L, storage.insert(finite, 9L, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(0L, storage.remainingCapacity(finite));
        assertEquals(0L, storage.insert(finite, 1L, Actionable.MODULATE, IActionSource.empty()),
                "an exhausted finite key cannot return a negative accepted amount");
        assertEquals(15L, storage.totalInserted());
    }

    @Test
    void fastExportRetryIsNeverSelectedForNormalMode() {
        int threshold = OverloadedInterfaceBlockEntity.EXPORT_REJECT_FAST_RETRY_THRESHOLD;
        assertFalse(OverloadedInterfaceBlockEntity.shouldUseFastExportRejectRetry(
                OverloadedInterfaceBlockEntity.IOSpeedMode.NORMAL, threshold));
        assertFalse(OverloadedInterfaceBlockEntity.shouldUseFastExportRejectRetry(
                OverloadedInterfaceBlockEntity.IOSpeedMode.NORMAL, threshold + 10));
        assertFalse(OverloadedInterfaceBlockEntity.shouldUseFastExportRejectRetry(
                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, threshold - 1));
        assertTrue(OverloadedInterfaceBlockEntity.shouldUseFastExportRejectRetry(
                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, threshold));
    }

    private static final class ProgrammableStorage implements MEStorage {
        private Predicate<AEKey> rejection;
        private final Map<AEKey, Long> capacity;
        private final Map<AEKey, Long> inserted = new HashMap<>();
        private int attempts;

        private ProgrammableStorage(Predicate<AEKey> rejection) {
            this(rejection, Map.of());
        }

        private ProgrammableStorage(
                Predicate<AEKey> rejection, Map<? extends AEKey, Long> capacity) {
            this.rejection = rejection;
            this.capacity = new HashMap<>(capacity);
        }

        @Override
        public Component getDescription() {
            return Component.literal("programmable test storage");
        }

        @Override
        public long insert(
                AEKey key, long amount, Actionable mode, IActionSource source) {
            attempts++;
            if (rejection.test(key) || amount <= 0) return 0;
            Long remaining = capacity.get(key);
            long accepted = remaining == null
                    ? amount
                    : Math.min(amount, Math.max(0L, remaining));
            if (mode == Actionable.MODULATE && accepted > 0) {
                inserted.merge(key, accepted, Long::sum);
                if (remaining != null) {
                    capacity.put(key, remaining - accepted);
                }
            }
            return accepted;
        }

        private long totalInserted() {
            return inserted.values().stream().mapToLong(Long::longValue).sum();
        }

        private long remainingCapacity(AEKey key) {
            return capacity.getOrDefault(key, Long.MAX_VALUE);
        }
    }

    private static final class TestKey extends AEKey {
        private final AEKeyType type;
        private final String id;

        private TestKey(AEKeyType type, String id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return type;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean hasComponents() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestKey other
                    && type == other.type
                    && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(type) + id.hashCode();
        }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType(String id) {
            super(
                    ResourceLocation.fromNamespaceAndPath("ae2lt_test", id),
                    TestKey.class,
                    Component.literal(id));
        }

        @Override
        public MapCodec<? extends AEKey> codec() {
            return null;
        }

        @Override
        public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
            return null;
        }
    }
}
