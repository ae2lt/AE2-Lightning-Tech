package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.api.storage.AEKeySlotFilter;
import appeng.util.inv.filter.IAEItemFilter;

import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyAPI;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyDistributor;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;

import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;

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
public class OverloadedPatternProviderLogic extends PatternProviderLogic {

    /** Full return inventory with totalPages * 9 slots. */
    private final UnlimitedReturnInventory fullReturnInv;
    /** 9-slot return page view exposed via getReturnInv() for the GUI. */
    private final UnlimitedReturnInventory returnPageView;
    private boolean returnSyncing = false;

    private final OverloadedPatternProviderBlockEntity overloadedHost;
    private final IManagedGridNode gridNode;
    private final IActionSource wirelessSource;
    private final int totalCapacity;

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

    /** Currently displayed page index (0-based). */
    private int currentPage = 0;

    /** Set by readFromNBT when Level is not yet available; consumed by onReady(). */
    private boolean needsSavedDataLoad = false;

    @Nullable
    private MatchMode pendingUnlockMatchMode;
    @Nullable
    private ItemStack pendingUnlockTemplate;

    // ---- wireless dispatch state ------------------------------------------------

    private static final int GLOBAL_BUCKETS_MAX = OverloadedPatternProviderBlockEntity.MAX_WIRELESS_CONNECTIONS;
    private static final int GLOBAL_BUCKETS_REARM = 768;

    /** Per-target wireless overflow buckets. A bucketed connection is not eligible for new pushes. */
    private final Object2ObjectOpenHashMap<WirelessConnection, ConnBucket> pendingOverflowByConn =
            new Object2ObjectOpenHashMap<>();
    private long lastWirelessOverflowFlushTick = Long.MIN_VALUE;

    /** Runtime-local pattern id tables used by compact overflow buckets. */
    private final Object2IntOpenHashMap<IPatternDetails> patternTable = new Object2IntOpenHashMap<>();
    private final Int2ObjectOpenHashMap<IPatternDetails> patternById = new Int2ObjectOpenHashMap<>();
    private int nextPatternId = 0;

    private boolean wirelessGlobalBackpressure = false;
    private final Map<Integer, ItemStack> pendingOverflowPatternDefinitions = new HashMap<>();
    private final List<PendingBucketLoad> pendingOverflowBuckets = new ArrayList<>();

    /** Legacy persisted round-robin position. */
    private int wirelessRoundRobin = 0;

    // ---- unified per-connection state ---------------------------------------------

    private static final int COOLDOWN_INIT = 5;
    private static final int COOLDOWN_MIN  = 1;
    private static final int COOLDOWN_MAX  = 40;
    /** When (searchHi - searchLo) <= this, switch from binary search to +/- fine tuning. */
    private static final int COOLDOWN_NEAR_BAND = 4;
    /** Consecutive post-cooldown successes in fine mode before decreasing cooldownN by 1. */
    private static final int COOLDOWN_STABLE_SUCCESSES = 2;
    private static final float[] PROBE_LEVELS = {5f, 3f, 2f, 1f, 0.5f, 0.3f, 0.1f};
    private static final int PROBE_LEVEL_INIT_IDX = 0;

    static final class ConnectionState implements ReadyDispatchQueue.State {
        // push cooldown — far: binary search on [searchLo, searchHi]; near: +/- with streaks
        long cooldownUntil = -1;
        int cooldownN = COOLDOWN_INIT;
        int searchLo = COOLDOWN_MIN;
        int searchHi = COOLDOWN_MAX;
        /** Fine mode: successes after cooldown before one tick down. */
        int cooldownStableSuccessStreak;
        /** Fine mode: consecutive post-cooldown fails. */
        int cooldownFailStreak;

        // early-probe: fixed level table, one probe per cooldown cycle
        int probeLevelIdx = PROBE_LEVEL_INIT_IDX;
        int probeSkipCounter;
        boolean probedThisCycle;

        // timing-wheel dispatch state
        boolean ready = true;
        boolean probeArmed;
        boolean queued;

        // adapter cache (avoids MachineAdapterRegistry.find() scan every push)
        @Nullable WeakReference<BlockEntity> adapterBERef;
        @Nullable MachineAdapter cachedAdapter;

