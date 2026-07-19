package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moakiee.thunderbolt.core.craft.CopyAssembler;
import com.moakiee.thunderbolt.core.craft.CraftingCoreHost;
import com.moakiee.thunderbolt.core.craft.CraftingCoreRegistry;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopBatchPatternDetails;
import com.moakiee.thunderbolt.ae2.batch.BatchCopyLimitPattern;
import com.mojang.serialization.MapCodec;
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

class MatrixCraftingClusterTest {
    private static final FakePattern PATTERN = new FakePattern();
    private static final AEKey OUTPUT = new TestKey("diamond");

    @Test
    void aggregatesCraftingProfileFromCraftCores() {
        var cluster = cluster(List.of(
                new FakeCraftCore(MatrixCraftingUnit.quantumCore(), MatrixCraftingUnit.t2Threader()),
                new FakeCraftCore(MatrixCraftingUnit.t2Multiplier(), MatrixCraftingUnit.t2Cooler(2))));

        var profile = cluster.craftingProfile();

        assertEquals(MatrixCoreMode.QUANTUM, profile.mode());
        assertEquals(1.0D, profile.threadPower(), 0.0001D);
        assertEquals(1.0D, profile.multiPower(), 0.0001D);
        assertEquals(0.75D, profile.coolPower(), 0.0001D);
    }

    @Test
    void limiterTickAdvancesAndPersistsHeatState() {
        var host = new FakeHost();
        host.time = 42;
        var cluster = cluster(host, List.of(new FakeCraftCore(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.t1Threader(),
                MatrixCraftingUnit.t1Multiplier())));

        var snapshot = cluster.tickLimiter();
        var tag = new CompoundTag();
        cluster.writeEngineTo(tag, null);

        var restored = cluster(List.of());
        restored.readEngineFrom(tag, null);

        assertEquals(0.0D, snapshot.heat(), 0.0001D);
        assertEquals(snapshot.heat(), restored.heat(), 0.0001D);
        assertEquals(42L, restored.lastLimiterTick());
    }

    @Test
    void pushBatchConsumesLimiterBudgetForCurrentTick() {
        var host = new FakeHost();
        var assembler = new FakeAssembler();
        var cluster = cluster(host, List.of(new FakeCraftCore(
                MatrixCraftingUnit.stableCore(),
                MatrixCraftingUnit.t1Threader())), assembler);

        long capacity = cluster.getBatchCapacity(PATTERN);
        assertTrue(capacity > 0);

        long firstLeftover = cluster.pushBatch(PATTERN, emptyInputs(), capacity + 10L);
        long secondLeftover = cluster.pushBatch(PATTERN, emptyInputs(), 1L);

        assertEquals(10, firstLeftover);
        assertEquals(1, secondLeftover);
        assertEquals(1, assembler.calls);
        assertEquals(capacity, cluster.threadsInFlight());
        assertEquals(0, cluster.getBatchCapacity(PATTERN));

        host.time = 1;

        assertTrue(cluster.getBatchCapacity(PATTERN) > 0);
        assertTrue(cluster.heat() > 0.0D);
    }

    @Test
    void invalidProfileReportsNoBatchCapacity() {
        var cluster = cluster(List.of(new FakeCraftCore(MatrixCraftingUnit.t2Threader())));

        assertEquals(0, cluster.getBatchCapacity(PATTERN));
        assertEquals(12, cluster.pushBatch(PATTERN, emptyInputs(), 12));
    }

    @Test
    void closedLoopBatchRunsThroughBuiltInMainCore() {
        var loopPattern = new FakeLoopPattern();
        var cores = List.of(new FakeCraftCore(
                MatrixCraftingUnit.stableCore(), MatrixCraftingUnit.t2Threader()));
        var assembler = new FakeAssembler();
        var cluster = new MatrixCraftingCluster(
                () -> true,
                List.of(() -> List.of(loopPattern)), cores,
                new FakeHost(), assembler, new CraftingCoreRegistry());

        assertEquals(32, cluster.getBatchCapacity(loopPattern));
        assertTrue(cluster.pushSingle(loopPattern, emptyInputs()));
        assertEquals(1, assembler.calls);
    }

    @Test
    void providerCallFuseProtectsHighThroughputMatrixFromNonBatchCpu() {
        var host = new FakeHost();
        var assembler = new FakeAssembler();
        var units = new java.util.ArrayList<MatrixCraftingUnit>();
        units.add(MatrixCraftingUnit.overloadCore());
        for (int i = 0; i < 4; i++) units.add(MatrixCraftingUnit.t1Threader());
        for (int i = 0; i < 15; i++) units.add(MatrixCraftingUnit.t1Multiplier());
        var cluster = cluster(
                host,
                List.of(new FakeCraftCore(units.toArray(MatrixCraftingUnit[]::new))),
                assembler);

        for (int i = 0; i < com.moakiee.ae2lt.logic.compute.MatrixComputeEnvelope
                .MAX_PROVIDER_CALLS_PER_TICK; i++) {
            assertTrue(cluster.pushSingle(PATTERN, emptyInputs()));
        }

        assertEquals(0, cluster.availableProviderCalls());
        assertTrue(cluster.availableCapacity() > 0L);
        assertTrue(cluster.isBusy());
        assertFalse(cluster.pushSingle(PATTERN, emptyInputs()));
        assertEquals(
                com.moakiee.ae2lt.logic.compute.MatrixComputeEnvelope.MAX_PROVIDER_CALLS_PER_TICK,
                assembler.calls);

        host.time++;
        assertEquals(
                com.moakiee.ae2lt.logic.compute.MatrixComputeEnvelope.MAX_PROVIDER_CALLS_PER_TICK,
                cluster.availableProviderCalls());
    }

    private static MatrixCraftingCluster cluster(List<FakeCraftCore> cores) {
        return cluster(new FakeHost(), cores);
    }

    private static MatrixCraftingCluster cluster(FakeHost host, List<FakeCraftCore> cores) {
        return cluster(host, cores, (details, oneCopyInputs) -> null);
    }

    private static MatrixCraftingCluster cluster(FakeHost host, List<FakeCraftCore> cores, CopyAssembler assembler) {
        return new MatrixCraftingCluster(
                () -> true,
                List.of(() -> List.of(PATTERN)),
                cores,
                host,
                assembler,
                new CraftingCoreRegistry());
    }

    private static KeyCounter[] emptyInputs() {
        return new KeyCounter[0];
    }

    private static final class FakeAssembler implements CopyAssembler {
        int calls;

        @Override
        public AssembledCopy assembleOneCopy(IPatternDetails details, KeyCounter[] oneCopyInputs) {
            calls++;
            return new AssembledCopy(OUTPUT, 1, List.of());
        }
    }

    private static final class FakeCraftCore implements MatrixCraftCore {
        private final List<MatrixCraftingUnit> units;

        FakeCraftCore(MatrixCraftingUnit... units) {
            this.units = List.of(units);
        }

        @Override
        public List<MatrixCraftingUnit> craftingUnits() {
            return units;
        }
    }

    private static final class FakeHost implements CraftingCoreHost {
        long time;

        @Override
        public long getGameTime() {
            return time;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public long insertToNetwork(AEKey key, long amount) {
            return amount;
        }

        @Override
        public void spawnToWorld(AEKey key, long amount) {
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

    private static final class FakeLoopPattern extends FakePattern
            implements ClosedLoopBatchPatternDetails, BatchCopyLimitPattern {
        @Override public long maxBatchCopies() { return 32L; }
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
