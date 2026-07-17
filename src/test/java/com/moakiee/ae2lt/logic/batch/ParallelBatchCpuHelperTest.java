package com.moakiee.ae2lt.logic.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.thunderbolt.ae2.batch.BatchProviderFilterIterable;
import com.moakiee.thunderbolt.ae2.batch.BatchJobView;
import com.moakiee.thunderbolt.ae2.batch.BatchTaskHandle;
import com.moakiee.thunderbolt.ae2.batch.ParallelBatchCpuHelper;
import com.moakiee.thunderbolt.ae2.batch.SharedBatchInputPattern;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;

class ParallelBatchCpuHelperTest {
    @Test
    void bulkExtractClampsToTheSmallestWholeCopyCount() {
        var stick = key("stick");
        var cobble = key("cobble");
        var inv = inventory();
        inv.insert(stick, 10, Actionable.MODULATE);
        inv.insert(cobble, 7, Actionable.MODULATE);

        var pattern = pattern(input(stack(stick, 1), 1), input(stack(cobble, 2), 1));

        var result = ParallelBatchCpuHelper.bulkExtract(pattern, inv, 10);

        assertNotNull(result);
        assertEquals(3, result.actualCopies);
        assertEquals(3, result.scaledInputs[0].get(stick));
        assertEquals(6, result.scaledInputs[1].get(cobble));
        assertEquals(7, inv.extract(stick, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(1, inv.extract(cobble, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void bulkExtractAggregatesDemandWhenMultipleSlotsChooseTheSameKey() {
        var stick = key("stick");
        var inv = inventory();
        inv.insert(stick, 8, Actionable.MODULATE);
        var pattern = pattern(input(stack(stick, 1), 1), input(stack(stick, 2), 1));

        var result = ParallelBatchCpuHelper.bulkExtract(pattern, inv, 10);

        assertNotNull(result);
        assertEquals(2, result.actualCopies);
        assertEquals(2, result.scaledInputs[0].get(stick));
        assertEquals(4, result.scaledInputs[1].get(stick));
        assertEquals(2, inv.extract(stick, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void bulkExtractReturnsNullWhenAnySlotCannotSatisfyOneCopy() {
        var stick = key("stick");
        var cobble = key("cobble");
        var inv = inventory();
        inv.insert(stick, 10, Actionable.MODULATE);
        var pattern = pattern(input(stack(stick, 1), 1), input(stack(cobble, 1), 1));

        assertNull(ParallelBatchCpuHelper.bulkExtract(pattern, inv, 10));
        assertEquals(10, inv.extract(stick, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void reinjectRestoresLeftoverCopiesAndTrimsScaledInputs() {
        var stick = key("stick");
        var inv = inventory();
        inv.insert(stick, 10, Actionable.MODULATE);
        var result = ParallelBatchCpuHelper.bulkExtract(pattern(input(stack(stick, 1), 1)), inv, 6);

        assertNotNull(result);
        ParallelBatchCpuHelper.reinject(result, 2, inv);

        assertEquals(6, inv.extract(stick, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(4, result.scaledInputs[0].get(stick));
    }

    @Test
    void copySliceAndMarkDispatchedOperateOnCopyCounts() {
        var stick = key("stick");
        var inv = inventory();
        inv.insert(stick, 10, Actionable.MODULATE);
        var result = ParallelBatchCpuHelper.bulkExtract(pattern(input(stack(stick, 2), 1)), inv, 5);

        assertNotNull(result);
        var slice = ParallelBatchCpuHelper.copySlice(result, 2);
        ParallelBatchCpuHelper.markDispatched(result, 2);

        assertEquals(4, slice[0].get(stick));
        assertEquals(6, result.scaledInputs[0].get(stick));
    }

    @Test
    void singleSeedBatchExtractsOneSeedAndScalesOnlyConsumableInputs() {
        var seed = key("infusion_crystal");
        var essence = key("essence");
        var inv = inventory();
        inv.insert(seed, 1, Actionable.MODULATE);
        inv.insert(essence, 40, Actionable.MODULATE);
        var pattern = new FakeSingleSeedPattern(
                new IPatternDetails.IInput[] {
                        input(stack(seed, 1), 1), input(stack(essence, 4), 1)
                }, seed);

        var result = ParallelBatchCpuHelper.bulkExtract(pattern, inv, 10);

        assertNotNull(result);
        assertEquals(10, result.actualCopies);
        assertEquals(1, result.scaledInputs[0].get(seed));
        assertEquals(40, result.scaledInputs[1].get(essence));

        ParallelBatchCpuHelper.reinject(result, 3, inv);
        ParallelBatchCpuHelper.markDispatched(result, 7);

        assertEquals(0, inv.extract(seed, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(12, inv.extract(essence, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(0, result.scaledInputs[0].get(seed));
        assertEquals(0, result.scaledInputs[1].get(essence));
    }

    @Test
    void partialSingleSeedAcceptanceReinjectsOnlyUnacceptedConsumables() {
        var seed = key("master_infusion_crystal");
        var essence = key("inferium_essence");
        var upgraded = key("prudentium_essence");
        var inv = inventory();
        inv.insert(seed, 1, Actionable.MODULATE);
        inv.insert(essence, 4_000, Actionable.MODULATE);
        var pattern = new FakeReturnedSeedPattern(
                new IPatternDetails.IInput[] {
                        returningInput(stack(seed, 1), 1, seed), input(stack(essence, 4), 1)
                }, seed, upgraded);

        var first = ParallelBatchCpuHelper.bulkExtract(pattern, inv, 1_000);
        assertNotNull(first);

        ParallelBatchCpuHelper.markDispatched(first, 500);
        var waiting = inventory();
        ParallelBatchCpuHelper.registerExpectedOutputs(
                new FakeBatchJobView(waiting), pattern, first, 500);
        ParallelBatchCpuHelper.reinject(first, 500, inv);

        assertEquals(0, inv.extract(seed, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(2_000, inv.extract(essence, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(1, waiting.extract(seed, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(500, waiting.extract(upgraded, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(0, first.scaledInputs[0].get(seed));
        assertEquals(0, first.scaledInputs[1].get(essence));

        inv.insert(seed, 1, Actionable.MODULATE); // first physical batch returned its seed
        var second = ParallelBatchCpuHelper.bulkExtract(pattern, inv, 500);

        assertNotNull(second);
        assertEquals(500, second.actualCopies);
        assertEquals(1, second.scaledInputs[0].get(seed));
        assertEquals(2_000, second.scaledInputs[1].get(essence));
    }

    @Test
    void rejectedSingleSeedBatchReinjectsTheSeedExactlyOnce() {
        var seed = key("template");
        var material = key("diamond");
        var inv = inventory();
        inv.insert(seed, 1, Actionable.MODULATE);
        inv.insert(material, 14, Actionable.MODULATE);
        var pattern = new FakeSingleSeedPattern(
                new IPatternDetails.IInput[] {
                        input(stack(seed, 1), 1), input(stack(material, 7), 1)
                }, seed);
        var result = ParallelBatchCpuHelper.bulkExtract(pattern, inv, 2);

        assertNotNull(result);
        ParallelBatchCpuHelper.reinject(result, 2, inv);

        assertEquals(1, inv.extract(seed, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(14, inv.extract(material, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void regeneratedSeedOutputIsExpectedOnceAndNetOutputScales() {
        var template = key("smithing_template");
        var diamond = key("diamond");
        var inv = inventory();
        inv.insert(template, 1, Actionable.MODULATE);
        inv.insert(diamond, 35, Actionable.MODULATE);
        var pattern = new FakeRegeneratedSeedPattern(
                new IPatternDetails.IInput[] {
                        input(stack(template, 1), 1), input(stack(diamond, 7), 1)
                }, template);
        var result = ParallelBatchCpuHelper.bulkExtract(pattern, inv, 5);
        var waiting = inventory();
        var job = new FakeBatchJobView(waiting);

        assertNotNull(result);
        ParallelBatchCpuHelper.registerExpectedOutputs(job, pattern, result, 5);

        assertEquals(6, waiting.extract(template, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void sharedAndConsumableSlotsUsingSameKeyReserveSeedBeforeScaling() {
        var key = key("same_key");
        var inv = inventory();
        inv.insert(key, 10, Actionable.MODULATE);
        var pattern = new FakeSingleSeedPattern(new IPatternDetails.IInput[] {
                input(stack(key, 1), 1), input(stack(key, 2), 1)
        }, key);

        var result = ParallelBatchCpuHelper.bulkExtract(pattern, inv, 10);

        assertNotNull(result);
        assertEquals(4, result.actualCopies);
        assertEquals(1, result.scaledInputs[0].get(key));
        assertEquals(8, result.scaledInputs[1].get(key));
        assertEquals(1, inv.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void multipleSharedSlotsOfSameKeyAreAllFixedAndReturnedExactlyOncePerSlot() {
        var key = key("double_seed");
        var inv = inventory();
        inv.insert(key, 3, Actionable.MODULATE);
        var pattern = new FakeAllSharedPattern(new IPatternDetails.IInput[] {
                input(stack(key, 1), 1), input(stack(key, 2), 1)
        });

        var result = ParallelBatchCpuHelper.bulkExtract(pattern, inv, Integer.MAX_VALUE);
        assertNotNull(result);
        assertEquals(Integer.MAX_VALUE, result.actualCopies);
        assertEquals(0, inv.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));

        ParallelBatchCpuHelper.reinject(result, Integer.MAX_VALUE, inv);
        assertEquals(3, inv.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void providerFilterSkipsOnlyIdentityMatchedProviders() {
        var first = new FakeProvider();
        var skipped = new FakeProvider();
        var second = new FakeProvider();
        var excluded = new IdentityHashMap<ICraftingProvider, Boolean>();
        excluded.put(skipped, Boolean.TRUE);

        var visible = new ArrayList<ICraftingProvider>();
        for (var provider : new BatchProviderFilterIterable(List.of(first, skipped, second), excluded)) {
            visible.add(provider);
        }

        assertIterableEquals(List.of(first, second), visible);
    }

    private static ListCraftingInventory inventory() {
        return new ListCraftingInventory(key -> {});
    }

    private static TestKey key(String id) {
        return new TestKey(id);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static IPatternDetails pattern(IPatternDetails.IInput... inputs) {
        return new FakePattern(inputs);
    }

    private static IPatternDetails.IInput input(GenericStack stack, long multiplier) {
        return new FakeInput(new GenericStack[] {stack}, multiplier);
    }

    private static IPatternDetails.IInput returningInput(
            GenericStack stack, long multiplier, AEKey remainder) {
        return new FakeReturningInput(new GenericStack[] {stack}, multiplier, remainder);
    }

    private record FakePattern(IPatternDetails.IInput[] inputs) implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return null;
        }

        @Override
        public IPatternDetails.IInput[] getInputs() {
            return inputs;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of();
        }
    }

    private record FakeSingleSeedPattern(IPatternDetails.IInput[] inputs, AEKey seed)
            implements IPatternDetails, SharedBatchInputPattern {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(); }
        @Override public boolean isSharedBatchInput(int slot, AEKey concreteKey) {
            return slot == 0 && seed.equals(concreteKey);
        }
    }

    private record FakeRegeneratedSeedPattern(IPatternDetails.IInput[] inputs, AEKey seed)
            implements IPatternDetails, SharedBatchInputPattern {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(stack(seed, 2)); }
        @Override public boolean isSharedBatchInput(int slot, AEKey concreteKey) {
            return slot == 0 && seed.equals(concreteKey);
        }
        @Override public long sharedBatchOutputAmount(AEKey outputKey) {
            return seed.equals(outputKey) ? 1L : 0L;
        }
    }

    private record FakeReturnedSeedPattern(
            IPatternDetails.IInput[] inputs, AEKey seed, AEKey output)
            implements IPatternDetails, SharedBatchInputPattern {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(stack(output, 1)); }
        @Override public boolean isSharedBatchInput(int slot, AEKey concreteKey) {
            return slot == 0 && seed.equals(concreteKey);
        }
    }

    private record FakeAllSharedPattern(IPatternDetails.IInput[] inputs)
            implements IPatternDetails, SharedBatchInputPattern {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return List.of(); }
        @Override public boolean isSharedBatchInput(int slot, AEKey concreteKey) { return true; }
    }

    private record FakeBatchJobView(ListCraftingInventory waitingFor) implements BatchJobView {
        @Override public java.util.Iterator<BatchTaskHandle> taskIterator() {
            return java.util.Collections.emptyIterator();
        }
        @Override public void addContainerMaxItems(long count, AEKeyType type) { }
    }

    private record FakeInput(GenericStack[] possibleInputs, long multiplier) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            for (var possible : possibleInputs) {
                if (possible.what().equals(key)) return true;
            }
            return false;
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private record FakeReturningInput(
            GenericStack[] possibleInputs, long multiplier, AEKey remainder)
            implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return possibleInputs; }
        @Override public long getMultiplier() { return multiplier; }
        @Override public boolean isValid(AEKey key, Level level) {
            for (var possible : possibleInputs) {
                if (possible.what().equals(key)) return true;
            }
            return false;
        }
        @Override public AEKey getRemainingKey(AEKey template) { return remainder; }
    }

    private static final class FakeProvider implements ICraftingProvider {
        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            return false;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
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
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "key"), TestKey.class,
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
