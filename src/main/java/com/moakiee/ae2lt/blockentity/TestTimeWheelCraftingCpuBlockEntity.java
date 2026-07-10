package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.MachineSource;

import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class TestTimeWheelCraftingCpuBlockEntity extends AENetworkedBlockEntity implements TimeWheelCraftingCpuPoolHost {
    public static final long STORAGE_BYTES = Long.MAX_VALUE;
    public static final int PARALLELISM = 16_384;

    private static final String TAG_CPU_POOL = "cpuPool";

    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final TimeWheelCraftingCpuPool cpuPool = new TimeWheelCraftingCpuPool(
            this,
            STORAGE_BYTES,
            PARALLELISM);
    private long lastCpuDirtyTick = Long.MIN_VALUE;

    public TestTimeWheelCraftingCpuBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEST_TIME_WHEEL_CRAFTING_CPU.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("test_time_wheel_crafting_cpu")
                .setVisualRepresentation(ModBlocks.TEST_TIME_WHEEL_CRAFTING_CPU.get())
                .setIdlePowerUsage(16.0D);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool() {
        return cpuPool;
    }

    public IActionSource getActionSource() {
        return actionSource;
    }

    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    public boolean isCpuActive() {
        return getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    public void markCpuDirty() {
        long now = TickHandler.instance().getCurrentTick();
        if (lastCpuDirtyTick == now) {
            return;
        }
        lastCpuDirtyTick = now;
        saveChanges();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ae2lt.test_time_wheel_crafting_cpu");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!cpuPool.hasPersistentState()) {
            return;
        }

        var poolTag = new CompoundTag();
        cpuPool.writeToNBT(poolTag, registries);
        if (!poolTag.isEmpty()) {
            tag.put(TAG_CPU_POOL, poolTag);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.contains(TAG_CPU_POOL, CompoundTag.TAG_COMPOUND)) {
            cpuPool.readFromNBT(tag.getCompound(TAG_CPU_POOL), registries);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        cpuPool.resolvePendingLoad();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        cpuPool.addRemovalDrops(level, pos, drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        cpuPool.clearRemovedContent();
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.TEST_TIME_WHEEL_CRAFTING_CPU.get().asItem();
    }
}
