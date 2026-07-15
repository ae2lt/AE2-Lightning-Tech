package com.moakiee.thunderbolt.ae2.overload.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class OverloadSeedRoutingTest {
    @Test
    void idOnlyVariantKeepsTheReusableSeedPrefixBeforeRoutingFinalOutput() {
        var fixture = fixture();
        var seedGroup = UUID.randomUUID();
        fixture.state().registerExpectedOutput(
                fixture.reference(), 0, fixture.itemId(), fixture.expectedKey(), 3L, true,
                new OverloadReusableSeedMetadata(seedGroup, true, 1L));

        var returnedVariant = key("pickaxe", "returned_damage");
        assertNotEquals(fixture.expectedKey(), returnedVariant);
        assertEquals(fixture.expectedKey().dropSecondary(), returnedVariant.dropSecondary());

        var result = fixture.state().claimByItemId(returnedVariant.getId(), 3L, true);

        assertEquals(3L, result.claimedAmount());
        assertEquals(1L, result.claimedForInventory(), "seed portion must stay in the CPU");
        assertEquals(2L, result.claimedForRequester());
        assertEquals(1, result.claims().size());
        var claim = result.claims().getFirst();
        assertEquals(fixture.expectedKey(), claim.exactExpectedKey());
        assertEquals(1L, claim.reusableSeedAmount());
        assertEquals(seedGroup, claim.reusableSeedGroupId());
        assertTrue(claim.sharedReusableSeedPool());
        assertTrue(fixture.state().isEmpty());
    }

    @Test
    void simulationDoesNotConsumePendingOutputOrItsSeedPrefix() {
        var fixture = fixture();
        var seedGroup = UUID.randomUUID();
        fixture.state().registerExpectedOutput(
                fixture.reference(), 0, fixture.itemId(), fixture.expectedKey(), 4L, true,
                new OverloadReusableSeedMetadata(seedGroup, false, 2L));

        var simulated = fixture.state().claimByItemId(fixture.itemId(), 3L, false);

        assertEquals(2L, simulated.claimedForInventory());
        assertEquals(1L, simulated.claimedForRequester());
        var stillPending = fixture.state().allPending().iterator().next();
        assertEquals(4L, stillPending.remainingAmount());
        assertEquals(2L, stillPending.remainingReusableSeedAmount());

        var firstRealReturn = fixture.state().claimByItemId(fixture.itemId(), 1L, true);
        assertEquals(1L, firstRealReturn.claimedForInventory());
        assertEquals(0L, firstRealReturn.claimedForRequester());
        stillPending = fixture.state().allPending().iterator().next();
        assertEquals(3L, stillPending.remainingAmount());
        assertEquals(1L, stillPending.remainingReusableSeedAmount());
    }

    @Test
    void repeatedHugeRegistrationsSaturateInsteadOfWrappingPendingCounts() {
        var fixture = fixture();
        var metadata = new OverloadReusableSeedMetadata(
                UUID.randomUUID(), false, Long.MAX_VALUE);

        fixture.state().registerExpectedOutput(
                fixture.reference(), 0, fixture.itemId(), fixture.expectedKey(),
                Long.MAX_VALUE, false, metadata);
        fixture.state().registerExpectedOutput(
                fixture.reference(), 0, fixture.itemId(), fixture.expectedKey(),
                Long.MAX_VALUE, false, metadata);

        var pending = fixture.state().allPending().iterator().next();
        assertEquals(Long.MAX_VALUE, pending.remainingAmount());
        assertEquals(Long.MAX_VALUE, pending.remainingReusableSeedAmount());
    }

    @Test
    void mergedPendingOutputCannotSilentlyChangeItsDedicatedSeedOwner() {
        var fixture = fixture();
        fixture.state().registerExpectedOutput(
                fixture.reference(), 0, fixture.itemId(), fixture.expectedKey(), 1L, false,
                new OverloadReusableSeedMetadata(UUID.randomUUID(), false, 1L));

        assertThrows(IllegalArgumentException.class, () -> fixture.state().registerExpectedOutput(
                fixture.reference(), 0, fixture.itemId(), fixture.expectedKey(), 1L, false,
                new OverloadReusableSeedMetadata(UUID.randomUUID(), false, 1L)));
    }

    private static Fixture fixture() {
        var craftingId = UUID.randomUUID();
        var logic = new Object();
        var state = new OverloadCpuState(OverloadCpuOwner.from(craftingId, logic));
        var source = new SourcePatternSnapshot(
                ResourceLocation.fromNamespaceAndPath("ae2lt_test", "overload_pattern"),
                null,
                null);
        return new Fixture(
                logic,
                state,
                new OverloadPatternReference("test:overload-seed", source),
                ResourceLocation.fromNamespaceAndPath("ae2lt_test", "pickaxe"),
                key("pickaxe", "expected_damage"));
    }

    private static TestKey key(String id, String secondary) {
        return new TestKey(id, secondary);
    }

    /** Keeps the weakly referenced owner alive for the duration of each test. */
    private record Fixture(
            Object logic,
            OverloadCpuState state,
            OverloadPatternReference reference,
            ResourceLocation itemId,
            TestKey expectedKey) {
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
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "overload_key"),
                    TestKey.class, Component.literal("overload key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