        @Nullable
        MachineAdapter resolveAdapter(ServerLevel level, BlockPos pos) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                adapterBERef = null;
                cachedAdapter = null;
                return null;
            }
            if (adapterBERef != null && adapterBERef.get() == be && cachedAdapter != null) {
                return cachedAdapter;
            }
            var adapter = MachineAdapterRegistry.find(level, pos);
            adapterBERef = new WeakReference<>(be);
            cachedAdapter = adapter;
            return adapter;
        }

        // auto-return backoff
        long nextPollTick;
        int backoffInterval = BACKOFF_MIN;

        boolean isInProbeWindow(long gameTick) {
            if (cooldownUntil < 0 || gameTick >= cooldownUntil) return false;
            if (probedThisCycle) return false;
            float level = PROBE_LEVELS[probeLevelIdx];
            if (level >= 1.0f) {
                return gameTick == cooldownUntil - (int) level;
            } else {
                int interval = Math.round(1.0f / level);
                return probeSkipCounter >= interval && gameTick == cooldownUntil - 1;
            }
        }

        void onProbeSuccess() {
            probeLevelIdx = 0;
            probeSkipCounter = 0;
            probedThisCycle = true;
            cooldownUntil = -1;
        }

        void onProbeFail() {
            probeLevelIdx = Math.min(probeLevelIdx + 1, PROBE_LEVELS.length - 1);
            probeSkipCounter = 0;
            probedThisCycle = true;
        }

        /** True when search interval is narrow — use +/- instead of binary. */
        private boolean nearBand() {
            return searchHi - searchLo <= COOLDOWN_NEAR_BAND;
        }

        void onPushSuccess(long gameTick) {
            if (cooldownUntil < 0) return;
            cooldownFailStreak = 0;
            if (nearBand()) {
                cooldownStableSuccessStreak++;
                if (cooldownStableSuccessStreak >= COOLDOWN_STABLE_SUCCESSES) {
                    cooldownN = Math.max(COOLDOWN_MIN, cooldownN - 1);
                    cooldownStableSuccessStreak = 0;
                }
            } else {
                cooldownStableSuccessStreak = 0;
                searchHi = cooldownN;
                cooldownN = Math.max(searchLo, (searchLo + searchHi) / 2);
                cooldownN = Math.clamp(cooldownN, COOLDOWN_MIN, COOLDOWN_MAX);
            }
            cooldownUntil = -1;
        }

        void onPushFail(long gameTick) {
            if (cooldownUntil < 0) {
                cooldownUntil = gameTick + cooldownN;
                cooldownStableSuccessStreak = 0;
                probedThisCycle = false;
                probeSkipCounter++;
                return;
            }
            cooldownStableSuccessStreak = 0;
            if (nearBand()) {
                cooldownFailStreak++;
                int step = Math.min(2, 1 + (cooldownFailStreak - 1) / 4);
                cooldownN = Math.min(COOLDOWN_MAX, cooldownN + step);
            } else {
                cooldownFailStreak = 0;
                searchLo = cooldownN + 1;
                if (searchLo > searchHi) {
                    searchLo = COOLDOWN_MAX;
                    searchHi = COOLDOWN_MAX;
                    cooldownN = COOLDOWN_MAX;
                } else {
                    cooldownN = (searchLo + searchHi) / 2;
                    cooldownN = Math.clamp(cooldownN, COOLDOWN_MIN, COOLDOWN_MAX);
                }
            }
            cooldownUntil = gameTick + cooldownN;
            probedThisCycle = false;
            probeSkipCounter++;
        }

        void resetBackoff(long gameTick) {
            backoffInterval = BACKOFF_MIN;
            nextPollTick = gameTick + BACKOFF_MIN;
        }

        void updateBackoff(long gameTick, boolean foundItems) {
            backoffInterval = foundItems ? BACKOFF_MIN
                    : Math.min(backoffInterval * 2, BACKOFF_MAX);
            nextPollTick = gameTick + backoffInterval;
        }

        @Override
        public boolean isQueued() {
            return queued;
        }

        @Override
        public void setQueued(boolean queued) {
            this.queued = queued;
        }
    }

    private final Map<WirelessConnection, ConnectionState> connectionStates = new HashMap<>();

    private ConnectionState getOrCreateState(WirelessConnection conn) {
        return connectionStates.computeIfAbsent(conn, k -> new ConnectionState());
    }

    // ---- push timing wheel + ready queue ----------------------------------------

    private static final int PUSH_WHEEL_BITS = 6;
    private static final int PUSH_WHEEL_SIZE = 1 << PUSH_WHEEL_BITS; // 64 — comfortably covers COOLDOWN_MAX (40)
    private static final int PUSH_WHEEL_MASK = PUSH_WHEEL_SIZE - 1;

    @SuppressWarnings("unchecked")
    private final List<WirelessConnection>[] pushWheel = new List[PUSH_WHEEL_SIZE];
    {
        for (int i = 0; i < PUSH_WHEEL_SIZE; i++) pushWheel[i] = new ArrayList<>();
    }

    private final ReadyDispatchQueue<WirelessConnection, ConnectionState> singleReadyQueue =
            new ReadyDispatchQueue<>(connectionStates::get);
    private final ReadyDispatchQueue<WirelessConnection, ConnectionState> evenReadyQueue =
            new ReadyDispatchQueue<>(connectionStates::get);
    private final PatternDispatchPenaltyTracker<WirelessConnection, AEItemKey> patternPenalties =
            new PatternDispatchPenaltyTracker<>();
    private List<WirelessConnection> pushWheelValidRef = List.of();
    private boolean pushStructuresDirty = true;
    private long lastPushWheelTick = -1;

    private static final class ConnBucket {
        final boolean compactMode;
        short patternId;
        short stuckIdx;
        long remaining;
        final List<GenericStack> fallbackList;

        private ConnBucket(boolean compactMode, short patternId, short stuckIdx,
                           long remaining, List<GenericStack> fallbackList) {
            this.compactMode = compactMode;
            this.patternId = patternId;
            this.stuckIdx = stuckIdx;
            this.remaining = remaining;
            this.fallbackList = fallbackList;
        }

        static ConnBucket compact(short patternId, short stuckIdx, long remaining) {
            return new ConnBucket(true, patternId, stuckIdx, remaining, new ArrayList<>());
        }

        static ConnBucket fallback(short patternId, List<GenericStack> overflow) {
            return new ConnBucket(false, patternId, (short) 0, 0, new ArrayList<>(overflow));
        }
    }

    private record PendingBucketLoad(WirelessConnection conn, short patternId, short stuckIdx,
                                     long remaining, List<GenericStack> fallbackList,
                                     boolean compactMode) {}

    // ---- PatternProviderTarget cache (avoids recreating strategies every push) -----

    private record TargetCacheKey(ResourceKey<Level> dimension, long posLong, Direction face) {}

    private static final class TargetCacheEntry {
        final WeakReference<BlockEntity> beRef;
        final PatternProviderTarget target;
        final long createdTick;

        TargetCacheEntry(BlockEntity be, PatternProviderTarget target, long tick) {
            this.beRef = new WeakReference<>(be);
            this.target = target;
            this.createdTick = tick;
        }

        boolean isValid(BlockEntity currentBE, long currentTick) {
            return beRef.get() == currentBE && (currentTick - createdTick) < TARGET_CACHE_TTL;
        }
    }

    private static final int TARGET_CACHE_TTL = 20;
    private final Map<TargetCacheKey, TargetCacheEntry> targetCache = new HashMap<>();

    /** Cached output filter derived from loaded patterns. */
    @Nullable
    private AllowedOutputFilter cachedOutputFilter;

    /** Marks the cached output filter dirty when patterns change. */
    private boolean outputFilterDirty = true;

    // ---- auto-return state (per-machine exponential backoff) --------------------

    /** Per-machine key for backoff maps without transient String allocation. */
    private record MachineId(ResourceKey<Level> dimension, long posLong, Direction face) {}

    /** Per-machine: game-tick at which the next poll is allowed. */
    private final Map<MachineId, Long> machineNextPoll = new HashMap<>();

    /** Per-machine: current backoff interval in ticks. */
    private final Map<MachineId, Integer> machineBackoff = new HashMap<>();

    /** Minimum polling interval (reset value after a successful extraction). */
    private static final int BACKOFF_MIN = 10;    // 0.5 second

    /** Maximum polling interval (cap for exponential growth). */
    private static final int BACKOFF_MAX = 1200;  // 60 seconds

    /** Wireless round-robin return: spread all machines across this many ticks. */
    private static final int RETURN_SPREAD_TICKS = 20;

    /** AE2 grid tick range for the overloaded provider's custom scheduler. */
    private static final int GRID_TICK_MIN = 1;
    private static final int GRID_TICK_MAX = 20;

    /** Refresh the validated wireless-connection view at most once per second. */
    private static final int VALIDATE_INTERVAL = 20;

    /** Cached list of valid wireless connections (shared by energy + auto-return). */
    private List<WirelessConnection> validConnectionsCache = List.of();

    /** Game tick at which validConnectionsCache was last refreshed. */
    private long validConnectionsCacheTick = -1;

    /** External host changes force the next wireless lookup to rebuild the cache immediately. */
    private boolean connectionsDirty = true;

    /** Prevents double execution when both BlockEntityTicker and AE2 Grid Ticker fire in the same tick. */
    private long lastEnergyTickGameTime = -1;

    /** Wireless round-robin: index into valid connections for spread return. */
    private int returnRobinIndex = 0;

    /** Last game tick when round-robin return was executed. */
    private long lastReturnRobinTick = -1;

    /** Last game tick when single-machine pre-distribution return was executed. */
    private long lastSingleReturnTick = -1;

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
        this.gridNode = mainNode;
        this.wirelessSource = new MachineSource(mainNode::getNode);
        this.totalCapacity = patternInventorySize;
        this.wirelessDistributor = new WirelessEnergyDistributor(new DistributorHost());
        this.patternTable.defaultReturnValue(-1);

        var accessor = (PatternProviderLogicAccessor) this;
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
        AEKeySlotFilter returnFilter = (slot, key) -> {
            if (!overloadedHost.isFilteredImport()) return true;
            var filter = getOrBuildOutputFilter();
            return !filter.isEmpty() && filter.matches(key);
        };

        int totalPages = (totalCapacity + 35) / 36;
        int fullReturnSlots = totalPages * 9;

        if (fullReturnSlots > 9) {
            this.fullReturnInv = UnlimitedReturnInventory.create(returnListener, returnFilter, fullReturnSlots);
        } else {
            this.fullReturnInv = UnlimitedReturnInventory.create(returnListener, returnFilter);
        }

        this.returnPageView = UnlimitedReturnInventory.create(() -> {
            if (!returnSyncing) {
                syncReturnFullFromPageView();
                returnListener.run();
            }
        }, returnFilter);

        accessor.setReturnInv(this.fullReturnInv);
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
        return returnPageView;
    }

    public PatternProviderReturnInventory getInternalReturnInv() {
        return fullReturnInv;
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
        clearPendingUnlockRule();
    }

    @Override
    public void onChangeInventory(appeng.util.inv.AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
    }

    // ---- page management --------------------------------------------------------

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return (totalCapacity + 35) / 36;
    }

    public void setCurrentPage(int page) {
        int maxPage = getTotalPages() - 1;
        page = Math.max(0, Math.min(page, maxPage));
        if (page == currentPage) return;
        syncReturnFullFromPageView();
        currentPage = page;
        syncReturnPageViewFromFull();
    }

    /** Copy 9 slots from fullReturnInv to returnPageView based on currentPage. */
    public void syncReturnPageViewFromFull() {
        returnSyncing = true;
        try {
            int offset = currentPage * 9;
            for (int i = 0; i < 9; i++) {
                int fullIdx = offset + i;
                if (fullIdx < fullReturnInv.size()) {
                    returnPageView.setStack(i, fullReturnInv.getStack(fullIdx));
                } else {
                    returnPageView.setStack(i, null);
                }
            }
        } finally {
            returnSyncing = false;
        }
    }

    /** Copy 9 slots from returnPageView back to fullReturnInv. */
    private void syncReturnFullFromPageView() {
        int offset = currentPage * 9;
        for (int i = 0; i < 9; i++) {
            int fullIdx = offset + i;
            if (fullIdx < fullReturnInv.size()) {
                fullReturnInv.setStack(fullIdx, returnPageView.getStack(i));
            }
        }
    }

    @Override
    public void updatePatterns() {
        patternPenalties.clear();
        var accessor = (PatternProviderLogicAccessor) this;
        var patterns = accessor.getPatterns();
        var patternInputs = accessor.getPatternInputs();
        var inventory = accessor.getPatternInventory();

        patterns.clear();
        patternInputs.clear();

        var level = overloadedHost.getLevel();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var patternStack = inventory.getStackInSlot(slot);
            var details = PatternDetailsHelper.decodePattern(patternStack, level);
            if (details == null) {
                continue;
            }

            patterns.add(details);
            for (var input : details.getInputs()) {
                for (var possibleInput : input.getPossibleInputs()) {
                    patternInputs.add(possibleInput.what().dropSecondary());
                }
            }
        }
        outputFilterDirty = true;
        refreshEjectRegistrations();

        // EAP smart-doubling compat: re-apply the eap$allowScaling marker to
        // every pattern. Vanilla updatePatterns has a TAIL mixin from EAP that
        // does this; since we fully override updatePatterns without calling
        // super, we replicate it here.
        SmartDoublingCompat.applyTo(this, patterns);

        ICraftingProvider.requestUpdate(accessor.getMainNode());
        alertGridTick();
    }

    // ---- pushPattern override ---------------------------------------------------

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Always try to flush wireless overflow (handles mode-switching edge case)
        if (!pendingOverflowByConn.isEmpty()) {
            flushWirelessSends();
        }

        if (overloadedHost.getProviderMode() == ProviderMode.NORMAL) {
            double cost = PowerCostUtil.totalCost(inputHolder);
            var grid = gridNode.getGrid();
            if (!PowerCostUtil.canAfford(grid, cost)) {
                return false;
            }
            boolean result;
            if (AdvancedAECompat.isDirectional(patternDetails)) {
                result = pushPatternDirectionally(patternDetails, inputHolder);
            } else {
                result = super.pushPattern(patternDetails, inputHolder);
                if (result) {
                    syncPendingUnlockRule(patternDetails);
                }
            }
            if (result) {
                PowerCostUtil.consumeRaw(grid, cost);
                if (overloadedHost.isAutoReturn()) {
                    resetBackoffAllTargets();
                }
                alertGridTick();
            }
            return result;
        }
        return wirelessPushPattern(patternDetails, inputHolder);
    }

    private boolean wirelessPushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        refreshGlobalBackpressure();
        if (wirelessGlobalBackpressure) return false;
        if (!gridNode.isActive()) return false;
        if (!SmartDoublingCompat.containsOrUnwrapped(getAvailablePatterns(), pattern)) return false;
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

        if (pushStructuresDirty || (valid != pushWheelValidRef && !valid.equals(pushWheelValidRef))) {
            rebuildPushStructures(valid, gameTick, dispatchMode);
        }
        advancePushWheel(gameTick, fastMode, dispatchMode);

        return switch (dispatchMode) {
            case EVEN_DISTRIBUTION -> wirelessPushEvenDistribution(pattern, inputs, valid, server, gameTick);
            case SINGLE_TARGET -> wirelessPushSingleTarget(pattern, inputs, valid, server, gameTick, fastMode);
        };
    }

    // ---- EVEN_DISTRIBUTION: ready-target round-robin push -------------------------

    private boolean wirelessPushEvenDistribution(IPatternDetails pattern, KeyCounter[] inputs,
            List<WirelessConnection> valid, net.minecraft.server.MinecraftServer server,
            long gameTick) {
        int scanBudget = evenReadyQueue.size();
        var patternKey = pattern.getDefinition();
        while (scanBudget-- > 0 && !evenReadyQueue.isEmpty()) {
            var conn = evenReadyQueue.peek();
            if (pendingOverflowByConn.containsKey(conn)) {
                evenReadyQueue.removeHead();
                continue;
            }

            if (patternPenalties.shouldSkip(conn, patternKey, gameTick)) {
                evenReadyQueue.rotateHeadToTail();
                continue;
            }

            var state = connectionStates.get(conn);
            if (state == null || !state.ready) {
                evenReadyQueue.removeHead();
                continue;
            }

            boolean probing = state.probeArmed
                    && state.cooldownUntil >= 0 && gameTick < state.cooldownUntil;

            var outcome = tryPushToConnection(pattern, inputs, conn, server);
            if (outcome.consumesTargetAttempt()) {
                state.probeArmed = false;
            }
            switch (outcome) {
                case SUCCESS -> {
                    patternPenalties.recordSuccess(conn, patternKey);
                    if (probing) {
                        state.onProbeSuccess();
                    } else {
                        state.onPushSuccess(gameTick);
                    }
                    if (pendingOverflowByConn.containsKey(conn)) {
                        evenReadyQueue.removeHead();
                    } else {
                        evenReadyQueue.rotateHeadToTail();
                    }
                    return true;
                }
                case HARD_FAIL -> {
                    if (!isConnectionAlive(conn, server)) {
                        evenReadyQueue.removeHead();
                        patternPenalties.removeTarget(conn);
                        connectionsDirty = true;
                    } else {
                        evenReadyQueue.rotateHeadToTail();
                    }
                }
                case SOFT_FAIL -> {
                    patternPenalties.recordRejection(conn, patternKey, gameTick);
                    evenReadyQueue.rotateHeadToTail();
                }
                case GLOBAL_ABORT -> {
                    return false;
                }
            }
        }
        return false;
    }

    // ---- SINGLE_TARGET: sticky round-robin push -----------------------------------

    private boolean wirelessPushSingleTarget(IPatternDetails pattern, KeyCounter[] inputs,
            List<WirelessConnection> valid, net.minecraft.server.MinecraftServer server,
            long gameTick, boolean fastMode) {

        while (!singleReadyQueue.isEmpty()) {
            var conn = singleReadyQueue.peek();
            if (pendingOverflowByConn.containsKey(conn)) {
                singleReadyQueue.removeHead();
                continue;
            }
            var state = connectionStates.get(conn);

            if (state == null) {
                singleReadyQueue.removeHead();
                continue;
            }

            boolean probing = state.probeArmed
                    && state.cooldownUntil >= 0 && gameTick < state.cooldownUntil;

            var outcome = tryPushToConnection(pattern, inputs, conn, server);
            if (outcome.consumesTargetAttempt()) {
                state.probeArmed = false;
            }
            switch (outcome) {
                case SUCCESS -> {
                    if (probing) {
                        state.onProbeSuccess();
                    } else {
                        state.onPushSuccess(gameTick);
                    }
                    if (pendingOverflowByConn.containsKey(conn)) {
                        singleReadyQueue.removeHead();
                    }
                    return true;
                }
                case SOFT_FAIL -> {
                    singleReadyQueue.removeHead();
                    if (probing) {
                        state.onProbeFail();
                    } else {
                        state.onPushFail(gameTick);
                    }
                    schedulePushWheel(conn, state, fastMode, WirelessDispatchMode.SINGLE_TARGET);
                }
                case HARD_FAIL -> {
                    singleReadyQueue.removeHead();
                    if (!isConnectionAlive(conn, server)) {
                        connectionsDirty = true;
                    }
                }
                case GLOBAL_ABORT -> {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isConnectionAlive(WirelessConnection conn,
                                             net.minecraft.server.MinecraftServer server) {
        var level = server.getLevel(conn.dimension());
        return level != null && level.isLoaded(conn.pos())
                && level.getBlockEntity(conn.pos()) != null;
    }

    private void rebuildPushStructures(List<WirelessConnection> valid, long gameTick,
                                       WirelessDispatchMode dispatchMode) {
        for (var slot : pushWheel) slot.clear();
        clearReadyQueuesAndQueuedFlags();
        for (var conn : valid) {
            if (pendingOverflowByConn.containsKey(conn)) {
                continue;
            }
            var state = getOrCreateState(conn);
            if (state.cooldownUntil >= 0 && gameTick < state.cooldownUntil) {
                state.ready = false;
                state.probeArmed = false;
                int slot = (int) (state.cooldownUntil & PUSH_WHEEL_MASK);
                pushWheel[slot].add(conn);
            } else {
                state.ready = true;
                state.probeArmed = false;
                enqueueReady(conn, state, dispatchMode);
            }
        }
        pushWheelValidRef = valid;
        pushStructuresDirty = false;
        lastPushWheelTick = gameTick;
    }

    private void advancePushWheel(long gameTick, boolean fastMode,
                                  WirelessDispatchMode dispatchMode) {
        if (lastPushWheelTick < 0) lastPushWheelTick = gameTick - 1;
        long delta = gameTick - lastPushWheelTick;
        if (delta <= 0) return;
        int steps = (int) Math.min(delta, PUSH_WHEEL_SIZE);
        for (int i = 0; i < steps; i++) {
            long t = lastPushWheelTick + 1 + i;
            int slot = (int) (t & PUSH_WHEEL_MASK);
            var list = pushWheel[slot];
            if (list.isEmpty()) continue;
            var iter = list.iterator();
            while (iter.hasNext()) {
                var conn = iter.next();
                if (pendingOverflowByConn.containsKey(conn)) {
                    iter.remove();
                    continue;
                }
                var state = connectionStates.get(conn);
                if (state == null) { iter.remove(); continue; }
                boolean fire = false;
                boolean probe = false;
                if (state.cooldownUntil < 0 || t >= state.cooldownUntil) {
                    fire = true;
                } else if (fastMode && !state.probedThisCycle && state.isInProbeWindow(t)) {
                    fire = true;
                    probe = true;
                }
                if (fire) {
                    iter.remove();
                    state.ready = true;
                    state.probeArmed = probe;
                    enqueueReady(conn, state, dispatchMode);
                }
            }
        }
        lastPushWheelTick = gameTick;
    }

    private void schedulePushWheel(WirelessConnection conn, ConnectionState state,
                                   boolean fastMode, WirelessDispatchMode dispatchMode) {
        if (pendingOverflowByConn.containsKey(conn)) {
            return;
        }
        state.ready = false;
        state.probeArmed = false;
        if (state.cooldownUntil < 0) {
            state.ready = true;
            enqueueReady(conn, state, dispatchMode);
            return;
        }
        long targetTick = state.cooldownUntil;
        if (fastMode && !state.probedThisCycle) {
            float level = PROBE_LEVELS[state.probeLevelIdx];
            if (level >= 1.0f) {
                long probeTick = state.cooldownUntil - (int) level;
                if (probeTick > lastPushWheelTick) targetTick = probeTick;
            } else {
                int interval = Math.round(1.0f / level);
                if (state.probeSkipCounter >= interval) {
                    long probeTick = state.cooldownUntil - 1;
                    if (probeTick > lastPushWheelTick) targetTick = probeTick;
                }
            }
        }
        targetTick = Math.max(targetTick, lastPushWheelTick + 1);
        int slot = (int) (targetTick & PUSH_WHEEL_MASK);
        pushWheel[slot].add(conn);
    }

    private void enqueueReady(WirelessConnection conn, ConnectionState state,
                              WirelessDispatchMode dispatchMode) {
        if (!state.ready || pendingOverflowByConn.containsKey(conn)) {
            return;
        }
        switch (dispatchMode) {
            case SINGLE_TARGET -> singleReadyQueue.offer(conn);
            case EVEN_DISTRIBUTION -> evenReadyQueue.offer(conn);
        }
    }

    private void clearReadyQueuesAndQueuedFlags() {
        singleReadyQueue.clear();
        evenReadyQueue.clear();
        for (var state : connectionStates.values()) {
            state.setQueued(false);
        }
    }

    private WirelessPushOutcome tryPushToConnection(IPatternDetails pattern, KeyCounter[] inputs,
            WirelessConnection conn, net.minecraft.server.MinecraftServer server) {
        if (pendingOverflowByConn.containsKey(conn)) return WirelessPushOutcome.SOFT_FAIL;
        if (AdvancedAECompat.isDirectional(pattern)) {
            return tryPushToConnectionDirectionally(pattern, inputs, conn, server);
        }

        var targetLevel = server.getLevel(conn.dimension());
        if (targetLevel == null) return WirelessPushOutcome.HARD_FAIL;

        autoReturnBeforePush(targetLevel, conn);

        var state = getOrCreateState(conn);
        var adapter = state.resolveAdapter(targetLevel, conn.pos());
        if (adapter == null) return WirelessPushOutcome.HARD_FAIL;

        double cost = PowerCostUtil.totalCost(inputs);
        var grid = gridNode.getGrid();
        if (!PowerCostUtil.canAfford(grid, cost)) {
            return WirelessPushOutcome.GLOBAL_ABORT;
        }

        boolean blocking = isBlocking();
        var patternInputs = ((PatternProviderLogicAccessor) this).getPatternInputs();
        // 预取 cached target：blocking 校验 + 后续 pushCopies 共用同一份，
        // 把 capability 查询从"每 push 1~2 次"降到"每 20 tick 1 次"
        var targetBe = targetLevel.getBlockEntity(conn.pos());
        PatternProviderTarget cachedTarget = (targetBe != null)
                ? getCachedTarget(targetLevel, conn.pos(), targetBe, conn.boundFace(), wirelessSource)
                : null;
        // EAP advanced-blocking compat: when ADVANCED_BLOCKING is on and the
        // target fully covers every input slot, treat the push as not blocked
        // (mirrors EAP's @Redirect on PatternProviderTarget.containsPatternInput).
        if (blocking && cachedTarget != null
                && AdvancedBlockingCompat.shouldBypassBlocking(this, cachedTarget, pattern)) {
            blocking = false;
        }
        var result = adapter.pushCopies(
                targetLevel, conn.pos(), conn.boundFace(),
                pattern, inputs, 1,
                blocking, patternInputs, wirelessSource, cachedTarget);
        if (result.acceptedCopies() == 0) return WirelessPushOutcome.SOFT_FAIL;

        PowerCostUtil.consumeRaw(grid, cost);

        if (!result.overflow().isEmpty()) {
            bucketOverflow(conn, pattern, result.overflow(), false);
        }

        ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(pattern);
        syncPendingUnlockRule(pattern);
        alertGridTick();

        if (overloadedHost.isAutoReturn()) {
            getOrCreateState(conn).resetBackoff(targetLevel.getGameTime());
        }
        return WirelessPushOutcome.SUCCESS;
    }

    private void bucketOverflow(WirelessConnection conn, IPatternDetails pattern,
                                List<GenericStack> overflow, boolean forceFallback) {
        if (overflow.isEmpty()) return;

        short patternId = internPattern(pattern);
        ConnBucket bucket;
        if (!forceFallback && isCompactEligible(pattern)) {
            var inputs = pattern.getInputs();
            var first = overflow.get(0);
            int stuckIdx = findSlotIndex(inputs, first.what());
            if (stuckIdx >= 0 && verifySequentialOverflow(inputs, stuckIdx, overflow)) {
                bucket = ConnBucket.compact(patternId, (short) stuckIdx, first.amount());
            } else {
                bucket = ConnBucket.fallback(patternId, overflow);
            }
        } else {
            bucket = ConnBucket.fallback(patternId, overflow);
        }

        pendingOverflowByConn.put(conn, bucket);
        connectionsDirty = true;
        pushStructuresDirty = true;
        refreshGlobalBackpressure();
        alertGridTick();
    }

    private short internPattern(IPatternDetails pattern) {
        int id = patternTable.getInt(pattern);
        if (id >= 0) {
            return (short) id;
        }

        if (patternById.size() >= 0xFFFF) {
            compactRuntimePatternTable();
        }

        for (int attempts = 0; attempts <= 0xFFFF; attempts++) {
            id = nextPatternId++ & 0xFFFF;
            if (nextPatternId > 0xFFFF) {
                nextPatternId = 0;
            }
            if (!patternById.containsKey(id)) {
                patternTable.put(pattern, id);
                patternById.put(id, pattern);
                return (short) id;
            }
        }

        compactRuntimePatternTable();
        id = nextPatternId++ & 0xFFFF;
        patternTable.put(pattern, id);
        patternById.put(id, pattern);
        return (short) id;
    }

    private void compactRuntimePatternTable() {
        var oldPatternById = new Int2ObjectOpenHashMap<IPatternDetails>(patternById);
        patternTable.clear();
        patternById.clear();
        nextPatternId = 0;

        for (var bucket : pendingOverflowByConn.values()) {
            if (!bucket.compactMode) continue;
            var pattern = oldPatternById.get(Short.toUnsignedInt(bucket.patternId));
            if (pattern == null) continue;
            int id = nextPatternId++ & 0xFFFF;
            bucket.patternId = (short) id;
            patternTable.put(pattern, id);
            patternById.put(id, pattern);
        }
    }

    private static boolean isCompactEligible(IPatternDetails pattern) {
        if (SmartDoublingCompat.unwrap(pattern) != null) {
            return false;
        }
        OverloadPatternDetails overloadDetails = pattern instanceof OverloadedProviderOnlyPatternDetails overload
                ? overload.overloadPatternDetailsView()
                : null;
        var seen = new java.util.HashSet<AEKey>();
        var inputs = pattern.getInputs();
        for (int i = 0; i < inputs.length; i++) {
            if (overloadDetails != null && overloadDetails.inputMode(i) != MatchMode.STRICT) {
                return false;
            }
            var input = inputs[i];
            var possible = input.getPossibleInputs();
            if (possible.length != 1) {
                return false;
            }
            if (!seen.add(possible[0].what())) {
                return false;
            }
        }
        return true;
    }

    private static int findSlotIndex(IPatternDetails.IInput[] inputs, AEKey key) {
        for (int i = 0; i < inputs.length; i++) {
            var possible = inputs[i].getPossibleInputs();
            if (possible.length == 1 && possible[0].what().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean verifySequentialOverflow(IPatternDetails.IInput[] inputs, int stuckIdx,
                                                    List<GenericStack> overflow) {
        if (stuckIdx < 0 || stuckIdx >= inputs.length) return false;
        if (overflow.size() != inputs.length - stuckIdx) return false;

        for (int i = 0; i < overflow.size(); i++) {
            var stack = overflow.get(i);
            var input = inputs[stuckIdx + i];
            var possible = input.getPossibleInputs();
            if (possible.length != 1 || !possible[0].what().equals(stack.what())) {
                return false;
            }
            long fullAmount = inputAmount(input);
            if (i == 0) {
                if (stack.amount() <= 0 || stack.amount() > fullAmount) {
                    return false;
                }
            } else if (stack.amount() != fullAmount) {
                return false;
            }
        }
        return true;
    }

    private static long inputAmount(IPatternDetails.IInput input) {
        var possible = input.getPossibleInputs();
        if (possible.length == 0) {
            return 0;
        }
        return possible[0].amount() * input.getMultiplier();
    }

    // ---- AdvancedAE directional push (NORMAL mode) --------------------------------

    /**
     * Push a directional AdvancedAE pattern through adjacent machines in NORMAL mode.
     * Each input key is routed to the target-machine face specified by the pattern's
     * directionMap; keys without a mapping use the default face (pushDir.getOpposite()).
     */
    private boolean pushPatternDirectionally(IPatternDetails pattern, KeyCounter[] inputs) {
        var accessor = (PatternProviderLogicAccessor) this;
        if (!accessor.getSendList().isEmpty()) return false;
        if (!gridNode.isActive()) return false;
        if (!SmartDoublingCompat.containsOrUnwrapped(getAvailablePatterns(), pattern)) return false;
        if (getCraftingLockedReason() != LockCraftingMode.NONE) return false;
        if (!pattern.supportsPushInputsToExternalInventory()) return false;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return false;

        var targets = overloadedHost.getTargets();
        if (targets.isEmpty()) return false;

        var providerPos = overloadedHost.getBlockPos();
        var patternInputKeys = accessor.getPatternInputs();

        EjectModeRegistry.setBypass(true);
        try {
            for (var pushDir : targets) {
                var targetPos = providerPos.relative(pushDir);
                var defaultFace = pushDir.getOpposite();
                var be = sl.getBlockEntity(targetPos);
                if (be == null) continue;

                var faceToTarget = buildDirectionalTargets(
                        sl, targetPos, be, defaultFace, pattern, inputs, wirelessSource);
                if (faceToTarget == null) continue;

                if (isBlocking()) {
                    var anyTarget = faceToTarget.values().iterator().next();
                    // EAP advanced-blocking compat: bypass when target fully matches.
                    if (anyTarget.containsPatternInput(patternInputKeys)
                            && !AdvancedBlockingCompat.shouldBypassBlocking(this, anyTarget, pattern)) continue;
                }

                if (!simulateDirectionalAcceptance(faceToTarget, defaultFace, pattern, inputs)) continue;

                commitDirectionalPush(pattern, inputs, faceToTarget, defaultFace);

                accessor.setSendDirection(defaultFace);
                accessor.invokeSendStacksOut();
                accessor.invokeOnPushPatternSuccess(pattern);
                syncPendingUnlockRule(pattern);
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
    private WirelessPushOutcome tryPushToConnectionDirectionally(IPatternDetails pattern, KeyCounter[] inputs,
            WirelessConnection conn, net.minecraft.server.MinecraftServer server) {
        if (pendingOverflowByConn.containsKey(conn)) return WirelessPushOutcome.SOFT_FAIL;
        var targetLevel = server.getLevel(conn.dimension());
        if (targetLevel == null) return WirelessPushOutcome.HARD_FAIL;
        if (!targetLevel.isLoaded(conn.pos())) return WirelessPushOutcome.HARD_FAIL;
        if (!pattern.supportsPushInputsToExternalInventory()) return WirelessPushOutcome.SOFT_FAIL;

        autoReturnBeforePush(targetLevel, conn);

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
                    targetLevel, conn.pos(), be, defaultFace, pattern, inputs, wirelessSource);
            if (faceToTarget == null) return WirelessPushOutcome.SOFT_FAIL;

            if (isBlocking()) {
                var patternInputKeys = ((PatternProviderLogicAccessor) this).getPatternInputs();
                var anyTarget = faceToTarget.values().iterator().next();
                // EAP advanced-blocking compat: bypass when target fully matches.
                if (anyTarget.containsPatternInput(patternInputKeys)
                        && !AdvancedBlockingCompat.shouldBypassBlocking(this, anyTarget, pattern)) return WirelessPushOutcome.SOFT_FAIL;
            }

            if (!simulateDirectionalAcceptance(faceToTarget, defaultFace, pattern, inputs))
                return WirelessPushOutcome.SOFT_FAIL;

            var overflow = commitDirectionalPushWithOverflow(pattern, inputs, faceToTarget, defaultFace);
            PowerCostUtil.consumeRaw(grid, cost);
            if (!overflow.isEmpty()) {
                bucketOverflow(conn, pattern, overflow, true);
            }
        } finally {
            EjectModeRegistry.setBypass(false);
        }

        ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(pattern);
        syncPendingUnlockRule(pattern);
        alertGridTick();

        if (overloadedHost.isAutoReturn()) {
            getOrCreateState(conn).resetBackoff(targetLevel.getGameTime());
        }
        return WirelessPushOutcome.SUCCESS;
    }

    // ---- directional push helpers ------------------------------------------------

    @Nullable
    private PatternProviderTarget getCachedTarget(
            ServerLevel level, BlockPos pos, BlockEntity be, Direction face, IActionSource source) {
        long gameTick = level.getGameTime();
        var key = new TargetCacheKey(level.dimension(), pos.asLong(), face);
        var entry = targetCache.get(key);
        if (entry != null && entry.isValid(be, gameTick)) {
            return entry.target;
        }
        var target = PatternProviderTarget.get(level, pos, be, face, source);
        if (target != null) {
            targetCache.put(key, new TargetCacheEntry(be, target, gameTick));
        } else {
            targetCache.remove(key);
        }
        return target;
    }

    /**
     * Build a map of face -> PatternProviderTarget for all unique faces
     * referenced by the directional pattern's inputs.
     *
     * @return the map, or {@code null} if any required target cannot be resolved
     */
    @Nullable
    private Map<Direction, PatternProviderTarget> buildDirectionalTargets(
            ServerLevel level, BlockPos targetPos, BlockEntity be,
            Direction defaultFace, IPatternDetails pattern,
            KeyCounter[] inputs, IActionSource source) {
        var map = new HashMap<Direction, PatternProviderTarget>();
        for (var inputList : inputs) {
            for (var entry : inputList) {
                var dir = AdvancedAECompat.getDirectionForKey(pattern, entry.getKey());
                var face = dir != null ? dir : defaultFace;
                map.computeIfAbsent(face, f -> getCachedTarget(level, targetPos, be, f, source));
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
     * Commit directional push for NORMAL mode.
     * Overflow goes to the parent's sendList via accessor.
     */
    private void commitDirectionalPush(IPatternDetails pattern, KeyCounter[] inputs,
            Map<Direction, PatternProviderTarget> faceToTarget, Direction defaultFace) {
        var accessor = (PatternProviderLogicAccessor) this;
        pattern.pushInputsToExternalInventory(inputs, (what, amount) -> {
            var dir = AdvancedAECompat.getDirectionForKey(pattern, what);
            var face = dir != null ? dir : defaultFace;
            var target = faceToTarget.get(face);
            if (target != null) {
                var inserted = target.insert(what, amount, Actionable.MODULATE);
                if (inserted < amount) {
                    accessor.invokeAddToSendList(what, amount - inserted);
                }
            } else {
                accessor.invokeAddToSendList(what, amount);
            }
        });
    }

    /**
     * Commit directional push for WIRELESS mode.
     * Returns overflow items directly instead of using the parent's sendList.
     */
    private static List<GenericStack> commitDirectionalPushWithOverflow(
            IPatternDetails pattern, KeyCounter[] inputs,
            Map<Direction, PatternProviderTarget> faceToTarget, Direction defaultFace) {
        var overflow = new ArrayList<GenericStack>();
        pattern.pushInputsToExternalInventory(inputs, (what, amount) -> {
            var dir = AdvancedAECompat.getDirectionForKey(pattern, what);
            var face = dir != null ? dir : defaultFace;
            var target = faceToTarget.get(face);
            if (target != null) {
                var inserted = target.insert(what, amount, Actionable.MODULATE);
                if (inserted < amount) {
                    overflow.add(new GenericStack(what, amount - inserted));
                }
            } else {
                overflow.add(new GenericStack(what, amount));
            }
        });
        return overflow;
    }

    // ---- overflow flush ---------------------------------------------------------

    private void flushWirelessSends() {
        if (pendingOverflowByConn.isEmpty()) return;
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        long gameTick = sl.getGameTime();
        if (lastWirelessOverflowFlushTick == gameTick) return;
        lastWirelessOverflowFlushTick = gameTick;
        var server = sl.getServer();

        var iter = pendingOverflowByConn.object2ObjectEntrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            var conn = entry.getKey();
            var bucket = entry.getValue();

            var targetLevel = server.getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) continue;

            var state = getOrCreateState(conn);
            var adapter = state.resolveAdapter(targetLevel, conn.pos());
            if (adapter == null) continue;

            var be = targetLevel.getBlockEntity(conn.pos());
            var cached = (be != null)
                    ? getCachedTarget(targetLevel, conn.pos(), be, conn.boundFace(), wirelessSource)
                    : null;

            boolean cleared = bucket.compactMode
                    ? flushCompactBucket(bucket, conn, targetLevel, adapter, cached)
                    : flushFallbackBucket(bucket, conn, targetLevel, adapter, cached);

            if (cleared) {
                iter.remove();
                connectionsDirty = true;
                pushStructuresDirty = true;
            }
        }
        refreshGlobalBackpressure();
    }

    private boolean flushCompactBucket(ConnBucket bucket, WirelessConnection conn,
            ServerLevel targetLevel, MachineAdapter adapter, @Nullable PatternProviderTarget cached) {
        var pattern = patternById.get(Short.toUnsignedInt(bucket.patternId));
        if (pattern == null) return true;
        var inputs = pattern.getInputs();

        while (bucket.stuckIdx < inputs.length) {
            var input = inputs[bucket.stuckIdx];
            var possible = input.getPossibleInputs();
            if (possible.length != 1) {
                return true;
            }
            var single = new ArrayList<GenericStack>(1);
            single.add(new GenericStack(possible[0].what(), bucket.remaining));
            adapter.flushOverflow(targetLevel, conn.pos(), conn.boundFace(),
                    single, wirelessSource, cached);

            long left = single.isEmpty() ? 0 : single.get(0).amount();
            long inserted = bucket.remaining - left;
            if (inserted == 0) return false;
            if (left > 0) {
                bucket.remaining = left;
                return false;
            }

            bucket.stuckIdx++;
            if (bucket.stuckIdx < inputs.length) {
                bucket.remaining = inputAmount(inputs[bucket.stuckIdx]);
            }
        }
        return true;
    }

    private boolean flushFallbackBucket(ConnBucket bucket, WirelessConnection conn,
            ServerLevel targetLevel, MachineAdapter adapter, @Nullable PatternProviderTarget cached) {
        adapter.flushOverflow(targetLevel, conn.pos(), conn.boundFace(),
                bucket.fallbackList, wirelessSource, cached);
        return bucket.fallbackList.isEmpty();
    }

    private void refreshGlobalBackpressure() {
        int total = pendingOverflowByConn.size();
        if (wirelessGlobalBackpressure) {
            if (total <= GLOBAL_BUCKETS_REARM) {
                wirelessGlobalBackpressure = false;
            }
        } else if (total >= GLOBAL_BUCKETS_MAX) {
            wirelessGlobalBackpressure = true;
        }
    }

    // ---- auto-return (full-scan + per-machine exponential backoff) ---------------

    /**
     * Called every server tick (via BlockEntityTicker).
     * <p>
     * Iterates <b>all</b> connected machines each tick, but only actually polls
     * a machine when its individual backoff timer has elapsed.
     * <ul>
     *   <li>Extraction found → reset that machine's interval to {@link #BACKOFF_MIN}.</li>
     *   <li>Empty poll → double the interval (capped at {@link #BACKOFF_MAX}).</li>
     * </ul>
     * Only items whose {@link AEKey} matches a loaded pattern output are extracted.
     */
    public void tickAutoReturn() {
        if (!hasAnyTickWork()) return;

        tickWirelessInductionEnergy();

        var returnMode = overloadedHost.getReturnMode();
        if (returnMode != ReturnMode.AUTO) return;
        if (!gridNode.isActive()) return;

        var allowedOutputs = getOrBuildOutputFilter();
        if (allowedOutputs.isEmpty()) return;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;

        long gameTick = sl.getGameTime();

        if (overloadedHost.getProviderMode() == ProviderMode.NORMAL) {
            autoReturnNormal(sl, allowedOutputs, gameTick);
        } else {
            autoReturnWireless(sl, allowedOutputs, gameTick);
        }
    }

    /**
     * Quick check: is there any reason to run the server tick at all?
     * Returns false when NORMAL mode + autoReturn off + no wireless overflow,
     * allowing the tick to be completely skipped.
     */
    public boolean hasAnyTickWork() {
        if (!pendingOverflowByConn.isEmpty()) return true;
        if (overloadedHost.getProviderMode() == ProviderMode.WIRELESS
                && gridNode.isActive()
                && isInductionCardInstalled()
                && CACHED_APPFLUX_FE_KEY != null) return true;
        if (overloadedHost.isAutoReturn()) return true;
        if (!fullReturnInv.isEmpty()) return true;
        return false;
    }

    protected AllowedOutputFilter getOrBuildOutputFilter() {
        if (!outputFilterDirty && cachedOutputFilter != null) {
            return cachedOutputFilter;
        }

        cachedOutputFilter = collectPatternOutputFilter();
        outputFilterDirty = false;
        return cachedOutputFilter;
    }

    /**
     * Collect all output AEKeys from every pattern loaded in this provider.
     * Uses AE2-level outputs (GenericStack) to handle both items and fluids,
     * cross-referencing with overload details for ID_ONLY match modes.
     */
    private AllowedOutputFilter collectPatternOutputFilter() {
        var filter = new AllowedOutputFilter();
        for (var pattern : getAvailablePatterns()) {
            if (pattern instanceof OverloadedProviderOnlyPatternDetails overloadDetails) {
                var ae2Outputs = pattern.getOutputs();
                var overloadOutputs = overloadDetails.overloadPatternDetailsView().outputs();
                int count = Math.min(ae2Outputs.size(), overloadOutputs.size());
                for (int i = 0; i < count; i++) {
                    var aeKey = ae2Outputs.get(i).what();
                    if (overloadOutputs.get(i).matchMode() == MatchMode.ID_ONLY) {
                        filter.allowIdOnly(aeKey);
                    } else {
                        filter.allowStrict(aeKey);
                    }
                }
                continue;
            }

            for (var output : pattern.getOutputs()) {
                filter.allowStrict(output.what());
            }
        }
        return filter;
    }

    private void autoReturnNormal(ServerLevel level, AllowedOutputFilter allowedOutputs, long gameTick) {
        var providerPos = overloadedHost.getBlockPos();
        for (var dir : overloadedHost.getTargets()) {
            var targetPos = providerPos.relative(dir);
            var key = machineKey(level, targetPos, dir.getOpposite());

            if (gameTick < machineNextPoll.getOrDefault(key, 0L)) continue;

            var adapter = MachineAdapterRegistry.find(level, targetPos);
            if (adapter == null) continue;

            var face = dir.getOpposite();
            boolean found = adapter.extractOutputs(
                    level, targetPos, face, allowedOutputs, wirelessSource, returnInvSink);
            updateBackoff(key, gameTick, found);
        }
    }

    private void autoReturnWireless(ServerLevel sl, AllowedOutputFilter allowedOutputs, long gameTick) {
        var valid = getOrRefreshValidConnections(sl, gameTick);
        int total = valid.size();
        if (total == 0) return;

        long elapsed = lastReturnRobinTick >= 0 ? gameTick - lastReturnRobinTick : 1;
        lastReturnRobinTick = gameTick;

        int perTick = Math.max(1, (total + RETURN_SPREAD_TICKS - 1) / RETURN_SPREAD_TICKS);
        int toProcess = (int) Math.min((long) perTick * elapsed, total);

        for (int i = 0; i < toProcess; i++) {
            int idx = returnRobinIndex % total;
            returnRobinIndex = (returnRobinIndex + 1) % total;

            var conn = valid.get(idx);
            var targetLevel = resolveTargetLevel(sl, conn);
            if (targetLevel == null) continue;

            var state = getOrCreateState(conn);
            var adapter = state.resolveAdapter(targetLevel, conn.pos());
            if (adapter == null) continue;

            adapter.extractOutputs(
                    targetLevel, conn.pos(), conn.boundFace(), allowedOutputs, wirelessSource,
                    returnInvSink);
        }
    }

    /** Unified machine key without transient String allocation. */
    private static MachineId machineKey(ServerLevel level, BlockPos pos, Direction face) {
        return machineKey(level.dimension(), pos, face);
    }

    private static MachineId machineKey(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        return new MachineId(dimension, pos.asLong(), face);
    }

    /**
     * Reset backoff for all adjacent machine targets (NORMAL mode).
     * Called after a successful pushPattern so auto-return starts
     * checking promptly.
     */
    private void resetBackoffAllTargets() {
        var lvl = overloadedHost.getLevel();
        if (!(lvl instanceof ServerLevel sl)) return;
        long gameTick = sl.getGameTime();
        var providerPos = overloadedHost.getBlockPos();
        for (var dir : overloadedHost.getTargets()) {
            var key = machineKey(sl, providerPos.relative(dir), dir.getOpposite());
            machineBackoff.put(key, BACKOFF_MIN);
            machineNextPoll.put(key, gameTick + BACKOFF_MIN);
        }
    }

    /**
     * After polling a machine, update its backoff state.
     *
     * @param foundItems true if at least one output was extracted
     */
    private void updateBackoff(MachineId key, long gameTick, boolean foundItems) {
        int interval;
        if (foundItems) {
            interval = BACKOFF_MIN;
        } else {
            int current = machineBackoff.getOrDefault(key, BACKOFF_MIN);
            interval = Math.min(current * 2, BACKOFF_MAX);
        }
        machineBackoff.put(key, interval);
        machineNextPoll.put(key, gameTick + interval);
    }

    /** Stores auto-returned outputs into the wireless return inventory (WIRELESS mode). */
    private final MachineAdapter.OutputSink returnInvSink = new MachineAdapter.OutputSink() {
        @Override
        public long maxAccept(AEKey what, long available) {
            long affordable = PowerCostUtil.maxAffordable(gridNode.getGrid(), what, available);
            if (affordable <= 0) return 0;
            return fullReturnInv.insert(0, what, affordable, Actionable.SIMULATE);
        }

        @Override
        public long accept(AEKey what, long amount) {
            long inserted = fullReturnInv.insert(0, what, amount, Actionable.MODULATE);
            if (inserted > 0) {
                PowerCostUtil.consume(gridNode.getGrid(), what, inserted);
            }
            return inserted;
        }

        @Override
        public void acceptOverflow(AEKey what, long amount) {
            forceInsertToNetwork(what, amount);
        }
    };

    /** Power-free last-resort insert; losing job items is worse than free power. */
    private void forceInsertToNetwork(AEKey what, long amount) {
        var grid = gridNode.getGrid();
        long inserted = grid == null ? 0
                : grid.getStorageService().getInventory()
                        .insert(what, amount, Actionable.MODULATE, wirelessSource);
        if (inserted < amount) {
            logVoidedReturn(what, amount - inserted);
        }
        if (inserted > 0) {
            handleOverloadUnlockOnReturnedStack(new GenericStack(what, inserted));
        }
    }

    private static void logVoidedReturn(AEKey what, long amount) {
        org.slf4j.LoggerFactory.getLogger("ae2lt").warn(
                "Auto-return voided {} x{}: return inventory, machine and network all rejected it",
                what, amount);
    }


    private void autoReturnBeforePush(ServerLevel sl, WirelessConnection conn) {
        if (overloadedHost.getReturnMode() != ReturnMode.AUTO) return;
        long gameTick = sl.getGameTime();
        if (gameTick == lastSingleReturnTick) return;
        lastSingleReturnTick = gameTick;

        var allowedOutputs = getOrBuildOutputFilter();
        if (allowedOutputs.isEmpty()) return;

        var targetLevel = resolveTargetLevel(sl, conn);
        if (targetLevel == null) return;

        var state = getOrCreateState(conn);
        var adapter = state.resolveAdapter(targetLevel, conn.pos());
        if (adapter == null) return;

        adapter.extractOutputs(
                targetLevel, conn.pos(), conn.boundFace(), allowedOutputs, wirelessSource,
                returnInvSink);
    }

    public boolean handleOverloadUnlockOnReturnedStack(GenericStack returnedStack) {
        if (getCraftingLockedReason() != LockCraftingMode.LOCK_UNTIL_RESULT) {
            clearPendingUnlockRule();
            return false;
        }

        var unlockStack = getUnlockStack();
        if (unlockStack == null) {
            resetCraftingLock();
            return true;
        }

        var result = ReturnedCraftingUnlock.resolveMatchedAmount(
                returnedStackMatchesUnlock(unlockStack, returnedStack),
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

    private boolean returnedStackMatchesUnlock(GenericStack unlockStack, GenericStack returnedStack) {
        if (pendingUnlockMatchMode == MatchMode.ID_ONLY) {
            Item expectedItem = null;
            if (pendingUnlockTemplate != null && !pendingUnlockTemplate.isEmpty()) {
                expectedItem = pendingUnlockTemplate.getItem();
            } else if (unlockStack.what() instanceof AEItemKey unlockItemKey) {
                expectedItem = unlockItemKey.getItem();
            }
            return expectedItem != null
                    && returnedStack.what() instanceof AEItemKey returnedItemKey
                    && returnedItemKey.getItem() == expectedItem;
        }

        return unlockStack.what().equals(returnedStack.what());
    }

    protected void syncPendingUnlockRule(IPatternDetails pattern) {
        clearPendingUnlockRule();
        if (getCraftingLockedReason() != LockCraftingMode.LOCK_UNTIL_RESULT) {
            return;
        }
        if (!(pattern instanceof OverloadedProviderOnlyPatternDetails overloadPattern)) {
            return;
        }

        int unlockOutputIndex = resolveUnlockOutputIndex(pattern, overloadPattern.overloadPatternDetailsView());
        var overloadOutputs = overloadPattern.overloadPatternDetailsView().outputs();
        if (unlockOutputIndex < 0 || unlockOutputIndex >= overloadOutputs.size()) {
            return;
        }

        var unlockOutput = overloadOutputs.get(unlockOutputIndex);
        pendingUnlockMatchMode = unlockOutput.matchMode();
        pendingUnlockTemplate = unlockOutput.template();
    }

    private static int resolveUnlockOutputIndex(IPatternDetails pattern, OverloadPatternDetails overloadDetails) {
        var actualOutputs = pattern.getOutputs();
        var overloadOutputs = overloadDetails.outputs();
        int count = Math.min(actualOutputs.size(), overloadOutputs.size());
        if (count <= 0) {
            return -1;
        }

        var primaryOutput = pattern.getPrimaryOutput();
        for (int i = 0; i < count; i++) {
            var candidate = actualOutputs.get(i);
            if (candidate.what().equals(primaryOutput.what()) && candidate.amount() == primaryOutput.amount()) {
                return i;
            }
        }

        for (int i = 0; i < count; i++) {
            if (overloadOutputs.get(i).primaryOutput()) {
                return i;
            }
        }

        return 0;
    }

    private void clearPendingUnlockRule() {
        pendingUnlockMatchMode = null;
        pendingUnlockTemplate = null;
    }

    // ---- eject mode lifecycle ----------------------------------------------------

    /**
     * Rebuild eject-mode registrations based on the current return mode
     * and wireless connections. Should be called whenever return mode,
     * connections, or patterns change.
     */
    public void refreshEjectRegistrations() {
        var removed = EjectModeRegistry.unregisterAll(overloadedHost, true);
        invalidateCapabilitiesAt(removed);

        if (overloadedHost.getReturnMode() != ReturnMode.EJECT) return;
        if (overloadedHost.getProviderMode() != ProviderMode.WIRELESS) return;

        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;

        for (var conn : overloadedHost.getConnections()) {
            if (!conn.dimension().equals(sl.dimension())) continue;
            var targetLevel = sl.getServer().getLevel(conn.dimension());
            if (targetLevel == null) continue;

            var adjacentPos = conn.pos().relative(conn.boundFace());
            var queryFace = conn.boundFace().getOpposite();
            var ghostBE = new GhostOutputBlockEntity(adjacentPos);
            ghostBE.setLevel(targetLevel);

            var entry = new EjectModeRegistry.EjectEntry(
                    new java.lang.ref.WeakReference<>(overloadedHost),
                    ghostBE,
                    sl.dimension(),
                    overloadedHost.getBlockPos()
            );

            EjectModeRegistry.register(targetLevel.dimension(), adjacentPos.asLong(), queryFace, entry);
            invalidateCapabilitiesAt(targetLevel, adjacentPos);
        }
    }

    private void invalidateCapabilitiesAt(List<EjectModeRegistry.DimPos> positions) {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        var server = sl.getServer();
        for (var dp : positions) {
            var targetLevel = server.getLevel(dp.dimension());
            if (targetLevel != null) {
                targetLevel.invalidateCapabilities(dp.pos());
            }
        }
    }

    private static void invalidateCapabilitiesAt(@Nullable ServerLevel level, BlockPos pos) {
        if (level != null) {
            level.invalidateCapabilities(pos);
        }
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

        // Validate + collect in a single pass
        overloadedHost.clearInvalidConnections();
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
            valid.add(conn);
        }
        validConnectionsCache = List.copyOf(valid);
        pruneMachineBackoffState(validConnectionsCache);
        validConnectionsCacheTick = gameTick;
        connectionsDirty = false;
        rebuildValidTargets();
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

    private void pruneMachineBackoffState(List<WirelessConnection> validConnections) {
        var activeMachineIds = new java.util.HashSet<MachineId>();
        for (var conn : validConnections) {
            activeMachineIds.add(new MachineId(conn.dimension(), conn.pos().asLong(), conn.boundFace()));
        }
        machineNextPoll.keySet().retainAll(activeMachineIds);
        machineBackoff.keySet().retainAll(activeMachineIds);
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
        var bucket = pendingOverflowByConn.get(conn);
        if (bucket == null) {
            return true;
        }
        if (!drainBucketToNetwork(bucket)) {
            return false;
        }
        pendingOverflowByConn.remove(conn);
        connectionsDirty = true;
        pushStructuresDirty = true;
        refreshGlobalBackpressure();
        alertGridTick();
        return true;
    }

    private boolean drainBucketToNetwork(ConnBucket bucket) {
        if (bucket.compactMode) {
            return drainCompactBucketToNetwork(bucket);
        }

        for (int i = 0; i < bucket.fallbackList.size(); ) {
            var stack = bucket.fallbackList.get(i);
            long remaining = insertStackToNetwork(stack.what(), stack.amount());
            if (remaining <= 0) {
                bucket.fallbackList.remove(i);
                continue;
            }
            bucket.fallbackList.set(i, new GenericStack(stack.what(), remaining));
            return false;
        }
        return true;
    }

    private boolean drainCompactBucketToNetwork(ConnBucket bucket) {
        var pattern = patternById.get(Short.toUnsignedInt(bucket.patternId));
        if (pattern == null) {
            return true;
        }
        var inputs = pattern.getInputs();

        while (bucket.stuckIdx < inputs.length) {
            var possible = inputs[bucket.stuckIdx].getPossibleInputs();
            if (possible.length != 1) {
                return true;
            }

            long remaining = insertStackToNetwork(possible[0].what(), bucket.remaining);
            if (remaining > 0) {
                bucket.remaining = remaining;
                return false;
            }

            bucket.stuckIdx++;
            if (bucket.stuckIdx < inputs.length) {
                bucket.remaining = inputAmount(inputs[bucket.stuckIdx]);
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
        if (!pendingOverflowByConn.isEmpty()) {
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
        if (!overloadedHost.isAutoReturn() || !gridNode.isActive()) {
            return false;
        }
        if (getOrBuildOutputFilter().isEmpty()) {
            return false;
        }
        if (overloadedHost.getProviderMode() == ProviderMode.WIRELESS) {
            return true;
        }
        return getNextAutoReturnPollTick() <= gameTick;
    }

    private long getNextAutoReturnPollTick() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return Long.MAX_VALUE;
        }

        long nextPollTick = Long.MAX_VALUE;
        if (overloadedHost.getProviderMode() == ProviderMode.NORMAL) {
            var providerPos = overloadedHost.getBlockPos();
            var targets = overloadedHost.getTargets();
            if (targets.isEmpty()) {
                return Long.MAX_VALUE;
            }

            for (var dir : targets) {
                var key = machineKey(sl.dimension(), providerPos.relative(dir), dir.getOpposite());
                nextPollTick = Math.min(nextPollTick, machineNextPoll.getOrDefault(key, 0L));
            }
            return nextPollTick;
        }

        var connections = overloadedHost.getConnections();
        if (connections.isEmpty()) {
            return Long.MAX_VALUE;
        }

        for (var conn : connections) {
            var state = connectionStates.get(conn);
            if (state != null) {
                nextPollTick = Math.min(nextPollTick, state.nextPollTick);
            } else {
                return 0L;
            }
        }
        return nextPollTick;
    }

    protected void alertGridTick() {
        gridNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private void invalidateValidConnectionsCache() {
        connectionsDirty = true;
        validConnectionsCache = List.of();
        validConnectionsCacheTick = -1;
        validTargetsCache = List.of();
        validTargetsVersion++;
        pushWheelValidRef = List.of();
        pushStructuresDirty = true;
        for (var slot : pushWheel) slot.clear();
        clearReadyQueuesAndQueuedFlags();
        lastPushWheelTick = -1;
        targetCache.clear();
        connectionStates.clear();
        patternPenalties.clear();
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


    // ---- SavedData persistence helpers ------------------------------------------

    private boolean loadFromSavedData() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").warn(
                    "[SavedData] loadFromSavedData skipped: level={} pos={}",
                    level, overloadedHost.getBlockPos());
            return false;
        }
        var savedData = PatternStorageSavedData.get(sl);
        var stored = savedData.get(overloadedHost.getBlockPos().asLong());
        if (stored == null) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").info(
                    "[SavedData] No stored data for pos={}", overloadedHost.getBlockPos());
            return false;
        }
        org.slf4j.LoggerFactory.getLogger("ae2lt").info(
                "[SavedData] Loaded {} patterns for pos={}", stored.length, overloadedHost.getBlockPos());
        var inv = ((PatternProviderLogicAccessor) this).getPatternInventory();
        int limit = Math.min(stored.length, inv.size());
        for (int i = 0; i < limit; i++) {
            inv.setItemDirect(i, stored[i] != null ? stored[i] : ItemStack.EMPTY);
        }
        savedData.remove(overloadedHost.getBlockPos().asLong());
        return true;
    }

    public void removeSavedData() {
        var level = overloadedHost.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        PatternStorageSavedData.get(sl).remove(overloadedHost.getBlockPos().asLong());
    }

    /**
     * Called from {@code OverloadedPatternProviderBlockEntity.onReady()} when
     * Level is guaranteed to be available. Completes deferred SavedData loading
     * that was skipped during readFromNBT (where Level is still null).
     */
    public void onBlockEntityReady() {
        if (needsSavedDataLoad) {
            needsSavedDataLoad = false;
            if (loadFromSavedData()) {
                updatePatterns();
                saveChanges();
            }
        }
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
            return new TickingRequest(GRID_TICK_MIN, GRID_TICK_MAX, !hasCombinedGridTickWork());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!gridNode.isActive()) {
                return TickRateModulation.SLEEP;
            }

            var accessor = (PatternProviderLogicAccessor) OverloadedPatternProviderLogic.this;
            boolean parentDidWork = accessor.invokeDoWork();
            flushWirelessSends();
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
        return super.isBusy();
    }

    // ---- drops & clearing -------------------------------------------------------

    @Override
    public void addDrops(List<ItemStack> drops) {
        super.addDrops(drops);
        for (var bucket : pendingOverflowByConn.values()) {
            addBucketDrops(bucket, drops);
        }
        if (totalCapacity > 36) {
            removeSavedData();
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        if (totalCapacity > 36) {
            removeSavedData();
        }
        clearWirelessOverflowState();
        connectionStates.clear();
        machineNextPoll.clear();
        machineBackoff.clear();
        cachedOutputFilter = null;
        outputFilterDirty = true;
        invalidateValidConnectionsCache();
        inductionCardCacheDirty = true;
        lastEnergyTickGameTime = -1;
        returnRobinIndex = 0;
        lastReturnRobinTick = -1;
        lastSingleReturnTick = -1;
        invalidateCapabilitiesAt(EjectModeRegistry.unregisterAll(overloadedHost, true));
    }

    // ---- NBT persistence --------------------------------------------------------

    private static final String TAG_W_SEND_LIST = "WirelessSendList";
    private static final String TAG_W_SEND_CONN = "WirelessSendConn";
    private static final String TAG_WIRELESS_OVERFLOW = "ae2lt:wireless_overflow";
    private static final String TAG_OVERFLOW_PATTERNS = "patterns";
    private static final String TAG_OVERFLOW_PATTERN_ID = "id";
    private static final String TAG_OVERFLOW_PATTERN = "pattern";
    private static final String TAG_OVERFLOW_BUCKETS = "buckets";
    private static final String TAG_OVERFLOW_CONN = "conn";
    private static final String TAG_OVERFLOW_PID = "pid";
    private static final String TAG_OVERFLOW_IDX = "idx";
    private static final String TAG_OVERFLOW_REMAINING = "remaining";
    private static final String TAG_OVERFLOW_FALLBACK = "fallback";
    private static final String TAG_OVERFLOW_COMPACT = "compact";
    private static final String TAG_W_ROUND_ROBIN = "WirelessRoundRobin";
    private static final String TAG_UNLOCK_MATCH_MODE = "Ae2ltUnlockMatchMode";
    private static final String TAG_UNLOCK_TEMPLATE = "Ae2ltUnlockTemplate";

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        tag.putInt(TAG_W_ROUND_ROBIN, wirelessRoundRobin);
        if (pendingUnlockMatchMode != null) {
            tag.putString(TAG_UNLOCK_MATCH_MODE, pendingUnlockMatchMode.name());
        }
        if (pendingUnlockTemplate != null && !pendingUnlockTemplate.isEmpty()) {
            tag.put(TAG_UNLOCK_TEMPLATE, pendingUnlockTemplate.saveOptional(registries));
        }
        writeWirelessOverflowToNBT(tag, registries);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        needsSavedDataLoad = totalCapacity > 36 && !hasPatternInventoryContents();
        wirelessRoundRobin = tag.getInt(TAG_W_ROUND_ROBIN);
        pendingUnlockMatchMode = null;
        pendingUnlockTemplate = null;
        if (tag.contains(TAG_UNLOCK_MATCH_MODE, Tag.TAG_STRING)) {
            try {
                pendingUnlockMatchMode = MatchMode.valueOf(tag.getString(TAG_UNLOCK_MATCH_MODE));
            } catch (IllegalArgumentException ignored) {
                pendingUnlockMatchMode = null;
            }
        }
        if (tag.contains(TAG_UNLOCK_TEMPLATE, Tag.TAG_COMPOUND)) {
            pendingUnlockTemplate = ItemStack.parseOptional(registries, tag.getCompound(TAG_UNLOCK_TEMPLATE));
            if (pendingUnlockTemplate.isEmpty()) {
                pendingUnlockTemplate = null;
            }
        }
        readWirelessOverflowFromNBT(tag, registries);
        cachedOutputFilter = null;
        outputFilterDirty = true;
        invalidateValidConnectionsCache();
        inductionCardCacheDirty = true;
        lastEnergyTickGameTime = -1;
        returnRobinIndex = 0;
        lastReturnRobinTick = -1;
        lastSingleReturnTick = -1;
        refreshEjectRegistrations();
    }

    private boolean hasPatternInventoryContents() {
        var inv = ((PatternProviderLogicAccessor) this).getPatternInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void addBucketDrops(ConnBucket bucket, List<ItemStack> drops) {
        if (bucket.compactMode) {
            var pattern = patternById.get(Short.toUnsignedInt(bucket.patternId));
            if (pattern == null) return;
            var inputs = pattern.getInputs();
            for (int i = bucket.stuckIdx; i < inputs.length; i++) {
                var possible = inputs[i].getPossibleInputs();
                if (possible.length != 1) continue;
                long amount = i == bucket.stuckIdx ? bucket.remaining : inputAmount(inputs[i]);
                if (amount > 0) {
                    possible[0].what().addDrops(amount, drops,
                            overloadedHost.getLevel(), overloadedHost.getBlockPos());
                }
            }
            return;
        }

        for (var stack : bucket.fallbackList) {
            stack.what().addDrops(stack.amount(), drops,
                    overloadedHost.getLevel(), overloadedHost.getBlockPos());
        }
    }

    private void writeWirelessOverflowToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        if (pendingOverflowByConn.isEmpty()) return;

        var overflowTag = new CompoundTag();
        var patternList = new ListTag();
        var remappedIds = new HashMap<Integer, Short>();
        short nextWriteId = 0;

        for (var bucket : pendingOverflowByConn.values()) {
            if (!bucket.compactMode) continue;
            int runtimeId = Short.toUnsignedInt(bucket.patternId);
            if (remappedIds.containsKey(runtimeId)) continue;
            var pattern = patternById.get(runtimeId);
            if (pattern == null) continue;

            short writeId = nextWriteId++;
            remappedIds.put(runtimeId, writeId);
            var patternTag = new CompoundTag();
            patternTag.putShort(TAG_OVERFLOW_PATTERN_ID, writeId);
            patternTag.put(TAG_OVERFLOW_PATTERN,
                    pattern.getDefinition().toStack().saveOptional(registries));
            patternList.add(patternTag);
        }
        overflowTag.put(TAG_OVERFLOW_PATTERNS, patternList);

        var bucketList = new ListTag();
        for (var entry : pendingOverflowByConn.object2ObjectEntrySet()) {
            var bucket = entry.getValue();
            var bucketTag = new CompoundTag();
            bucketTag.put(TAG_OVERFLOW_CONN, entry.getKey().toTag());
            bucketTag.putBoolean(TAG_OVERFLOW_COMPACT, bucket.compactMode);
            if (bucket.compactMode) {
                var remapped = remappedIds.get(Short.toUnsignedInt(bucket.patternId));
                if (remapped == null) {
                    continue;
                }
                bucketTag.putShort(TAG_OVERFLOW_PID, remapped);
                bucketTag.putShort(TAG_OVERFLOW_IDX, bucket.stuckIdx);
                bucketTag.putLong(TAG_OVERFLOW_REMAINING, bucket.remaining);
            } else {
                var fallback = new ListTag();
                for (var stack : bucket.fallbackList) {
                    fallback.add(GenericStack.writeTag(registries, stack));
                }
                bucketTag.put(TAG_OVERFLOW_FALLBACK, fallback);
            }
            bucketList.add(bucketTag);
        }
        overflowTag.put(TAG_OVERFLOW_BUCKETS, bucketList);
        tag.put(TAG_WIRELESS_OVERFLOW, overflowTag);
    }

    private void readWirelessOverflowFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        clearWirelessOverflowState();

        if (tag.contains(TAG_WIRELESS_OVERFLOW, Tag.TAG_COMPOUND)) {
            var overflowTag = tag.getCompound(TAG_WIRELESS_OVERFLOW);
            var patterns = overflowTag.getList(TAG_OVERFLOW_PATTERNS, Tag.TAG_COMPOUND);
            for (int i = 0; i < patterns.size(); i++) {
                var patternTag = patterns.getCompound(i);
                int id = Short.toUnsignedInt(patternTag.getShort(TAG_OVERFLOW_PATTERN_ID));
                var stack = ItemStack.parseOptional(registries,
                        patternTag.getCompound(TAG_OVERFLOW_PATTERN));
                if (!stack.isEmpty()) {
                    pendingOverflowPatternDefinitions.put(id, stack);
                }
            }

            var buckets = overflowTag.getList(TAG_OVERFLOW_BUCKETS, Tag.TAG_COMPOUND);
            for (int i = 0; i < buckets.size(); i++) {
                var bucketTag = buckets.getCompound(i);
                if (!bucketTag.contains(TAG_OVERFLOW_CONN, Tag.TAG_COMPOUND)) continue;
                var conn = WirelessConnection.fromTag(bucketTag.getCompound(TAG_OVERFLOW_CONN));
                boolean compact = bucketTag.getBoolean(TAG_OVERFLOW_COMPACT);
                if (compact) {
                    pendingOverflowBuckets.add(new PendingBucketLoad(
                            conn,
                            bucketTag.getShort(TAG_OVERFLOW_PID),
                            bucketTag.getShort(TAG_OVERFLOW_IDX),
                            bucketTag.getLong(TAG_OVERFLOW_REMAINING),
                            List.of(),
                            true));
                } else {
                    var fallback = readGenericStackList(registries,
                            bucketTag.getList(TAG_OVERFLOW_FALLBACK, Tag.TAG_COMPOUND));
                    if (!fallback.isEmpty()) {
                        pendingOverflowBuckets.add(new PendingBucketLoad(
                                conn, (short) 0, (short) 0, 0, fallback, false));
                    }
                }
            }
        } else {
            readLegacyWirelessOverflowFromNBT(tag, registries);
        }

        finishPendingWirelessOverflowLoad();
    }

    private void readLegacyWirelessOverflowFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_W_SEND_LIST, Tag.TAG_LIST)
                || !tag.contains(TAG_W_SEND_CONN, Tag.TAG_COMPOUND)) {
            return;
        }
        var fallback = readGenericStackList(registries, tag.getList(TAG_W_SEND_LIST, Tag.TAG_COMPOUND));
        if (!fallback.isEmpty()) {
            pendingOverflowBuckets.add(new PendingBucketLoad(
                    WirelessConnection.fromTag(tag.getCompound(TAG_W_SEND_CONN)),
                    (short) 0, (short) 0, 0, fallback, false));
        }
    }

    private static List<GenericStack> readGenericStackList(HolderLookup.Provider registries, ListTag list) {
        var stacks = new ArrayList<GenericStack>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private void finishPendingWirelessOverflowLoad() {
        if (pendingOverflowBuckets.isEmpty()) return;

        var level = overloadedHost.getLevel();
        if (!pendingOverflowPatternDefinitions.isEmpty() && level == null) {
            return;
        }

        for (var entry : pendingOverflowPatternDefinitions.entrySet()) {
            var details = PatternDetailsHelper.decodePattern(entry.getValue(), level);
            if (details != null) {
                patternById.put(entry.getKey(), details);
                patternTable.put(details, entry.getKey());
                nextPatternId = Math.max(nextPatternId, (entry.getKey() + 1) & 0xFFFF);
            }
        }

        for (var pending : pendingOverflowBuckets) {
            if (pending.compactMode()) {
                int patternId = Short.toUnsignedInt(pending.patternId());
                var pattern = patternById.get(patternId);
                if (pattern == null || pending.remaining() <= 0) {
                    continue;
                }
                var inputs = pattern.getInputs();
                if (pending.stuckIdx() < 0 || pending.stuckIdx() >= inputs.length) {
                    continue;
                }
                pendingOverflowByConn.put(pending.conn(),
                        ConnBucket.compact(pending.patternId(), pending.stuckIdx(), pending.remaining()));
            } else if (!pending.fallbackList().isEmpty()) {
                pendingOverflowByConn.put(pending.conn(),
                        ConnBucket.fallback(pending.patternId(), pending.fallbackList()));
            }
        }

        pendingOverflowPatternDefinitions.clear();
        pendingOverflowBuckets.clear();
        connectionsDirty = true;
        pushStructuresDirty = true;
        refreshGlobalBackpressure();
    }

    private void clearWirelessOverflowState() {
        pendingOverflowByConn.clear();
        pendingOverflowPatternDefinitions.clear();
        pendingOverflowBuckets.clear();
        patternTable.clear();
        patternById.clear();
        nextPatternId = 0;
        wirelessGlobalBackpressure = false;
    }
}
