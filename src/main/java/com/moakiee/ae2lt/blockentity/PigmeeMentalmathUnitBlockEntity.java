package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.MachineSource;

import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class PigmeeMentalmathUnitBlockEntity extends AENetworkedBlockEntity
        implements TimeWheelCraftingCpuPoolHost {
    public static final long STORAGE_BYTES = 256L;
    public static final int PARALLELISM = 1;

    private static final String TAG_CPU_POOL = "CpuPool";

    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final TimeWheelCraftingCpuPool cpuPool = new TimeWheelCraftingCpuPool(
            this,
            STORAGE_BYTES,
            PARALLELISM);
    private long lastCpuDirtyTick = Long.MIN_VALUE;

    public PigmeeMentalmathUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGMEE_MENTALMATH_UNIT.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("pigmee_mentalmath_unit")
                .setVisualRepresentation(ModBlocks.PIGMEE_MENTALMATH_UNIT.get())
                .setIdlePowerUsage(1.0D);
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

    @Override
    public IActionSource getActionSource() {
        return actionSource;
    }

    @Override
    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public boolean isCpuActive() {
        return getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    @Override
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
        return Component.translatable("block.ae2lt.pigmee_mentalmath_unit");
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
        return ModBlocks.PIGMEE_MENTALMATH_UNIT.get().asItem();
    }
}
