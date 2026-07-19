package com.moakiee.ae2lt.blockentity;

import java.util.List;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.MachineSource;

import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A vanilla AE2 crafting unit with 256 bytes of storage and no extra co-processors. */
public final class PigmeeMentalmathUnitBlockEntity extends CraftingBlockEntity {
    private static final String TAG_LEGACY_CPU_POOL = "CpuPool";

    /**
     * The migration host is deliberately not the block entity. This lets us decode and cancel the
     * old split-CPU state without exposing a second CPU cluster from the new vanilla crafting unit.
     */
    private final LegacyCpuMigrationHost legacyMigrationHost = new LegacyCpuMigrationHost();
    private final TimeWheelCraftingCpuPool legacyCpuPool = new TimeWheelCraftingCpuPool(
            legacyMigrationHost,
            com.moakiee.ae2lt.logic.craft.PigmeeCraftingUnitType.STORAGE_BYTES,
            1,
            1L,
            false);
    private boolean legacyCpuMigrationPending;
    private boolean legacyCpuMigrationScheduled;

    public PigmeeMentalmathUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGMEE_MENTALMATH_UNIT.get(), pos, state);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        legacyCpuMigrationScheduled = false;
        legacyCpuMigrationPending = loadLegacyCpuState(legacyCpuPool, tag, registries);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (legacyCpuMigrationPending && legacyCpuPool.hasPersistentState()) {
            var poolTag = new CompoundTag();
            legacyCpuPool.writeToNBT(poolTag, registries);
            if (!poolTag.isEmpty()) {
                tag.put(TAG_LEGACY_CPU_POOL, poolTag);
            }
        } else {
            tag.remove(TAG_LEGACY_CPU_POOL);
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        scheduleLegacyCpuMigration();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State state) {
        super.onMainNodeStateChanged(state);
        scheduleLegacyCpuMigration();
    }

    private void scheduleLegacyCpuMigration() {
        if (legacyCpuMigrationScheduled || !isLegacyMigrationReady(
                legacyCpuMigrationPending,
                level != null && !level.isClientSide,
                getMainNode().isActive(),
                getMainNode().getGrid() != null)) {
            return;
        }
        legacyCpuMigrationScheduled = true;
        TickHandler.instance().addCallable(level, () -> {
            legacyCpuMigrationScheduled = false;
            if (level == null || isRemoved() || !level.isLoaded(worldPosition)
                    || level.getBlockEntity(worldPosition) != this
                    || !isLegacyMigrationReady(
                            legacyCpuMigrationPending,
                            !level.isClientSide,
                            getMainNode().isActive(),
                            getMainNode().getGrid() != null)) {
                return;
            }
            migrateLegacyCpu();
        });
    }

    private void migrateLegacyCpu() {
        var drops = new java.util.ArrayList<ItemStack>();
        recoverLegacyCpu(level, worldPosition, drops);
        spawnDrops(drops);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (legacyCpuMigrationPending) {
            recoverLegacyCpu(level, pos, drops);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        if (legacyCpuMigrationPending) {
            if (level != null && !level.isClientSide) {
                var drops = new java.util.ArrayList<ItemStack>();
                recoverLegacyCpu(level, worldPosition, drops);
                spawnDrops(drops);
            } else {
                // Client-side content is only a mirror. Do not leave a decoded legacy pool around
                // after vanilla asks this block entity to clear itself.
                legacyCpuPool.clearRemovedContent();
                legacyCpuMigrationPending = false;
                legacyCpuMigrationScheduled = false;
            }
        }
    }

    private void recoverLegacyCpu(Level removalLevel, BlockPos removalPos, List<ItemStack> drops) {
        // Restore the serialized link first so cancellation reaches CraftingService. Then return
        // everything the connected network accepts before converting only the residue into drops.
        legacyCpuPool.resolvePendingLoad();
        legacyCpuPool.tryReleaseContents();
        legacyCpuPool.addRemovalDrops(removalLevel, removalPos, drops);
        legacyCpuPool.clearRemovedContent();
        legacyCpuMigrationPending = false;
        legacyCpuMigrationScheduled = false;
        saveChanges();
    }

    private void spawnDrops(List<ItemStack> drops) {
        for (var drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, worldPosition, drop);
            }
        }
    }

    static boolean loadLegacyCpuState(
            TimeWheelCraftingCpuPool pool,
            CompoundTag ownerTag,
            HolderLookup.Provider registries) {
        var poolTag = ownerTag.contains(TAG_LEGACY_CPU_POOL, Tag.TAG_COMPOUND)
                ? ownerTag.getCompound(TAG_LEGACY_CPU_POOL)
                : new CompoundTag();
        // loadTag can be called more than once; an absent legacy tag must clear any earlier state.
        pool.readFromNBT(poolTag, registries);
        return pool.hasPersistentState();
    }

    static boolean isLegacyMigrationReady(
            boolean pending,
            boolean serverSide,
            boolean nodeActive,
            boolean gridAvailable) {
        return pending && serverSide && nodeActive && gridAvailable;
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.PIGMEE_MENTALMATH_UNIT.get().asItem();
    }

    private final class LegacyCpuMigrationHost implements TimeWheelCraftingCpuPoolHost {
        private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);

        @Override
        public TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool() {
            return legacyCpuPool;
        }

        @Override
        public boolean isCpuActive() {
            return false;
        }

        @Override
        public IGrid getGrid() {
            return getMainNode().getGrid();
        }

        @Override
        public IActionSource getActionSource() {
            return actionSource;
        }

        @Override
        public Level getLevel() {
            return PigmeeMentalmathUnitBlockEntity.this.getLevel();
        }

        @Override
        public void markCpuDirty() {
            saveChanges();
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("block.ae2lt.pigmee_mentalmath_unit");
        }
    }
}
