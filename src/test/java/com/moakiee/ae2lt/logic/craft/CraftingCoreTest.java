package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.thunderbolt.core.craft.CopyAssembler;
import com.moakiee.thunderbolt.core.craft.CraftingCore;
import com.moakiee.thunderbolt.core.craft.CraftingCoreHost;
import com.moakiee.thunderbolt.core.craft.CraftingCoreRegistry;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.moakiee.thunderbolt.ae2.batch.SharedBatchInputPattern;

class CraftingCoreTest {
    @Test
    void assemblesOnceAndDeliversAfterDelay() {
        var host = new FakeHost(100);
        var registry = new CraftingCoreRegistry();
        var assembler = new FakeAssembler(key("diamond"), 2, stack(key("bucket"), 1));
        var core = new CraftingCore(host, assembler, registry);

        core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 5, 2);

        assertEquals(1, assembler.calls);
        assertEquals(5, core.threadsInFlight());

        host.time = 101;
        registry.tickAll();
        assertEquals(0, host.network.getLong(key("diamond")));

        host.time = 102;
        registry.tickAll();
        assertEquals(10, host.network.getLong(key("diamond")));
        assertEquals(5, host.network.getLong(key("bucket")));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void getSizeReportsScheduledCopiesInTargetSlot() {
        var host = new FakeHost(0);
        var core = new CraftingCore(host, new FakeAssembler(key("diamond"), 1), new CraftingCoreRegistry());

        core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 4, 3); // now=0, due=3 -> wheel[3]

