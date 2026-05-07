package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.server.level.ServerLevel;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.MEStorage;

import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.logic.energy.BufferedMEStorage;
import com.moakiee.ae2lt.logic.energy.TargetAccess;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyAPI;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyDistributor;

/**
 * Wireless FE distribution logic for the Overloaded Power Supply.
 *
 * <p>NORMAL-mode dispatch is fully delegated to a shared
 * {@link WirelessEnergyDistributor} instance — the same engine used by the
 * Overloaded ME Interface and the Overloaded Pattern Provider — so all
 * three BEs share the per-target adaptive scheduling wheel, capability
 * invalidation listeners, and one-shot ME batch extract.
 *
 * <p>OVERLOAD-mode dispatch (cell-backed, ticket-rotated, up to 64 calls/tick
 * per target) lives only here, but reuses the distributor's cached target
 * resolutions ({@link WirelessEnergyDistributor#resolveTargetAtIndex}) so
 * cap listeners are registered once across both modes.
 *
 * <p>Gating by Flux Cell presence:
 * <ul>
 * <li><b>No cell installed</b>: NORMAL is the only available mode.
 *     OVERLOAD refuses to run and reports {@link Status#NO_CELL}.</li>
 * <li><b>Cell installed</b>: unlocks OVERLOAD. NORMAL still runs through the
 *     same shared distributor; the Flux Cell becomes the
 *     {@link BufferedMEStorage} backing buffer (AE2 ME Chest pattern).</li>
 * </ul>
 */
public class OverloadedPowerSupplyLogic implements IGridTickable {

    public enum Status {
        IDLE,
        APPFLUX_UNAVAILABLE,
        NO_CELL,
        NO_GRID,
        NO_CONNECTIONS,
        NO_VALID_TARGETS,
        NO_NETWORK_FE,
        TARGET_UNSUPPORTED,
        TARGET_BLOCKED,
        ACTIVE
    }

    private static final int TICK_MIN = 1;
    private static final int TICK_MAX = 20;
    private static final int OVERLOAD_MAX_CONNECTIONS =
            OverloadedPowerSupplyBlockEntity.MAX_WIRELESS_CONNECTIONS;
    private static final int OVERLOAD_MAX_CALLS = 64;
    private static final int TICKET_DURATION = 20;
    private static final int SENTINEL_BUCKETS = 5;
    /**
     * Period (ticks) between full re-validation of valid target connections.
     * Cap-invalidation listeners (registered inside the distributor's
     * {@link WirelessEnergyDistributor.BlockEnergyTargetCache}) deliver
     * real-time updates between sweeps; this periodic sweep is purely a
     * defensive net for chunk-load/dimension events that aren't surfaced as
     * cap invalidations. The {@link #revalidationOffset} stagger spreads
     * multiple BE instances across the 5 s window.
     */
    private static final int REVALIDATION_INTERVAL = 100;

    private final OverloadedPowerSupplyBlockEntity host;
    private final IActionSource actionSource;
    private final WirelessEnergyDistributor distributor;

    /** Snapshot of {@link OverloadedPowerSupplyBlockEntity#getConnections}. */
    private List<OverloadedPowerSupplyBlockEntity.WirelessConnection> cachedConnections = List.of();
    /** Index-aligned with {@link #cachedConnections}. */
    private List<WirelessEnergyAPI.Target> cachedConnectionTargets = List.of();
    /** Result of the current periodic sweep — also fed to the distributor. */
    private final List<WirelessEnergyAPI.Target> cachedValidTargets = new ArrayList<>();
    private final ArrayList<WirelessEnergyAPI.Target> nextValidTargets = new ArrayList<>();
    private final ArrayList<OverloadedPowerSupplyBlockEntity.WirelessConnection> invalidConnections = new ArrayList<>();
    private List<WirelessEnergyAPI.Target> exposedValidTargets = List.of();
    private long validTargetsCacheTick = Long.MIN_VALUE;
    private int validTargetsVersion;
    private final int revalidationOffset = (System.identityHashCode(this) & 0x7FFFFFFF) % REVALIDATION_INTERVAL;
    private int cachedConnectionVersion = -1;

