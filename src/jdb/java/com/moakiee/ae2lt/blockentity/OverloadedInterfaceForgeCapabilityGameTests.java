package com.moakiee.ae2lt.blockentity;


import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_io")
@PrefixGameTestTemplate(false)
public final class OverloadedInterfaceForgeCapabilityGameTests {
    @GameTest(template = "wireless_io_empty")
    public static void resolvesReplacementCapabilityAndHonorsBoundFace(GameTestHelper helper) {
        var target = new Target();
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        state.storageBERef = new WeakReference<>(target);
        state.itemHandlerFace = Direction.NORTH;
        var original = state.resolveItemHandler();
        assertSame(target.handler, original);
        target.capability.invalidate();
        target.handler = new ItemStackHandler(2);
        target.capability = LazyOptional.of(() -> target.handler);
        assertSame(target.handler, state.resolveItemHandler());
        assertNotSame(original, state.resolveItemHandler());
        state.itemHandlerFace = Direction.SOUTH;
        assertNull(state.resolveItemHandler());
        state.itemHandlerFace = Direction.NORTH;
        target.setRemoved();
        assertNull(state.resolveItemHandler());
        state.storageBERef.clear();
        assertNull(state.resolveItemHandler());
        helper.succeed();
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new IllegalStateException("capability identity mismatch");
    }

    private static void assertNotSame(Object expected, Object actual) {
        if (expected == actual) throw new IllegalStateException("stale capability retained");
    }

    private static void assertNull(Object actual) {
        if (actual != null) throw new IllegalStateException("unavailable capability exposed");
    }

    private static final class Target extends BlockEntity {
        IItemHandler handler = new ItemStackHandler(1);
        LazyOptional<IItemHandler> capability = LazyOptional.of(() -> handler);

        Target() {
            super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return cap == ForgeCapabilities.ITEM_HANDLER && side == Direction.NORTH
                    ? capability.cast() : LazyOptional.empty();
        }
    }
}
