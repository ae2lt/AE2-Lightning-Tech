package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

class MatrixMultiblockInterfaceTest {
    private static final FakePattern PATTERN = new FakePattern("encoded");
    private static final AEKey OUTPUT = new TestKey("interface-output");

    @Test
    void interfaceRoleIsTheOnlyAeNetworkEndpoint() {
        assertTrue(MatrixMultiblockPortRole.INTERFACE.exposesAeNetwork());
        assertFalse(MatrixMultiblockPortRole.CONTROLLER.exposesAeNetwork());
        assertTrue(MatrixMultiblockPortRole.INTERFACE.requiredForFormation());
        assertTrue(MatrixMultiblockPortRole.CONTROLLER.requiredForFormation());
    }

    @Test
    void portLayoutTracksPreferredSameFacePlacementWithoutFinalOffsets() {
        var sameFace = new MatrixMultiblockPortLayout(Direction.NORTH, Direction.NORTH);
        var splitFace = new MatrixMultiblockPortLayout(Direction.NORTH, Direction.SOUTH);

        assertSame(Direction.NORTH, sameFace.controllerFace());
        assertSame(Direction.NORTH, sameFace.interfaceFace());
        assertTrue(sameFace.usesPreferredSameFacePlacement());
        assertFalse(splitFace.usesPreferredSameFacePlacement());
    }

    @Test
    void interfaceExposesClusterProviderAndPatternInventoryTogether() {
        var host = new FakeHost();
        var assembler = new FakeAssembler();
        var cluster = cluster(host, List.of(new FakeCraftCore(
                MatrixCraftingUnit.stableCore(),
                MatrixCraftingUnit.threadPower(160),
                MatrixCraftingUnit.multiplierPower(20))), assembler);
        var patternUnit = unit(PATTERN);
        var repository = new MatrixPatternRepository(List.of(patternUnit));

        var matrixInterface = new MatrixMultiblockInterface(cluster, repository);

        assertEquals(List.of(PATTERN), matrixInterface.getAvailablePatterns());
        assertSame(patternUnit, matrixInterface.exposedPatternUnit());
        assertEquals(3903, matrixInterface.getBatchCapacity(PATTERN));
        assertEquals(0, matrixInterface.pushBatch(PATTERN, emptyInputs(), 12));
        assertEquals(1, assembler.calls);
    }

    @Test
    void interfaceDoesNotExposeOrAcceptNonMolecularPatterns() {
        var assembler = new FakeAssembler();
        var cluster = cluster(new FakeHost(), List.of(new FakeCraftCore(
                MatrixCraftingUnit.quantumCore(),
                MatrixCraftingUnit.threadPower(160),
                MatrixCraftingUnit.multiplierPower(20))), assembler);
        var plainPattern = new FakePlainPattern("plain");
        var repository = new MatrixPatternRepository(List.of(unit(PATTERN, plainPattern)));

        var matrixInterface = new MatrixMultiblockInterface(cluster, repository);

        assertEquals(List.of(PATTERN), matrixInterface.getAvailablePatterns());
        assertEquals(0, matrixInterface.getBatchCapacity(plainPattern));
        assertEquals(7, matrixInterface.pushBatch(plainPattern, emptyInputs(), 7));
        assertEquals(0, assembler.calls);
    }

    @Test
    void interfacePatternInsertionUsesRepositoryBackpressure() {
        var cluster = cluster(new FakeHost(), List.of(new FakeCraftCore(MatrixCraftingUnit.quantumCore())),
                (details, oneCopyInputs) -> null);
        var repository = new MatrixPatternRepository(List.of(new MatrixPatternStorageUnit(1)));
        var matrixInterface = new MatrixMultiblockInterface(cluster, repository);
        var first = new FakePattern("first");
        var overflow = new FakePattern("overflow");

        assertTrue(matrixInterface.insertPattern(first));
        assertFalse(matrixInterface.insertPattern(overflow));
        assertEquals(List.of(first), matrixInterface.getAvailablePatterns());
        assertSame(first, matrixInterface.exposedPatternUnit().get(0));
    }

    @Test
    void interfaceRejectsNonMolecularPatternInsertion() {
        var cluster = cluster(new FakeHost(), List.of(new FakeCraftCore(MatrixCraftingUnit.quantumCore())),
                (details, oneCopyInputs) -> null);
        var repository = new MatrixPatternRepository(List.of(new MatrixPatternStorageUnit(2)));
        var matrixInterface = new MatrixMultiblockInterface(cluster, repository);
        var plain = new FakePlainPattern("plain");

        assertFalse(matrixInterface.insertPattern(plain));
        assertEquals(List.of(plain), matrixInterface.insertPatterns(List.of(plain)));
        assertEquals(0, repository.usedSlots());
        assertEquals(List.of(), matrixInterface.getAvailablePatterns());
    }

    private static MatrixCraftingCluster cluster(FakeHost host, List<FakeCraftCore> cores, CopyAssembler assembler) {
        return new MatrixCraftingCluster(
                () -> true,
                List.of(),
                cores,
                host,
                assembler,
                new CraftingCoreRegistry());
    }

    private static KeyCounter[] emptyInputs() {
        return new KeyCounter[0];
    }

    private static MatrixPatternStorageUnit unit(IPatternDetails... patterns) {
        var unit = new MatrixPatternStorageUnit(patterns.length);
        for (var pattern : patterns) {
            unit.insert(pattern);
        }
        return unit;
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
        @Override
        public long getGameTime() {
            return 0;
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

    private static final class FakePattern implements IMolecularAssemblerSupportedPattern {
        private final String id;

        private FakePattern(String id) {
            this.id = id;
        }

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

    private static final class FakePlainPattern implements IPatternDetails {
        private final String id;

        private FakePlainPattern(String id) {
            this.id = id;
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
