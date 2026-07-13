package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternMultiplier;
import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ProcessingPatternMultiplierTest {
    @Test
    void multiplyPreservesEmptySlotsAndRatios() {
        var result = ProcessingPatternMultiplier.scaled(Arrays.asList(
                new GenericStack(new TestKey("iron"), 2),
                null,
                new GenericStack(new TestKey("gold"), 5)), 5);

        assertEquals(10L, result.get(0).amount());
        assertNull(result.get(1));
        assertEquals(25L, result.get(2).amount());
    }

    @Test
    void divisionIsAtomicAndRejectsBrokenRatios() {
        var stacks = Arrays.asList(
                new GenericStack(new TestKey("iron"), 10),
                new GenericStack(new TestKey("gold"), 3));

        assertNull(ProcessingPatternMultiplier.scaled(stacks, -2));
        assertEquals(5L, ProcessingPatternMultiplier.scaled(Arrays.asList(
                new GenericStack(new TestKey("iron"), 10)), -2).getFirst().amount());
    }

    @Test
    void multiplicationRejectsOverflow() {
        assertNull(ProcessingPatternMultiplier.scaled(Arrays.asList(
                new GenericStack(new TestKey("iron"), Long.MAX_VALUE)), 2));
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;
        private TestKey(String id) { this.id = id; }
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() { return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id); }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal(id); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object other) { return other instanceof TestKey key && id.equals(key.id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() { super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "multiplier"), TestKey.class, Component.literal("test")); }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