    /**
     * Per-connection ticket expiry, index-aligned with
     * {@link #cachedValidTargets}. Reset whenever the valid target set
     * changes (in {@link #onValidTargetsChanged}).
     */
    private long[] connectionTicketExpiry = new long[0];
    private WirelessEnergyAPI.Target[] overloadBatchTargets = new WirelessEnergyAPI.Target[OVERLOAD_MAX_CONNECTIONS];
    private TargetAccess[] overloadBatchEnergyTargets = new TargetAccess[OVERLOAD_MAX_CONNECTIONS];
    private boolean[] overloadBatchTicketed = new boolean[OVERLOAD_MAX_CONNECTIONS];
    private int overloadBatchSize;
    private int sentinelIndex;

    private Status lastStatus = Status.IDLE;
    private long lastTransferAmount;

    public OverloadedPowerSupplyLogic(OverloadedPowerSupplyBlockEntity host) {
        this.host = host;
        this.actionSource = IActionSource.ofMachine(host);
        this.distributor = new WirelessEnergyDistributor(new DistributorHost());
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_MIN, TICK_MAX, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!(host.getLevel() instanceof ServerLevel serverLevel)) {
            return TickRateModulation.SLEEP;
        }

        boolean didWork = tick(serverLevel);
        // Reflect the just-finished tick on the world block state so the
        // off / on / on_overloaded models swap in real time. updateVisualState
        // is a no-op when nothing changed, so this is cheap on idle ticks.
        host.updateVisualState(lastStatus == Status.ACTIVE,
                host.getMode() == OverloadedPowerSupplyBlockEntity.PowerMode.OVERLOAD);

        if (isOverloadActive() && getActiveTicketCount() > 0) {
            return TickRateModulation.URGENT;
        }

        if (host.getConnections().isEmpty()) {
            return TickRateModulation.IDLE;
        }

        if (host.getMode() == OverloadedPowerSupplyBlockEntity.PowerMode.NORMAL) {
            return TickRateModulation.URGENT;
        }

        return didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    /**
     * Overload mode is only active when both the player has selected it AND
     * a Flux Cell is installed to back the cache.
     */
    private boolean isOverloadActive() {
        return host.getMode() == OverloadedPowerSupplyBlockEntity.PowerMode.OVERLOAD
                && host.getBufferCapacity() > 0L;
    }

    public void onStateChanged() {
        // Defensively persist the cell on every state-change boundary
        // (cell removed, mode toggled, connections edited, grid detached).
        // The cell IS the buffer now, so we can never carry stale FE forward
        // — but we still want the ItemStack data component to reflect the
        // latest in-memory storedEnergy before the cell potentially leaves
        // this BE.
        distributor.flushBufferToNetwork();
        if (!isOverloadActive() && connectionTicketExpiry.length > 0) {
            Arrays.fill(connectionTicketExpiry, 0L);
        }
        cachedConnections = List.of();
        cachedConnectionTargets = List.of();
        cachedValidTargets.clear();
        exposedValidTargets = List.of();
        validTargetsCacheTick = Long.MIN_VALUE;
        cachedConnectionVersion = -1;
        sentinelIndex = 0;
        distributor.clearTickState(false);
        if (!AppFluxBridge.canUseEnergyHandler()) {
            setStatus(Status.APPFLUX_UNAVAILABLE);
        } else if (host.getConnections().isEmpty()) {
            setStatus(Status.NO_CONNECTIONS);
        } else {
            setStatus(Status.IDLE);
        }
        host.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    /**
     * Flush any cached FE back to the ME network and zero the buffer. Safe
     * to call on any lifecycle boundary: cell removed, grid disconnected,
     * BE removed / unloaded.
     */
    public void flushBufferToNetwork() {
        distributor.flushBufferToNetwork();
    }

    public void persistCellCache() {
        distributor.persistCellCache();
    }

    public long getBufferedEnergy() {
        return distributor.getBufferedEnergy();
    }

    public int getActiveTicketCount() {
        long[] expiry = connectionTicketExpiry;
        int n = Math.min(expiry.length, cachedValidTargets.size());
        if (n == 0) {
            return 0;
        }

        if (!(host.getLevel() instanceof ServerLevel serverLevel)) {
            int count = 0;
            for (int i = 0; i < n; i++) {
                if (expiry[i] > 0L) {
                    count++;
                }
            }
            return count;
        }

        long gameTime = serverLevel.getGameTime();
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (expiry[i] >= gameTime) {
                count++;
            }
        }
        return count;
    }

    public Status getLastStatus() {
        return lastStatus;
    }

    public long getLastTransferAmount() {
        return lastTransferAmount;
    }

