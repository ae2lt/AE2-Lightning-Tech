package com.moakiee.thunderbolt.ae2.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import com.moakiee.thunderbolt.core.planner.Sat;

class LoopSeedLedgerBookEdgeCaseTest {
    @Test
    void dispatchPrecreditsOutputButStillProtectsItFromAnotherDedicatedPool() {
        var a = key("a", "");
        var b = key("b", "");
        var firstGroup = UUID.randomUUID();
        var secondGroup = UUID.randomUUID();
        var firstPool = LoopSeedLedgerBook.poolFor(firstGroup, false);
        var secondPool = LoopSeedLedgerBook.poolFor(secondGroup, false);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(
                Map.of(firstGroup, Map.of(a, 1L), secondGroup, Map.of(a, 1L)),
                Set.of(firstGroup, secondGroup));

        ledgers.recordDispatch(firstPool, counter(a, 1L), counter(b, 1L), 1L);

        assertEquals(0L, ledgers.balance(firstPool, a));
        assertEquals(1L, ledgers.balance(firstPool, b),
                "successful dispatch must credit its expected seed output immediately");
        assertEquals(1L, ledgers.totalReserved(a));
        assertEquals(1L, ledgers.totalReserved(b));

        var firstTaskView = ledgers.reservationView(firstPool, b::equals);
        assertNull(firstTaskView.get(b), "a pool may use its own precredited output once physical");
        var secondTaskView = ledgers.reservationView(secondPool, ignored -> false);
        assertEquals(1L, secondTaskView.get(b),
                "other dedicated pools must not borrow that precredited output");
    }

    @Test
    void sameKeyInputAndOutputCollapseToOneStableSeedBalance() {
        var seed = key("stable_seed", "");
        var group = UUID.randomUUID();
        var pool = LoopSeedLedgerBook.poolFor(group, true);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(Map.of(group, Map.of(seed, 1L)), Set.of());

        ledgers.recordDispatch(pool, counter(seed, 1L), counter(seed, 1L), Long.MAX_VALUE);

        assertEquals(1L, ledgers.balance(pool, seed));
        assertEquals(Map.of(seed, 1L), ledgers.positiveSnapshot());
    }

    @Test
    void fuzzyOverloadReturnRekeysTheFinalRecoveryQuotaToTheActualVariant() {
        var expected = key("tool", "expected_damage");
        var actual = key("tool", "returned_damage");
        var group = UUID.randomUUID();
        var pool = LoopSeedLedgerBook.poolFor(group, true);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(Map.of(group, Map.of(expected, 1L)), Set.of());

        ledgers.recordDispatch(pool, counter(expected, 1L), counter(expected, 1L), 1L);
        ledgers.rekey(pool, expected, actual, 1L);

        assertEquals(0L, ledgers.balance(pool, expected));
        assertEquals(1L, ledgers.balance(pool, actual));
        assertEquals(Map.of(actual, 1L), ledgers.positiveSnapshot());
        assertFalse(ledgers.positiveSnapshot().containsKey(expected));
    }

    @Test
    void sharedSingleSeedGroupsUseOneMaximumPhysicalReserve() {
        var seed = key("shared_seed", "");
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var ledgers = new LoopSeedLedgerBook();

        ledgers.initialize(
                Map.of(first, Map.of(seed, 1L), second, Map.of(seed, 3L)),
                Set.of());

        assertEquals(3L, ledgers.totalReserved(seed));
        assertEquals(3L, ledgers.balance(LoopSeedLedgerBook.SHARED_POOL, seed));
        assertEquals(1, ledgers.ledgerCount());
    }

    @Test
    void dedicatedReserveAggregateDoesNotWrapAtExtremeAmounts() {
        var seed = key("huge_seed", "");
        var groups = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        var requirements = new java.util.LinkedHashMap<UUID, Map<AEKey, Long>>();
        for (var group : groups) requirements.put(group, Map.of(seed, Sat.SAT));
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(requirements, Set.copyOf(groups));

        assertEquals(Long.MAX_VALUE, ledgers.totalReserved(seed));
        ledgers.recordDispatch(
                LoopSeedLedgerBook.poolFor(groups.getFirst(), false),
                counter(seed, Sat.SAT), new KeyCounter(), 1L);

        assertEquals(Sat.SAT * 4L, ledgers.totalReserved(seed));
        assertTrue(ledgers.totalReserved(seed) > 0L);
    }

