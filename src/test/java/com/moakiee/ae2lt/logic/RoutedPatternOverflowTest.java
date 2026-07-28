package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

class RoutedPatternOverflowTest {

    @Test
    void blockedFaceDoesNotRerouteOrBlockOtherFaces() {
        var northKey = new TestKey("north");
        var southKey = new TestKey("south");
        var overflow = RoutedPatternOverflow.routed(List.of(
                new RoutedPatternOverflow.Entry(
                        Direction.NORTH, new GenericStack(northKey, 10L)),
                new RoutedPatternOverflow.Entry(
                        Direction.SOUTH, new GenericStack(southKey, 10L))));
        var visited = new ArrayList<Direction>();

        boolean progressed = overflow.flush(
                Direction.DOWN,
                (face, what, amount) -> {
                    visited.add(face);
                    return face == Direction.SOUTH ? 4L : 0L;
                });

        assertTrue(progressed);
        assertEquals(List.of(Direction.NORTH, Direction.SOUTH), visited);
        assertEquals(2, overflow.snapshot().size());
        assertEquals(Direction.NORTH, overflow.snapshot().get(0).face());
        assertEquals(10L, overflow.snapshot().get(0).stack().amount());
        assertEquals(Direction.SOUTH, overflow.snapshot().get(1).face());
        assertEquals(6L, overflow.snapshot().get(1).stack().amount());
    }

    @Test
    void unroutedEntriesUseOnlyTheDispatchDefaultFace() {
        var overflow = RoutedPatternOverflow.unrouted(List.of(
                new GenericStack(new TestKey("first"), 3L),
                new GenericStack(new TestKey("second"), 2L)));
        var visited = new ArrayList<Direction>();

        boolean progressed = overflow.flush(
                Direction.WEST,
                (face, what, amount) -> {
                    visited.add(face);
                    return amount;
                });

        assertTrue(progressed);
        assertTrue(overflow.isEmpty());
        assertFalse(visited.isEmpty());
        assertEquals(List.of(Direction.WEST, Direction.WEST), visited);
    }

    @Test
    void unroutedOverflowRetainsTheLegacyMutableAdapterContract() {
        var first = new TestKey("first");
        var second = new TestKey("second");
        var overflow = RoutedPatternOverflow.unrouted(List.of(
                new GenericStack(first, 3L),
                new GenericStack(second, 5L)));

        boolean progressed = overflow.flushUnrouted(stacks -> {
            stacks.remove(0);
            stacks.set(0, new GenericStack(second, 2L));
        });

        assertTrue(progressed);
        assertFalse(overflow.hasExplicitFaces());
        assertEquals(
                List.of(new GenericStack(second, 2L)),
                overflow.snapshot().stream().map(RoutedPatternOverflow.Entry::stack).toList());
    }

    @Test
    void invalidInsertionAmountCannotCorruptTheOverflowLedger() {
        var key = new TestKey("invalid");
        var overflow = RoutedPatternOverflow.routed(List.of(
                new RoutedPatternOverflow.Entry(
                        Direction.UP, new GenericStack(key, 4L))));

        assertThrows(
                IllegalStateException.class,
                () -> overflow.flush(
                        Direction.DOWN,
                        (face, what, amount) -> amount + 1L));
        assertEquals(
                List.of(new GenericStack(key, 4L)),
                overflow.snapshot().stream()
                        .map(RoutedPatternOverflow.Entry::stack)
                        .toList());
    }

    @Test
    void routedOverflowCannotAccidentallyUseTheUnroutedAdapterPath() {
        var key = new TestKey("routed");
        var overflow = RoutedPatternOverflow.routed(List.of(
                new RoutedPatternOverflow.Entry(
                        Direction.EAST, new GenericStack(key, 2L))));

        assertThrows(
                IllegalStateException.class,
                () -> overflow.flushUnrouted(List::clear));
        assertEquals(Direction.EAST, overflow.snapshot().getFirst().face());
        assertEquals(2L, overflow.snapshot().getFirst().stack().amount());
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
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
            return obj instanceof TestKey other && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(
                    ResourceLocation.fromNamespaceAndPath("ae2lt_test", "key"),
                    TestKey.class,
                    Component.literal("test key"));
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
