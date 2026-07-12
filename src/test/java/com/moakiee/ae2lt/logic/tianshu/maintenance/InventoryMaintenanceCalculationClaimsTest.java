package com.moakiee.ae2lt.logic.tianshu.maintenance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class InventoryMaintenanceCalculationClaimsTest {
    @Test
    void claimIsPerGridPerExactKeyAndExpiresAtLeaseBoundary() {
        var grid = grid();
        var otherGrid = grid();
        var key = new TestKey("diamond");
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();

        assertTrue(InventoryMaintenanceCalculationClaims.tryClaim(grid, key, owner, 100));
        assertTrue(InventoryMaintenanceCalculationClaims.tryClaim(grid, key, owner, 101));
        assertFalse(InventoryMaintenanceCalculationClaims.tryClaim(grid, key, other, 101));
        assertTrue(InventoryMaintenanceCalculationClaims.claimedByOther(grid, key, other, 1_300));
        assertFalse(InventoryMaintenanceCalculationClaims.claimedByOther(grid, key, other, 1_301));
        assertTrue(InventoryMaintenanceCalculationClaims.tryClaim(grid, key, other, 1_301));
        assertTrue(InventoryMaintenanceCalculationClaims.tryClaim(otherGrid, key, owner, 1_301));
    }

    @Test
    void releaseRequiresOwnerAndNullArgumentsNeverClaim() {
        var grid = grid();
        var key = new TestKey("emerald");
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();
        assertFalse(InventoryMaintenanceCalculationClaims.tryClaim(null, key, owner, 0));
        assertTrue(InventoryMaintenanceCalculationClaims.tryClaim(grid, key, owner, Long.MAX_VALUE));
        InventoryMaintenanceCalculationClaims.release(grid, key, other);
        assertTrue(InventoryMaintenanceCalculationClaims.claimedByOther(grid, key, other, Long.MAX_VALUE - 1));
        InventoryMaintenanceCalculationClaims.release(grid, key, owner);
        assertFalse(InventoryMaintenanceCalculationClaims.claimedByOther(grid, key, other, Long.MAX_VALUE - 1));
    }

    private static IGrid grid() {
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] {IGrid.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "grid@" + System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;
        private TestKey(String id) { this.id = id; }
        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag(); tag.putString("id", id); return tag;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("ae2lt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal(id); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return false; }
        @Override public boolean equals(Object obj) { return obj instanceof TestKey other && id.equals(other.id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("ae2lt_test", "claim_key"), TestKey.class,
                    Component.literal("test key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
