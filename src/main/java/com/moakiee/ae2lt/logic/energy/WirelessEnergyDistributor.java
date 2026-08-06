package com.moakiee.ae2lt.logic.energy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;

/**
 * Reusable, host-agnostic NORMAL (no-disk) wireless FE distribution engine.
 *
 * <p>Originally extracted from {@code OverloadedPowerSupplyLogic} so that
 * the Overloaded ME Interface and Overloaded Pattern Provider can share the
 * same hot-path implementation:
 * <ul>
 * <li>32-slot power-of-two adaptive scheduling wheel ({@code 1..20} ticks
 *     per-target delay, halved on success / incremented on starvation)</li>
 * <li>Per-target {@link BlockEnergyTargetCache} that registers a NeoForge
 *     {@link ICapabilityInvalidationListener} on the target dimension, so
 *     cap-changes invalidate the cache instantly without per-tick polling</li>
 * <li>Single-shot {@link BufferedMEStorage#beginMemoryBatch} per tick:
 *     pre-pulls the entire NORMAL-batch demand from the ME network in one
 *     extract, then per-target {@code sendToTargetKnownDemand} consumes
 *     from the in-memory buffer; leftover returned via {@code endBatch}</li>
 * <li>O(1) cache invalidation via {@link Host#getValidTargetsVersion()}
 *     monotonic stamp — no per-tick {@code List.equals} on 64 records</li>
 * </ul>
 *
 * <p>The Overloaded Power Supply additionally drives an OVERLOAD path that
 * shares this distributor's target cache pool via
 * {@link #resolveTargetAtIndex(int, ServerLevel)} and friends, so
 * cap-invalidation listeners are registered once per target across both
 * modes.
 *
 * <p>Connection validation (BE-presence checks, periodic sweeps, removing
 * connections whose target chunk has unloaded) is intentionally
 * <b>NOT</b> handled here — each host BE owns its own validation rules and
 * passes the already-validated list via {@link Host#getValidTargets()}.
 */
public final class WirelessEnergyDistributor {

    public enum Status {
        IDLE,
        APPFLUX_UNAVAILABLE,
        NO_GRID,
        NO_CONNECTIONS,
        NO_VALID_TARGETS,
        NO_NETWORK_FE,
        TARGET_UNSUPPORTED,
        TARGET_BLOCKED,
        ACTIVE
    }

    public interface Host {
        IManagedGridNode getMainNode();

        IActionSource actionSource();

        boolean isHostRemoved();

        /**
         * Targets already filtered by the host (BE present, chunk loaded, dim
         * accessible). The distributor does <b>not</b> re-validate; it only
         * detects list changes via {@link #getValidTargetsVersion()}.
         */
        List<WirelessEnergyAPI.Target> getValidTargets();

        /**
         * Monotonic version stamp; must increment whenever the result of
         * {@link #getValidTargets()} differs from the previous tick's. The
         * distributor uses this as an O(1) cache-invalidation key — supplying
         * a stale version will cause stale caches to persist.
         */
        int getValidTargetsVersion();

        /**
         * Optional Flux-Cell-aware backing storage (see
         * {@link BufferedMEStorage}). NORMAL distribution treats the cell as
         * the buffer when present, mirroring AE2's ME Chest pattern. Hosts
         * with no cell concept return {@code null} (default).
         */
        @Nullable
        default Supplier<MEStorage> getCellStorageSupplier() {
            return null;
        }

        /**
         * Optional callback fired by {@link BufferedMEStorage#endTick} after
         * the cell's in-memory state has been written back to its ItemStack
         * data component. Hosts without cells return {@code null}.
         */
        @Nullable
        default Runnable getCellPersistCallback() {
            return null;
        }
    }

    // ---- adaptive scheduling wheel ---------------------------------------

