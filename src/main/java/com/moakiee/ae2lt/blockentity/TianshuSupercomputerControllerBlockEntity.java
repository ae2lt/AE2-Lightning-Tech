package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerStructureBlock;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanAttempt;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanResult;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreProfile;
import com.moakiee.ae2lt.logic.tianshu.CpuMainCoreTier;
import com.moakiee.ae2lt.logic.tianshu.TianshuFunctionProfile;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TianshuSupercomputerControllerBlockEntity extends BlockEntity {
    private static final long NO_SCAN = Long.MIN_VALUE;
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_PORT_POS = "PortPos";
    private static final String TAG_MIN_POS = "MinPos";
    private static final String TAG_MAX_POS = "MaxPos";
    private static final String TAG_MEMBER_COUNT = "MemberCount";
    private static final String TAG_MAIN_CORE = "MainCore";
    private static final String TAG_CAPACITY_CORES = "CapacityCores";
    private static final String TAG_PARALLEL_CORES = "ParallelCores";
    private static final String TAG_MAINTENANCE_CORES = "MaintenanceCores";
    private static final String TAG_CLOSED_LOOP_CORES = "ClosedLoopCores";
    private static final String TAG_CLOSED_LOOP_STORAGES = "ClosedLoopStorages";
    private static final String TAG_SEED_STORAGES = "SeedStorages";
    private boolean formed;
    private BlockPos portPos;
    private BlockPos minPos;
    private BlockPos maxPos;
    private int memberCount;
    private CpuInternalCoreProfile coreProfile = CpuInternalCoreProfile.empty();
    private TianshuFunctionProfile functionProfile = TianshuFunctionProfile.empty();
    private long scheduledScanTick = NO_SCAN;
    private List<TianshuMultiblockScanIssue> lastIssues = List.of();

    public TianshuSupercomputerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_SUPERCOMPUTER_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TianshuSupercomputerControllerBlockEntity controller) {
        if (controller.scheduledScanTick != NO_SCAN && level.getGameTime() >= controller.scheduledScanTick) {
            controller.scheduledScanTick = NO_SCAN;
            controller.scanNow();
        }
        if (controller.formed && controller.portPos != null
                && level.getBlockEntity(controller.portPos) instanceof TianshuSupercomputerPortBlockEntity port) {
            port.applyPendingProfile();
            port.tickTianshuFunctions();
        }
    }

    public boolean isFormed() {
        return formed;
    }

    public String issueText() {
        return lastIssues.isEmpty() ? profileText() : lastIssues.stream()
                .map(Object::toString).collect(Collectors.joining(", "));
    }

    private String profileText() {
        if (coreProfile.mainCore() == null) return "0";
        String storage = coreProfile.storageBytes() == Long.MAX_VALUE
                ? "∞" : Long.toString(coreProfile.storageBytes());
        return coreProfile.mainCore() + ", " + storage + " bytes, "
                + coreProfile.parallelism() + " parallel";
    }

    public CpuInternalCoreProfile getCoreProfile() {
        return coreProfile;
    }

    public TianshuFunctionProfile getFunctionProfile() {
        return functionProfile;
    }

    public int getPrimaryIssueOrdinal() {
        if (lastIssues.isEmpty()) return -1;
        return lastIssues.getFirst().ordinal();
    }

    public int memberCount() {
        return memberCount;
    }

    public void scheduleStructureCheck() {
        if (level != null && !level.isClientSide) {
            scheduledScanTick = level.getGameTime() + 1;
        }
    }

    public void scanNow() {
        if (level == null || level.isClientSide) return;
        Direction orientation = getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        TianshuMultiblockScanAttempt attempt = TianshuMultiblockScanner.scan(level, worldPosition, orientation);
        lastIssues = attempt.issues();
        if (attempt.formed()) form(attempt.result()); else deform();
    }

    private void form(TianshuMultiblockScanResult result) {
        if (formed && result.portPos().equals(portPos) && result.minPos().equals(minPos)
                && result.maxPos().equals(maxPos) && result.coreProfile().equals(coreProfile)
                && result.functionProfile().equals(functionProfile)) {
            for (BlockPos pos : result.members()) setMemberFormed(pos, true);
            syncControllerState();
            return;
        }
        clearStructureBindings();
        formed = true;
        portPos = result.portPos();
        minPos = result.minPos();
        maxPos = result.maxPos();
        memberCount = result.members().size();
        coreProfile = result.coreProfile();
        functionProfile = result.functionProfile();
        for (BlockPos pos : result.members()) setMemberFormed(pos, true);
        if (level.getBlockEntity(portPos) instanceof TianshuSupercomputerPortBlockEntity port) {
            port.bindToController(worldPosition, coreProfile, functionProfile);
        }
        syncControllerState();
    }

    private void deform() {
        clearStructureBindings();
        formed = false;
        portPos = null;
        minPos = null;
        maxPos = null;
        memberCount = 0;
        coreProfile = CpuInternalCoreProfile.empty();
        functionProfile = TianshuFunctionProfile.empty();
        syncControllerState();
    }

    public void clearStructureBindings() {
        if (level == null || minPos == null || maxPos == null) return;
        for (BlockPos mutable : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockPos pos = mutable.immutable();
            setMemberFormed(pos, false);
            if (level.getBlockEntity(pos) instanceof TianshuSupercomputerPortBlockEntity port
                    && worldPosition.equals(port.getControllerPos())) {
                port.bindToController(null);
            }
        }
    }

    private void setMemberFormed(BlockPos pos, boolean value) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(TianshuSupercomputerStructureBlock.FORMED)
                && state.getValue(TianshuSupercomputerStructureBlock.FORMED) != value) {
            level.setBlock(pos, state.setValue(TianshuSupercomputerStructureBlock.FORMED, value), Block.UPDATE_CLIENTS);
        } else if (state.hasProperty(TianshuSupercomputerPortBlock.FORMED)
                && state.getValue(TianshuSupercomputerPortBlock.FORMED) != value) {
            level.setBlock(pos, state.setValue(TianshuSupercomputerPortBlock.FORMED, value), Block.UPDATE_CLIENTS);
        }
    }

    private void syncControllerState() {
        BlockState state = getBlockState();
        if (state.getValue(TianshuSupercomputerControllerBlock.FORMED) != formed) {
            level.setBlock(worldPosition, state.setValue(TianshuSupercomputerControllerBlock.FORMED, formed), Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(TAG_FORMED, formed);
        if (portPos != null) tag.putLong(TAG_PORT_POS, portPos.asLong());
        if (minPos != null) tag.putLong(TAG_MIN_POS, minPos.asLong());
        if (maxPos != null) tag.putLong(TAG_MAX_POS, maxPos.asLong());
        tag.putInt(TAG_MEMBER_COUNT, memberCount);
        if (coreProfile.mainCore() != null) {
            tag.putString(TAG_MAIN_CORE, coreProfile.mainCore().name());
            tag.putInt(TAG_CAPACITY_CORES, coreProfile.capacityCoreCount());
            tag.putInt(TAG_PARALLEL_CORES, coreProfile.parallelCoreCount());
        }
        tag.putInt(TAG_MAINTENANCE_CORES, functionProfile.inventoryMaintenanceCoreCount());
        tag.putInt(TAG_CLOSED_LOOP_CORES, functionProfile.closedLoopPatternCoreCount());
        tag.putInt(TAG_CLOSED_LOOP_STORAGES, functionProfile.closedLoopPatternStorageCount());
        tag.putInt(TAG_SEED_STORAGES, functionProfile.closedLoopSeedStorageCount());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean(TAG_FORMED);
        portPos = tag.contains(TAG_PORT_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_PORT_POS)) : null;
        minPos = tag.contains(TAG_MIN_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_MIN_POS)) : null;
        maxPos = tag.contains(TAG_MAX_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_MAX_POS)) : null;
        memberCount = tag.getInt(TAG_MEMBER_COUNT);
        if (tag.contains(TAG_MAIN_CORE, Tag.TAG_STRING)) {
            try {
                var tier = CpuMainCoreTier.valueOf(tag.getString(TAG_MAIN_CORE));
                coreProfile = com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreCalculator.calculate(
                        tier, tag.getInt(TAG_CAPACITY_CORES), tag.getInt(TAG_PARALLEL_CORES));
            } catch (IllegalArgumentException ignored) {
                coreProfile = CpuInternalCoreProfile.empty();
            }
        }
        functionProfile = new TianshuFunctionProfile(
                Math.max(0, tag.getInt(TAG_MAINTENANCE_CORES)),
                Math.max(0, tag.getInt(TAG_CLOSED_LOOP_CORES)),
                Math.max(0, tag.getInt(TAG_CLOSED_LOOP_STORAGES)),
                Math.max(0, tag.getInt(TAG_SEED_STORAGES)));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleStructureCheck();
    }
}