    private boolean tick(ServerLevel serverLevel) {
        if (!AppFluxBridge.canUseEnergyHandler()) {
            enterIdleState(Status.APPFLUX_UNAVAILABLE, true);
            return false;
        }

        var mainNode = host.getMainNode();
        if (!mainNode.isActive() || mainNode.getGrid() == null) {
            setStatus(Status.NO_GRID);
            return false;
        }

        boolean wantsOverload = host.getMode() == OverloadedPowerSupplyBlockEntity.PowerMode.OVERLOAD;
        boolean hasCell = host.getBufferCapacity() > 0L;
        boolean overloadActive = wantsOverload && hasCell;

        if (wantsOverload && !hasCell) {
            enterIdleState(Status.NO_CELL, false);
            return false;
        }

        // Supply-side validation: 5 s sweep + auto-removal of invalid
        // connections (BE missing, dim unloaded). Bumps validTargetsVersion
        // when the set changes, which the distributor picks up via
        // refreshTargets() on its next tick / refresh.
        List<WirelessEnergyAPI.Target> validTargets = getValidTargets(serverLevel);
        if (validTargets.isEmpty()) {
            enterIdleState(host.getConnections().isEmpty() ? Status.NO_CONNECTIONS : Status.NO_VALID_TARGETS,
                    overloadActive);
            return false;
        }

        if (!overloadActive) {
            return tickNormalDelegate(serverLevel);
        }

        return tickOverloadFlow(serverLevel);
    }

    private boolean tickNormalDelegate(ServerLevel serverLevel) {
        boolean didWork = distributor.tickNormal(serverLevel);
        lastStatus = mapStatus(distributor.getStatus());
        lastTransferAmount = distributor.getLastTransferAmount();
        return didWork;
    }

    private boolean tickOverloadFlow(ServerLevel serverLevel) {
        BufferedMEStorage buffer = distributor.prepareTick();
        if (buffer == null) {
            setStatus(Status.NO_GRID);
            return false;
        }
        buffer.setCostMultiplier(2);
        setStatus(Status.IDLE);
        lastTransferAmount = 0L;

        boolean didWork;
        try {
            didWork = tickOverloadStaged(serverLevel);
        } finally {
            var feKey = AppFluxBridge.FE_KEY;
            if (feKey != null) {
                buffer.endTick(feKey, actionSource);
            }
        }
        return didWork;
    }

    /**
     * Drop all per-tick state and (optionally) flush the FE buffer back to
     * ME. Used by every {@link #tick} short-circuit so that lifecycle
     * transitions never leak tickets, target caches, or schedule-wheel
     * entries.
     */
    private void enterIdleState(Status status, boolean flushBuffer) {
        if (connectionTicketExpiry.length > 0) {
            Arrays.fill(connectionTicketExpiry, 0L);
        }
        distributor.clearTickState(flushBuffer);
        setStatus(status);
    }

    private boolean tickOverloadStaged(ServerLevel serverLevel) {
        long gameTime = serverLevel.getGameTime();
        try {
            int targetCount = prepareOverloadTargets(serverLevel);
            if (targetCount == 0) {
                setStatus(Status.NO_VALID_TARGETS);
                return false;
            }

            long[] expiry = connectionTicketExpiry;
            boolean[] ticketed = overloadBatchTicketed;
            TargetAccess[] energyTargets = overloadBatchEnergyTargets;

            int idleCount = 0;
            for (int i = 0; i < targetCount; i++) {
                TargetAccess energyTarget = energyTargets[i];
                if (energyTarget == null) {
                    ticketed[i] = false;
                    continue;
                }
                boolean hasTicket = expiry[i] >= gameTime;
                ticketed[i] = hasTicket;
                if (!hasTicket) {
                    idleCount++;
                }
            }

            boolean didWork = false;
            int scans = idleCount == 0
                    ? 0
                    : Math.max(1, (idleCount + SENTINEL_BUCKETS - 1) / SENTINEL_BUCKETS);
            if (idleCount > 0) {
                int index = sentinelIndex % targetCount;
                int visited = 0;
                int found = 0;
                while (visited < targetCount && found < scans) {
                    int targetIndex = index;
                    index = (index + 1) % targetCount;
                    visited++;
                    TargetAccess energyTarget = energyTargets[targetIndex];
                    if (ticketed[targetIndex] || energyTarget == null) {
                        continue;
                    }
                    found++;
                    long pushed = pushPrepared(energyTarget, 1);
                    if (pushed > 0L) {
                        expiry[targetIndex] = gameTime + TICKET_DURATION;
                        ticketed[targetIndex] = true;
                        didWork = true;
                    }
                }
                sentinelIndex = index;
            } else {
                sentinelIndex = 0;
            }

            for (int i = 0; i < targetCount; i++) {
                if (!ticketed[i]) {
                    continue;
                }
                long pushed = pushPrepared(energyTargets[i], OVERLOAD_MAX_CALLS);
                if (pushed > 0L) {
                    expiry[i] = gameTime + TICKET_DURATION;
                    didWork = true;
                }
            }

            updateIdleFailureStatus(didWork, true);
            return didWork;
        } finally {
            clearOverloadBatch();
        }
    }

