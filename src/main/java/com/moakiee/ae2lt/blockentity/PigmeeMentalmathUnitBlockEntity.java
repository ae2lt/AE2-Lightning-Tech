package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.MachineSource;

import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPoolHost;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPoolProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.RegistryAccess;

/**
 * Thunderbolt-backed CPU host for the Pigmee mental arithmetic unit. AE2 indexes machine owners by
 * exact class, so a CraftingBlockEntity subclass cannot participate in its base-class CPU scan.
 */
public final class PigmeeMentalmathUnitBlockEntity extends AENetworkBlockEntity
        implements TimeWheelCraftingCpuPoolHost {
    public static final long STORAGE_BYTES = 256L;
    public static final int PARALLELISM = 1;
    private static final String TAG_CPU_POOL = "CpuPool";

    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final TimeWheelCraftingCpuPool cpuPool = new TimeWheelCraftingCpuPool(
            this,
            STORAGE_BYTES,
            PARALLELISM,
            1L,
            false);
    private long lastCpuDirtyTick = Long.MIN_VALUE;

    public PigmeeMentalmathUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGMEE_MENTALMATH_UNIT.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("pigmee_mentalmath_unit")
                .setVisualRepresentation(ModBlocks.PIGMEE_MENTALMATH_UNIT.get())
                .setIdlePowerUsage(1.0D)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(
                        com.moakiee.thunderbolt.api.crafting.cpu.ExtendedCraftingCpuClusterProvider.class,
                        this)
                .addService(TimeWheelCraftingCpuPoolProvider.class, this);
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
        if (lastCpuDirtyTick != now) {
            lastCpuDirtyTick = now;
            saveChanges();
        }
    }

    @Override
    public Component getCpuDisplayName() {
        return Component.translatable("block.ae2lt.pigmee_mentalmath_unit");
    }

    @Nullable
    @Override
    public Level getCpuLevel() {
        // Named getCpuLevel (not getLevel): getLevel collides with BlockEntity.getLevel,
        // so host impls get SRG-remapped while the interface method keeps its Mojang name,
        // producing an AbstractMethodError across the two mod jars.
        return level;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.remove(TAG_CPU_POOL);
        if (cpuPool.hasPersistentState()) {
            var poolTag = new CompoundTag();
            cpuPool.writeToNBT(poolTag, level != null ? level.registryAccess() : null);
            if (!poolTag.isEmpty()) tag.put(TAG_CPU_POOL, poolTag);
        }
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        cpuPool.readFromNBT(
                tag.contains(TAG_CPU_POOL, Tag.TAG_COMPOUND)
                        ? tag.getCompound(TAG_CPU_POOL) : new CompoundTag(),
                level != null ? level.registryAccess() : null);
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
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.PIGMEE_MENTALMATH_UNIT.get().asItem();
    }
}

