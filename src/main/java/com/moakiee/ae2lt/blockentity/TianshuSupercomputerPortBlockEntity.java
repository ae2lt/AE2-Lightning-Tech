package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreProfile;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.MachineSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TianshuSupercomputerPortBlockEntity extends AENetworkedBlockEntity
        implements TimeWheelCraftingCpuPoolHost {
    private static final String TAG_CONTROLLER_POS = "ControllerPos";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_CPU_POOL = "CpuPool";
    private static final String TAG_CPU_STORAGE = "CpuStorage";
    private static final String TAG_CPU_PARALLEL = "CpuParallel";
    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final TimeWheelCraftingCpuPool cpuPool = new TimeWheelCraftingCpuPool(this);
    private BlockPos controllerPos;
    private boolean formed;
    private long lastCpuDirtyTick = Long.MIN_VALUE;
    private long pendingStorage = -1L;
    private int pendingParallel = -1;

    public TianshuSupercomputerPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_SUPERCOMPUTER_PORT.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("tianshu_supercomputer_port")
                .setVisualRepresentation(ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get())
                .setIdlePowerUsage(8.0D);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return formed ? AECableType.DENSE_SMART : AECableType.NONE;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? EnumSet.allOf(Direction.class) : Collections.emptySet();
    }

    public void bindToController(BlockPos controllerPos) {
        bindToController(controllerPos, CpuInternalCoreProfile.empty());
    }

    public void bindToController(BlockPos controllerPos, CpuInternalCoreProfile profile) {
        this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
        this.formed = controllerPos != null;
        if (formed && profile.mainCore() != null
                && (cpuPool.getTotalStorage() != profile.storageBytes()
                || cpuPool.getCoProcessors() != profile.parallelism())) {
            pendingStorage = profile.storageBytes();
            pendingParallel = profile.parallelism();
            applyPendingProfile();
        }
        if (level != null && !level.isClientSide) {
            var state = getBlockState();
            if (state.hasProperty(TianshuSupercomputerPortBlock.FORMED)
                    && state.getValue(TianshuSupercomputerPortBlock.FORMED) != formed) {
                level.setBlock(worldPosition, state.setValue(TianshuSupercomputerPortBlock.FORMED, formed), Block.UPDATE_ALL);
            }
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
        saveChanges();
        markForUpdate();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public boolean isFormed() {
        return formed;
    }

    @Override
    public TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool() {
        return cpuPool;
    }

    @Override
    public boolean isCpuActive() {
        return formed && getController() != null && getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    @Override
    public IGrid getGrid() {
        return formed ? getMainNode().getGrid() : null;
    }

    @Override
    public IActionSource getActionSource() {
        return actionSource;
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
    public Component getDisplayName() {
        return Component.translatable("block.ae2lt.tianshu_supercomputer_controller");
    }

    public TianshuSupercomputerControllerBlockEntity getController() {
        if (!formed || controllerPos == null || level == null || !level.isLoaded(controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof TianshuSupercomputerControllerBlockEntity controller
                ? controller : null;
    }

    public void applyPendingProfile() {
        if (pendingStorage >= 0L && !cpuPool.hasPersistentState()) {
            cpuPool.reconfigure(pendingStorage, pendingParallel);
            pendingStorage = -1L;
            pendingParallel = -1;
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong(TAG_CONTROLLER_POS, controllerPos.asLong());
        tag.putBoolean(TAG_FORMED, formed);
        tag.putLong(TAG_CPU_STORAGE, cpuPool.getTotalStorage());
        tag.putInt(TAG_CPU_PARALLEL, cpuPool.getCoProcessors());
        if (cpuPool.hasPersistentState()) {
            var poolTag = new CompoundTag();
            cpuPool.writeToNBT(poolTag, registries);
            if (!poolTag.isEmpty()) tag.put(TAG_CPU_POOL, poolTag);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        controllerPos = tag.contains(TAG_CONTROLLER_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_CONTROLLER_POS)) : null;
        formed = tag.getBoolean(TAG_FORMED) && controllerPos != null;
        if (tag.contains(TAG_CPU_STORAGE, Tag.TAG_LONG)) {
            cpuPool.reconfigure(Math.max(0L, tag.getLong(TAG_CPU_STORAGE)), Math.max(0, tag.getInt(TAG_CPU_PARALLEL)));
        }
        if (tag.contains(TAG_CPU_POOL, Tag.TAG_COMPOUND)) {
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
        return ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().asItem();
    }
}