        assertEquals(4, core.getSize(3));
        assertEquals(0, core.getSize(2));
        assertEquals(4, core.threadsInFlight());
    }

    @Test
    void nonMolecularPatternsAreRejectedWithoutSideEffects() {
        var host = new FakeHost(0);
        var assembler = new FakeAssembler(key("diamond"), 1);
        var core = new CraftingCore(host, assembler, new CraftingCoreRegistry());

        core.pushBatch(new FakePlainPattern(), inputs(key("stick"), 1), 3, 1);

        assertEquals(0, assembler.calls);
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void partialNetworkInsertKeepsThreadsUntilFullyDrained() {
        var host = new FakeHost(0);
        host.maxInsertPerCall = 3;
        var output = key("diamond");
        var core = new CraftingCore(host, new FakeAssembler(output, 5), new CraftingCoreRegistry());

        core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 2, 1); // now=0, due=1

        host.time = 1;
        core.sweepTick();
        assertEquals(3, host.network.getLong(output));
        assertEquals(2, core.threadsInFlight());

        host.maxInsertPerCall = Long.MAX_VALUE;
        host.time = 2;
        core.sweepTick();
        assertEquals(10, host.network.getLong(output));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void rejectsNewPushesWhileMaturedOutputsAreBlocked() {
        var host = new FakeHost(0);
        host.maxInsertPerCall = 0;
        var assembler = new FakeAssembler(key("diamond"), 1);
        var core = new CraftingCore(host, assembler, new CraftingCoreRegistry());

        long firstAccepted = core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 2L, 1);
        host.time = 1;
        long blockedAccepted = core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 3L, 1);

        assertEquals(2, firstAccepted);
        assertEquals(0, blockedAccepted);
        assertEquals(1, assembler.calls);
        assertEquals(2, core.threadsInFlight());
        assertEquals(0, host.network.getLong(key("diamond")));
    }

    @Test
    void copyCountersContinuePastIntegerRange() {
        var host = new FakeHost(0);
        var core = new CraftingCore(host, new FakeAssembler(key("diamond"), 1), new CraftingCoreRegistry());

        long requested = (long) Integer.MAX_VALUE + 17L;
        long accepted = core.pushBatch(new FakePattern(), inputs(key("stick"), 1), requested, 1);

        assertEquals(requested, accepted);
        assertEquals(requested, core.threadsInFlight());
        assertEquals(requested, core.getSize(1));
    }

    @Test
    void disconnectedHostKeepsOutputsInWheelUntilReconnect() {
        var host = new FakeHost(0);
        host.connected = false;
        var output = key("diamond");
        var core = new CraftingCore(host, new FakeAssembler(output, 1), new CraftingCoreRegistry());

        core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 2, 1);
        host.time = 1;
        core.sweepTick();

        assertEquals(0, host.network.getLong(output));
        assertEquals(2, core.threadsInFlight());

        host.connected = true;
        host.time = 2;
        core.sweepTick();

        assertEquals(2, host.network.getLong(output));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void gracefulRemovalDrainReleasesAcceptedOutputsAndKeepsRemainder() {
        var host = new FakeHost(0);
        host.maxInsertPerCall = 3;
        var output = key("diamond");
        var core = new CraftingCore(host, new FakeAssembler(output, 5), new CraftingCoreRegistry());

        core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 2, 20);
        core.drainAll(false);

        assertEquals(3, host.network.getLong(output));
        assertEquals(2, core.threadsInFlight());

        host.maxInsertPerCall = Long.MAX_VALUE;
        core.drainAll(false);

        assertEquals(10, host.network.getLong(output));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void sharedSeedRemainderIsReturnedOnceForTheWholeBatch() {
        var host = new FakeHost(0);
        var output = key("higher_essence");
        var seed = key("infusion_crystal");
        CopyAssembler assembler = (details, inputs) -> new CopyAssembler.AssembledCopy(
                output, 1, List.of(), List.of(stack(seed, 1)));
        var core = new CraftingCore(host, assembler, new CraftingCoreRegistry());

        assertEquals(8, core.pushBatch(new FakePattern(), inputs(seed, 1), 8, 1));
        host.time = 1;
        core.sweepTick();

        assertEquals(8, host.network.getLong(output));
        assertEquals(1, host.network.getLong(seed));
    }

    @Test
    void cachedAssemblyReturnsSharedSeedAfterEveryPartialPush() {
        var host = new FakeHost(0);
        var output = key("prudentium_essence");
        var seed = key("master_infusion_crystal");
        var essence = key("inferium_essence");
        var assemblerCalls = new int[1];
        CopyAssembler assembler = (details, inputs) -> {
            assemblerCalls[0]++;
            return new CopyAssembler.AssembledCopy(
                    output, 1, List.of(), List.of(stack(seed, 1)));
        };
        var core = new CraftingCore(host, assembler, new CraftingCoreRegistry());
        var pattern = new FakeOutputPattern(output, 1);
        var oneCopyInputs = new KeyCounter[] {
                inputs(seed, 1)[0], inputs(essence, 4)[0]
        };

        assertEquals(500, core.pushBatch(pattern, oneCopyInputs, 500, 1));
        host.time = 1;
        core.sweepTick();
        assertEquals(500, host.network.getLong(output));
        assertEquals(1, host.network.getLong(seed));

        host.network.removeLong(seed); // CPU borrows the returned seed for the second push
        assertEquals(500, core.pushBatch(pattern, oneCopyInputs, 500, 1));
        host.time = 2;
        core.sweepTick();

        assertEquals(1, assemblerCalls[0], "the second push must exercise the assembly cache");
        assertEquals(1_000, host.network.getLong(output));
        assertEquals(1, host.network.getLong(seed));
    }

    @Test
    void regeneratedSeedOutputIsReturnedOnceAndOnlyNetGainScales() {
        var host = new FakeHost(0);
        var template = key("smithing_template");
        var core = new CraftingCore(host,
                new FakeAssembler(template, 2), new CraftingCoreRegistry());

        assertEquals(5, core.pushBatch(new FakeSharedOutputPattern(template),
                inputs(template, 1), 5, 1));
        host.time = 1;
        core.sweepTick();

        assertEquals(6, host.network.getLong(template));
    }

    @Test
    void removedHostHardFlushesToWorldAndDeregisters() {
        var host = new FakeHost(0);
        var output = key("diamond");
        var registry = new CraftingCoreRegistry();
        var core = new CraftingCore(host, new FakeAssembler(output, 1), registry);

        core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 2, 1);
        host.removed = true;

        assertFalse(core.sweepTick());
        assertEquals(2, host.spawned.getLong(output));
        assertEquals(0, core.threadsInFlight());
    }

    @Test
    void suspendDropsOnlyTheLocalMirrorWithoutSpawningPersistedOutputs() {
        var host = new FakeHost(0);
        var output = key("diamond");
        var registry = new CraftingCoreRegistry();
        var core = new CraftingCore(host, new FakeAssembler(output, 1), registry);

        core.pushBatch(new FakePattern(), inputs(key("stick"), 1), 2, 1);
        core.suspend();
        host.removed = true;
        registry.tickAll();

        assertEquals(0, host.spawned.getLong(output));
        assertEquals(0, host.network.getLong(output));
        assertEquals(0, core.threadsInFlight());
    }

    private static KeyCounter[] inputs(AEKey key, long amount) {
        var counter = new KeyCounter();
        counter.add(key, amount);
        return new KeyCounter[] {counter};
    }

    private static TestKey key(String id) {
        return new TestKey(id);
    }

    private static CopyAssembler.Stack stack(AEKey key, long count) {
        return new CopyAssembler.Stack(key, count);
    }

    private static final class FakeHost implements CraftingCoreHost {
        long time;
        boolean connected = true;
        boolean removed;
        long maxInsertPerCall = Long.MAX_VALUE;
        final Object2LongOpenHashMap<AEKey> network = new Object2LongOpenHashMap<>();
        final Object2LongOpenHashMap<AEKey> spawned = new Object2LongOpenHashMap<>();

        FakeHost(long time) {
            this.time = time;
        }

        @Override
        public long getGameTime() {
            return time;
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public long insertToNetwork(AEKey key, long amount) {
            long inserted = Math.min(amount, maxInsertPerCall);
            network.addTo(key, inserted);
            return inserted;
        }

        @Override
        public void spawnToWorld(AEKey key, long amount) {
            spawned.addTo(key, amount);
        }
    }

    private static final class FakeAssembler implements CopyAssembler {
        final AEKey output;
        final long outputCount;
        final List<Stack> remainders;
        int calls;

        FakeAssembler(AEKey output, long outputCount, Stack... remainders) {
            this.output = output;
            this.outputCount = outputCount;
            this.remainders = List.of(remainders);
        }

        @Override
        public AssembledCopy assembleOneCopy(IPatternDetails details, KeyCounter[] oneCopyInputs) {
            calls++;
            return new AssembledCopy(output, outputCount, remainders);
        }
    }

    private static final class FakePlainPattern implements IPatternDetails {
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
    }

    private static class FakePattern implements IMolecularAssemblerSupportedPattern {
        @Override
        public ItemStack assemble(CraftingInput input, Level level) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slotIndex, AEItemKey key, Level level) {
            return true;
        }

        @Override
        public boolean isSlotEnabled(int slot) {
            return true;
        }

        @Override
        public void fillCraftingGrid(KeyCounter[] inputHolder, CraftingGridAccessor accessor) {
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
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            return NonNullList.create();
        }
    }

    private static final class FakeOutputPattern extends FakePattern {
        private final AEKey output;
        private final long amount;

        private FakeOutputPattern(AEKey output, long amount) {
            this.output = output;
            this.amount = amount;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(output, amount));
        }
    }

    private static final class FakeSharedOutputPattern extends FakePattern
            implements SharedBatchInputPattern {
        private final AEKey seed;
        private FakeSharedOutputPattern(AEKey seed) { this.seed = seed; }
        @Override public boolean isSharedBatchInput(int slot, AEKey concreteKey) {
            return seed.equals(concreteKey);
        }
        @Override public long sharedBatchOutputAmount(AEKey outputKey) {
            return seed.equals(outputKey) ? 1L : 0L;
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