    private int prepareOverloadTargets(ServerLevel serverLevel) {
        int targetCount = Math.min(distributor.getValidTargetCount(), OVERLOAD_MAX_CONNECTIONS);
        ensureOverloadBatchCapacity(targetCount);
        long[] expiry = connectionTicketExpiry;
        WirelessEnergyAPI.Target[] batchTargets = overloadBatchTargets;
        TargetAccess[] batchEnergyTargets = overloadBatchEnergyTargets;
        for (int i = 0; i < targetCount; i++) {
            WirelessEnergyAPI.Target target = distributor.getValidTarget(i);
            TargetAccess energyTarget = distributor.resolveTargetAtIndex(i, serverLevel);
            batchTargets[i] = target;
            batchEnergyTargets[i] = energyTarget;
            if (energyTarget == null && i < expiry.length) {
                expiry[i] = 0L;
            }
        }
        overloadBatchSize = targetCount;
        return targetCount;
    }

    private void ensureOverloadBatchCapacity(int size) {
        if (overloadBatchTargets.length < size) {
            overloadBatchTargets = new WirelessEnergyAPI.Target[size];
        }
        if (overloadBatchEnergyTargets.length < size) {
            overloadBatchEnergyTargets = new TargetAccess[size];
        }
        if (overloadBatchTicketed.length < size) {
            overloadBatchTicketed = new boolean[size];
        }
    }

    private void clearOverloadBatch() {
        for (int i = 0; i < overloadBatchSize; i++) {
            overloadBatchTargets[i] = null;
            overloadBatchEnergyTargets[i] = null;
            overloadBatchTicketed[i] = false;
        }
        overloadBatchSize = 0;
    }

    private long pushPrepared(TargetAccess energyTarget, int maxCalls) {
        BufferedMEStorage buffer = distributor.getBufferedStorage();
        if (buffer == null) {
            return 0L;
        }

        long pushed = WirelessEnergyAPI.sendToTargetRepeatedOptimistic(
                energyTarget, buffer, actionSource, Math.max(0L, AppFluxBridge.TRANSFER_RATE), maxCalls);
        if (pushed > 0L) {
            setActive(pushed);
            return pushed;
        }
        if (pushed < 0L) {
            setStatus(Status.TARGET_UNSUPPORTED);
        }
        return 0L;
    }

