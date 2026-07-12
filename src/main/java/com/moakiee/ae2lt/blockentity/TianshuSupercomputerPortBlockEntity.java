package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreProfile;
import com.moakiee.ae2lt.logic.tianshu.TianshuFunctionProfile;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternDecoder;
import com.moakiee.ae2lt.logic.tianshu.loop.Ae2ClosedLoopPatternDetails;
import com.moakiee.ae2lt.logic.tianshu.loop.TianshuSeedStorage;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceService;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import com.google.common.collect.ImmutableSet;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.MachineSource;
import appeng.me.service.CraftingService;
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
        implements TimeWheelCraftingCpuPoolHost, ICraftingProvider, ICraftingRequester {
    private static final String TAG_CONTROLLER_POS = "ControllerPos";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_CPU_POOL = "CpuPool";
    private static final String TAG_CPU_STORAGE = "CpuStorage";
    private static final String TAG_CPU_PARALLEL = "CpuParallel";
    private static final String TAG_FUNCTION_PROFILE = "FunctionProfile";
    private static final String TAG_CLOSED_LOOP_PATTERNS = "ClosedLoopPatterns";
    private static final String TAG_TIANSHU_ID = "TianshuId";
    private static final String TAG_SEED_STORAGE = "SeedStorage";
    private static final String TAG_MAINTENANCE = "InventoryMaintenance";
    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final TimeWheelCraftingCpuPool cpuPool = new TimeWheelCraftingCpuPool(this);
    private BlockPos controllerPos;
    private boolean formed;
    private long lastCpuDirtyTick = Long.MIN_VALUE;
    private long pendingStorage = -1L;
    private int pendingParallel = -1;
    private TianshuFunctionProfile functionProfile = TianshuFunctionProfile.empty();
    private final ClosedLoopPatternRepository closedLoopPatterns =
            new ClosedLoopPatternRepository(() -> functionProfile.closedLoopPatternCapacity());
    private java.util.UUID tianshuId = java.util.UUID.randomUUID();
    private final TianshuSeedStorage seedStorage =
            new TianshuSeedStorage(() -> functionProfile.seedTypeCapacity());
    private final TianshuInventoryMaintenanceService maintenance =
            new TianshuInventoryMaintenanceService(this);

    public TianshuSupercomputerPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_SUPERCOMPUTER_PORT.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("tianshu_supercomputer_port")
                .setVisualRepresentation(ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get())
                .setIdlePowerUsage(8.0D)
                .addService(ICraftingProvider.class, this)
                .addService(ICraftingRequester.class, this);
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
        bindToController(controllerPos, CpuInternalCoreProfile.empty(), TianshuFunctionProfile.empty());
    }

    public void bindToController(BlockPos controllerPos, CpuInternalCoreProfile profile) {
        bindToController(controllerPos, profile, TianshuFunctionProfile.empty());
    }

    public void bindToController(BlockPos controllerPos, CpuInternalCoreProfile profile,
                                 TianshuFunctionProfile functionProfile) {
        this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
        this.formed = controllerPos != null;
        this.functionProfile = formed && functionProfile != null
                ? functionProfile : TianshuFunctionProfile.empty();
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

    public TianshuFunctionProfile getFunctionProfile() {
        return functionProfile;
    }

    public ClosedLoopPatternRepository getClosedLoopPatternRepository() {
        return closedLoopPatterns;
    }

    public java.util.UUID getTianshuId() {
        return tianshuId;
    }

    public TianshuSeedStorage getSeedStorage() {
        return seedStorage;
    }

    public TianshuInventoryMaintenanceService getInventoryMaintenance() {
        return maintenance;
    }

    public void tickTianshuFunctions() {
        maintenance.tick();
    }

    public void maintenanceStateChanged() {
        saveChanges();
    }

    @Override public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return maintenance.getRequestedJobs();
    }

    @Override public long insertCraftedItems(
            ICraftingLink link, AEKey what, long amount, Actionable mode) {
        return maintenance.insertCraftedItems(link, what, amount, mode);
    }

    @Override public void jobStateChange(ICraftingLink link) {
        maintenance.jobStateChange(link);
    }

    @Override
    public long extractReusableSeed(AEKey key, long amount, Actionable mode) {
        if (!formed || !functionProfile.supportsClosedLoopSeeds()) return 0L;
        long extracted = seedStorage.extract(key, amount, mode);
        if (extracted > 0 && mode == Actionable.MODULATE) saveChanges();
        return extracted;
    }

    @Override
    public long insertReusableSeed(AEKey key, long amount, Actionable mode) {
        if (!formed || !functionProfile.supportsClosedLoopSeeds()) return 0L;
        long inserted = seedStorage.insert(key, amount, mode);
        if (inserted > 0 && mode == Actionable.MODULATE) saveChanges();
        return inserted;
    }

    public void closedLoopPatternsChanged() {
        saveChanges();
        var grid = getMainNode().getGrid();
        if (grid != null) grid.getCraftingService().refreshNodeCraftingProvider(getMainNode().getNode());
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (!formed || level == null || !functionProfile.supportsClosedLoopPatterns()
                || functionProfile.closedLoopPatternCapacity() <= 0) {
            return List.of();
        }
        var result = new java.util.ArrayList<IPatternDetails>();
        for (var payload : closedLoopPatterns.patterns()) {
            if (!payload.enabled() || !membersAreAvailable(payload)) continue;
            var item = (com.moakiee.ae2lt.item.ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get();
            var stack = item.createStack(payload, level.registryAccess());
            var key = appeng.api.stacks.AEItemKey.of(stack);
            IPatternDetails details;
            try {
                details = key != null ? new Ae2ClosedLoopPatternDetails(
                        key, payload, level, tianshuId, availableSeedsFor(payload)) : null;
            } catch (RuntimeException ignored) {
                details = null;
            }
            if (details != null) result.add(details);
        }
        return List.copyOf(result);
    }

    private java.util.Map<AEKey, Long> availableSeedsFor(
            com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload payload) {
        var result = new java.util.LinkedHashMap<AEKey, Long>();
        for (var seed : payload.seeds()) {
            long available = Math.min(seed.amount(), seedStorage.amount(seed.what()));
            if (available > 0) result.merge(seed.what(), available, Math::max);
        }
        return java.util.Map.copyOf(result);
    }

    private boolean membersAreAvailable(
            com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload payload) {
        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        var crafting = (CraftingService) grid.getCraftingService();
        for (var member : payload.memberPatterns()) {
            var details = PatternDetailsHelper.decodePattern(
                    member.pattern().toItemStack(level.registryAccess()), level);
            if (details == null || !crafting.getProviders(details).iterator().hasNext()) return false;
        }
        return true;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Closed-loop macros are contracted planning nodes, never executable provider recipes.
        return false;
    }

    @Override
    public boolean isBusy() {
        return !isCpuActive();
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
        var functionTag = new CompoundTag();
        functionTag.putInt("Maintenance", functionProfile.inventoryMaintenanceCoreCount());
        functionTag.putInt("LoopCore", functionProfile.closedLoopPatternCoreCount());
        functionTag.putInt("LoopStorage", functionProfile.closedLoopPatternStorageCount());
        functionTag.putInt("SeedStorage", functionProfile.closedLoopSeedStorageCount());
        tag.put(TAG_FUNCTION_PROFILE, functionTag);
        var closedLoopTag = new CompoundTag();
        closedLoopPatterns.writeTo(closedLoopTag, registries);
        tag.put(TAG_CLOSED_LOOP_PATTERNS, closedLoopTag);
        tag.putUUID(TAG_TIANSHU_ID, tianshuId);
        var seedTag = new CompoundTag();
        seedStorage.writeTo(seedTag, registries);
        tag.put(TAG_SEED_STORAGE, seedTag);
        var maintenanceTag = new CompoundTag();
        maintenance.writeTo(maintenanceTag, registries);
        tag.put(TAG_MAINTENANCE, maintenanceTag);
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
        tianshuId = tag.hasUUID(TAG_TIANSHU_ID) ? tag.getUUID(TAG_TIANSHU_ID) : java.util.UUID.randomUUID();
        if (tag.contains(TAG_FUNCTION_PROFILE, Tag.TAG_COMPOUND)) {
            var functionTag = tag.getCompound(TAG_FUNCTION_PROFILE);
            functionProfile = new TianshuFunctionProfile(
                    Math.max(0, functionTag.getInt("Maintenance")),
                    Math.max(0, functionTag.getInt("LoopCore")),
                    Math.max(0, functionTag.getInt("LoopStorage")),
                    Math.max(0, functionTag.getInt("SeedStorage")));
        } else {
            functionProfile = TianshuFunctionProfile.empty();
        }
        if (tag.contains(TAG_CLOSED_LOOP_PATTERNS, Tag.TAG_COMPOUND)) {
            closedLoopPatterns.readFrom(tag.getCompound(TAG_CLOSED_LOOP_PATTERNS), registries);
        }
        if (tag.contains(TAG_SEED_STORAGE, Tag.TAG_COMPOUND)) {
            seedStorage.readFrom(tag.getCompound(TAG_SEED_STORAGE), registries);
        }
        if (tag.contains(TAG_MAINTENANCE, Tag.TAG_COMPOUND)) {
            maintenance.readFrom(tag.getCompound(TAG_MAINTENANCE), registries);
        }
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
