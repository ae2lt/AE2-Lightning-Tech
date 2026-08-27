package com.moakiee.ae2lt.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.thunderbolt.core.crafting.loop.ISeedPreservingCraftingTask;
import com.moakiee.thunderbolt.core.crafting.loop.CraftingTaskPersistenceDefinition;
import com.moakiee.thunderbolt.core.crafting.pattern.IProviderLookupPattern;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;

import org.junit.jupiter.api.Test;

class ExecuteLoopPatternEqualityTest {
    private static final AEKey SEED = new TestKey("seed");
    private static final UUID GROUP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CONSUMER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void equalExecutionStateHasEqualHashCode() {
        var delegate = new FakeSeedPattern("crafting_table", GROUP);
        var left = pattern(delegate, CONSUMER, 1, 2, 3, 4);
        var right = pattern(delegate, CONSUMER, 1, 2, 3, 4);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void everySeedAccountingFieldParticipatesInEquality() {
        var delegate = new FakeSeedPattern("crafting_table", GROUP);
        var baseline = pattern(delegate, CONSUMER, 1, 2, 3, 4);

        assertNotEquals(baseline, pattern(
                delegate, UUID.fromString("00000000-0000-0000-0000-000000000004"),
                1, 2, 3, 4));
        assertNotEquals(baseline, pattern(delegate, CONSUMER, 9, 2, 3, 4));
        assertNotEquals(baseline, pattern(delegate, CONSUMER, 1, 9, 3, 4));
        assertNotEquals(baseline, pattern(delegate, CONSUMER, 1, 2, 9, 4));
        assertNotEquals(baseline, pattern(delegate, CONSUMER, 1, 2, 3, 9));
        assertNotEquals(baseline, pattern(
                new FakeSeedPattern("furnace", GROUP),
                CONSUMER, 1, 2, 3, 4));
    }

    @Test
    void batchExecutionKeepsClosedLoopSemanticsWhileProviderLookupReachesTheRecipe() {
        var provider = new FakeSeedPattern("provider", GROUP);
        var execution = new FakeProviderLookupSeedPattern("execution", GROUP, provider);
        var task = pattern(execution, CONSUMER, 1, 2, 3, 4);

        assertSame(execution, CraftingPatternDelegates.forBatchExecution(task));
        assertSame(provider, CraftingPatternDelegates.forProviderLookup(task));
    }

    private static ExecuteLoopPattern pattern(
            IPatternDetails delegate,
            UUID consumer,
            long initial,
            long input,
            long output,
            long sharedOutput) {
        return new ExecuteLoopPattern(
                delegate,
                consumer,
                counter(initial),
                counter(input),
                Map.of(RECIPIENT, counter(output)),
                Map.of(RECIPIENT, counter(sharedOutput)));
    }

    private static KeyCounter counter(long amount) {
        var result = new KeyCounter();
        result.add(SEED, amount);
        return result;
    }

    private record FakeSeedPattern(String identity, UUID group)
            implements IPatternDetails, ISeedPreservingCraftingTask,
            CraftingTaskPersistenceDefinition {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return new IInput[0]; }
        @Override public List<GenericStack> getOutputs() { return List.of(); }
        @Override public UUID reusableSeedGroupId() { return group; }
        @Override public Set<AEKey> reusableSeedCycleKeys() { return Set.of(SEED); }
        @Override public boolean hasSingleSeedInputPerMember() { return true; }
        @Override public AEItemKey craftingTaskPersistenceDefinition() { return null; }
    }

    private record FakeProviderLookupSeedPattern(
            String identity, UUID group, IPatternDetails provider)
            implements IPatternDetails, ISeedPreservingCraftingTask,
            CraftingTaskPersistenceDefinition, IProviderLookupPattern {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return new IInput[0]; }
        @Override public List<GenericStack> getOutputs() { return List.of(); }
        @Override public UUID reusableSeedGroupId() { return group; }
        @Override public Set<AEKey> reusableSeedCycleKeys() { return Set.of(SEED); }
        @Override public boolean hasSingleSeedInputPerMember() { return true; }
        @Override public AEItemKey craftingTaskPersistenceDefinition() { return null; }
        @Override public IPatternDetails providerLookupPattern() { return provider; }
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
            return tag;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("thunderbolt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal(id); }
        @Override public void addDrops(
                long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id);
        }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("thunderbolt_test", "execute_loop_key"),
                    TestKey.class, Component.literal("execute loop key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