    private static final int ENERGY_DELAY_MEAN = 5;
    private static final int ENERGY_DELAY_MAX = 20;
    private static final int ENERGY_DELAY_MIN = 1;
    /**
     * Wheel size rounded up to the next power of two so {@code (pointer +
     * delay) & ENERGY_WHEEL_SLOTS_MASK} replaces the {@code % 20} division
     * that showed up under tickNormal in profiling.
     */
    private static final int ENERGY_WHEEL_SLOTS = 32;
    private static final int ENERGY_WHEEL_SLOTS_MASK = ENERGY_WHEEL_SLOTS - 1;
    private static final int ENERGY_WHEEL_INITIAL_CAPACITY = 32;
    private static final int INITIAL_CACHE_CAPACITY = 64;

    /**
     * Inline-int wheel slot. Stores raw int target indices (into
     * {@link #cachedValidTargets} / {@link #connectionTargetCaches}),
     * eliminating per-tick record accessor indirection and per-rebuild
     * {@code ScheduleEntry} allocations.
     */
    private static final class IntWheelSlot {
        int[] data = new int[ENERGY_WHEEL_INITIAL_CAPACITY];
        int size;

        void add(int i) {
            if (size >= data.length) {
                data = Arrays.copyOf(data, data.length << 1);
            }
            data[size++] = i;
        }

        void clear() {
            size = 0;
        }
    }

    private final Host host;
    private final Map<WirelessEnergyAPI.Target, BlockEnergyTargetCache> targetCachePool = new HashMap<>();

    private final IntWheelSlot[] normalEnergyWheel = new IntWheelSlot[ENERGY_WHEEL_SLOTS];
    {
        for (int i = 0; i < ENERGY_WHEEL_SLOTS; i++) {
            normalEnergyWheel[i] = new IntWheelSlot();
        }
    }
    private IntWheelSlot spareNormalWheelSlot = new IntWheelSlot();

    private final List<WirelessEnergyAPI.Target> cachedValidTargets = new ArrayList<>();
    private final Set<WirelessEnergyAPI.Target> cachedValidTargetSet = new HashSet<>();
    private int hostVersion = Integer.MIN_VALUE;
    /** Distributor-internal version: bumps when target list actually changes. */
    private int validTargetsVersion;
    private int normalWheelTargetsVersion = -1;
    private boolean normalWheelDirty = true;
    private int normalWheelPointer;

    private BlockEnergyTargetCache[] connectionTargetCaches = new BlockEnergyTargetCache[0];
    private long[] normalBatchDemand = new long[0];
    /**
     * Resolved energy target reference per due slot, cached during the
     * simulate pass so the modulate pass does not have to call
     * {@link BlockEnergyTargetCache#resolve} a second time.
     */
    private TargetAccess[] normalBatchEnergyTargets = new TargetAccess[0];

    @Nullable
    private IStorageService delegateStorageService;
    @Nullable
    private BufferedMEStorage bufferedStorage;
    @Nullable
    private BufferedStorageService storageProxy;

    private Status lastStatus = Status.IDLE;
    private long lastTransferAmount;

    public WirelessEnergyDistributor(Host host) {
        this.host = host;
    }

    // ---- public lifecycle ------------------------------------------------

    /**
     * Run a single NORMAL-mode tick. Pulls valid targets from {@link Host},
     * advances the scheduling wheel, batches the ME-network extract, and
     * distributes FE per due target. Returns {@code true} iff at least one
     * FE was actually delivered to a target.
     */
    public boolean tickNormal(ServerLevel serverLevel) {
        if (!AppFluxBridge.canUseEnergyHandler()) {
            enterIdleState(Status.APPFLUX_UNAVAILABLE, true);
            return false;
        }

        var mainNode = host.getMainNode();
        if (!mainNode.isActive() || mainNode.getGrid() == null) {
            setStatus(Status.NO_GRID);
            return false;
        }

        BufferedStorageService proxy = ensureStorageProxy();
        BufferedMEStorage buffer = bufferedStorage;
        if (proxy == null || buffer == null) {
            setStatus(Status.NO_GRID);
            return false;
        }

        buffer.advanceHistory();
        buffer.setCostMultiplier(1);

        refreshTargets();
        if (cachedValidTargets.isEmpty()) {
            enterIdleState(host.getValidTargets().isEmpty()
                    ? Status.NO_CONNECTIONS
                    : Status.NO_VALID_TARGETS,
                    false);
            return false;
        }

        setStatus(Status.IDLE);
        lastTransferAmount = 0L;

        boolean didWork;
        try {
            didWork = runNormalTick(serverLevel, buffer);
        } finally {
            var feKey = AppFluxBridge.FE_KEY;
            if (feKey != null) {
                buffer.endTick(feKey, host.actionSource());
            }
        }
        return didWork;
    }

