package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.MapCodec;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ClosedLoopPatternFlattenerTest {
    @Test
    void recursivelyFlattensNestedCopiesWithoutInheritingJobMultipliers() {
        var first = snapshot("first");
        var second = snapshot("second");
        var inner = snapshot("inner");
        var firstDetails = new FakePattern("first");
        var secondDetails = new FakePattern("second");
        var nested = payload(UUID.randomUUID(), List.of(
                new ClosedLoopMemberPattern(first, 1L),
                new ClosedLoopMemberPattern(second, 2L)), 37, 41);
        var resolver = resolver(Map.of(
                first.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(firstDetails),
                second.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(secondDetails),
                inner.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(nested)));

        var result = ClosedLoopPatternFlattener.flatten(
                List.of(new ClosedLoopMemberPattern(inner, 1L)), resolver);

        assertTrue(result.valid());
        assertEquals(2, result.members().size());
        assertSame(firstDetails, result.members().get(0).details());
        assertEquals(1L, result.members().get(0).totalCopies());
        assertSame(secondDetails, result.members().get(1).details());
        assertEquals(2L, result.members().get(1).totalCopies());
    }

    @Test
    void recursiveReferenceCountsRemainStoichiometricAndUseOneWave() {
        var first = snapshot("recursive_first");
        var second = snapshot("recursive_second");
        var anchor = snapshot("recursive_anchor");
        var inner = snapshot("recursive_inner");
        var middle = snapshot("recursive_middle");
        var innerPayload = payload(UUID.randomUUID(), List.of(
                new ClosedLoopMemberPattern(first, 1L),
                new ClosedLoopMemberPattern(second, 2L)), 5, 7);
        var middlePayload = payload(UUID.randomUUID(), List.of(
                new ClosedLoopMemberPattern(inner, 3L),
                new ClosedLoopMemberPattern(anchor, 1L)), 11, 13);
        var resolver = resolver(Map.of(
                first.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("recursive_first")),
                second.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("recursive_second")),
                anchor.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("recursive_anchor")),
                inner.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(innerPayload),
                middle.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(middlePayload)));

        var result = ClosedLoopPatternFlattener.flatten(
                List.of(new ClosedLoopMemberPattern(middle, 1L)), resolver);

        assertTrue(result.valid());
        assertEquals(3L, result.members().get(0).totalCopies());
        assertEquals(6L, result.members().get(1).totalCopies());
        assertEquals(1L, result.members().get(2).totalCopies());
    }

    @Test
    void rejectsACommonScaleAcrossIndependentNestedReferences() {
        var first = snapshot("gcd_first");
        var second = snapshot("gcd_second");
        var firstMacro = snapshot("gcd_first_macro");
        var secondMacro = snapshot("gcd_second_macro");
        var resolver = resolver(Map.of(
                first.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("gcd_first")),
                second.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("gcd_second")),
                firstMacro.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(
                        payload(UUID.randomUUID(), List.of(
                                new ClosedLoopMemberPattern(first, 1L)), 1, 1)),
                secondMacro.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(
                        payload(UUID.randomUUID(), List.of(
                                new ClosedLoopMemberPattern(second, 1L)), 1, 1))));

        var result = ClosedLoopPatternFlattener.flatten(List.of(
                new ClosedLoopMemberPattern(firstMacro, 100L),
                new ClosedLoopMemberPattern(secondMacro, 50L)), resolver);

        assertEquals(ClosedLoopPatternFlattener.Status.NON_MINIMAL_COPIES, result.status());
    }

    @Test
    void topLevelOrdinaryMemberKeepsThePrimitiveRatio() {
        var leaf = snapshot("mixed_leaf");
        var macro = snapshot("mixed_macro");
        var ordinary = snapshot("mixed_ordinary");
        var resolver = resolver(Map.of(
                leaf.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("mixed_leaf")),
                macro.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(
                        payload(UUID.randomUUID(), List.of(
                                new ClosedLoopMemberPattern(leaf, 1L)), 1, 1)),
                ordinary.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("mixed_ordinary"))));

        var result = ClosedLoopPatternFlattener.flatten(List.of(
                new ClosedLoopMemberPattern(macro, 25L),
                new ClosedLoopMemberPattern(ordinary, 1L)), resolver);

        assertTrue(result.valid());
        assertEquals(25L, result.members().get(0).totalCopies());
        assertEquals(1L, result.members().get(1).totalCopies());
    }

    @Test
    void rejectsANonMinimalExistingFlatPayload() {
        var leaf = snapshot("stored_wave_leaf");
        var details = new FakePattern("stored_wave_leaf");

        var result = ClosedLoopPatternFlattener.flatten(
                List.of(new ClosedLoopMemberPattern(leaf, 100L)),
                resolver(Map.of(
                        leaf.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(details))));

        assertEquals(ClosedLoopPatternFlattener.Status.NON_MINIMAL_COPIES, result.status());
    }

    @Test
    void repeatedSiblingReferenceIsNotMistakenForAnAncestorCycle() {
        var leaf = snapshot("sibling_leaf");
        var macro = snapshot("sibling_macro");
        var sharedPayload = payload(UUID.randomUUID(), List.of(
                new ClosedLoopMemberPattern(leaf, 1L)), 1, 1);
        var resolver = resolver(Map.of(
                leaf.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("sibling_leaf")),
                macro.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(sharedPayload)));

        var result = ClosedLoopPatternFlattener.flatten(List.of(
                new ClosedLoopMemberPattern(macro, 2L),
                new ClosedLoopMemberPattern(macro, 3L)), resolver);

        assertTrue(result.valid());
        assertEquals(2, result.members().size());
    }

    @Test
    void rejectsAnAncestorReferenceCycle() {
        var first = snapshot("cycle_first");
        var second = snapshot("cycle_second");
        var firstPayload = payload(UUID.randomUUID(), List.of(
                new ClosedLoopMemberPattern(second, 1L)), 1, 1);
        var secondPayload = payload(UUID.randomUUID(), List.of(
                new ClosedLoopMemberPattern(first, 1L)), 1, 1);
        var resolver = resolver(Map.of(
                first.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(firstPayload),
                second.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(secondPayload)));

        var result = ClosedLoopPatternFlattener.flatten(
                List.of(new ClosedLoopMemberPattern(first, 1L)), resolver);

        assertEquals(ClosedLoopPatternFlattener.Status.CYCLIC_REFERENCE, result.status());
    }

    @Test
    void rejectsNestingBeyondEightMacroBoundaries() {
        var resolved = new LinkedHashMap<ResourceLocation,
                ClosedLoopPatternFlattener.ResolvedMember>();
        var leaf = snapshot("depth_leaf");
        resolved.put(leaf.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                new FakePattern("depth_leaf")));
        var next = leaf;
        for (int depth = ClosedLoopPatternFlattener.MAX_NESTING_DEPTH; depth >= 0; depth--) {
            var macro = snapshot("depth_macro_" + depth);
            resolved.put(macro.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(
                    payload(UUID.randomUUID(), List.of(
                            new ClosedLoopMemberPattern(next, 1L)), 1, 1)));
            next = macro;
        }

        var result = ClosedLoopPatternFlattener.flatten(
                List.of(new ClosedLoopMemberPattern(next, 1L)), resolver(resolved));

        assertEquals(ClosedLoopPatternFlattener.Status.NESTING_TOO_DEEP, result.status());
    }

    @Test
    void rejectsMoreThanTheRuntimeMemberLimit() {
        var members = new java.util.ArrayList<ClosedLoopMemberPattern>();
        var resolved = new LinkedHashMap<ResourceLocation,
                ClosedLoopPatternFlattener.ResolvedMember>();
        for (int i = 0; i <= ClosedLoopPatternAnalyzer.MAX_MEMBERS; i++) {
            var leaf = snapshot("limit_leaf_" + i);
            members.add(new ClosedLoopMemberPattern(leaf, 1L));
            resolved.put(leaf.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                    new FakePattern("limit_leaf_" + i)));
        }
        var result = ClosedLoopPatternFlattener.flatten(members, resolver(resolved));

        assertEquals(ClosedLoopPatternFlattener.Status.TOO_MANY_MEMBERS, result.status());
    }

    @Test
    void rejectsExactCopyCountOverflow() {
        var first = snapshot("overflow_first");
        var second = snapshot("overflow_second");
        var macro = snapshot("overflow_macro");
        var resolver = resolver(Map.of(
                first.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("overflow_first")),
                second.itemId(), ClosedLoopPatternFlattener.ResolvedMember.leaf(
                        new FakePattern("overflow_second")),
                macro.itemId(), ClosedLoopPatternFlattener.ResolvedMember.macro(
                        payload(UUID.randomUUID(), List.of(
                                new ClosedLoopMemberPattern(first, 1L),
                                new ClosedLoopMemberPattern(second, 2L)), 1, 1))));

        var result = ClosedLoopPatternFlattener.flatten(List.of(
                new ClosedLoopMemberPattern(macro, Long.MAX_VALUE)), resolver);

        assertEquals(ClosedLoopPatternFlattener.Status.ARITHMETIC_OVERFLOW, result.status());
    }

    @Test
    void rejectsExecutionMemberPersistenceReferences() {
        var executionMember = snapshot("execution_member");

        var result = ClosedLoopPatternFlattener.flatten(
                List.of(new ClosedLoopMemberPattern(executionMember, 1L)),
                ignored -> ClosedLoopPatternFlattener.ResolvedMember.executionReference());

        assertEquals(
                ClosedLoopPatternFlattener.Status.EXECUTION_MEMBER_REFERENCE, result.status());
    }

    private static ClosedLoopPatternFlattener.MemberResolver resolver(
            Map<ResourceLocation, ClosedLoopPatternFlattener.ResolvedMember> resolved) {
        return snapshot -> resolved.get(snapshot.itemId());
    }

    private static ClosedLoopPatternPayload payload(
            UUID id, List<ClosedLoopMemberPattern> members,
            int executionMultiplier, int storedTaskMultiplier) {
        return new ClosedLoopPatternPayload(
                id, 1L, members,
                List.of(new GenericStack(new TestKey("seed"), 1L)),
                List.of(),
                List.of(new GenericStack(new TestKey("output"), 1L)),
                executionMultiplier, storedTaskMultiplier, true);
    }

    private static SourcePatternSnapshot snapshot(String id) {
        return new SourcePatternSnapshot(
                ResourceLocation.fromNamespaceAndPath("ae2lt_flatten_test", id), null, null);
    }

    private record FakePattern(String id) implements IPatternDetails {
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
            return ResourceLocation.fromNamespaceAndPath("ae2lt_flatten_test", id);
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
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_flatten_test", "key"),
                    TestKey.class, Component.literal("test key"));
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