    /**
     * Validates the host's wireless connections, removes targets whose BE has
     * permanently disappeared, and exposes the result to the distributor via
     * {@link DistributorHost#getValidTargets}. Cap-invalidation listeners
     * registered by the distributor handle real-time updates; this periodic
     * sweep is the defensive net for chunk-load/dimension transitions.
     */
    private List<WirelessEnergyAPI.Target> getValidTargets(ServerLevel serverLevel) {
        // O(1) version check replaces a per-tick 64-element record-equals
        // comparison: the host bumps its connection version on every list
        // mutation, so when nothing has changed we only do an int compare.
        int version = host.getConnectionVersion();
        if (cachedConnectionVersion != version) {
            var connections = host.getConnections();
            var rebuilt = new ArrayList<WirelessEnergyAPI.Target>(connections.size());
            for (var conn : connections) {
                rebuilt.add(new WirelessEnergyAPI.Target(conn.dimension(), conn.pos(), conn.boundFace()));
            }
            cachedConnections = List.copyOf(connections);
            cachedConnectionTargets = List.copyOf(rebuilt);
            cachedValidTargets.clear();
            exposedValidTargets = List.of();
            validTargetsCacheTick = Long.MIN_VALUE;
            cachedConnectionVersion = version;
        }

        long gameTime = serverLevel.getGameTime();
        // First-call seed + periodic 5 s sweep (offset per-BE so that 64
        // wireless supplies don't all spike the same tick). Cap-invalidation
        // listeners deliver real-time updates between sweeps.
        if (validTargetsCacheTick == Long.MIN_VALUE
                || (gameTime + revalidationOffset) % REVALIDATION_INTERVAL == 0L) {
            nextValidTargets.clear();
            invalidConnections.clear();

            var server = serverLevel.getServer();
            for (int i = 0; i < cachedConnectionTargets.size(); i++) {
                var target = cachedConnectionTargets.get(i);
                if (!target.dimension().equals(serverLevel.dimension())) {
                    invalidConnections.add(cachedConnections.get(i));
                    continue;
                }
                ServerLevel targetLevel = server.getLevel(target.dimension());
                if (targetLevel == null) {
                    invalidConnections.add(cachedConnections.get(i));
                    continue;
                }
                if (!targetLevel.isLoaded(target.pos())) {
                    continue;
                }
                if (targetLevel.getBlockEntity(target.pos()) == null) {
                    invalidConnections.add(cachedConnections.get(i));
                    continue;
                }

                nextValidTargets.add(target);
            }

            if (!invalidConnections.isEmpty()) {
                host.removeConnections(invalidConnections);
                invalidConnections.clear();
                return getValidTargets(serverLevel);
            }

            if (!cachedValidTargets.equals(nextValidTargets)) {
                cachedValidTargets.clear();
                cachedValidTargets.addAll(nextValidTargets);
                exposedValidTargets = List.copyOf(cachedValidTargets);
                validTargetsVersion++;
                onValidTargetsChanged();
            }
            validTargetsCacheTick = gameTime;
        }

        return cachedValidTargets;
    }

    /**
     * Called whenever {@link #cachedValidTargets} changes. The distributor's
     * own caches are rebuilt automatically on its next refresh via the
     * {@link DistributorHost#getValidTargetsVersion} stamp; here we only need
     * to keep the OVERLOAD-specific {@link #connectionTicketExpiry} array in
     * sync.
     */
    private void onValidTargetsChanged() {
        int n = cachedValidTargets.size();
        if (connectionTicketExpiry.length < n) {
            connectionTicketExpiry = new long[Math.max(n, OVERLOAD_MAX_CONNECTIONS)];
        } else if (connectionTicketExpiry.length > 0) {
            Arrays.fill(connectionTicketExpiry, 0L);
        }
    }

    private void updateIdleFailureStatus(boolean didWork, boolean hadEnergyBudget) {
        if (didWork || lastStatus != Status.IDLE) {
            return;
        }
        setStatus(hadEnergyBudget ? Status.TARGET_BLOCKED : Status.NO_NETWORK_FE);
    }

    private void setStatus(Status status) {
        lastStatus = status;
        if (status != Status.ACTIVE) {
            lastTransferAmount = 0L;
        }
    }

    private void setActive(long amount) {
        lastStatus = Status.ACTIVE;
        lastTransferAmount += amount;
    }

    private static Status mapStatus(WirelessEnergyDistributor.Status dist) {
        return switch (dist) {
            case IDLE -> Status.IDLE;
            case APPFLUX_UNAVAILABLE -> Status.APPFLUX_UNAVAILABLE;
            case NO_GRID -> Status.NO_GRID;
            case NO_CONNECTIONS -> Status.NO_CONNECTIONS;
            case NO_VALID_TARGETS -> Status.NO_VALID_TARGETS;
            case NO_NETWORK_FE -> Status.NO_NETWORK_FE;
            case TARGET_UNSUPPORTED -> Status.TARGET_UNSUPPORTED;
            case TARGET_BLOCKED -> Status.TARGET_BLOCKED;
            case ACTIVE -> Status.ACTIVE;
        };
    }

    private final class DistributorHost implements WirelessEnergyDistributor.Host {
        @Override
        public IManagedGridNode getMainNode() {
            return host.getMainNode();
        }

        @Override
        public IActionSource actionSource() {
            return actionSource;
        }

        @Override
        public boolean isHostRemoved() {
            return host.isRemoved();
        }

        @Override
        public List<WirelessEnergyAPI.Target> getValidTargets() {
            return exposedValidTargets;
        }

        @Override
        public int getValidTargetsVersion() {
            return validTargetsVersion;
        }

        @Override
        public java.util.function.Supplier<MEStorage> getCellStorageSupplier() {
            return host::getInstalledCellStorage;
        }

        @Override
        public Runnable getCellPersistCallback() {
            return host::persistCellStorage;
        }
    }
}
