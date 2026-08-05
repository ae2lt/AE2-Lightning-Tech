package com.moakiee.ae2lt.crafting.timewheel;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;

public final class TimeWheelCraftingCPU implements ICraftingCPU {
    private final TimeWheelCraftingCpuHost host;
    private final long storageBytes;
    private final int coProcessors;
    private final long maxCopiesPerTick;
    private final boolean unboundedBatch;
    private final Ae2LtTimeWheelCraftingCpuLogic craftingLogic = new Ae2LtTimeWheelCraftingCpuLogic(this);

    private GenericStack finalOutput;

    public TimeWheelCraftingCPU(TimeWheelCraftingCpuHost host,
                                long storageBytes,
                                int coProcessors,
                                long maxCopiesPerTick,
                                boolean unboundedBatch) {
        this.host = host;
        this.storageBytes = storageBytes;
        this.coProcessors = coProcessors;
        this.maxCopiesPerTick = Math.max(1L, maxCopiesPerTick);
        this.unboundedBatch = unboundedBatch;
    }

    public Ae2LtTimeWheelCraftingCpuLogic getCraftingLogic() {
        return craftingLogic;
    }

    public TimeWheelCraftingCpuHost getHost() {
        return host;
    }

    @Override
    public boolean isBusy() {
        return craftingLogic.hasPersistentState();
    }

    @Nullable
    @Override
    public CraftingJobStatus getJobStatus() {
        var output = craftingLogic.getFinalJobOutput();
        if (output == null) {
            return null;
        }

        var elapsedTimeTracker = craftingLogic.getElapsedTimeTracker();
        var progress = Math.max(
                0,
                elapsedTimeTracker.getStartItemCount() - elapsedTimeTracker.getRemainingItemCount());
        return new CraftingJobStatus(
                output,
                elapsedTimeTracker.getStartItemCount(),
                progress,
                elapsedTimeTracker.getElapsedTime());
    }

    @Override
    public void cancelJob() {
        craftingLogic.cancel();
    }

    @Override
    public long getAvailableStorage() {
        return storageBytes;
    }

    @Override
    public int getCoProcessors() {
        return coProcessors;
    }

    public int getSuccessfulDispatchesPerTick() {
        return coProcessors >= Integer.MAX_VALUE - 1 ? Integer.MAX_VALUE : coProcessors + 1;
    }

    public long getMaxCopiesPerTick() {
        return maxCopiesPerTick;
    }

    public boolean hasUnboundedBatch() {
        return unboundedBatch;
    }

    @Nullable
    @Override
    public Component getName() {
        return host.getDisplayName();
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return host.getSelectionMode();
    }

    public boolean isActive() {
        return host.isCpuActive();
    }

    @Nullable
    public IGrid getGrid() {
        return host.getGrid();
    }

    public IActionSource getSrc() {
        return host.getActionSource();
    }

    public Level getLevel() {
        return host.getLevel();
    }

    public boolean canBeAutoSelectedFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    public boolean isPreferredFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> false;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    public ICraftingSubmitResult submitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
                                           @Nullable ICraftingRequester requester) {
        return craftingLogic.trySubmitJob(grid, plan, src, requester);
    }

    public void updateOutput(@Nullable GenericStack stack) {
        if (stack != null && stack.amount() <= 0) {
            stack = null;
        }
        this.finalOutput = stack;
    }

    @Nullable
    public GenericStack getDisplayedOutput() {
        return finalOutput;
    }

    public void markDirty() {
        host.markCpuDirty();
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        craftingLogic.writeToNBT(tag, registries);
    }

    public boolean hasPersistentState() {
        return craftingLogic.hasPersistentState();
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        craftingLogic.readFromNBT(tag, registries);
    }

    public void resolvePendingLoad() {
        craftingLogic.resolvePendingLoad();
    }

    public void addRemovalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        craftingLogic.addStoredDrops(level, pos, drops);
    }

    public void clearRemovedContent() {
        craftingLogic.clearRemovedContent();
    }

    void tryReleaseContents() {
        craftingLogic.tryReleaseContents();
    }
}
