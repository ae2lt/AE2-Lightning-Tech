package com.moakiee.thunderbolt.ae2.timewheel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private static KeyCounter counter(AEKey key, long amount) {
        var result = new KeyCounter();
        result.add(key, amount);
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
