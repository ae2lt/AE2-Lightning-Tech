package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.filter.IAEItemFilter;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.BlockingMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyAPI;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyDistributor;
import com.moakiee.ae2lt.logic.WirelessOverflowQueue.Bucket;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;

import com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider;

/**
 * Extended pattern-provider logic that adds a wireless dispatch path.
 * <p>
 * In {@link ProviderMode#NORMAL} every call delegates to the vanilla
 * {@link PatternProviderLogic} implementation (incl. ticker, sendList, etc.).
 * <p>
 * In {@link ProviderMode#WIRELESS} the {@link #pushPattern} override performs
 * either round-robin single-target dispatch or even distribution across the
 * host's wireless connection list.
 */
public class OverloadedPatternProviderLogic extends PatternProviderLogic
        implements IBatchCraftingProvider {

    private final OverloadedReturnInventoryController returnInventory;

    private final OverloadedPatternProviderBlockEntity overloadedHost;
    private final OverloadedEjectController ejectController;
    private final IManagedGridNode gridNode;
    private final IActionSource wirelessSource;
    private final int totalCapacity;
    private final OverloadedProviderStorageController storage;

    /** Shared NORMAL-mode distributor (32-slot adaptive wheel + cap listeners). */
    private final WirelessEnergyDistributor wirelessDistributor;
    /**
     * Target snapshot exposed to the distributor. Rebuilt in lockstep with
     * {@link #validConnectionsCache}. {@link #validTargetsVersion} bumps on
     * every replacement so the distributor's per-target caches refresh
     * exactly when the validated connection set changes.
     */
    private List<WirelessEnergyAPI.Target> validTargetsCache = List.of();
    private int validTargetsVersion;

    private final OverloadedReturnPolicy returnPolicy =
            new OverloadedReturnPolicy();

    // ---- wireless dispatch state ------------------------------------------------

    private static final int OVERFLOW_FLUSH_BUDGET = 64;

    /** Owns wireless scheduling, target retries and overflow deadlines. */
    private final ProviderWirelessDispatch wirelessDispatch =
            new ProviderWirelessDispatch();
    private final WirelessOverflowQueue wirelessOverflow =
            wirelessDispatch.overflow();
    private final WirelessOverflowPersistence wirelessOverflowPersistence =
            new WirelessOverflowPersistence();
    private final AdaptiveBatchStatePersistence adaptiveBatchStatePersistence =
            new AdaptiveBatchStatePersistence();

    // ---- push timing wheel + ready queue ----------------------------------------

    /** Ordinary one-copy dispatch also stays O(1) in configured target count. */
    private static final int SINGLE_PUSH_TARGET_ATTEMPTS = 2;

    @Nullable
    private ProviderTarget pendingLocalDirectionalOverflowTarget;
    @Nullable
    private PendingLocalDirectionalOverflow pendingLocalDirectionalOverflowLoad;

    private record PendingLocalDirectionalOverflow(
            Direction pushDirection, RoutedPatternOverflow overflow) {
    }

    /** Discovers and schedules stable adjacent physical targets. */
    private final ProviderNormalDispatch normalDispatch =
            new ProviderNormalDispatch();

    // ---- adaptive batch dispatch -------------------------------------------------

    private record BatchTargetContext(
            ServerLevel level,
            ProviderTarget target) {}

    private record BatchTargetDispatchResult(
            long ownedCopies,
            int attemptedCopies,
            boolean acceptedFullChunk,
            boolean requestLimited,
            ProviderTarget.BaselineStatus baselineStatus,
            WirelessPushOutcome outcome) {
        private static BatchTargetDispatchResult rejected(
                WirelessPushOutcome outcome) {
            return new BatchTargetDispatchResult(
                    0L, 0, false, false,
                    ProviderTarget.BaselineStatus.NONE, outcome);
        }
    }

    /** Current provider-owned decoded patterns and their stable scheduling handles. */
    private final OverloadedProviderPatternCatalog patternCatalog =
            new OverloadedProviderPatternCatalog();

    // ---- auto-return ------------------------------------------------------------

    private final OverloadedAutoReturnController autoReturn;

    /** AE2 grid tick range for the overloaded provider's custom scheduler. */
    private static final int GRID_TICK_MIN = 1;
    private static final int GRID_TICK_MAX = 20;

    /** Refresh the validated wireless-connection view at most once per second. */
    private static final int VALIDATE_INTERVAL = 20;

    /** Cached list of valid wireless connections (shared by energy + auto-return). */
    private List<WirelessConnection> validConnectionsCache = List.of();
    private Set<WirelessConnection> validConnectionSet = Set.of();

    /** Game tick at which validConnectionsCache was last refreshed. */
    private long validConnectionsCacheTick = -1;

    /** External host changes force the next wireless lookup to rebuild the cache immediately. */
    private boolean connectionsDirty = true;

    /** Prevents double execution when both BlockEntityTicker and AE2 Grid Ticker fire in the same tick. */
    private long lastEnergyTickGameTime = -1;

    // ---- eject mode state --------------------------------------------------------

    /** Cached result of induction card check; invalidated on state/upgrade change. */
    private boolean cachedInductionCardInstalled;
    private boolean inductionCardCacheDirty = true;

    // ---- construction -----------------------------------------------------------

    public OverloadedPatternProviderLogic(IManagedGridNode mainNode,
                                          OverloadedPatternProviderBlockEntity host,
                                          int patternInventorySize) {
        super(mainNode, host, Math.min(patternInventorySize, 36));
        mainNode.addService(IGridTickable.class, new Ticker());
        this.overloadedHost = host;
        this.ejectController = new OverloadedEjectController(host);
        this.gridNode = mainNode;
        this.wirelessSource = new MachineSource(mainNode::getNode);
        this.totalCapacity = patternInventorySize;
        this.wirelessDistributor = new WirelessEnergyDistributor(new DistributorHost());
        var accessor = (PatternProviderLogicAccessor) this;
        this.storage = new OverloadedProviderStorageController(
                host, accessor, totalCapacity);
        IAEItemFilter patternFilter = new IAEItemFilter() {
            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
                return PatternDetailsHelper.isEncodedPattern(stack);
            }
        };

        if (totalCapacity > 36) {
            var largeInv = new appeng.util.inv.AppEngInternalInventory(this, totalCapacity);
            largeInv.setFilter(patternFilter);
            accessor.setPatternInventory(largeInv);
        } else {
            accessor.getPatternInventory().setFilter(patternFilter);
        }

        Runnable returnListener = () -> {
            gridNode.ifPresent((grid, node) ->
                    grid.getTickManager().alertDevice(node));
            overloadedHost.saveChanges();
        };
        ReturnSlotFilter returnFilter = (slot, key) -> {
            if (!overloadedHost.isFilteredImport()) return true;
            var filter = getOrBuildOutputFilter();
            return !filter.isEmpty() && filter.matches(key);
        };

        this.returnInventory = new OverloadedReturnInventoryController(
                totalCapacity, returnListener, returnFilter);
        accessor.setReturnInv(returnInventory.full());
        this.autoReturn = new OverloadedAutoReturnController(
                new AutoReturnEnvironment());
    }

    protected OverloadedPatternProviderBlockEntity getOverloadedHost() {
        return overloadedHost;
    }

    protected IManagedGridNode getGridNode() {
        return gridNode;
    }

    protected IActionSource getActionSource() {
        return wirelessSource;
    }

    @Override
    public PatternProviderReturnInventory getReturnInv() {
        return returnInventory.pageView();
    }

    public PatternProviderReturnInventory getInternalReturnInv() {
        return returnInventory.full();
    }

    /**
     * Cap {@code amount} to what the grid can afford for an external EJECT-mode
     * insert. Returns 0 when the grid is unavailable or out of power.
     */
    public long maxAffordableExternalReturn(AEKey what, long amount) {
        return PowerCostUtil.maxAffordable(gridNode.getGrid(), what, amount);
    }

    /** Drain the AE corresponding to {@code amount} of {@code what} for an external EJECT-mode insert. */
    public void consumeExternalReturnPower(AEKey what, long amount) {
        PowerCostUtil.consume(gridNode.getGrid(), what, amount);
    }

    @Override
    public void resetCraftingLock() {
        super.resetCraftingLock();
        returnPolicy.clearUnlockRule();
    }

    // ---- page management --------------------------------------------------------

    public int getCurrentPage() {
        return returnInventory.currentPage();
    }

    public int getTotalPages() {
        return returnInventory.totalPages();
    }

    public void setCurrentPage(int page) {
        returnInventory.setCurrentPage(page);
    }

    /** Refresh the visible nine-slot return page from the complete buffer. */
    public void syncReturnPageViewFromFull() {
        returnInventory.copyFullToPage();
    }

    @Override
    public void updatePatterns() {
        wirelessDispatch.patternsChanged();
        normalDispatch.patternsChanged();
        var accessor = (PatternProviderLogicAccessor) this;
        var patterns = accessor.getPatterns();
        var patternInputs = accessor.getPatternInputs();
        var inventory = accessor.getPatternInventory();

        var level = overloadedHost.getLevel();
        patternCatalog.rebuild(inventory, level, patterns, patternInputs);
        SmartDoublingCompat.applyTo(this, patterns);
        finishPendingAdaptiveBatchStateLoad();
        returnPolicy.patternsChanged();
        refreshEjectRegistrations();

        ICraftingProvider.requestUpdate(accessor.getMainNode());
        alertGridTick();
    }

    // ---- pushPattern override ---------------------------------------------------

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Always try to flush wireless overflow (handles mode-switching edge case)
        if (!wirelessOverflow.isEmpty()) {
            flushWirelessSends();
        }

        if (overloadedHost.getProviderMode() == ProviderMode.NORMAL) {
            if (hasLocalDirectionalOverflow()) {
                flushLocalDirectionalOverflow();
                if (hasLocalDirectionalOverflow()) {
                    return false;
                }
            }
            if (!AdvancedAECompat.isDirectional(patternDetails)
                    && overloadedHost.getBlockingMode() == BlockingMode.SAME_PATTERN) {
                return pushNormalBatch(
                        patternDetails,
                        inputHolder,
                        1L,
                        PatternInputAcceptance.VANILLA_SINGLE_COPY) == 0L;
            }
            double cost = PowerCostUtil.totalCost(inputHolder);
            var grid = gridNode.getGrid();
            if (!PowerCostUtil.canAfford(grid, cost)) {
                return false;
            }
            boolean result;
            if (AdvancedAECompat.isDirectional(patternDetails)) {
                result = pushPatternDirectionally(patternDetails, inputHolder);
            } else {
                if (overloadedHost.getLevel() instanceof ServerLevel serverLevel) {
                    prepareNormalTargetsForDispatch(serverLevel, patternDetails);
                }
                result = super.pushPattern(patternDetails, inputHolder);
                if (result) {
                    syncPendingUnlockRule(patternDetails);
                }
            }
            if (result) {
                PowerCostUtil.consumeRaw(grid, cost);
                alertGridTick();
            }
            return result;
        }
        return wirelessPushPattern(patternDetails, inputHolder);
    }

    /**
     * Vanilla owns NORMAL-mode target selection, so clean every currently
     * eligible adjacent target before handing the actual single-copy dispatch
     * back to it. There are at most six such targets, and ProviderTarget keeps
     * the cleanup to one scan per target and server tick.
     */
    private void prepareNormalTargetsForDispatch(
            ServerLevel level, IPatternDetails pattern) {
        if (overloadedHost.getReturnMode() != ReturnMode.AUTO) {
            return;
        }
        var patternHandle = patternCatalog.resolve(pattern);
        if (patternHandle == null) {
            return;
        }
        for (var direction : activeNormalTargetDirections()) {
            var target = normalDispatch.target(
                    level, overloadedHost.getBlockPos(), direction);
            if (!target.canAccept(level, pattern, wirelessSource)
                    || isTargetBlocked(target, level, patternHandle)) {
                continue;
            }
            autoReturn.beforeDispatch(level, target);
        }
    }

    @Override
    public long getBatchCapacity(IPatternDetails details) {
        if (isBusy()) {
            return 0L;
        }
        return canUseAdaptiveBatch(details) ? Long.MAX_VALUE : 1L;
    }

    @Override
    public long pushBatch(
            IPatternDetails details, KeyCounter[] oneCopyTemplate, long maxCraft) {
        if (maxCraft <= 0L) {
            return 0L;
        }
        if (oneCopyTemplate == null) {
            return maxCraft;
        }
        if (!canUseAdaptiveBatch(details)) {
            return pushPattern(details, oneCopyTemplate) ? maxCraft - 1L : maxCraft;
        }

        // A bucket from a previous partial aggregate owns its inputs already.
        // Try it once before selecting any target for new CPU-owned work.
        if (!wirelessOverflow.isEmpty()) {
            flushWirelessSends();
        }

        return overloadedHost.getProviderMode() == ProviderMode.WIRELESS
                ? pushWirelessBatch(details, oneCopyTemplate, maxCraft)
                : pushNormalBatch(
                        details,
                        oneCopyTemplate,
                        maxCraft,
                        PatternInputAcceptance.COMPLETE_BATCH);
    }

    private boolean canUseAdaptiveBatch(IPatternDetails details) {
        return overloadedHost.isAdaptiveBatchEnabled()
                && details != null
                && details.supportsPushInputsToExternalInventory()
                && !AdvancedAECompat.isDirectional(details);
    }

    private long pushNormalBatch(
            IPatternDetails pattern,
            KeyCounter[] oneCopyTemplate,
            long maxCraft,
            PatternInputAcceptance inputAcceptance) {
        var accessor = (PatternProviderLogicAccessor) this;
        var patternHandle = patternCatalog.resolve(pattern);
        if (hasLocalDirectionalOverflow()
                || !accessor.getSendList().isEmpty()
                || !gridNode.isActive()
                || patternHandle == null
                || getCraftingLockedReason() != LockCraftingMode.NONE) {
            return maxCraft;
        }

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return maxCraft;
        }

        var targetList = activeNormalTargetDirections();
        if (targetList.isEmpty()) {
            return maxCraft;
        }

        var contexts = new LinkedHashMap<ProviderTarget, BatchTargetContext>();
        for (var pushDirection : normalDispatch.dispatchOrder(targetList)) {
            var context = resolveNormalBatchTarget(
                    serverLevel, pushDirection, pattern);
            if (context != null) {
                contexts.put(context.target(), context);
            }
        }
        if (contexts.isEmpty()) {
            return maxCraft;
        }

        double oneCopyCost = PowerCostUtil.totalCost(oneCopyTemplate);
        return normalDispatch.dispatchBatch(
                contexts.keySet(),
                maxCraft,
                (target, share) -> {
                    var context = contexts.get(target);
                    if (context == null) {
                        return new ProviderNormalDispatch.BatchAttemptResult(
                                0L, false, false);
                    }
                    if (isBatchTargetBlocked(context, patternHandle)) {
                        return new ProviderNormalDispatch.BatchAttemptResult(
                                0L, false, false);
                    }
                    autoReturn.beforeDispatch(context.level(), context.target());
                    var ramp = dispatchNormalBatchRamp(
                            context,
                            pattern,
                            patternHandle,
                            oneCopyTemplate,
                            share,
                            oneCopyCost,
                            inputAcceptance);
                    if (ramp.ownedCopies() <= 0L) {
                        return new ProviderNormalDispatch.BatchAttemptResult(
                                0L, ramp.globalAbort(), false);
                    }

                    // PatternProviderLogic stores the direction from provider to
                    // machine; the adapter face is the opposite side of that target.
                    accessor.setSendDirection(
                            context.target().boundFace().getOpposite());
                    accessor.invokeSendStacksOut();
                    alertGridTick();
                    return new ProviderNormalDispatch.BatchAttemptResult(
                            ramp.ownedCopies(),
                            ramp.globalAbort(),
                            !accessor.getSendList().isEmpty());
                });
    }

    @Nullable
    private BatchTargetContext resolveNormalBatchTarget(
            ServerLevel level, Direction pushDirection, IPatternDetails pattern) {
        var target = normalDispatch.target(
                level, overloadedHost.getBlockPos(), pushDirection);
        if (!target.canAccept(level, pattern, wirelessSource)) {
            return null;
        }
        return new BatchTargetContext(level, target);
    }

    private long pushWirelessBatch(
            IPatternDetails pattern,
            KeyCounter[] oneCopyTemplate,
            long maxCraft) {
        wirelessOverflow.refreshBackpressure();
        var patternHandle = patternCatalog.resolve(pattern);
        if (wirelessOverflow.isBackpressured()
                || !gridNode.isActive()
                || patternHandle == null
                || getCraftingLockedReason() != LockCraftingMode.NONE) {
            return maxCraft;
        }

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel providerLevel)) {
            return maxCraft;
        }
        var server = providerLevel.getServer();
        long gameTick = providerLevel.getGameTime();
        var valid = getOrRefreshValidConnections(providerLevel, gameTick);
        if (valid.isEmpty()) {
            return maxCraft;
        }

        var dispatchMode = overloadedHost.getWirelessDispatchMode();
        boolean fastMode = overloadedHost.getWirelessSpeedMode()
                == OverloadedPatternProviderBlockEntity.WirelessSpeedMode.FAST;
        wirelessDispatch.prepare(valid, gameTick, fastMode, dispatchMode);

        return pushWirelessBatchTargets(
                pattern, patternHandle, oneCopyTemplate, maxCraft,
                server, gameTick, fastMode, dispatchMode);
    }

    private long pushWirelessBatchTargets(
            IPatternDetails pattern,
            IPatternDetails patternHandle,
            KeyCounter[] oneCopyTemplate,
            long maxCraft,
            net.minecraft.server.MinecraftServer server,
            long gameTick,
            boolean fastMode,
            WirelessDispatchMode dispatchMode) {
        double oneCopyCost = PowerCostUtil.totalCost(oneCopyTemplate);
        return wirelessDispatch.dispatchBatch(
                dispatchMode,
                patternHandle,
                maxCraft,
                gameTick,
                fastMode,
                (connection, share, exploratoryAttempt,
                        preserveBatchHistoryOnRejection) -> {
                    var result = tryPushBatchToConnection(
                            pattern,
                            patternHandle,
                            oneCopyTemplate,
                            share,
                            oneCopyCost,
                            preserveBatchHistoryOnRejection,
                            connection,
                            server);
                    return new ProviderWirelessDispatch.BatchAttemptResult(
                            result.ownedCopies(),
                            result.attemptedCopies(),
                            result.acceptedFullChunk(),
                            result.requestLimited(),
                            result.baselineStatus(),
                            result.outcome());
                },
                connection -> isConnectionAlive(connection, server),
                connection -> connectionsDirty = true);
    }

    private BatchTargetDispatchResult tryPushBatchToConnection(
            IPatternDetails pattern,
            IPatternDetails patternHandle,
            KeyCounter[] oneCopyTemplate,
            long maxCraft,
            double oneCopyCost,
            boolean preserveBatchHistoryOnRejection,
            WirelessConnection conn,
            net.minecraft.server.MinecraftServer server) {
        if (wirelessOverflow.contains(conn)) {
            return BatchTargetDispatchResult.rejected(
                    WirelessPushOutcome.SOFT_FAIL);
        }

        var targetLevel = server.getLevel(conn.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
            return BatchTargetDispatchResult.rejected(
                    WirelessPushOutcome.HARD_FAIL);
        }

        if (!conn.canAccept(targetLevel, pattern, wirelessSource)) {
            return BatchTargetDispatchResult.rejected(
                    WirelessPushOutcome.HARD_FAIL);
        }
        var context = new BatchTargetContext(targetLevel, conn);
        if (isBatchTargetBlocked(context, patternHandle)) {
            return BatchTargetDispatchResult.rejected(
                    WirelessPushOutcome.SOFT_FAIL);
        }
        autoReturn.beforeDispatch(targetLevel, conn);
        var step = dispatchWirelessBatchStep(
                context,
                pattern,
                patternHandle,
                oneCopyTemplate,
                maxCraft,
                oneCopyCost,
                preserveBatchHistoryOnRejection);
        if (step.ownedCopies() <= 0L) {
            return new BatchTargetDispatchResult(
                    0L,
                    step.attemptedCopies(),
                    false,
                    step.requestLimited(),
                    step.baselineStatus(),
                    step.globalAbort()
                            ? WirelessPushOutcome.GLOBAL_ABORT
                            : WirelessPushOutcome.SOFT_FAIL);
        }

        alertGridTick();
        return new BatchTargetDispatchResult(
                    step.ownedCopies(),
                    step.attemptedCopies(),
                    step.acceptedFullChunk(),
                    step.requestLimited(),
                    step.baselineStatus(),
                    step.globalAbort()
                        ? WirelessPushOutcome.GLOBAL_ABORT
                        : WirelessPushOutcome.SUCCESS);
    }

    private boolean isBatchTargetBlocked(
            BatchTargetContext context, IPatternDetails pattern) {
        return isTargetBlocked(
                context.target(),
                context.level(),
                pattern);
    }

    private boolean isTargetBlocked(
            ProviderTarget target,
            ServerLevel level,
            IPatternDetails pattern) {
        boolean craftingLocked = getCraftingLockedReason() != LockCraftingMode.NONE;
        boolean blockingEnabled = isBlocking();
        if (!craftingLocked && blockingEnabled) {
            var storageTarget = target.resolveStorageTarget(level, wirelessSource);
            if (storageTarget != null
                    && AdvancedBlockingCompat.shouldBypassBlocking(
                            this, storageTarget, pattern)) {
                return false;
            }
        }
        return target.isBlocked(
                level,
                wirelessSource,
                pattern,
                craftingLocked,
                blockingEnabled,
                overloadedHost.getBlockingMode() == BlockingMode.SAME_PATTERN,
                ((PatternProviderLogicAccessor) this).getPatternInputs());
    }

    private ProviderTarget.BatchDispatchResult dispatchNormalBatchRamp(
            BatchTargetContext context,
            IPatternDetails pattern,
            IPatternDetails patternHandle,
            KeyCounter[] oneCopyTemplate,
            long maxCraft,
            double oneCopyCost,
            PatternInputAcceptance inputAcceptance) {
        boolean batchSupported = context.target().supportsBatch(
                context.level(), pattern);
        long targetMaxCraft = Math.min(
                maxCraft,
                context.target().batchCopyLimit(context.level()));
        var result = context.target().pushPattern(
                patternHandle,
                targetMaxCraft,
                batchSupported,
                () -> isBatchTargetBlocked(context, patternHandle),
                copies -> pushBatchChunk(
                        context,
                        pattern,
                        patternHandle,
                        oneCopyTemplate,
                        copies,
                        oneCopyCost,
                        inputAcceptance));
        saveAdaptiveBatchStateIfDirty(context.target());
        return result;
    }

    private ProviderTarget.BatchStepResult dispatchWirelessBatchStep(
            BatchTargetContext context,
            IPatternDetails pattern,
            IPatternDetails patternHandle,
            KeyCounter[] oneCopyTemplate,
            long maxCraft,
            double oneCopyCost,
            boolean preserveBatchHistoryOnRejection) {
        boolean batchSupported = context.target().supportsBatch(
                context.level(), pattern);
        var result = context.target().pushPatternStep(
                patternHandle,
                maxCraft,
                context.level().getGameTime(),
                batchSupported,
                preserveBatchHistoryOnRejection,
                () -> isBatchTargetBlocked(context, patternHandle),
                copies -> pushBatchChunk(
                        context,
                        pattern,
                        patternHandle,
                        oneCopyTemplate,
                        copies,
                        oneCopyCost,
                        PatternInputAcceptance.COMPLETE_BATCH));
        saveAdaptiveBatchStateIfDirty(context.target());
        return result;
    }

    private void saveAdaptiveBatchStateIfDirty(ProviderTarget target) {
        if (target.consumeAdaptiveBatchHistoryDirty()) {
            saveChanges();
        }
    }

    private ProviderTarget.BatchChunk pushBatchChunk(
            BatchTargetContext context,
            IPatternDetails pattern,
            IPatternDetails patternHandle,
            KeyCounter[] oneCopyTemplate,
            int copies,
            double oneCopyCost,
            PatternInputAcceptance inputAcceptance) {
        if (copies <= 0) {
            return ProviderTarget.BatchChunk.REJECTED;
        }

        double requestedCost = oneCopyCost * copies;
        var grid = gridNode.getGrid();
        if (!Double.isFinite(requestedCost)
                || !PowerCostUtil.canAfford(grid, requestedCost)) {
            return ProviderTarget.BatchChunk.GLOBAL_ABORT;
        }

        var result = context.target().pushCopies(
                context.level(), pattern, oneCopyTemplate, copies,
                inputAcceptance,
                ((PatternProviderLogicAccessor) this).getPatternInputs(),
                wirelessSource);
        long ownedCopies = Math.min(copies, Math.max(0, result.acceptedCopies()));
        if (ownedCopies <= 0L) {
            return ProviderTarget.BatchChunk.REJECTED;
        }

        PowerCostUtil.consumeRaw(grid, oneCopyCost * ownedCopies);
        if (!result.overflow().isEmpty()) {
            if (context.target() instanceof WirelessConnection connection) {
                // Aggregated amounts do not satisfy the compact bucket's
                // one-copy shape invariant, so retain the exact list.
                bucketOverflow(
                        connection, pattern,
                        result.overflow(), true);
            } else {
                var accessor = (PatternProviderLogicAccessor) this;
                for (var overflow : result.overflow()) {
                    accessor.invokeAddToSendList(
                            overflow.what(), overflow.amount());
                }
            }
        }

        recordSuccessfulBatchChunk(context, pattern, patternHandle);

        return new ProviderTarget.BatchChunk(
                ownedCopies,
                ownedCopies == copies && result.overflow().isEmpty(),
                false);
    }

    private void recordSuccessfulBatchChunk(
            BatchTargetContext context,
            IPatternDetails pattern,
            IPatternDetails patternHandle) {
        ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(pattern);
        syncPendingUnlockRule(pattern);
        context.target().markPatternDispatched(
                context.level(), patternHandle);
    }

    private boolean wirelessPushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        wirelessOverflow.refreshBackpressure();
        var patternHandle = patternCatalog.resolve(pattern);
        if (wirelessOverflow.isBackpressured()) return false;
        if (!gridNode.isActive()) return false;
        if (patternHandle == null) return false;
        if (getCraftingLockedReason() != LockCraftingMode.NONE) return false;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return false;
        var server = sl.getServer();
        var dispatchMode = overloadedHost.getWirelessDispatchMode();

        var valid = getOrRefreshValidConnections(sl, sl.getGameTime());
        if (valid.isEmpty()) return false;

        // Power availability is a grid-wide condition. Do not scan and penalize
        // individual machines when no target could possibly accept the job.
        double cost = PowerCostUtil.totalCost(inputs);
        var grid = gridNode.getGrid();
        if (!PowerCostUtil.canAfford(grid, cost)) return false;

        long gameTick = sl.getGameTime();
        boolean fastMode = overloadedHost.getWirelessSpeedMode()
                == OverloadedPatternProviderBlockEntity.WirelessSpeedMode.FAST;

        wirelessDispatch.prepare(valid, gameTick, fastMode, dispatchMode);

        return wirelessDispatch.dispatchSingleCopy(
                dispatchMode,
                patternHandle,
                gameTick,
                fastMode,
                SINGLE_PUSH_TARGET_ATTEMPTS,
                connection -> tryPushToConnection(
                        pattern,
                        patternHandle,
                        inputs,
                        connection,
                        server),
                connection -> isConnectionAlive(connection, server),
                connection -> connectionsDirty = true);
    }

    private static boolean isConnectionAlive(WirelessConnection conn,
                                             net.minecraft.server.MinecraftServer server) {
        var level = server.getLevel(conn.dimension());
        return level != null && conn.isAlive(level);
    }

    private WirelessPushOutcome tryPushToConnection(
            IPatternDetails pattern,
            IPatternDetails patternHandle,
            KeyCounter[] inputs,
            WirelessConnection conn,
            net.minecraft.server.MinecraftServer server) {
        if (wirelessOverflow.contains(conn)) return WirelessPushOutcome.SOFT_FAIL;
        if (AdvancedAECompat.isDirectional(pattern)) {
            return tryPushToConnectionDirectionally(
                    pattern, patternHandle, inputs, conn, server);
        }

        var targetLevel = server.getLevel(conn.dimension());
        if (targetLevel == null) return WirelessPushOutcome.HARD_FAIL;

        if (!conn.canAccept(targetLevel, pattern, wirelessSource)) {
            return WirelessPushOutcome.HARD_FAIL;
        }

        double cost = PowerCostUtil.totalCost(inputs);
        var grid = gridNode.getGrid();
        if (!PowerCostUtil.canAfford(grid, cost)) {
            return WirelessPushOutcome.GLOBAL_ABORT;
        }

        if (isTargetBlocked(conn, targetLevel, patternHandle)) {
            return WirelessPushOutcome.SOFT_FAIL;
        }
        autoReturn.beforeDispatch(targetLevel, conn);
        var result = conn.pushCopies(
                targetLevel, pattern, inputs, 1,
                PatternInputAcceptance.VANILLA_SINGLE_COPY,
                ((PatternProviderLogicAccessor) this).getPatternInputs(),
                wirelessSource);
        if (result.acceptedCopies() == 0) return WirelessPushOutcome.SOFT_FAIL;

        PowerCostUtil.consumeRaw(grid, cost);

        if (!result.overflow().isEmpty()) {
            bucketOverflow(conn, pattern, result.overflow(), false);
        }

        ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(pattern);
        syncPendingUnlockRule(pattern);
        conn.markPatternDispatched(targetLevel, patternHandle);
        alertGridTick();
        return WirelessPushOutcome.SUCCESS;
    }

    private void bucketOverflow(WirelessConnection conn, IPatternDetails pattern,
                                List<GenericStack> overflow, boolean forceFallback) {
        if (overflow.isEmpty()) return;

        wirelessOverflow.store(
                conn, pattern, overflow, forceFallback, currentGameTick());
        // Overflow changes target readiness, not wireless topology. Rebuilding
        // the validated connection cache here would re-check up to 1024 block
        // entities after every partial push.
        wirelessDispatch.markDirty();
        alertGridTick();
        saveChanges();
    }

    private void bucketRoutedOverflow(
            WirelessConnection conn,
            IPatternDetails pattern,
            List<RoutedPatternOverflow.Entry> overflow) {
        if (overflow.isEmpty()) {
            return;
        }

        wirelessOverflow.storeRouted(
                conn, pattern, overflow, currentGameTick());
        // The address remains valid; only dispatch eligibility changed.
        wirelessDispatch.markDirty();
        alertGridTick();
        saveChanges();
    }

    // ---- AdvancedAE directional push (NORMAL mode) --------------------------------

    /**
     * Push a directional AdvancedAE pattern through adjacent machines in NORMAL mode.
     * Each input key is routed to the target-machine face specified by the pattern's
     * directionMap; keys without a mapping use the default face (pushDir.getOpposite()).
     */
    private boolean pushPatternDirectionally(IPatternDetails pattern, KeyCounter[] inputs) {
        var accessor = (PatternProviderLogicAccessor) this;
        var patternHandle = patternCatalog.resolve(pattern);
        if (hasLocalDirectionalOverflow()) return false;
        if (!accessor.getSendList().isEmpty()) return false;
        if (!gridNode.isActive()) return false;
        if (patternHandle == null) return false;
        if (getCraftingLockedReason() != LockCraftingMode.NONE) return false;
        if (!pattern.supportsPushInputsToExternalInventory()) return false;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return false;

        var targets = activeNormalTargetDirections();
        if (targets.isEmpty()) return false;

        EjectModeRegistry.setBypass(true);
        try {
            for (var pushDir : targets) {
                var target = normalDispatch.target(
                        sl, overloadedHost.getBlockPos(), pushDir);
                var defaultFace = target.boundFace();
                var be = target.resolveBlockEntity(sl);
                if (be == null) continue;

                var faceToTarget = buildDirectionalTargets(
                        sl, target, defaultFace, pattern, inputs, wirelessSource);
                if (faceToTarget == null) continue;

                if (isTargetBlocked(target, sl, patternHandle)) continue;

                autoReturn.beforeDispatch(sl, target);
                if (!simulateDirectionalAcceptance(faceToTarget, defaultFace, pattern, inputs)) continue;

                var overflow = commitDirectionalPush(
                        pattern, inputs, faceToTarget, defaultFace);
                if (!overflow.isEmpty()) {
                    target.setDirectionalOverflow(
                            RoutedPatternOverflow.routed(overflow));
                    pendingLocalDirectionalOverflowTarget = target;
                    if (!flushLocalDirectionalOverflow()) {
                        saveChanges();
                    }
                }
                accessor.invokeOnPushPatternSuccess(pattern);
                syncPendingUnlockRule(pattern);
                target.markPatternDispatched(sl, patternHandle);
                return true;
            }
            return false;
        } finally {
            EjectModeRegistry.setBypass(false);
        }
    }

    // ---- AdvancedAE directional push (WIRELESS mode) -----------------------------

    /**
     * Push a directional AdvancedAE pattern to a wireless target.
     * Behaves as if the provider were physically placed on {@code conn.boundFace()}.
     * Each input key is routed to the target-machine face from the directionMap;
     * keys without a mapping default to {@code conn.boundFace()}.
     */
    private WirelessPushOutcome tryPushToConnectionDirectionally(
            IPatternDetails pattern,
            IPatternDetails patternHandle,
            KeyCounter[] inputs,
            WirelessConnection conn,
            net.minecraft.server.MinecraftServer server) {
        if (wirelessOverflow.contains(conn)) return WirelessPushOutcome.SOFT_FAIL;
        var targetLevel = server.getLevel(conn.dimension());
        if (targetLevel == null) return WirelessPushOutcome.HARD_FAIL;
        if (!targetLevel.isLoaded(conn.pos())) return WirelessPushOutcome.HARD_FAIL;
        if (!pattern.supportsPushInputsToExternalInventory()) return WirelessPushOutcome.SOFT_FAIL;

        var be = targetLevel.getBlockEntity(conn.pos());
        if (be == null) return WirelessPushOutcome.HARD_FAIL;

        double cost = PowerCostUtil.totalCost(inputs);
        var grid = gridNode.getGrid();
        if (!PowerCostUtil.canAfford(grid, cost)) {
            return WirelessPushOutcome.GLOBAL_ABORT;
        }

        var defaultFace = conn.boundFace();

        EjectModeRegistry.setBypass(true);
        try {
            var faceToTarget = buildDirectionalTargets(
                    targetLevel, conn, defaultFace, pattern, inputs, wirelessSource);
            if (faceToTarget == null) return WirelessPushOutcome.SOFT_FAIL;

            if (isTargetBlocked(conn, targetLevel, patternHandle)) {
                return WirelessPushOutcome.SOFT_FAIL;
            }

            autoReturn.beforeDispatch(targetLevel, conn);
            if (!simulateDirectionalAcceptance(faceToTarget, defaultFace, pattern, inputs))
                return WirelessPushOutcome.SOFT_FAIL;

            var overflow = commitDirectionalPush(
                    pattern, inputs, faceToTarget, defaultFace);
            PowerCostUtil.consumeRaw(grid, cost);
            if (!overflow.isEmpty()) {
                bucketRoutedOverflow(conn, pattern, overflow);
            }
        } finally {
            EjectModeRegistry.setBypass(false);
        }

        ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(pattern);
        syncPendingUnlockRule(pattern);
        conn.markPatternDispatched(targetLevel, patternHandle);
        alertGridTick();
        return WirelessPushOutcome.SUCCESS;
    }

    // ---- directional push helpers ------------------------------------------------

    /**
     * Build a map of face -> PatternProviderTarget for all unique faces
     * referenced by the directional pattern's inputs.
     *
     * @return the map, or {@code null} if any required target cannot be resolved
     */
    @Nullable
    private Map<Direction, PatternProviderTarget> buildDirectionalTargets(
            ServerLevel level, ProviderTarget providerTarget,
            Direction defaultFace, IPatternDetails pattern,
            KeyCounter[] inputs, IActionSource source) {
        var map = new HashMap<Direction, PatternProviderTarget>();
        for (var inputList : inputs) {
            for (var entry : inputList) {
                var dir = AdvancedAECompat.getDirectionForKey(pattern, entry.getKey());
                var face = dir != null ? dir : defaultFace;
                map.computeIfAbsent(
                        face,
                        f -> providerTarget.resolveStorageTarget(level, f, source));
            }
        }
        if (map.isEmpty() || map.containsValue(null)) return null;
        return map;
    }

    /**
     * Simulate whether all directional targets can accept their respective inputs.
     */
    private static boolean simulateDirectionalAcceptance(
            Map<Direction, PatternProviderTarget> faceToTarget,
            Direction defaultFace,
            IPatternDetails pattern, KeyCounter[] inputs) {
        for (var inputList : inputs) {
            for (var entry : inputList) {
                var dir = AdvancedAECompat.getDirectionForKey(pattern, entry.getKey());
                var face = dir != null ? dir : defaultFace;
                var target = faceToTarget.get(face);
                if (target == null) return false;
                if (target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Commits one directional push while retaining the exact target face for every
     * remainder. Both local and wireless retries consume this routed form.
     */
    private static List<RoutedPatternOverflow.Entry> commitDirectionalPush(
            IPatternDetails pattern, KeyCounter[] inputs,
            Map<Direction, PatternProviderTarget> faceToTarget, Direction defaultFace) {
        var overflow = new ArrayList<RoutedPatternOverflow.Entry>();
        pattern.pushInputsToExternalInventory(inputs, (what, amount) -> {
            var dir = AdvancedAECompat.getDirectionForKey(pattern, what);
            var face = dir != null ? dir : defaultFace;
            var target = faceToTarget.get(face);
            if (target != null) {
                var inserted = target.insert(what, amount, Actionable.MODULATE);
                if (inserted < amount) {
                    overflow.add(new RoutedPatternOverflow.Entry(
                            face, new GenericStack(what, amount - inserted)));
                }
            } else {
                overflow.add(new RoutedPatternOverflow.Entry(
                        face, new GenericStack(what, amount)));
            }
        });
        return overflow;
    }

    // ---- overflow flush ---------------------------------------------------------

    private boolean flushLocalDirectionalOverflow() {
        finishPendingLocalDirectionalOverflowLoad();
        var target = pendingLocalDirectionalOverflowTarget;
        if (target == null) {
            return false;
        }
        var overflow = target.directionalOverflow();
        if (overflow == null) {
            pendingLocalDirectionalOverflowTarget = null;
            return false;
        }

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        var targetBlockEntity = target.resolveBlockEntity(serverLevel);
        if (targetBlockEntity == null) {
            return false;
        }

        autoReturn.beforeDispatch(serverLevel, target);
        EjectModeRegistry.setBypass(true);
        boolean progressed;
        try {
            progressed = overflow.flush(
                    target.boundFace(),
                    (face, what, amount) -> {
                        var storageTarget = target.resolveStorageTarget(
                                serverLevel, face, wirelessSource);
                        return storageTarget == null
                                ? 0L
                                : storageTarget.insert(
                                        what, amount, Actionable.MODULATE);
                    });
        } finally {
            EjectModeRegistry.setBypass(false);
        }

        if (overflow.isEmpty()) {
            target.clearDirectionalOverflow();
            pendingLocalDirectionalOverflowTarget = null;
        }
        if (progressed) {
            saveChanges();
        }
        return progressed;
    }

    private void flushWirelessSends() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        long gameTick = sl.getGameTime();
        if (!wirelessOverflow.beginFlush(gameTick)) return;
        var server = sl.getServer();

        int attempts = 0;
        boolean overflowStateChanged = false;
        while (attempts < OVERFLOW_FLUSH_BUDGET) {
            var conn = wirelessOverflow.pollDue(gameTick);
            if (conn == null) {
                break;
            }
            var bucket = wirelessOverflow.get(conn);
            if (bucket == null) {
                continue;
            }
            attempts++;

            var targetLevel = server.getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                wirelessOverflow.rescheduleBlocked(conn, bucket, gameTick);
                continue;
            }

            if (conn.resolveAdapter(targetLevel) == null) {
                wirelessOverflow.rescheduleBlocked(conn, bucket, gameTick);
                continue;
            }

            autoReturn.beforeDispatch(targetLevel, conn);
            var be = conn.resolveBlockEntity(targetLevel);

            var result = bucket.compactMode
                    ? flushCompactBucket(bucket, conn, targetLevel)
                    : flushFallbackBucket(bucket, conn, targetLevel, be);

            if (result.removeBucket()) {
                wirelessOverflow.remove(conn);
                wirelessDispatch.resumeTarget(conn, gameTick);
                // Resuming a paused target is a scheduler-state change only.
                wirelessDispatch.markDirty();
            } else if (result.reschedule()) {
                wirelessOverflow.reschedule(conn, bucket, gameTick, result);
            }
            overflowStateChanged |= result.persistentStateChanged();
        }
        if (overflowStateChanged) {
            saveChanges();
        }
    }

    /**
     * Called by the already-active block-entity ticker. This only performs an
     * O(1) due-queue check until a retry is actually due; capability access
     * remains bounded by {@link #OVERFLOW_FLUSH_BUDGET}.
     */
    public void tickOverflowRetries() {
        flushWirelessSends();
    }

    private WirelessOverflowQueue.OverflowAttemptResult flushCompactBucket(
            Bucket bucket, WirelessConnection conn,
            ServerLevel targetLevel) {
        var pattern = wirelessOverflow.pattern(
                Short.toUnsignedInt(bucket.patternId));
        if (pattern == null) return WirelessOverflowQueue.OverflowAttemptResult.CLEARED;
        var inputs = pattern.getInputs();
        boolean progressed = false;

        while (bucket.stuckIndex < inputs.length) {
            var input = inputs[bucket.stuckIndex];
            var possible = input.getPossibleInputs();
            if (possible.length != 1) {
                return WirelessOverflowQueue.OverflowAttemptResult.CLEARED;
            }
            var single = new ArrayList<GenericStack>(1);
            single.add(new GenericStack(possible[0].what(), bucket.remaining));
            conn.flushOverflow(targetLevel, single, wirelessSource);

            long left = single.isEmpty() ? 0 : single.get(0).amount();
            long inserted = bucket.remaining - left;
            if (inserted == 0) {
                return progressed
                        ? WirelessOverflowQueue.OverflowAttemptResult.PROGRESSED
                        : WirelessOverflowQueue.OverflowAttemptResult.BLOCKED;
            }
            progressed = true;
            if (left > 0) {
                bucket.remaining = left;
                return WirelessOverflowQueue.OverflowAttemptResult.PROGRESSED;
            }

            bucket.stuckIndex++;
            if (bucket.stuckIndex < inputs.length) {
                bucket.remaining = WirelessOverflowPatternTable.inputAmount(
                        inputs[bucket.stuckIndex]);
            }
        }
        return WirelessOverflowQueue.OverflowAttemptResult.CLEARED;
    }

    private WirelessOverflowQueue.OverflowAttemptResult flushFallbackBucket(
            Bucket bucket, WirelessConnection conn,
            ServerLevel targetLevel,
            @Nullable BlockEntity targetBlockEntity) {
        if (!bucket.fallback.hasExplicitFaces()) {
            boolean progressed = bucket.fallback.flushUnrouted(
                    stacks -> conn.flushOverflow(
                            targetLevel, stacks, wirelessSource));
            if (bucket.fallback.isEmpty()) {
                return WirelessOverflowQueue.OverflowAttemptResult.CLEARED;
            }
            return progressed
                    ? WirelessOverflowQueue.OverflowAttemptResult.PROGRESSED
                    : WirelessOverflowQueue.OverflowAttemptResult.BLOCKED;
        }

        if (targetBlockEntity == null) {
            return WirelessOverflowQueue.OverflowAttemptResult.BLOCKED;
        }

        EjectModeRegistry.setBypass(true);
        boolean progressed;
        try {
            progressed = bucket.fallback.flush(
                    conn.boundFace(),
                    (face, what, amount) -> {
                        var target = conn.resolveStorageTarget(
                                targetLevel, face, wirelessSource);
                        return target == null
                                ? 0L
                                : target.insert(what, amount, Actionable.MODULATE);
                    });
        } finally {
            EjectModeRegistry.setBypass(false);
        }

        if (bucket.fallback.isEmpty()) {
            return WirelessOverflowQueue.OverflowAttemptResult.CLEARED;
        }
        return progressed
                ? WirelessOverflowQueue.OverflowAttemptResult.PROGRESSED
                : WirelessOverflowQueue.OverflowAttemptResult.BLOCKED;
    }

    private long currentGameTick() {
        var level = overloadedHost.getLevel();
        return level instanceof ServerLevel serverLevel ? serverLevel.getGameTime() : 0L;
    }

    // ---- auto-return ------------------------------------------------------------

    /** Runs wireless energy transfer and the independent auto-return subsystem. */
    public void tickAutoReturn() {
        if (!hasAnyTickWork()) return;
        tickWirelessInductionEnergy();
        autoReturn.tick();
    }

    /**
     * Quick check: is there any reason to run the server tick at all?
     * Returns false when NORMAL mode + autoReturn off + no wireless overflow,
     * allowing the tick to be completely skipped.
     */
    public boolean hasAnyTickWork() {
        if (hasLocalDirectionalOverflow()) return true;
        if (!wirelessOverflow.isEmpty()) return true;
        if (storage.hasPendingRestore()) return true;
        if (overloadedHost.getProviderMode() == ProviderMode.WIRELESS
                && gridNode.isActive()
                && isInductionCardInstalled()
                && CACHED_APPFLUX_FE_KEY != null) return true;
        if (overloadedHost.getReturnMode() == ReturnMode.AUTO
                && !getOrBuildOutputFilter().isEmpty()) return true;
        if (!returnInventory.full().isEmpty()) return true;
        return false;
    }

    protected AllowedOutputFilter getOrBuildOutputFilter() {
        return returnPolicy.outputFilter(getAvailablePatterns());
    }

    public boolean handleOverloadUnlockOnReturnedStack(GenericStack returnedStack) {
        if (getCraftingLockedReason() != LockCraftingMode.LOCK_UNTIL_RESULT) {
            returnPolicy.clearUnlockRule();
            return false;
        }

        var unlockStack = getUnlockStack();
        if (unlockStack == null) {
            resetCraftingLock();
            return true;
        }

        var result = ReturnedCraftingUnlock.resolveMatchedAmount(
                returnPolicy.matchesUnlock(unlockStack, returnedStack),
                unlockStack.amount(),
                returnedStack.amount());
        if (!result.matched()) {
            return false;
        }

        if (result.shouldResetLock()) {
            resetCraftingLock();
        } else {
            ((PatternProviderLogicAccessor) this)
                    .setUnlockStack(new GenericStack(unlockStack.what(), result.remainingAmount()));
            saveChanges();
        }

        return true;
    }

    protected void syncPendingUnlockRule(IPatternDetails pattern) {
        returnPolicy.synchronizeUnlockRule(
                pattern,
                getCraftingLockedReason()
                        == LockCraftingMode.LOCK_UNTIL_RESULT);
    }

    // ---- eject mode lifecycle ----------------------------------------------------

    /**
     * Rebuild eject-mode registrations based on the current return mode
     * and wireless connections. Should be called whenever return mode,
     * connections, or patterns change.
     */
    public void refreshEjectRegistrations() {
        ejectController.refresh();
    }

    protected void tickWirelessInductionEnergy() {
        if (overloadedHost.getProviderMode() != ProviderMode.WIRELESS) return;
        if (!gridNode.isActive() || !isInductionCardInstalled()) return;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;

        if (CACHED_APPFLUX_FE_KEY == null) return;
        if (CACHED_APPFLUX_TRANSFER_RATE <= 0) return;

        long gameTick = sl.getGameTime();
        if (gameTick == lastEnergyTickGameTime) return;
        lastEnergyTickGameTime = gameTick;

        // Refresh validation (host-owned: 20-tick sweep + per-machine cleanup)
        // — rebuildValidTargets() inside this call publishes the target
        // snapshot that DistributorHost exposes to the shared distributor.
        getOrRefreshValidConnections(sl, gameTick);
        wirelessDistributor.tickNormal(sl);
    }

    /**
     * Returns a cached list of valid wireless connections.
     * The cache is refreshed at most once per {@link #VALIDATE_INTERVAL} ticks.
     * Both the energy-induction path and auto-return path share this cache
     * to avoid duplicate world queries within a single tick.
     */
    private List<WirelessConnection> getOrRefreshValidConnections(ServerLevel providerLevel, long gameTick) {
        if (!connectionsDirty && gameTick - validConnectionsCacheTick < VALIDATE_INTERVAL) {
            return validConnectionsCache;
        }

        // The block entity already prunes at most 64 stale endpoints every
        // 100 ticks. Repeating an unbounded prune here used to validate the
        // complete connection list twice on every cache refresh.
        var server = providerLevel.getServer();
        var valid = new ArrayList<WirelessConnection>();
        for (var conn : overloadedHost.getConnections()) {
            if (!conn.dimension().equals(providerLevel.dimension())) {
                continue;
            }
            if (!WirelessConnectionRange.isConnectorLinkInRange(
                    providerLevel.dimension(), overloadedHost.getBlockPos(), conn.dimension(), conn.pos())) {
                continue;
            }
            var targetLevel = server.getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                continue;
            }
            if (targetLevel.getBlockEntity(conn.pos()) == null) {
                continue;
            }
            // Reuse an overflow-owned orphan when the same complete address is
            // re-added. This keeps one ProviderTarget runtime per endpoint.
            valid.add(wirelessOverflow.adopt(conn));
        }
        var refreshedConnections = List.copyOf(valid);
        boolean connectionsChanged = !refreshedConnections.equals(validConnectionsCache);
        if (connectionsChanged) {
            validConnectionsCache = refreshedConnections;
            validConnectionSet = Set.copyOf(refreshedConnections);

            var retainedStates = new HashSet<>(validConnectionSet);
            retainedStates.addAll(wirelessOverflow.connections());
            wirelessDispatch.retainStates(retainedStates);
            rebuildValidTargets();
        }
        validConnectionsCacheTick = gameTick;
        connectionsDirty = false;
        return validConnectionsCache;
    }

    protected List<WirelessConnection> getValidConnections(ServerLevel providerLevel, long gameTick) {
        return getOrRefreshValidConnections(providerLevel, gameTick);
    }

    /**
     * Mirror {@link #validConnectionsCache} into a {@link WirelessEnergyAPI.Target}
     * snapshot for the shared {@link WirelessEnergyDistributor}. Bumps
     * {@link #validTargetsVersion} on every replacement so the distributor
     * picks up the new set on its next tick.
     */
    private void rebuildValidTargets() {
        var targets = new ArrayList<WirelessEnergyAPI.Target>(validConnectionsCache.size());
        for (var conn : validConnectionsCache) {
            targets.add(new WirelessEnergyAPI.Target(conn.dimension(), conn.pos(), conn.boundFace()));
        }
        validTargetsCache = List.copyOf(targets);
        validTargetsVersion++;
    }

    // ---- target level resolution ---------------------------------------------

    @Nullable
    private ServerLevel resolveTargetLevel(ServerLevel providerLevel, WirelessConnection conn) {
        if (!conn.dimension().equals(providerLevel.dimension())) return null;
        if (!WirelessConnectionRange.isConnectorLinkInRange(
                providerLevel.dimension(), overloadedHost.getBlockPos(), conn.dimension(), conn.pos())) {
            return null;
        }
        var targetLevel = providerLevel.getServer().getLevel(conn.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) return null;
        return targetLevel;
    }

    public void onHostStateChanged() {
        invalidateValidConnectionsCache();
        inductionCardCacheDirty = true;
        refreshEjectRegistrations();
        alertGridTick();
    }

    /**
     * Flush any FE the shared distributor still has buffered back to the ME
     * network. Called from BE lifecycle hooks (chunk unload, removal) so we
     * never leak FE on world-side teardown.
     */
    public void flushWirelessEnergyBuffer() {
        wirelessDistributor.flushBufferToNetwork();
    }

    public boolean prepareInvalidConnectionRemoval(WirelessConnection conn) {
        var bucket = wirelessOverflow.get(conn);
        if (bucket == null) {
            return true;
        }
        if (!drainBucketToNetwork(bucket)) {
            return false;
        }
        wirelessOverflow.remove(conn);
        connectionsDirty = true;
        wirelessDispatch.markDirty();
        alertGridTick();
        saveChanges();
        return true;
    }

    private boolean drainBucketToNetwork(Bucket bucket) {
        if (bucket.compactMode) {
            return drainCompactBucketToNetwork(bucket);
        }

        bucket.fallback.flush(
                Direction.DOWN,
                (face, what, amount) -> {
                    long remaining = insertStackToNetwork(what, amount);
                    return amount - remaining;
                });
        return bucket.fallback.isEmpty();
    }

    private boolean drainCompactBucketToNetwork(Bucket bucket) {
        var pattern = wirelessOverflow.pattern(
                Short.toUnsignedInt(bucket.patternId));
        if (pattern == null) {
            return true;
        }
        var inputs = pattern.getInputs();

        while (bucket.stuckIndex < inputs.length) {
            var possible = inputs[bucket.stuckIndex].getPossibleInputs();
            if (possible.length != 1) {
                return true;
            }

            long remaining = insertStackToNetwork(possible[0].what(), bucket.remaining);
            if (remaining > 0) {
                bucket.remaining = remaining;
                return false;
            }

            bucket.stuckIndex++;
            if (bucket.stuckIndex < inputs.length) {
                bucket.remaining = WirelessOverflowPatternTable.inputAmount(
                        inputs[bucket.stuckIndex]);
            }
        }
        return true;
    }

    private long insertStackToNetwork(AEKey what, long amount) {
        var grid = gridNode.getGrid();
        if (grid == null || amount <= 0) {
            return amount;
        }

        var storage = grid.getStorageService().getInventory();
        long remaining = amount;
        while (remaining > 0) {
            long affordable = PowerCostUtil.maxAffordable(grid, what, remaining);
            if (affordable <= 0) {
                break;
            }
            long inserted = storage.insert(what, affordable, Actionable.MODULATE, wirelessSource);
            if (inserted <= 0) {
                break;
            }
            PowerCostUtil.consume(grid, what, inserted);
            remaining -= inserted;
        }
        return remaining;
    }

    public void onPersistentStateChanged() {
        inductionCardCacheDirty = true;
        alertGridTick();
    }

    public void onNeighborChanged() {
        alertGridTick();
    }

    private boolean hasCombinedGridTickWork() {
        var accessor = (PatternProviderLogicAccessor) this;
        return accessor.invokeHasWorkToDo() || hasAnyTickWork();
    }

    private boolean hasActiveOverloadedTickWork(long gameTick) {
        if (wirelessOverflow.nextDueTick() <= gameTick) {
            return true;
        }
        if (shouldTickWirelessEnergyNow(gameTick)) {
            return true;
        }
        return shouldPollAutoReturnNow(gameTick);
    }

    private boolean shouldTickWirelessEnergyNow(long gameTick) {
        if (overloadedHost.getProviderMode() != ProviderMode.WIRELESS) return false;
        if (!gridNode.isActive() || !isInductionCardInstalled()) return false;
        return CACHED_APPFLUX_FE_KEY != null && CACHED_APPFLUX_TRANSFER_RATE > 0;
    }

    private boolean shouldPollAutoReturnNow(long gameTick) {
        if (overloadedHost.getReturnMode() != ReturnMode.AUTO || !gridNode.isActive()) {
            return false;
        }
        if (getOrBuildOutputFilter().isEmpty()) {
            return false;
        }
        var level = overloadedHost.getLevel();
        return level instanceof ServerLevel serverLevel
                // Keep the grid ticker at one tick while a distributed sweep
                // has another target due next tick. Otherwise SLOWER modulation
                // would bunch a 1024-target round into 20-tick bursts.
                && autoReturn.nextPollTick(serverLevel) <= gameTick + 1L;
    }

    protected void alertGridTick() {
        gridNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private List<Direction> activeNormalTargetDirections() {
        return List.copyOf(
                ((PatternProviderLogicAccessor) this).invokeGetActiveSides());
    }

    private void prepareParentSendListForDispatch(
            PatternProviderLogicAccessor accessor) {
        if (overloadedHost.getProviderMode() != ProviderMode.NORMAL
                || accessor.getSendList().isEmpty()
                || !(overloadedHost.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var direction = accessor.getSendDirection();
        if (direction == null) {
            return;
        }
        var target = normalDispatch.target(
                level, overloadedHost.getBlockPos(), direction);
        autoReturn.beforeDispatch(level, target);
    }

    private void invalidateValidConnectionsCache() {
        normalDispatch.clearRuntimeState();
        var wirelessTargets = new HashSet<WirelessConnection>();
        wirelessTargets.addAll(validConnectionsCache);
        wirelessTargets.addAll(overloadedHost.getConnections());
        wirelessTargets.addAll(wirelessOverflow.connections());
        wirelessTargets.forEach(ProviderTarget::clearRuntimeState);

        connectionsDirty = true;
        validConnectionsCache = List.of();
        validConnectionSet = Set.of();
        validConnectionsCacheTick = -1;
        validTargetsCache = List.of();
        validTargetsVersion++;
        wirelessDispatch.clear();
        autoReturn.clearSchedule();
        wirelessDistributor.clearTickState(true);
    }

    private boolean isInductionCardInstalled() {
        if (inductionCardCacheDirty) {
            cachedInductionCardInstalled = computeInductionCardInstalled();
            inductionCardCacheDirty = false;
        }
        return cachedInductionCardInstalled;
    }

    private boolean computeInductionCardInstalled() {
        Item card = getAppliedFluxInductionCard();
        if (card == null) return false;
        if (this instanceof IUpgradeableObject upgradeableLogic) {
            return upgradeableLogic.getUpgrades().isInstalled(card);
        }
        return false;
    }

    private final class AutoReturnEnvironment
            implements OverloadedAutoReturnController.Environment {
        @Override
        public OverloadedPatternProviderBlockEntity provider() {
            return overloadedHost;
        }

        @Override
        public IManagedGridNode gridNode() {
            return gridNode;
        }

        @Override
        public IActionSource actionSource() {
            return wirelessSource;
        }

        @Override
        public AllowedOutputFilter outputFilter() {
            return getOrBuildOutputFilter();
        }

        @Override
        public PatternProviderReturnInventory returnInventory() {
            return returnInventory.full();
        }

        @Override
        public List<WirelessConnection> validConnections(
                ServerLevel providerLevel, long gameTick) {
            return getOrRefreshValidConnections(providerLevel, gameTick);
        }

        @Override
        @Nullable
        public ServerLevel resolveTargetLevel(
                ServerLevel providerLevel, WirelessConnection connection) {
            return OverloadedPatternProviderLogic.this.resolveTargetLevel(
                    providerLevel, connection);
        }

        @Override
        public ProviderTarget normalTarget(
                ServerLevel level, Direction pushDirection) {
            return normalDispatch.target(
                    level, overloadedHost.getBlockPos(), pushDirection);
        }

        @Override
        public List<Direction> normalTargetDirections() {
            return activeNormalTargetDirections();
        }

        @Override
        public void onReturnedStack(GenericStack returnedStack) {
            handleOverloadUnlockOnReturnedStack(returnedStack);
        }
    }

    private final class DistributorHost implements WirelessEnergyDistributor.Host {
        @Override
        public IManagedGridNode getMainNode() {
            return gridNode;
        }

        @Override
        public IActionSource actionSource() {
            return wirelessSource;
        }

        @Override
        public boolean isHostRemoved() {
            return overloadedHost.isRemoved();
        }

        @Override
        public List<WirelessEnergyAPI.Target> getValidTargets() {
            return validTargetsCache;
        }

        @Override
        public int getValidTargetsVersion() {
            return validTargetsVersion;
        }
    }

    private static final Item APPFLUX_INDUCTION_CARD =
            AppFluxHelper.getInductionCard();

    // ---- Cached reflection results (resolved once at class-load, never per-tick) ----

    /** Cached AEKey for Applied Flux FE energy type. Null if Applied Flux is not loaded. */
    @Nullable
    private static final AEKey CACHED_APPFLUX_FE_KEY = AppFluxHelper.FE_KEY;

    /** Cached transfer rate from Applied Flux config. 0 if not available. */
    private static final long CACHED_APPFLUX_TRANSFER_RATE = AppFluxHelper.TRANSFER_RATE;


    public void removeSavedData() {
        storage.removeSavedData();
    }

    /**
     * Called from {@code OverloadedPatternProviderBlockEntity.onReady()} when
     * Level is guaranteed to be available. Completes deferred SavedData loading
     * that was skipped during readFromNBT (where Level is still null).
     */
    public void onBlockEntityReady() {
        if (storage.loadOnReady()) {
            saveChanges();
        }
        finishPendingLocalDirectionalOverflowLoad();
        finishPendingWirelessOverflowLoad();
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    @Nullable
    private static Item getAppliedFluxInductionCard() {
        return APPFLUX_INDUCTION_CARD;
    }

    private class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(GRID_TICK_MIN, GRID_TICK_MAX, !hasCombinedGridTickWork(), true);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!gridNode.isActive()) {
                return TickRateModulation.SLEEP;
            }

            var accessor = (PatternProviderLogicAccessor) OverloadedPatternProviderLogic.this;
            prepareParentSendListForDispatch(accessor);
            boolean parentDidWork = accessor.invokeDoWork();
            boolean localDirectionalDidWork = flushLocalDirectionalOverflow();
            flushWirelessSends();
            storage.drainPendingRestore(
                    OverloadedPatternProviderLogic.this::insertStackToNetwork,
                    OverloadedPatternProviderLogic.this::saveChanges);
            tickAutoReturn();
            var level = overloadedHost.getLevel();
            long gameTick = level instanceof ServerLevel sl ? sl.getGameTime() : Long.MAX_VALUE;

            if (hasActiveOverloadedTickWork(gameTick)) {
                return TickRateModulation.URGENT;
            }

            boolean parentHasWork = accessor.invokeHasWorkToDo();
            if (parentHasWork) {
                return parentDidWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
            }
            if (hasLocalDirectionalOverflow()) {
                return localDirectionalDidWork
                        ? TickRateModulation.URGENT
                        : TickRateModulation.SLOWER;
            }

            if (hasAnyTickWork()) {
                return TickRateModulation.SLOWER;
            }

            return TickRateModulation.SLEEP;
        }
    }

    // ---- isBusy override --------------------------------------------------------

    @Override
    public boolean isBusy() {
        // In WIRELESS mode, never report busy: overflow is flushed at the start of
        // pushPattern(), so the crafting system keeps calling us each tick and we
        // get a chance to drain any leftover items.
        // Parent's sendList is always empty in wireless mode (getTargets = empty).
        if (overloadedHost.getProviderMode() == ProviderMode.WIRELESS) {
            return false;
        }
        return hasLocalDirectionalOverflow() || super.isBusy();
    }

    // ---- drops & clearing -------------------------------------------------------

    @Override
    public void addDrops(List<ItemStack> drops) {
        super.addDrops(drops);
        for (var bucket : wirelessOverflow.buckets()) {
            addBucketDrops(bucket, drops);
        }
        var localOverflow = pendingLocalDirectionalOverflowTarget != null
                ? pendingLocalDirectionalOverflowTarget.directionalOverflow()
                : pendingLocalDirectionalOverflowLoad == null
                        ? null
                        : pendingLocalDirectionalOverflowLoad.overflow();
        if (localOverflow != null) {
            for (var entry : localOverflow.snapshot()) {
                var stack = entry.stack();
                stack.what().addDrops(stack.amount(), drops,
                        overloadedHost.getLevel(), overloadedHost.getBlockPos());
            }
        }
        storage.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        storage.clear();
        if (pendingLocalDirectionalOverflowTarget != null) {
            pendingLocalDirectionalOverflowTarget.clearDirectionalOverflow();
            pendingLocalDirectionalOverflowTarget = null;
        }
        pendingLocalDirectionalOverflowLoad = null;
        clearWirelessOverflowState();
        normalDispatch.clear();
        wirelessDispatch.clear();
        autoReturn.clear();
        returnPolicy.patternsChanged();
        adaptiveBatchStatePersistence.clear();
        invalidateValidConnectionsCache();
        inductionCardCacheDirty = true;
        lastEnergyTickGameTime = -1;
        ejectController.clear();
    }

    // ---- NBT persistence --------------------------------------------------------

    private static final String TAG_LOCAL_DIRECTIONAL_OVERFLOW =
            "ae2lt:local_directional_overflow";
    private static final String TAG_LOCAL_TARGET_DIRECTION = "target_direction";
    private static final String TAG_LOCAL_OVERFLOW_ENTRIES = "entries";

    @Override
    public void writeToNBT(CompoundTag tag) {
        super.writeToNBT(tag);
        storage.writeToNBT(tag);
        returnPolicy.writeToNBT(tag);
        var localOverflow = pendingLocalDirectionalOverflowTarget != null
                ? pendingLocalDirectionalOverflowTarget.directionalOverflow()
                : pendingLocalDirectionalOverflowLoad == null
                        ? null
                        : pendingLocalDirectionalOverflowLoad.overflow();
        var localPushDirection = pendingLocalDirectionalOverflowTarget != null
                ? pendingLocalDirectionalOverflowTarget.boundFace().getOpposite()
                : pendingLocalDirectionalOverflowLoad == null
                        ? null
                        : pendingLocalDirectionalOverflowLoad.pushDirection();
        if (localOverflow != null && localPushDirection != null) {
            var localTag = new CompoundTag();
            localTag.putByte(
                    TAG_LOCAL_TARGET_DIRECTION,
                    (byte) localPushDirection.get3DDataValue());
            localTag.put(
                    TAG_LOCAL_OVERFLOW_ENTRIES,
                    WirelessOverflowPersistence.writeRoutedOverflow(
                            localOverflow));
            tag.put(TAG_LOCAL_DIRECTIONAL_OVERFLOW, localTag);
        }
        wirelessOverflowPersistence.write(tag, wirelessOverflow);
        adaptiveBatchStatePersistence.write(
                tag,
                ((PatternProviderLogicAccessor) this).getPatternInventory(),
                patternCatalog,
                normalDispatch,
                overloadedHost.getConnections());
    }

    @Override
    public void readFromNBT(CompoundTag tag) {
        super.readFromNBT(tag);
        storage.readFromNBT(tag, returnInventory.full());
        returnPolicy.readFromNBT(tag);
        pendingLocalDirectionalOverflowTarget = null;
        pendingLocalDirectionalOverflowLoad = null;
        if (tag.contains(TAG_LOCAL_DIRECTIONAL_OVERFLOW, Tag.TAG_COMPOUND)) {
            var localTag = tag.getCompound(TAG_LOCAL_DIRECTIONAL_OVERFLOW);
            int directionId = localTag.getByte(TAG_LOCAL_TARGET_DIRECTION);
            var entries = WirelessOverflowPersistence.readRoutedOverflow(
                    localTag.getList(TAG_LOCAL_OVERFLOW_ENTRIES, Tag.TAG_COMPOUND));
            if (directionId >= 0 && directionId < Direction.values().length
                    && !entries.isEmpty()) {
                pendingLocalDirectionalOverflowLoad =
                        new PendingLocalDirectionalOverflow(
                                Direction.from3DDataValue(directionId),
                                RoutedPatternOverflow.routed(entries));
                finishPendingLocalDirectionalOverflowLoad();
            }
        }
        clearWirelessOverflowState();
        wirelessOverflowPersistence.read(tag);
        finishPendingWirelessOverflowLoad();
        autoReturn.clear();
        invalidateValidConnectionsCache();
        adaptiveBatchStatePersistence.read(
                tag,
                totalCapacity,
                OverloadedPatternProviderBlockEntity.MAX_WIRELESS_CONNECTIONS);
        inductionCardCacheDirty = true;
        lastEnergyTickGameTime = -1;
        refreshEjectRegistrations();
    }

    private void addBucketDrops(Bucket bucket, List<ItemStack> drops) {
        if (bucket.compactMode) {
            var pattern = wirelessOverflow.pattern(
                    Short.toUnsignedInt(bucket.patternId));
            if (pattern == null) return;
            var inputs = pattern.getInputs();
            for (int i = bucket.stuckIndex; i < inputs.length; i++) {
                var possible = inputs[i].getPossibleInputs();
                if (possible.length != 1) continue;
                long amount = i == bucket.stuckIndex
                        ? bucket.remaining
                        : WirelessOverflowPatternTable.inputAmount(inputs[i]);
                if (amount > 0) {
                    possible[0].what().addDrops(amount, drops,
                            overloadedHost.getLevel(), overloadedHost.getBlockPos());
                }
            }
            return;
        }

        for (var entry : bucket.fallback.snapshot()) {
            var stack = entry.stack();
            stack.what().addDrops(stack.amount(), drops,
                    overloadedHost.getLevel(), overloadedHost.getBlockPos());
        }
    }

    private void finishPendingWirelessOverflowLoad() {
        boolean loaded = wirelessOverflowPersistence.finishLoad(
                overloadedHost.getLevel(),
                currentGameTick(),
                wirelessOverflow,
                this::resolveRestoredOverflowConnection,
                wirelessDispatch::pauseTarget);
        if (!loaded) {
            return;
        }
        connectionsDirty = true;
        wirelessDispatch.markDirty();
    }

    private void finishPendingLocalDirectionalOverflowLoad() {
        var pending = pendingLocalDirectionalOverflowLoad;
        var level = overloadedHost.getLevel();
        if (pending == null || level == null) {
            return;
        }
        var target = new ProviderTarget(
                level.dimension(),
                overloadedHost.getBlockPos().relative(pending.pushDirection()),
                pending.pushDirection().getOpposite());
        target.setDirectionalOverflow(pending.overflow());
        normalDispatch.restore(pending.pushDirection(), target);
        pendingLocalDirectionalOverflowTarget = target;
        pendingLocalDirectionalOverflowLoad = null;
    }

    private void finishPendingAdaptiveBatchStateLoad() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        adaptiveBatchStatePersistence.finishLoad(
                serverLevel,
                overloadedHost.getBlockPos(),
                ((PatternProviderLogicAccessor) this).getPatternInventory(),
                patternCatalog,
                normalDispatch,
                activeNormalTargetDirections(),
                overloadedHost.getConnections());
    }

    private boolean hasLocalDirectionalOverflow() {
        return pendingLocalDirectionalOverflowTarget != null
                || pendingLocalDirectionalOverflowLoad != null;
    }

    private WirelessConnection resolveRestoredOverflowConnection(
            WirelessConnection restored) {
        for (var connection : overloadedHost.getConnections()) {
            if (connection.equals(restored)) {
                return connection;
            }
        }
        return wirelessOverflow.adopt(restored);
    }

    private void clearWirelessOverflowState() {
        wirelessOverflow.clear();
        wirelessOverflowPersistence.clear();
        wirelessDispatch.patternsChanged();
    }
}
