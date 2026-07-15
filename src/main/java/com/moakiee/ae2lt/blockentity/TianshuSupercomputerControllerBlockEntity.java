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
import com.moakiee.ae2lt.logic.tianshu.TianshuCraftingCpuHost;
import com.moakiee.ae2lt.logic.tianshu.loop.Ae2ClosedLoopPatternDetails;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceHost;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceService;
import com.moakiee.ae2lt.logic.persistence.ControllerMachineIdentity;
import com.moakiee.ae2lt.logic.persistence.ControllerMachineStateSavedData;
import com.moakiee.ae2lt.logic.persistence.ControllerMachineStateSavedData.MachineType;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.ae2.timewheel.ReusableSeedPattern;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableSet;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TianshuSupercomputerControllerBlockEntity extends BlockEntity
        implements TimeWheelCraftingCpuPoolHost, TianshuInventoryMaintenanceHost,
        TianshuCraftingCpuHost {
    private static final long NO_SCAN = Long.MIN_VALUE;
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_PORT_POS = "PortPos";
    private static final String TAG_MIN_POS = "MinPos";
    private static final String TAG_MAX_POS = "MaxPos";
    private static final String TAG_MEMBER_COUNT = "MemberCount";
    private static final String TAG_MAIN_CORE = "MainCore";
    private static final String TAG_CAPACITY_CORES = "CapacityCores";
    private static final String TAG_PARALLEL_CORES = "ParallelCores";
    private static final String TAG_CLOSED_LOOP_STORAGES = "ClosedLoopStorages";
    private static final String TAG_SEED_STORAGES = "SeedStorages";
    private static final String TAG_MACHINE_ID = "MachineId";
    private static final String TAG_CPU_POOL = "CpuPool";
    private static final String TAG_MAINTENANCE = "InventoryMaintenance";
    private boolean formed;
    private BlockPos portPos;
    private BlockPos minPos;
    private BlockPos maxPos;
    private int memberCount;
    private CpuInternalCoreProfile coreProfile = CpuInternalCoreProfile.empty();
    private TianshuFunctionProfile functionProfile = TianshuFunctionProfile.empty();
    private final TimeWheelCraftingCpuPool cpuPool = new TimeWheelCraftingCpuPool(this);
    private final ClosedLoopPatternRepository closedLoopPatterns =
            new ClosedLoopPatternRepository(() -> functionProfile.closedLoopPatternCapacity());
    private final TianshuInventoryMaintenanceService maintenance =
            new TianshuInventoryMaintenanceService(this);
    private List<BlockPos> patternStoragePositions = List.of();
    private List<BlockPos> seedStoragePositions = List.of();
    private UUID machineId = UUID.randomUUID();
    private boolean identityInitialized;
    private boolean persistentStateOwner;
    private UUID loadedRuntimeId;
    private long lastCpuDirtyTick = Long.MIN_VALUE;
    private long pendingStorage = -1L;
    private int pendingParallel = -1;
    private long scheduledScanTick = NO_SCAN;
    private List<TianshuMultiblockScanIssue> lastIssues = List.of();

    public TianshuSupercomputerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_SUPERCOMPUTER_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TianshuSupercomputerControllerBlockEntity controller) {
        if (!controller.persistentStateOwner) {
            if (controller.formed) controller.deform();
            return;
        }
        if (controller.scheduledScanTick != NO_SCAN && level.getGameTime() >= controller.scheduledScanTick) {
            controller.scheduledScanTick = NO_SCAN;
            controller.scanNow();
        }
        if (controller.formed && controller.portPos != null
                && level.getBlockEntity(controller.portPos) instanceof TianshuSupercomputerPortBlockEntity port) {
            controller.applyPendingProfile();
            controller.maintenance.tick();
            controller.persistRuntimeStateIfChanged();
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

    public UUID getMachineId() {
        return machineId;
    }

    @Override
    public UUID getTianshuId() {
        return machineId;
    }

    public boolean isPersistentStateOwner() {
        return persistentStateOwner;
    }

    public void initializeIdentityFromItem(ItemStack stack) {
        var itemId = ControllerMachineIdentity.read(stack);
        identityInitialized = true;
        if (itemId != null) changeMachineId(itemId);
        claimPersistentState();
        setChanged();
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
        if (!persistentStateOwner) {
            deform();
            return;
        }
        Direction orientation = getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        TianshuMultiblockScanAttempt attempt = TianshuMultiblockScanner.scan(level, worldPosition, orientation);
        lastIssues = attempt.issues();
        if (attempt.formed()) form(attempt.result()); else deform();
    }

    private void form(TianshuMultiblockScanResult result) {
        if (!identityInitialized
                && level.getBlockEntity(result.portPos()) instanceof TianshuSupercomputerPortBlockEntity port
                && port.getLegacyTianshuId() != null) {
            identityInitialized = true;
            changeMachineId(port.getLegacyTianshuId());
        }
        if (!persistentStateOwner) {
            deform();
            return;
        }
        if (formed && result.portPos().equals(portPos) && result.minPos().equals(minPos)
                && result.maxPos().equals(maxPos) && result.coreProfile().equals(coreProfile)
                && result.functionProfile().equals(functionProfile)
                && result.patternStoragePositions().equals(patternStoragePositions)
                && result.seedStoragePositions().equals(seedStoragePositions)) {
            for (BlockPos pos : result.members()) setMemberFormed(pos, true);
            bindFunctionalMembers(result);
            syncControllerState();
            return;
        }
        persistRuntimeStateIfChanged();
        clearStructureBindings();
        formed = true;
        portPos = result.portPos();
        minPos = result.minPos();
        maxPos = result.maxPos();
        memberCount = result.members().size();
        coreProfile = result.coreProfile();
        functionProfile = result.functionProfile();
        patternStoragePositions = result.patternStoragePositions();
        seedStoragePositions = result.seedStoragePositions();
        for (BlockPos pos : result.members()) setMemberFormed(pos, true);
        bindFunctionalMembers(result);
        syncControllerState();
    }

    private void bindFunctionalMembers(TianshuMultiblockScanResult result) {
        if (level.getBlockEntity(result.portPos()) instanceof TianshuSupercomputerPortBlockEntity port) {
            prepareRuntime(port);
            port.bindToController(worldPosition, machineId, cpuPool);
            for (var patternStoragePos : result.patternStoragePositions()) {
                if (level.getBlockEntity(patternStoragePos) instanceof TianshuPatternStorageBlockEntity storage) {
                    storage.bindToPort(portPos);
                }
            }
            for (var seedStoragePos : result.seedStoragePositions()) {
                if (level.getBlockEntity(seedStoragePos) instanceof TianshuSeedStorageBlockEntity drive) {
                    drive.bindToPort(portPos);
                }
            }
            loadPatternsFromWarehouses(port);
            maintenance.functionCapacityChanged();
            cpuPool.resolvePendingLoad();
        }
    }

    private void deform() {
        persistRuntimeStateIfChanged();
        clearStructureBindings();
        formed = false;
        portPos = null;
        minPos = null;
        maxPos = null;
        memberCount = 0;
        coreProfile = CpuInternalCoreProfile.empty();
        functionProfile = TianshuFunctionProfile.empty();
        patternStoragePositions = List.of();
        seedStoragePositions = List.of();
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
            } else if (level.getBlockEntity(pos) instanceof TianshuPatternStorageBlockEntity storage) {
                storage.bindToPort(null);
            } else if (level.getBlockEntity(pos) instanceof TianshuSeedStorageBlockEntity drive) {
                drive.bindToPort(null);
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

    private void prepareRuntime(TianshuSupercomputerPortBlockEntity port) {
        if (!machineId.equals(loadedRuntimeId)) {
            maintenance.shutdownCalculations();
            clearTransientRuntime();
            cpuPool.reconfigure(coreProfile.storageBytes(), coreProfile.parallelism());
            loadRuntimeState(port);
        } else if (cpuPool.getTotalStorage() != coreProfile.storageBytes()
                || cpuPool.getCoProcessors() != coreProfile.parallelism()) {
            pendingStorage = coreProfile.storageBytes();
            pendingParallel = coreProfile.parallelism();
            applyPendingProfile();
        }
    }

    private void applyPendingProfile() {
        if (pendingStorage >= 0L && !cpuPool.hasPersistentState()) {
            cpuPool.reconfigure(pendingStorage, pendingParallel);
            pendingStorage = -1L;
            pendingParallel = -1;
        }
    }

    public ClosedLoopPatternRepository getClosedLoopPatternRepository() {
        return closedLoopPatterns;
    }

    public TianshuInventoryMaintenanceService getInventoryMaintenance() {
        return maintenance;
    }

    @Override
    public TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool() {
        return cpuPool;
    }

    @Override
    public boolean isCpuActive() {
        var port = getLinkedPort();
        return persistentStateOwner && formed && port != null
                && port.isLinkActive();
    }

    @Override
    public IGrid getGrid() {
        var port = getLinkedPort();
        return port != null ? port.getLinkGrid() : null;
    }

    @Override
    public IActionSource getActionSource() {
        var port = getPortLinkEndpoint();
        return port != null ? port.getLinkActionSource() : IActionSource.empty();
    }

    @Override
    public IGridNode getActionableNode() {
        var port = getPortLinkEndpoint();
        return port != null ? port.getMainNode().getNode() : null;
    }

    @Override
    public void markCpuDirty() {
        long now = TickHandler.instance().getCurrentTick();
        if (lastCpuDirtyTick != now) {
            lastCpuDirtyTick = now;
            persistRuntimeStateIfChanged();
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ae2lt.tianshu_supercomputer_controller");
    }

    @Override
    public void maintenanceStateChanged() {
        persistRuntimeStateIfChanged();
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return maintenance.getRequestedJobs();
    }

    public long insertCraftedItems(
            ICraftingLink link, AEKey what, long amount, Actionable mode) {
        return maintenance.insertCraftedItems(link, what, amount, mode);
    }

    public void jobStateChange(ICraftingLink link) {
        maintenance.jobStateChange(link);
    }

    @Override
    public long extractReusableSeed(AEKey key, long amount, Actionable mode) {
        if (!formed || !functionProfile.supportsClosedLoopSeeds()) return 0L;
        long remaining = Math.max(0L, amount);
        long extracted = 0L;
        for (var drive : seedDrives()) {
            long moved = drive.extract(key, remaining, mode, getActionSource());
            extracted = saturatingAdd(extracted, moved);
            remaining -= moved;
            if (remaining <= 0) break;
        }
        if (extracted > 0 && mode == Actionable.MODULATE) seedStorageChanged();
        return extracted;
    }

    @Override
    public KeyCounter extractReusableSeedVariants(
            AEKey planned,
            long amount,
            Predicate<AEKey> acceptsVariant,
            Actionable mode) {
        var result = new KeyCounter();
        if (!formed || !functionProfile.supportsClosedLoopSeeds()
                || planned == null || amount <= 0 || acceptsVariant == null) {
            return result;
        }
        var available = reusableSeedSnapshot();
        var candidates = new java.util.ArrayList<AEKey>();
        if (available.get(planned) > 0 && acceptsVariant.test(planned)) candidates.add(planned);
        for (var entry : available) {
            if (!entry.getKey().equals(planned)
                    && entry.getLongValue() > 0
                    && acceptsVariant.test(entry.getKey())) {
                candidates.add(entry.getKey());
            }
        }
        candidates.sort(java.util.Comparator
                .comparing((AEKey key) -> key.getId().toString())
                .thenComparing(Object::toString));
        if (candidates.remove(planned)) candidates.addFirst(planned);

        long remaining = amount;
        for (var actual : candidates) {
            long extracted = extractReusableSeed(actual, remaining, mode);
            if (extracted > 0) {
                result.add(actual, extracted);
                remaining -= extracted;
            }
            if (remaining <= 0) break;
        }
        return result;
    }

    @Override
    public long insertReusableSeed(AEKey key, long amount, Actionable mode) {
        if (!formed || !functionProfile.supportsClosedLoopSeeds()) return 0L;
        long remaining = Math.max(0L, amount);
        long inserted = 0L;
        for (var drive : seedDrives()) {
            long moved = drive.insert(key, remaining, mode, getActionSource());
            inserted = saturatingAdd(inserted, moved);
            remaining -= moved;
            if (remaining <= 0) break;
        }
        if (inserted > 0 && mode == Actionable.MODULATE) seedStorageChanged();
        return inserted;
    }

    public long reusableSeedAmount(AEKey key) {
        long amount = 0L;
        for (var drive : seedDrives()) {
            amount = saturatingAdd(amount, drive.amount(key, getActionSource()));
        }
        return amount;
    }

    public void seedStorageChanged() {
        var port = getLinkedPort();
        if (port != null) port.refreshCraftingProvider();
    }

    public void closedLoopPatternsChanged() {
        persistPatternsToWarehouses();
        var port = getLinkedPort();
        if (port != null) port.refreshCraftingProvider();
    }

    public List<IPatternDetails> getAvailablePatterns() {
        if (!formed || level == null || !functionProfile.supportsClosedLoopPatterns()
                || functionProfile.closedLoopPatternCapacity() <= 0) {
            return List.of();
        }
        var result = new java.util.ArrayList<IPatternDetails>();
        for (var payload : closedLoopPatterns.activePatterns()) {
            if (!payload.enabled()
                    || !com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternValidator
                            .validate(payload, level).valid()
                    || !membersAreAvailable(payload)) continue;
            var item = (com.moakiee.ae2lt.item.ClosedLoopPatternItem)
                    ModItems.CLOSED_LOOP_PATTERN.get();
            var key = AEItemKey.of(item.createStack(payload, level.registryAccess()));
            IPatternDetails details;
            try {
                details = key != null ? new Ae2ClosedLoopPatternDetails(
                        key, payload, level, machineId, this::availableSeedsFor) : null;
            } catch (RuntimeException ignored) {
                details = null;
            }
            if (details != null) result.add(details);
        }
        return List.copyOf(result);
    }

    private Map<AEKey, Long> availableSeedsFor(ReusableSeedPattern pattern) {
        var available = reusableSeedSnapshot();
        var result = new java.util.LinkedHashMap<AEKey, Long>();
        for (var requirement : pattern.totalReusableSeedRequirements().entrySet()) {
            long amount = 0L;
            for (var candidate : available) {
                if (candidate.getLongValue() > 0
                        && pattern.acceptsReusableSeedVariant(
                                requirement.getKey(), candidate.getKey())) {
                    amount = saturatingAdd(amount, candidate.getLongValue());
                }
            }
            if (amount > 0) result.put(requirement.getKey(), amount);
        }
        return Map.copyOf(result);
    }

    private boolean membersAreAvailable(
            com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload payload) {
        var grid = getGrid();
        if (grid == null) return false;
        var crafting = (CraftingService) grid.getCraftingService();
        for (var member : payload.memberPatterns()) {
            var details = PatternDetailsHelper.decodePattern(
                    member.pattern().toItemStack(level.registryAccess()), level);
            if (details == null) return false;
            var providerPattern = com.moakiee.thunderbolt.ae2.api.crafting.CraftingPatternDelegates
                    .forProviderLookup(details);
            if (!crafting.getProviders(providerPattern).iterator().hasNext()) return false;
        }
        return true;
    }

    public void persistRuntimeStateIfChanged() {
        if (!persistentStateOwner || !machineId.equals(loadedRuntimeId)
                || !(level instanceof ServerLevel serverLevel)) return;
        var state = new CompoundTag();
        var maintenanceTag = new CompoundTag();
        maintenance.writeTo(maintenanceTag, serverLevel.registryAccess());
        state.put(TAG_MAINTENANCE, maintenanceTag);
        if (cpuPool.hasPersistentState()) {
            var poolTag = new CompoundTag();
            cpuPool.writeToNBT(poolTag, serverLevel.registryAccess());
            if (!poolTag.isEmpty()) state.put(TAG_CPU_POOL, poolTag);
        }
        ControllerMachineStateSavedData.get(serverLevel)
                .setState(MachineType.TIANSHU, machineId, state);
    }

    /**
     * Called only for an actual block replacement, while the port link is still
     * available. Release what can be returned now, then persist any unfinished
     * closed-loop cancellation under this controller UUID.
     */
    public void prepareForControllerRemoval() {
        if (!persistentStateOwner || !(level instanceof ServerLevel)) return;
        cpuPool.tryReleaseContents();
        persistRuntimeStateIfChanged();
    }

    private void loadRuntimeState(TianshuSupercomputerPortBlockEntity port) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var data = ControllerMachineStateSavedData.get(serverLevel);
        boolean stored = data.hasState(MachineType.TIANSHU, machineId);
        var legacy = port.copyLegacyRuntimeState();
        var state = stored ? data.getState(MachineType.TIANSHU, machineId)
                : legacy != null ? legacy : new CompoundTag();
        maintenance.readFrom(state.getCompound(TAG_MAINTENANCE), serverLevel.registryAccess());
        cpuPool.readFromNBT(state.getCompound(TAG_CPU_POOL), serverLevel.registryAccess());
        loadedRuntimeId = machineId;
        if (!stored) persistRuntimeStateIfChanged();
        if (legacy != null) port.consumeLegacyRuntimeState();
    }

    private void clearTransientRuntime() {
        if (level == null) return;
        maintenance.readFrom(new CompoundTag(), level.registryAccess());
        cpuPool.readFromNBT(new CompoundTag(), level.registryAccess());
        closedLoopPatterns.clear();
        loadedRuntimeId = null;
        pendingStorage = -1L;
        pendingParallel = -1;
    }

    private void suspendRuntime() {
        maintenance.shutdownCalculations();
        clearTransientRuntime();
    }

    private void loadPatternsFromWarehouses(TianshuSupercomputerPortBlockEntity port) {
        var merged = new java.util.ArrayList<
                com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload>();
        for (var storage : patternStorages()) merged.addAll(storage.patterns());
        closedLoopPatterns.replaceAll(merged);
        var legacyState = port.copyLegacyPatternState();
        if (legacyState != null && level != null) {
            var legacy = new ClosedLoopPatternRepository(() -> Integer.MAX_VALUE);
            legacy.readFrom(legacyState, level.registryAccess());
            var item = (com.moakiee.ae2lt.item.ClosedLoopPatternItem)
                    ModItems.CLOSED_LOOP_PATTERN.get();
            for (var payload : legacy.patterns()) {
                var result = closedLoopPatterns.put(payload);
                if (result == ClosedLoopPatternRepository.PutResult.FULL
                        || result == ClosedLoopPatternRepository.PutResult.UNAVAILABLE) {
                    Block.popResource(level, port.getBlockPos(),
                            item.createStack(payload, level.registryAccess()));
                }
            }
            persistPatternsToWarehouses();
            port.consumeLegacyPatternState();
        }
    }

    private void persistPatternsToWarehouses() {
        var patterns = closedLoopPatterns.patterns();
        int offset = 0;
        for (var storage : patternStorages()) {
            int end = Math.min(patterns.size(),
                    offset + TianshuFunctionProfile.PATTERNS_PER_CLOSED_LOOP_STORAGE);
            storage.replacePatterns(offset < end ? patterns.subList(offset, end) : List.of());
            offset = end;
        }
    }

    private KeyCounter reusableSeedSnapshot() {
        var result = new KeyCounter();
        for (var drive : seedDrives()) drive.getAvailableStacks(result);
        return result;
    }

    private List<TianshuSeedStorageBlockEntity> seedDrives() {
        if (level == null) return List.of();
        var result = new java.util.ArrayList<TianshuSeedStorageBlockEntity>();
        for (var pos : seedStoragePositions) {
            if (level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof TianshuSeedStorageBlockEntity drive) {
                result.add(drive);
            }
        }
        return result;
    }

    private List<TianshuPatternStorageBlockEntity> patternStorages() {
        if (level == null) return List.of();
        var result = new java.util.ArrayList<TianshuPatternStorageBlockEntity>();
        for (var pos : patternStoragePositions) {
            if (level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof TianshuPatternStorageBlockEntity storage) {
                result.add(storage);
            }
        }
        return result;
    }

    private TianshuSupercomputerPortBlockEntity getLinkedPort() {
        var port = getPortLinkEndpoint();
        return port != null
                && port.isLinkedTo(worldPosition, machineId) ? port : null;
    }

    private TianshuSupercomputerPortBlockEntity getPortLinkEndpoint() {
        if (!formed || portPos == null || level == null || !level.isLoaded(portPos)) return null;
        return level.getBlockEntity(portPos) instanceof TianshuSupercomputerPortBlockEntity port
                ? port : null;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
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
        tag.putInt(TAG_CLOSED_LOOP_STORAGES, functionProfile.closedLoopPatternStorageCount());
        tag.putInt(TAG_SEED_STORAGES, functionProfile.closedLoopSeedStorageCount());
        tag.putUUID(TAG_MACHINE_ID, machineId);
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
                Math.max(0, tag.getInt(TAG_CLOSED_LOOP_STORAGES)),
                Math.max(0, tag.getInt(TAG_SEED_STORAGES)));
        identityInitialized = tag.hasUUID(TAG_MACHINE_ID);
        if (identityInitialized) machineId = tag.getUUID(TAG_MACHINE_ID);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        claimPersistentState();
        scheduleStructureCheck();
    }

    @Override
    public void onChunkUnloaded() {
        persistRuntimeStateIfChanged();
        suspendRuntime();
        releasePersistentState();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        persistRuntimeStateIfChanged();
        suspendRuntime();
        releasePersistentState();
        super.setRemoved();
    }

    private void changeMachineId(UUID newId) {
        if (newId == null || newId.equals(machineId)) return;
        persistRuntimeStateIfChanged();
        suspendRuntime();
        releasePersistentState();
        machineId = newId;
        claimPersistentState();
    }

    private void claimPersistentState() {
        if (level instanceof ServerLevel serverLevel) {
            persistentStateOwner = ControllerMachineStateSavedData.get(serverLevel)
                    .claim(MachineType.TIANSHU, machineId, serverLevel, worldPosition);
        }
    }

    private void releasePersistentState() {
        if (persistentStateOwner && level instanceof ServerLevel serverLevel) {
            ControllerMachineStateSavedData.get(serverLevel)
                    .release(MachineType.TIANSHU, machineId, serverLevel, worldPosition);
        }
        persistentStateOwner = false;
    }
}