    /**
     * Refreshes the index-aligned target/cache snapshots from {@link Host}
     * if the host's version stamp has changed. Idempotent; safe to call
     * multiple times per tick. The Overloaded Power Supply's OVERLOAD path
     * calls this before {@link #resolveTargetAtIndex} to guarantee the
     * caches are aligned with the host's current connection list even when
     * NORMAL-mode {@link #tickNormal} hasn't run.
     */
    public void refreshTargets() {
        int version = host.getValidTargetsVersion();
        if (hostVersion == version) {
            return;
        }
        hostVersion = version;

        var fresh = host.getValidTargets();
        if (cachedValidTargets.equals(fresh)) {
            return;
        }

        cachedValidTargets.clear();
        cachedValidTargets.addAll(fresh);
        cachedValidTargetSet.clear();
        cachedValidTargetSet.addAll(fresh);
        validTargetsVersion++;
        rebuildConnectionState();
    }

    /**
     * Drop all per-tick state (status, scheduling wheel, target caches) and
     * optionally flush any cached FE back to the ME network. Hosts call this
     * on lifecycle boundaries: grid detach, connection-list mutation, mode
     * toggle, BE removal.
     */
    public void onStateChanged() {
        if (bufferedStorage != null) {
            flushBufferToNetwork();
        }
        cachedValidTargets.clear();
        cachedValidTargetSet.clear();
        hostVersion = Integer.MIN_VALUE;
        clearTargetCaches();
        resetNormalWheel();
        if (!AppFluxBridge.canUseEnergyHandler()) {
            setStatus(Status.APPFLUX_UNAVAILABLE);
        } else if (host.getValidTargets().isEmpty()) {
            setStatus(Status.NO_CONNECTIONS);
        } else {
            setStatus(Status.IDLE);
        }
        host.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    /**
     * Flush any cached FE back to the ME network and zero the buffer. Safe
     * to call on any lifecycle boundary; FE the network refuses is dropped
     * (there is no persistent storage on this side by design).
     */
    public void flushBufferToNetwork() {
        var feKey = AppFluxBridge.FE_KEY;
        if (bufferedStorage == null) {
            return;
        }
        if (feKey != null) {
            bufferedStorage.flushAll(feKey, host.actionSource());
        }
        bufferedStorage.clearBuffer();
    }

    public void persistCellCache() {
        var feKey = AppFluxBridge.FE_KEY;
        if (bufferedStorage != null && feKey != null) {
            bufferedStorage.flushAll(feKey, host.actionSource());
        }
    }

    /**
     * Reset all per-tick scratch state without altering status. Used by
     * hosts that own their own status state machine (e.g. the supply BE
     * during OVERLOAD short-circuits) to drop schedule-wheel entries
     * between tick attempts.
     *
     * <p>Target caches are intentionally <b>not</b> dropped here — they
     * are persistent across ticks and get rebuilt automatically by
     * {@link #refreshTargets()} the next time the host's
     * {@link Host#getValidTargetsVersion} actually changes. Clearing them
     * unconditionally here would orphan {@link #connectionTargetCaches}
     * (all-null entries) while the distributor still believes the host
     * snapshot is fresh, leading to an NPE in {@link #runNormalTick} when
     * the wheel rebuild walks {@code cachedValidTargets.size()} indices.
     */
    public void clearTickState(boolean flushBuffer) {
        resetNormalWheel();
        if (flushBuffer) {
            flushBufferToNetwork();
        }
    }

    /**
     * Bootstrap the per-tick BufferedMEStorage cycle: ensure the storage
     * proxy is current, advance the consumption history, and resync target
     * caches with the host. Used by the supply's OVERLOAD path which runs
     * its own dispatch loop on top of the distributor's caches.
     *
     * <p>Returns the live {@link BufferedMEStorage} or {@code null} if the
     * grid has gone away. Callers <b>must</b> call
     * {@link BufferedMEStorage#endTick} before returning from their tick.
     */
    @Nullable
    public BufferedMEStorage prepareTick() {
        BufferedStorageService proxy = ensureStorageProxy();
        BufferedMEStorage buffer = bufferedStorage;
        if (proxy == null || buffer == null) {
            return null;
        }
        buffer.advanceHistory();
        refreshTargets();
        return buffer;
    }

    // ---- public query API ------------------------------------------------

    public Status getStatus() {
        return lastStatus;
    }

    public long getLastTransferAmount() {
        return lastTransferAmount;
    }

    public long getBufferedEnergy() {
        return bufferedStorage != null ? bufferedStorage.getBufferedEnergy() : 0L;
    }

    @Nullable
    public BufferedMEStorage getBufferedStorage() {
        return bufferedStorage;
    }

    @Nullable
    public BufferedStorageService getStorageProxy() {
        return storageProxy;
    }

    /**
     * Distributor-internal version stamp; bumped whenever
     * {@link #cachedValidTargets} actually changes (after diffing the host's
     * snapshot). External index-aligned arrays (e.g. the supply's OVERLOAD
     * ticket-expiry array) should compare against this value to know when
     * to reset.
     */
    public int getValidTargetsVersion() {
        return validTargetsVersion;
    }

    public int getValidTargetCount() {
        return cachedValidTargets.size();
    }

    public WirelessEnergyAPI.Target getValidTarget(int index) {
        return cachedValidTargets.get(index);
    }

    /**
     * Resolve the AppFlux energy handle at a given valid-target index, using
     * the distributor's listener-backed cache. Returns {@code null} if the
     * target BE is missing or doesn't expose an FE-compatible capability.
     */
    @Nullable
    public TargetAccess resolveTargetAtIndex(int index, ServerLevel serverLevel) {
        if (index < 0 || index >= cachedValidTargets.size()) {
            return null;
        }
        BlockEnergyTargetCache cache = connectionTargetCaches[index];
        return cache != null ? cache.resolve(serverLevel) : null;
    }

    /**
     * Mutator for hosts whose cost model differs from NORMAL (currently
     * only the supply's OVERLOAD mode, which doubles consumption to
     * {@code costMultiplier=2}). NORMAL ticks always reset to {@code 1}.
     */
    public void setCostMultiplier(int multiplier) {
        if (bufferedStorage != null) {
            bufferedStorage.setCostMultiplier(multiplier);
        }
    }

    public void setStatus(Status status) {
        lastStatus = status;
        if (status != Status.ACTIVE) {
            lastTransferAmount = 0L;
        }
    }

    /**
     * Record an active transfer originated outside the NORMAL pipeline (e.g.
     * the supply's OVERLOAD path). Sets status to {@link Status#ACTIVE} and
     * accumulates {@code amount} into the per-tick transfer total exposed
     * via {@link #getLastTransferAmount}.
     */
    public void recordActiveTransfer(long amount) {
        lastStatus = Status.ACTIVE;
        lastTransferAmount += amount;
    }

    // ---- NORMAL tick implementation -------------------------------------

    private boolean runNormalTick(ServerLevel serverLevel, BufferedMEStorage buffer) {
        if (normalWheelDirty || normalWheelTargetsVersion != validTargetsVersion) {
            rebuildNormalWheel();
        }

        normalWheelPointer = (normalWheelPointer + 1) & ENERGY_WHEEL_SLOTS_MASK;
        IntWheelSlot due = pollNormalWheel();
        int dueSize = due.size;
        if (dueSize == 0) {
            return false;
        }
        int[] dueData = due.data;
        BlockEnergyTargetCache[] caches = connectionTargetCaches;

        var feKey = AppFluxBridge.FE_KEY;
        if (feKey == null || storageProxy == null) {
            for (int i = 0; i < dueSize; i++) {
                scheduleNormalEntry(dueData[i]);
            }
            due.size = 0;
            return false;
        }

        ensureNormalBatchCapacity(dueSize);
        long[] demands = normalBatchDemand;
        TargetAccess[] resolvedTargets = normalBatchEnergyTargets;
        long totalDemand = 0L;
        long perCallLimit = Math.max(0L, AppFluxBridge.TRANSFER_RATE);
        for (int i = 0; i < dueSize; i++) {
            BlockEnergyTargetCache cache = caches[dueData[i]];
            TargetAccess energyTarget = cache.resolve(serverLevel);
            resolvedTargets[i] = energyTarget;
            long demand = 0L;
            if (energyTarget != null) {
                long sim = AppFluxBridge.simulateTarget(energyTarget, perCallLimit);
                demand = sim > perCallLimit ? perCallLimit : sim;
            }
            demands[i] = demand;
            totalDemand += demand;
        }

        if (totalDemand <= 0L) {
            for (int i = 0; i < dueSize; i++) {
                adjustAndScheduleNormalEntry(dueData[i], 0L);
                resolvedTargets[i] = null;
            }
            due.size = 0;
            // No simulate demand: targets full / wrong face / no cap — not an ME extract failure.
            updateIdleFailureStatus(false, true);
            return false;
        }

        long pulled = buffer.beginMemoryBatch(feKey, totalDemand, host.actionSource());
        if (pulled <= 0L) {
            try {
                requeueNormalEntriesSoon(due, 0);
                updateIdleFailureStatus(false, false);
                return false;
            } finally {
                buffer.endBatch(feKey, host.actionSource());
                Arrays.fill(resolvedTargets, 0, dueSize, null);
                due.size = 0;
            }
        }

        boolean didWork = false;
        long remainingBudget = pulled;
        IActionSource src = host.actionSource();
        try {
            for (int i = 0; i < dueSize; i++) {
                int targetIndex = dueData[i];
                long demand = demands[i];
                if (demand <= 0L) {
                    adjustAndScheduleNormalEntry(targetIndex, 0L);
                    continue;
                }
                if (remainingBudget <= 0L) {
                    requeueNormalEntriesSoon(due, i);
                    break;
                }

                long requested = demand < remainingBudget ? demand : remainingBudget;
                long pushed = AppFluxBridge.sendToTargetKnownDemand(
                        resolvedTargets[i], buffer, src, requested);
                if (pushed > 0L) {
                    lastStatus = Status.ACTIVE;
                    lastTransferAmount += pushed;
                    didWork = true;
                    remainingBudget -= pushed;
                }
                adjustAndScheduleNormalEntry(targetIndex, pushed);
            }
        } finally {
            buffer.endBatch(feKey, src);
            Arrays.fill(resolvedTargets, 0, dueSize, null);
            due.size = 0;
        }

        if (!didWork && lastStatus == Status.IDLE) {
            setStatus(Status.TARGET_BLOCKED);
        }
        return didWork;
    }

    private void enterIdleState(Status status, boolean flushBuffer) {
        // Target caches and the per-target cap listeners are deliberately
        // kept across idle transitions so that AppFlux availability /
        // empty-target cycles don't churn the listener registrations and
        // (more importantly) don't strand connectionTargetCaches[] in an
        // all-null state while refreshTargets() believes the host
        // snapshot is fresh — see clearTickState() rationale.
        resetNormalWheel();
        if (flushBuffer) {
            flushBufferToNetwork();
        }
        setStatus(status);
    }

    // ---- target cache pool ----------------------------------------------

    /**
     * Per-target lazy cache of the resolved AppFlux energy handle plus a
     * {@link ICapabilityInvalidationListener} hook. The listener is
     * registered once with each {@link ServerLevel} this target lives in
     * (NeoForge holds it weakly), so when this cache is dropped from
     * {@link #targetCachePool} it gets GC'd and silently unregisters.
     */
    public final class BlockEnergyTargetCache implements ICapabilityInvalidationListener {
        private final WirelessEnergyAPI.Target target;
        /** Per-target NORMAL-mode adaptive schedule delay (1..20 ticks). */
        int scheduleDelay = ENERGY_DELAY_MEAN;
        @Nullable
        private BlockEntity blockEntity;
        @Nullable
        private TargetAccess energyTarget;
        @Nullable
        private ServerLevel registeredLevel;

        private BlockEnergyTargetCache(WirelessEnergyAPI.Target target) {
            this.target = target;
        }

        @Override
        public boolean onInvalidate() {
            if (host.isHostRemoved()) {
                blockEntity = null;
                energyTarget = null;
                registeredLevel = null;
                return false;
            }
            blockEntity = null;
            energyTarget = null;
            return true;
        }

        @Nullable
        TargetAccess resolve(ServerLevel providerLevel) {
            if (energyTarget != null && blockEntity != null && !blockEntity.isRemoved()) {
                return energyTarget;
            }

            ServerLevel targetLevel = WirelessEnergyAPI.resolveLevel(providerLevel.getServer(), target);
            if (targetLevel == null) {
                blockEntity = null;
                energyTarget = null;
                return null;
            }

            BlockEntity currentBlockEntity = targetLevel.getBlockEntity(target.pos());
            if (currentBlockEntity == null) {
                ensureRegistered(targetLevel);
                blockEntity = null;
                energyTarget = null;
                return null;
            }

            ensureRegistered(targetLevel);
            blockEntity = currentBlockEntity;

            Supplier<IGrid> gridSupplier = () -> host.getMainNode().getGrid();
            Object capCache = WirelessEnergyAPI.resolveCapCache(providerLevel, target, gridSupplier);
            if (capCache == null) {
                energyTarget = null;
                return null;
            }

            TargetAccess resolved = WirelessEnergyAPI.resolveEnergyTarget(capCache, target.face());
            energyTarget = resolved;
            return resolved;
        }

        private void ensureRegistered(ServerLevel targetLevel) {
            if (registeredLevel == targetLevel) {
                return;
            }
            targetLevel.registerCapabilityListener(target.pos(), this);
            registeredLevel = targetLevel;
        }
    }

    private void rebuildConnectionState() {
        int n = cachedValidTargets.size();
        targetCachePool.keySet().retainAll(cachedValidTargetSet);

        if (connectionTargetCaches.length < n) {
            connectionTargetCaches = new BlockEnergyTargetCache[Math.max(n, INITIAL_CACHE_CAPACITY)];
        }
        for (int i = 0; i < n; i++) {
            connectionTargetCaches[i] = targetCachePool.computeIfAbsent(
                    cachedValidTargets.get(i), BlockEnergyTargetCache::new);
        }
        for (int i = n; i < connectionTargetCaches.length; i++) {
            connectionTargetCaches[i] = null;
        }

        normalWheelDirty = true;
    }

    private void clearTargetCaches() {
        targetCachePool.clear();
        if (connectionTargetCaches.length > 0) {
            Arrays.fill(connectionTargetCaches, null);
        }
    }

    // ---- scheduling wheel -----------------------------------------------

    private IntWheelSlot pollNormalWheel() {
        IntWheelSlot slot = normalEnergyWheel[normalWheelPointer];
        if (slot.size == 0) {
            return slot;
        }
        IntWheelSlot spare = spareNormalWheelSlot;
        spare.size = 0;
        normalEnergyWheel[normalWheelPointer] = spare;
        spareNormalWheelSlot = slot;
        return slot;
    }

    private void scheduleNormalEntry(int targetIndex) {
        int delay = connectionTargetCaches[targetIndex].scheduleDelay;
        int slot = (normalWheelPointer + delay) & ENERGY_WHEEL_SLOTS_MASK;
        normalEnergyWheel[slot].add(targetIndex);
    }

    private void requeueNormalEntriesSoon(IntWheelSlot due, int startIndex) {
        if (startIndex >= due.size) {
            return;
        }
        int slotIdx = (normalWheelPointer + 1) & ENERGY_WHEEL_SLOTS_MASK;
        IntWheelSlot targetSlot = normalEnergyWheel[slotIdx];
        int[] dueData = due.data;
        int dueSize = due.size;
        for (int i = startIndex; i < dueSize; i++) {
            targetSlot.add(dueData[i]);
        }
    }

    private void rebuildNormalWheel() {
        for (var slot : normalEnergyWheel) {
            slot.clear();
        }
        spareNormalWheelSlot.clear();
        normalWheelPointer = 0;
        int size = cachedValidTargets.size();
        for (int i = 0; i < size; i++) {
            // Stagger initial placement so that 64 connections don't all land
            // in the same wheel slot. Subsequent reschedules go through
            // scheduleNormalEntry which uses cache.scheduleDelay alone.
            int initialSlot = (normalWheelPointer + 1 + i) & ENERGY_WHEEL_SLOTS_MASK;
            normalEnergyWheel[initialSlot].add(i);
        }
        normalWheelTargetsVersion = validTargetsVersion;
        normalWheelDirty = false;
    }

    private void resetNormalWheel() {
        for (var slot : normalEnergyWheel) {
            slot.clear();
        }
        normalBatchDemand = new long[0];
        normalBatchEnergyTargets = new TargetAccess[0];
        spareNormalWheelSlot.clear();
        normalWheelPointer = 0;
        normalWheelTargetsVersion = -1;
        normalWheelDirty = true;
    }

    private void ensureNormalBatchCapacity(int size) {
        if (normalBatchDemand.length < size) {
            normalBatchDemand = new long[size];
            normalBatchEnergyTargets = new TargetAccess[size];
        }
    }

    private void adjustAndScheduleNormalEntry(int targetIndex, long pushed) {
        BlockEnergyTargetCache cache = connectionTargetCaches[targetIndex];
        int delay = cache.scheduleDelay;
        if (pushed > 0L) {
            delay = delay > ENERGY_DELAY_MEAN ? delay / 2 : delay - 1;
        } else {
            delay++;
        }
        if (delay < ENERGY_DELAY_MIN) {
            delay = ENERGY_DELAY_MIN;
        } else if (delay > ENERGY_DELAY_MAX) {
            delay = ENERGY_DELAY_MAX;
        }
        cache.scheduleDelay = delay;
        int slot = (normalWheelPointer + delay) & ENERGY_WHEEL_SLOTS_MASK;
        IntWheelSlot ws = normalEnergyWheel[slot];
        int[] data = ws.data;
        int size = ws.size;
        if (size >= data.length) {
            data = Arrays.copyOf(data, data.length << 1);
            ws.data = data;
        }
        data[size] = targetIndex;
        ws.size = size + 1;
    }

    // ---- storage proxy lifecycle ----------------------------------------

    @Nullable
    private BufferedStorageService ensureStorageProxy() {
        var grid = host.getMainNode().getGrid();
        if (grid == null) {
            // Grid gone: flush whatever cache we still hold to the OLD
            // delegate before dropping references, otherwise extracted FE
            // is silently deleted.
            flushBufferToNetwork();
            delegateStorageService = null;
            bufferedStorage = null;
            storageProxy = null;
            return null;
        }

        IStorageService storageService = grid.getStorageService();
        if (storageProxy == null || bufferedStorage == null || delegateStorageService != storageService) {
            if (bufferedStorage != null) {
                flushBufferToNetwork();
            }
            bufferedStorage = new BufferedMEStorage(
                    storageService.getInventory(),
                    host.getCellStorageSupplier(),
                    host.getCellPersistCallback());
            storageProxy = new BufferedStorageService(storageService, bufferedStorage);
            delegateStorageService = storageService;
        }

        return storageProxy;
    }

    // ---- status helpers --------------------------------------------------

    /**
     * @param targetSideFailure if {@code true}, the idle tick failed because
     *                          targets would not accept (simulate=0 or push
     *                          refused); if {@code false}, {@code beginMemoryBatch}
     *                          could not pull FE from the ME network.
     */
    private void updateIdleFailureStatus(boolean didWork, boolean targetSideFailure) {
        if (didWork || lastStatus != Status.IDLE) {
            return;
        }
        setStatus(targetSideFailure ? Status.TARGET_BLOCKED : Status.NO_NETWORK_FE);
    }
}
