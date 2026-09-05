package com.moakiee.ae2lt.logic.tianshu.maintenance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class MaintenanceRequestabilityTest {
    private static final AEKey KEY = new TestKey();

    @Test
    void encodedPatternOutputIsRequestable() {
        assertTrue(MaintenanceRequestability.isRequestable(crafting(true, false), KEY));
    }

    @Test
    void craftingEmitterOutputIsRequestableWithoutEncodedPattern() {
        assertTrue(MaintenanceRequestability.isRequestable(crafting(false, true), KEY));
    }

    @Test
    void unavailableOutputAndNullInputsAreRejected() {
        assertFalse(MaintenanceRequestability.isRequestable(crafting(false, false), KEY));
        assertFalse(MaintenanceRequestability.isRequestable(null, KEY));
        assertFalse(MaintenanceRequestability.isRequestable(crafting(true, true), null));
    }

    private static ICraftingService crafting(boolean pattern, boolean emitter) {
        return (ICraftingService) Proxy.newProxyInstance(
                ICraftingService.class.getClassLoader(),
                new Class<?>[] {ICraftingService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isCraftable" -> pattern;
                    case "canEmitFor" -> emitter;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "crafting-service";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public CompoundTag toTag() { return new CompoundTag(); }
        @Override public Object getPrimaryKey() { return "emitter_target"; }
        @Override public ResourceLocation getId() {
            return new ResourceLocation("ae2lt_test", "emitter_target");
        }
        @Override public void writeToPacket(FriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() { return Component.literal("emitter_target"); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(new ResourceLocation("ae2lt_test", "requestability_key"),
                    TestKey.class, Component.literal("test key"));
        }
        @Override public AEKey loadKeyFromTag(CompoundTag tag) { return null; }
        @Override public AEKey readFromPacket(FriendlyByteBuf input) { return null; }
    }
}