    @Test
    void concreteHostVariantRekeysOnlyTheBorrowedInitialReserve() {
        var expected = key("tool", "planned");
        var actual = key("tool", "stored");
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var firstPool = LoopSeedLedgerBook.poolFor(first, false);
        var secondPool = LoopSeedLedgerBook.poolFor(second, false);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(
                Map.of(first, Map.of(expected, 1L), second, Map.of(expected, 1L)),
                Set.of(first, second));

        ledgers.rekeyAvailable(firstPool, expected, actual, 1L);

        assertEquals(1L, ledgers.totalReserved(expected));
        assertEquals(1L, ledgers.totalReserved(actual));
        assertEquals(1L, ledgers.balance(firstPool, actual));
        assertEquals(0L, ledgers.balance(firstPool, expected));
        assertEquals(1L, ledgers.balance(secondPool, expected));
        assertEquals(0L, ledgers.balance(secondPool, actual));
    }

    @Test
    void crossPatternMultiLoopReturnsItsDedicatedLedgerToTheInitialState() {
        var a = key("cross_a", "");
        var b = key("cross_b", "");
        var c = key("cross_c", "");
        var d = key("cross_d", "");
        var group = UUID.randomUUID();
        var pool = LoopSeedLedgerBook.poolFor(group, false);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(Map.of(group, Map.of(a, 1L, c, 1L)), Set.of(group));

        ledgers.recordDispatch(pool, counter(a, 1L), counter(b, 2L), 1L);
        ledgers.recordDispatch(pool, counter(c, 1L), counter(d, 1L), 1L);
        ledgers.recordDispatch(pool, counter(b, 1L), counter(a, 1L), 1L);
        ledgers.recordDispatch(
                pool, counter(Map.of(b, 1L, d, 1L)), counter(c, 1L), 1L);

        assertEquals(Map.of(a, 1L, c, 1L), ledgers.positiveSnapshot());
        assertEquals(0L, ledgers.balance(pool, b));
        assertEquals(0L, ledgers.balance(pool, d));
    }

    @Test
    void crossInputMultiLoopCanPrecreditBothDConsumersWithoutExtraSeed() {
        var a = key("cross_input_a", "");
        var b = key("cross_input_b", "");
        var c = key("cross_input_c", "");
        var d = key("cross_input_d", "");
        var group = UUID.randomUUID();
        var pool = LoopSeedLedgerBook.poolFor(group, false);
        var ledgers = new LoopSeedLedgerBook();
        ledgers.initialize(Map.of(group, Map.of(a, 1L, c, 1L)), Set.of(group));

        ledgers.recordDispatch(pool, counter(c, 1L), counter(d, 2L), 1L);
        ledgers.recordDispatch(
                pool, counter(Map.of(a, 1L, d, 1L)), counter(b, 2L), 1L);
        ledgers.recordDispatch(pool, counter(b, 1L), counter(a, 1L), 1L);
        ledgers.recordDispatch(
                pool, counter(Map.of(b, 1L, d, 1L)), counter(c, 1L), 1L);

        assertEquals(Map.of(a, 1L, c, 1L), ledgers.positiveSnapshot());
        assertEquals(0L, ledgers.balance(pool, b));
        assertEquals(0L, ledgers.balance(pool, d));
    }

    private static KeyCounter counter(AEKey key, long amount) {
        var result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static KeyCounter counter(Map<AEKey, Long> amounts) {
        var result = new KeyCounter();
        for (var entry : amounts.entrySet()) {
            result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static TestKey key(String id, String secondary) {
        return new TestKey(id, secondary);
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;
        private final String secondary;

        private TestKey(String id, String secondary) {
            this.id = id;
            this.secondary = secondary;
        }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() {
            return secondary.isEmpty() ? this : new TestKey(id, "");
        }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
            tag.putString("secondary", secondary);
            return tag;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() {
            return Component.literal(id + secondary);
        }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return !secondary.isEmpty(); }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other
                    && id.equals(other.id)
                    && secondary.equals(other.secondary);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, secondary); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "ledger_key"),
                    TestKey.class, Component.literal("ledger key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
