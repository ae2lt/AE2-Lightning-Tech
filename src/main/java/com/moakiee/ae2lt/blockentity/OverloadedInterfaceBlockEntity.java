package com.moakiee.ae2lt.blockentity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import com.google.common.util.concurrent.Runnables;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.item.OverloadedFilterComponentItem;
import com.moakiee.ae2lt.logic.AppFluxHelper;
import com.moakiee.ae2lt.logic.ConnectionEndpoints;
import com.moakiee.ae2lt.logic.EjectModeRegistry;
import com.moakiee.ae2lt.debug.WirelessIoPerformanceProbe;
import com.moakiee.ae2lt.logic.FilteredInsertGenericInv;
import com.moakiee.ae2lt.logic.OverloadedInterfaceLogic;
import com.moakiee.ae2lt.logic.OverloadedInterfaceTickDecider;
import com.moakiee.ae2lt.logic.TransferPollSchedule;
import com.moakiee.ae2lt.logic.TransferCadence;
import com.moakiee.ae2lt.logic.WirelessConnectionLists;
import com.moakiee.ae2lt.logic.WirelessConnectionRange;
import com.moakiee.ae2lt.logic.WirelessConnectionRef;
import com.moakiee.ae2lt.logic.WirelessConnectionValidator;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyAPI;
import com.moakiee.ae2lt.logic.energy.WirelessEnergyDistributor;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.util.AECableType;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.parts.automation.StackWorldBehaviors;

public class OverloadedInterfaceBlockEntity extends InterfaceBlockEntity
        implements FrequencyBindingHost {

    public static final int SLOT_COUNT = 36;
    public static final int MAX_WIRELESS_CONNECTIONS = 1024;

    // ── Idle power (recomputed on mode/connection changes) ───────────────
    // Base interface upkeep is already heavier than vanilla because the
    // overloaded variant proxies an unbounded ME view; wireless mode and
    // FAST IO add further multiplicative cost.
    private static final double IDLE_BASE = 5.0;
    private static final double IDLE_WIRELESS_BONUS = 5.0;
    private static final double IDLE_PER_CONNECTION = 1.0;
    private static final double IDLE_FAST_MULTIPLIER = 1.5;

    // ── NBT tags ─────────────────────────────────────────────────────────

    private static final String TAG_INTERFACE_MODE = "InterfaceMode";
    private static final String TAG_EXPORT_MODE   = "ExportMode";
    private static final String TAG_IMPORT_MODE   = "ImportMode";
    private static final String TAG_IO_SPEED_MODE  = "IOSpeedMode";
    private static final String TAG_CONNECTIONS    = "WirelessConnections";
    private static final String TAG_ENERGY_DIR     = "EnergyDir";
    private static final String TAG_UNLIMITED_SLOTS = "UnlimitedSlots";
    private static final String TAG_FILTER_INV     = "FilterInv";
    private static final String TAG_IMPORT_BUFFER  = "ae2ltImportBuffer";
    private static final String TAG_IMPORT_FLUSH_TICK = "ae2ltImportFlushTick";

    public enum InterfaceMode { NORMAL, WIRELESS }
    public enum IOSpeedMode   { NORMAL, FAST }
    public enum ExportMode    { OFF, AUTO }
    public enum ImportMode    { OFF, AUTO, EJECT }

    private static final List<Direction> ALL_NORMAL_IO_DIRECTIONS =
            List.of(Direction.values());

    // ══════════════════════════════════════════════════════════════════════
    //  Transfer budget
    //  Reference: ExtAE extended bus — 96 base (4 speed cards) × 8 busSpeed
    //  = 768 items per activation.
    // ══════════════════════════════════════════════════════════════════════

    /** ExternalStorageStrategy wrapper cache staleness guard (both directions). */
    private static final int WRAPPER_REFRESH_TICKS = 20;

    // ══════════════════════════════════════════════════════════════════════
    //  Cooldown — per-mode parameters
    // ══════════════════════════════════════════════════════════════════════

    private static final int NORMAL_CD_MAX = 80;
    private static final int FAST_CD_MAX = 20;
    private static final int IMPORT_FLUSH_INTERVAL = 5;
    private static final int IMPORT_FLUSH_MAX_KEYS = 16_384;
    private static final int STOP_IMPORT_TTL = 20;
    private static final int IO_WHEEL_SLOTS = 128;
    private static final int EXPORT_TRANSFER_MAX_KEYS = 128;
    private static final long IMPORT_TRANSFER_LIMIT = Long.MAX_VALUE;

    private final ImportScanBuffer scanBuffer = new ImportScanBuffer();
    /** Persistent ownership buffer for imported stacks and export overflow. */
    private final Map<AEKey, Long> importBuffer = new LinkedHashMap<>();
    private final ImportBufferFlushState importBufferFlushState = new ImportBufferFlushState();
    private final Map<AEKeyType, Long> keyTypeLockUntil = new IdentityHashMap<>();
    private final Map<AEKeyType, List<ExportConfigEntry>> exportConfigCache = new IdentityHashMap<>();
    private long importBufferLastFlushTick = Long.MIN_VALUE;
    private long importBufferLastSaveTick = Long.MIN_VALUE;
    private boolean importBufferFlushLimited;
    private int importBufferRemainingKeys;
    private long exportConfigCacheTick = Long.MIN_VALUE;
    private int exportConfigCacheHash;
    private boolean exportConfigCacheValid;

    // ── WirelessConnection ───────────────────────────────────────────────

    public record WirelessConnection(
            ResourceKey<Level> dimension, BlockPos pos, Direction boundFace
    ) implements WirelessConnectionRef {
        private static final String TAG_DIM  = "Dim";
        private static final String TAG_POS  = "Pos";
        private static final String TAG_FACE = "Face";

        public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString(TAG_DIM, dimension.location().toString());
            tag.putLong(TAG_POS, pos.asLong());
            tag.putInt(TAG_FACE, boundFace.get3DDataValue());
            return tag;
        }

        public static WirelessConnection fromTag(CompoundTag tag) {
            var dim = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString(TAG_DIM)));
            return new WirelessConnection(
                    dim, BlockPos.of(tag.getLong(TAG_POS)),
                    Direction.from3DDataValue(tag.getInt(TAG_FACE)));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Wireless IO model — per key type and direction
    // ══════════════════════════════════════════════════════════════════════

    enum IoDirection { IMPORT, EXPORT }

    static final class CooldownTracker {
        private IOSpeedMode mode = IOSpeedMode.NORMAL;
        private final TransferPollSchedule schedule = new TransferPollSchedule();
        private long cooldownUntil = -1;

        long cooldownUntil() { return cooldownUntil; }

        void reset(IOSpeedMode newMode) {
            mode = newMode;
            schedule.reset();
            cooldownUntil = -1;
        }

        void onSuccess(long now, IOSpeedMode newMode) {
            if (mode != newMode) reset(newMode);
            schedule.success(now);
            // A drained output can immediately refill. Confirm continued
            // production next tick; only an actual empty observation may wait.
            cooldownUntil = now + 1;
        }

        void onFail(long now, IOSpeedMode newMode) {
            if (mode != newMode) reset(newMode);
            cooldownUntil = now + schedule.failure(now,
                    mode == IOSpeedMode.FAST ? FAST_CD_MAX : NORMAL_CD_MAX);
        }
    }

    static final class ImportScanBuffer {
        private static final int MAX_RETAINED_KEYS = 256;
        private KeyCounter counter = new KeyCounter();
        private boolean borrowed;

        KeyCounter acquire() {
            // A third-party storage callback may re-enter I/O. Its temporary
            // scan must not clear or mutate the outer pass's snapshot.
            if (borrowed) return new KeyCounter();
            borrowed = true;
            counter.clear();
            return counter;
        }

        void release(KeyCounter scanned) {
            if (scanned != counter) return;
            // clear() preserves AE2's per-primary maps. Remove primary types
            // absent from this target, otherwise unrelated machines slowly
            // enlarge every following clear/iteration.
            scanned.removeEmptySubmaps();
            if (scanned.size() > MAX_RETAINED_KEYS) {
                counter = new KeyCounter();
            }
            borrowed = false;
        }
    }

    /** Immutable item keys only; every transfer reads live slot contents. */
    static final class ImportSlotKeyCache {
        private static final int MAX_CACHED_SLOTS = 4096;
        private AEItemKey[] slotKeys;

        void prepareSlots(int slots) {
            int retained = Math.min(slots, MAX_CACHED_SLOTS);
            if (slotKeys == null || slotKeys.length != retained) {
                slotKeys = new AEItemKey[retained];
            }
        }

        @Nullable
        AEItemKey keyForSlot(int slot, ItemStack stack) {
            if (stack.isEmpty()) return null;
            if (slotKeys == null || slot >= slotKeys.length) return AEItemKey.of(stack);
            var key = slotKeys[slot];
            // Handlers may mutate a returned stack in place. Compare against
            // the key's immutable snapshot, never the live stack's identity.
            if (key == null || !key.matches(stack)) {
                key = AEItemKey.of(stack);
                slotKeys[slot] = key;
            }
            return key;
        }

        void clear() {
            slotKeys = null;
        }
    }

    static final class ExactImportPlan {
        private final Map<AEKeyType, List<AEKey>> keysByType = new IdentityHashMap<>();

        ExactImportPlan(Set<AEKey> filterKeys, Set<AEKey> exportKeys) {
            for (var key : filterKeys) {
                if (!isWirelessIoKeyType(key.getType()) || exportKeys.contains(key)) continue;
                keysByType.computeIfAbsent(key.getType(), ignored -> new ArrayList<>()).add(key);
            }
            // Keep the original order when the energy budget covers only a prefix.
            keysByType.replaceAll((ignored, keys) -> List.copyOf(keys));
        }

        boolean hasKeys() {
            return !keysByType.isEmpty();
        }

        boolean allowsType(AEKeyType type) {
            return keysByType.containsKey(type);
        }

        List<AEKey> keysFor(AEKeyType type) {
            return keysByType.getOrDefault(type, List.of());
        }
    }

    static final class ExportTransferState {
        long untilTick;
        private final TransferCadence schedule = new TransferCadence();

        void accepted(long now, long amount, boolean requestLimited, IOSpeedMode mode) {
            untilTick = now + Math.min(maximumDelay(mode), schedule.success(now, amount, requestLimited));
        }

        void rejected(long now, IOSpeedMode mode) {
            untilTick = now + Math.min(maximumDelay(mode), schedule.blocked(now));
        }

        void unavailable(long now, IOSpeedMode mode) {
            untilTick = now + schedule.unavailable(now, maximumDelay(mode));
        }

        private static int maximumDelay(IOSpeedMode mode) {
            return mode == IOSpeedMode.FAST ? FAST_CD_MAX : NORMAL_CD_MAX;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ConnectionState — AE2 storage bus caches + energy cache
    // ══════════════════════════════════════════════════════════════════════

    static final class ConnectionState {
        final Map<AEKeyType, CooldownTracker> importCDs = new IdentityHashMap<>();
        final Map<AEKeyType, CooldownTracker> exportCDs = new IdentityHashMap<>();
        final Map<AEKey, ExportTransferState> exportTransfers = new HashMap<>();
        final ImportSlotKeyCache importSlotKeys = new ImportSlotKeyCache();

        @Nullable WeakReference<BlockEntity> storageBERef;
        @Nullable Map<AEKeyType, ExternalStorageStrategy> storageStrategies;
        @Nullable Map<AEKeyType, MEStorage> storageWrappers;
        @Nullable BlockCapabilityCache<IItemHandler, Direction> itemHandlerCache;
        long storageWrapperTick = -1;

        CooldownTracker cdFor(AEKeyType type, IoDirection direction) {
            var cds = direction == IoDirection.IMPORT ? importCDs : exportCDs;
            return cds.computeIfAbsent(type, ignored -> new CooldownTracker());
        }

        void resetWirelessIo(IOSpeedMode mode) {
            importCDs.values().forEach(cd -> cd.reset(mode));
            exportCDs.values().forEach(cd -> cd.reset(mode));
            exportTransfers.clear();
            importSlotKeys.clear();
        }

        private ExportTransferState exportState(AEKey key) {
            var existing = exportTransfers.get(key);
            if (existing != null) return existing;
            if (exportTransfers.size() >= EXPORT_TRANSFER_MAX_KEYS) {
                exportTransfers.clear();
            }
            var created = new ExportTransferState();
            exportTransfers.put(key, created);
            return created;
        }

        /**
         * Resolve cached storage wrappers (MEStorage facades from ExternalStorageStrategy).
         * Supports both insert (export) and extract (import) on the same wrappers.
         * Strategy objects are stable (they hold internal BlockCapabilityCache);
         * wrappers are rebuilt every {@link #WRAPPER_REFRESH_TICKS}.
         */
        @Nullable
        Map<AEKeyType, MEStorage> resolveWrappers(
                ServerLevel level, WirelessConnection conn) {
            BlockEntity be = level.getBlockEntity(conn.pos());
            if (be == null) {
                storageBERef = null; storageStrategies = null;
                storageWrappers = null; itemHandlerCache = null;
                importSlotKeys.clear();
                return null;
            }
            if (storageBERef == null || storageBERef.get() != be
                    || storageStrategies == null) {
                storageStrategies = StackWorldBehaviors.createExternalStorageStrategies(
                        level, conn.pos(), conn.boundFace());
                storageBERef = new WeakReference<>(be);
                storageWrappers = null; storageWrapperTick = -1;
                importSlotKeys.clear();
                itemHandlerCache = BlockCapabilityCache.create(
                        Capabilities.ItemHandler.BLOCK, level, conn.pos(), conn.boundFace());
            }
            return refreshWrappers(level.getGameTime());
        }

        @Nullable
        IItemHandler resolveItemHandler() {
            var cache = itemHandlerCache;
            return cache != null ? cache.getCapability() : null;
        }

        @Nullable
        Map<AEKeyType, MEStorage> refreshWrappers(long gt) {
            if (storageStrategies.isEmpty()) return null;
            if (storageWrappers == null
                    || gt < storageWrapperTick || gt - storageWrapperTick >= WRAPPER_REFRESH_TICKS) {
                var map = new IdentityHashMap<AEKeyType, MEStorage>(
                        storageStrategies.size());
                for (var e : storageStrategies.entrySet()) {
                    var w = e.getValue().createWrapper(false, Runnables.doNothing());
                    if (w != null) map.put(e.getKey(), w);
                }
                storageWrappers = map;
                storageWrapperTick = gt;
            }
            return storageWrappers;
        }
    }

    // ── Energy timing wheel ──────────────────────────────────────────────

    private record IoEntryKey(WirelessConnection conn, AEKeyType keyType, IoDirection direction) {}
    private record ExportConfigEntry(AEKey key, long maxAmount) {}

    record ImportBufferFlushResult(
            long lastFlushTick,
            boolean flushLimited,
            int remainingKeys,
            boolean changed,
            int visitedKeys) {}

    static final class ImportBufferFlushState {
        private static final class TypeState {
            int pendingKeys;
            int untestedKeys;
            boolean progressed;
            boolean rejectionPassComplete;
            boolean passStarted;
            Set<AEKey> lateKeys;
        }

        private final Map<AEKeyType, TypeState> types = new IdentityHashMap<>();
        private boolean initialized;
        private boolean previousFlushHadRejection;

        void ensureInitialized(Map<AEKey, Long> importBuffer) {
            if (!initialized) {
                rebuildFrom(importBuffer);
            }
        }

        void rebuildFrom(Map<AEKey, Long> importBuffer) {
            types.clear();
            for (var entry : importBuffer.entrySet()) {
                if (entry.getValue() <= 0) continue;
                var type = entry.getKey().getType();
                types.computeIfAbsent(type, ignored -> new TypeState()).pendingKeys++;
            }
            for (var state : types.values()) {
                state.untestedKeys = state.pendingKeys;
                state.rejectionPassComplete = false;
                state.passStarted = false;
                state.lateKeys = null;
            }
            initialized = true;
            previousFlushHadRejection = false;
        }

        void onBuffered(AEKey key, boolean newKey) {
            initialized = true;
            var type = key.getType();
            var state = types.get(type);
            if (state == null) {
                state = new TypeState();
                state.pendingKeys = 1;
                state.untestedKeys = 1;
                types.put(type, state);
                return;
            }

            if (newKey) {
                state.pendingKeys++;
                if (state.passStarted || state.rejectionPassComplete) {
                    if (state.lateKeys == null) {
                        state.lateKeys = new HashSet<>();
                    }
                    state.lateKeys.add(key);
                } else {
                    state.untestedKeys++;
                }
            }
        }

        void onAttempt(AEKeyType type, AEKey key, boolean progressed, boolean removed) {
            var state = types.computeIfAbsent(type, ignored -> new TypeState());
            state.passStarted = true;
            boolean late = state.lateKeys != null && state.lateKeys.remove(key);
            if (!late && state.untestedKeys > 0) {
                state.untestedKeys--;
            }
            if (removed && state.pendingKeys > 0) {
                state.pendingKeys--;
            }
            if (progressed) {
                state.progressed = true;
            }
        }

        TypeFlushDecision finishType(AEKeyType type) {
            var state = types.get(type);
            if (state == null) {
                return new TypeFlushDecision(false, false);
            }

            boolean progressed = state.progressed;
            boolean rejectionPassComplete = !progressed
                    && state.pendingKeys > 0
                    && state.untestedKeys == 0
                    && (state.lateKeys == null || state.lateKeys.isEmpty());
            if (progressed) {
                if (state.pendingKeys == 0) {
                    types.remove(type);
                } else {
                    state.progressed = false;
                    state.untestedKeys = state.pendingKeys;
                    state.rejectionPassComplete = false;
                    state.passStarted = false;
                    state.lateKeys = null;
                }
            } else if (state.pendingKeys == 0) {
                types.remove(type);
            } else if (rejectionPassComplete) {
                // The current rejection pass has consumed all conservative
                // observation debt. New keys are tracked sparsely, while
                // merged updates to an existing key do not restart the pass
                // or scan the untouched tail. Release the sparse set after
                // the pass has accounted for it.
                state.rejectionPassComplete = true;
                state.lateKeys = null;
            }
            return new TypeFlushDecision(progressed, rejectionPassComplete);
        }

        int untestedKeys() {
            long total = 0;
            for (var state : types.values()) {
                total = Math.min(Integer.MAX_VALUE, total + state.untestedKeys);
            }
            return (int) total;
        }

        boolean previousFlushHadRejection() {
            return previousFlushHadRejection;
        }

        void recordFlush(boolean hadRejection) {
            previousFlushHadRejection = hadRejection;
        }

        void resetPasses() {
            for (var state : types.values()) {
                state.progressed = false;
                state.untestedKeys = state.pendingKeys;
                state.rejectionPassComplete = false;
                state.passStarted = false;
                state.lateKeys = null;
            }
            previousFlushHadRejection = false;
        }

        void clear() {
            types.clear();
            initialized = true;
            previousFlushHadRejection = false;
        }

        record TypeFlushDecision(boolean progressed, boolean rejectionPassComplete) {}
    }

    static final class ImportBackpressureWaiters {
        private final Map<AEKeyType, List<IoScheduledEntry>> byType = new IdentityHashMap<>();

        void park(IoScheduledEntry entry) {
            byType.computeIfAbsent(entry.keyType, ignored -> new ArrayList<>()).add(entry);
        }

        void resumeReady(Map<AEKeyType, Long> locks, long now, List<IoScheduledEntry> due) {
            if (byType.isEmpty()) return;
            var it = byType.entrySet().iterator();
            while (it.hasNext()) {
                var waiting = it.next();
                if (locks.getOrDefault(waiting.getKey(), 0L) > now) continue;
                for (var entry : waiting.getValue()) {
                    due.add(entry);
                }
                it.remove();
            }
        }

        void clear() {
            byType.clear();
        }
    }

    static final class IoScheduledEntry {
        final WirelessConnection conn;
        final ConnectionState state;
        final AEKeyType keyType;
        final IoDirection direction;
        final int generation;

        IoScheduledEntry(WirelessConnection conn, ConnectionState state,
                         AEKeyType keyType, IoDirection direction,
                         int generation) {
            this.conn = conn;
            this.state = state;
            this.keyType = keyType;
            this.direction = direction;
            this.generation = generation;
        }
    }

    private long lastEnergyTickGameTime = -1;

    // ── Connection validation cache ──────────────────────────────────────

    private static final int VALIDATE_INTERVAL = 20;

    private final Map<WirelessConnection, ConnectionState> connectionStates =
            new HashMap<>();
    private final Map<Direction, ConnectionState> normalConnectionStates =
            new EnumMap<>(Direction.class);
    private List<WirelessConnection> validConnectionsCache = List.of();
    private long    validConnectionsCacheTick = -1;
    private boolean connectionsDirty = true;
    private int invalidConnectionScanCursor;

    /**
     * Snapshot of the current valid wireless connections projected as
     * {@link WirelessEnergyAPI.Target} records. Rebuilt in lockstep with
     * {@link #validConnectionsCache} so the shared NORMAL-mode distributor
     * sees the exact same set the IO wheel uses.
     */
    private List<WirelessEnergyAPI.Target> validEnergyTargetsCache = List.of();
    /**
     * Monotonic stamp; bumped whenever {@link #validEnergyTargetsCache}
     * actually changes. The distributor uses this as an O(1)
     * cache-invalidation key (see {@link WirelessEnergyDistributor.Host}).
     */
    private int validEnergyTargetsVersion;

    @SuppressWarnings("unchecked")
    private final List<IoScheduledEntry>[] ioWheel = new ArrayList[IO_WHEEL_SLOTS];
    { for (int i = 0; i < IO_WHEEL_SLOTS; i++) ioWheel[i] = new ArrayList<>(); }
    private final Map<IoEntryKey, IoScheduledEntry> ioEntries = new HashMap<>();
    private final List<IoScheduledEntry> dueIoEntries = new ArrayList<>();
    private final ImportBackpressureWaiters importBackpressureWaiters = new ImportBackpressureWaiters();
    private long lastIOWheelTick = -1;
    private long lastIOEntryRefreshTick = Long.MIN_VALUE;
    private int ioScheduleGeneration = 1;
    private boolean ioWheelDirty = true;

    // ── Instance fields ──────────────────────────────────────────────────

    private InterfaceMode interfaceMode = InterfaceMode.NORMAL;
    private IOSpeedMode   ioSpeedMode   = IOSpeedMode.NORMAL;
    private ExportMode    exportMode    = ExportMode.OFF;
    private ImportMode    importMode    = ImportMode.OFF;
    private @Nullable Direction energyOutputDir = null;
    private final boolean[] unlimitedSlots = new boolean[SLOT_COUNT];
    private final List<WirelessConnection> connections = new ArrayList<>();
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);
    private final IActionSource machineSource = IActionSource.ofMachine(this);
    private final InternalInventoryHost filterInvHost = new InternalInventoryHost() {
        @Override
        public void saveChangedInventory(AppEngInternalInventory inv) {
            saveChanges(); markForUpdate();
        }

        @Override
        public void onChangeInventory(AppEngInternalInventory inv, int slot) {
            rebuildFilter();
        }

        @Override
        public boolean isClientSide() {
            return level != null && level.isClientSide();
        }
    };
    private final AppEngInternalInventory filterInv = new AppEngInternalInventory(filterInvHost, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof OverloadedFilterComponentItem;
        }
    };
    private @Nullable GenericInternalInventory exposedGenericInv;
    /** Shared NORMAL-mode distributor (32-slot adaptive wheel + cap listeners). */
    private final WirelessEnergyDistributor wirelessDistributor =
            new WirelessEnergyDistributor(new DistributorHost());
    private @Nullable Set<AEKey> importFilterKeys;
    private @Nullable FuzzyMode importFilterFuzzyMode;
    private boolean importFilterInverted;
    private @Nullable ExactImportPlan exactImportPlan;
    private boolean inductionCardCacheDirty = true;
    private boolean inductionCardInstalledCache = false;
    private boolean unloadingChunk = false;
    private transient int lastViewedPage = 0;

    public int getLastViewedPage() { return lastViewedPage; }
    public void setLastViewedPage(int p) { lastViewedPage = p; }

    // ── Constructors + basic overrides ────────────────────────────────────

    public OverloadedInterfaceBlockEntity(BlockEntityType<?> betype,
                                          BlockPos pos, BlockState state) {
        super(betype, pos, state);
    }

    public OverloadedInterfaceBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.OVERLOADED_INTERFACE.get(), pos, state);
    }

    @Override
    public FrequencyBindingHelper getFrequencyBinding() {
        return frequencyBinding;
    }

    @Override
    public AENetworkedBlockEntity getFrequencyBindingBlockEntity() {
        return this;
    }

    @Override
    public void saveFrequencyBindingChanges() {
        saveChanges();
    }

    @Override
    public void markFrequencyBindingForUpdate() {
        markForUpdate();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        unloadingChunk = false;
        recomputeIdlePower();
        if (level != null && !level.isClientSide() && importMode == ImportMode.EJECT) {
            refreshEjectRegistrations();
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        frequencyBinding.onMainNodeStateChanged(reason);
    }

    @Override
    protected InterfaceLogic createLogic() {
        return new OverloadedInterfaceLogic(getMainNode(), this,
                getItemFromBlockEntity().asItem(), SLOT_COUNT);
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        if (level instanceof ServerLevel) {
            clearInvalidConnections();
        }
        MenuOpener.open(OverloadedInterfaceMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(net.minecraft.world.entity.player.Player player,
                                  appeng.menu.ISubMenu subMenu) {
        MenuOpener.returnTo(OverloadedInterfaceMenu.TYPE, player, subMenu.getLocator());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    // ── Exposed generic inventory (pipes / eject forwarding) ─────────────

    /**
     * Capability-facing view of the proxied storage. Passive insertions
     * (pipes, eject-mode forwarding) go through the filter component;
     * internal paths (crafting returns, GUI) use the proxy directly.
     */
    public @Nullable GenericInternalInventory getExposedGenericInv() {
        if (exposedGenericInv == null
                && getInterfaceLogic() instanceof OverloadedInterfaceLogic ol) {
            exposedGenericInv = new FilteredInsertGenericInv(
                    ol.getProxiedStorage(), this::isInsertAllowedByFilter);
        }
        return exposedGenericInv;
    }

    public AppEngInternalInventory getFilterInv() {
        return filterInv;
    }

    public void rebuildFilter() {
        exactImportPlan = null;
        // 过滤器变动(无论是清空还是重填)都唤醒 IO:避免过滤器刚改完还卡在空转退避
        wakeWirelessIo();
        ItemStack filterStack = filterInv.getStackInSlot(0);
        if (filterStack.isEmpty()
                || !(filterStack.getItem() instanceof ICellWorkbenchItem cwi)) {
            importFilterKeys = null;
            importFilterFuzzyMode = null;
            importFilterInverted = false;
            return;
        }
        var config = cwi.getConfigInventory(filterStack);
        var keys = new HashSet<AEKey>();
        for (int i = 0; i < config.size(); i++) {
            var k = config.getKey(i); if (k != null) keys.add(k);
        }
        if (keys.isEmpty()) {
            importFilterKeys = null;
            importFilterFuzzyMode = null;
            importFilterInverted = false;
            return;
        }

        var upgrades = cwi.getUpgrades(filterStack);
        boolean hasFuzzy = upgrades.getInstalledUpgrades(AEItems.FUZZY_CARD) > 0;
        boolean hasInverter = upgrades.getInstalledUpgrades(AEItems.INVERTER_CARD) > 0;
        importFilterKeys = Set.copyOf(keys);
        importFilterFuzzyMode = hasFuzzy ? cwi.getFuzzyMode(filterStack) : null;
        importFilterInverted = hasInverter;
    }

    // ── Mode accessors ───────────────────────────────────────────────────

    public InterfaceMode getInterfaceMode() { return interfaceMode; }
    public void setInterfaceMode(InterfaceMode m) {
        if (interfaceMode == m) return; interfaceMode = m;
        invalidateConnectionCache(); refreshEjectRegistrations();
        recomputeIdlePower();
        saveChanges(); markForUpdate();
    }

    public IOSpeedMode getIOSpeedMode() { return ioSpeedMode; }
    public void setIOSpeedMode(IOSpeedMode m) {
        if (ioSpeedMode == m) return; ioSpeedMode = m;
        wakeWirelessIo();
        recomputeIdlePower();
        saveChanges(); markForUpdate();
    }

    public ExportMode getExportMode() { return exportMode; }
    public void setExportMode(ExportMode m) {
        if (exportMode == m) return; exportMode = m;
        wakeWirelessIo(); saveChanges(); markForUpdate();
    }

    public ImportMode getImportMode() { return importMode; }
    public void setImportMode(ImportMode m) {
        if (importMode == m) return;
        var old = importMode; importMode = m;
        if ((old == ImportMode.EJECT) != (m == ImportMode.EJECT)) refreshEjectRegistrations();
        wakeWirelessIo();
        saveChanges(); markForUpdate();
    }

    public boolean isSlotUnlimited(int slot) {
        return slot >= 0 && slot < SLOT_COUNT && unlimitedSlots[slot];
    }
    public void setSlotUnlimited(int slot, boolean unlimited) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        if (unlimitedSlots[slot] == unlimited) return;
        unlimitedSlots[slot] = unlimited;
        invalidateExportConfigCache();
        saveChanges(); markForUpdate();
    }

    public void onGridIoConfigChanged() {
        invalidateExportConfigCache();
        wakeWirelessIo();
    }

    public @Nullable Direction getEnergyOutputDir() { return energyOutputDir; }
    public void setEnergyOutputDir(@Nullable Direction d) {
        if (energyOutputDir == d) return; energyOutputDir = d;
        normalConnectionStates.clear();
        saveChanges(); markForUpdate();
    }

    public void invalidateInductionCardCache() {
        inductionCardCacheDirty = true;
        wakeWirelessIo();
    }

    void recomputeIdlePower() {
        double idle = IDLE_BASE;
        if (interfaceMode == InterfaceMode.WIRELESS) {
            idle += IDLE_WIRELESS_BONUS;
            idle += connections.size() * IDLE_PER_CONNECTION;
        }
        if (ioSpeedMode == IOSpeedMode.FAST) {
            idle *= IDLE_FAST_MULTIPLIER;
        }
        getMainNode().setIdlePowerUsage(idle);
    }

    // ── Wireless connections ─────────────────────────────────────────────

    public List<WirelessConnection> getConnections() { return Collections.unmodifiableList(connections); }

    public boolean addOrUpdateConnection(WirelessConnection conn) {
        if (!isLocalDimension(conn.dimension())) {
            return false;
        }
        int index = indexOfConnectionEndpoint(conn.dimension(), conn.pos(), conn.boundFace());
        if (index >= 0) {
            if (connections.get(index).equals(conn)) {
                return true;
            }
            connections.set(index, conn);
            invalidConnectionScanCursor = 0;
            invalidateConnectionCache(); refreshEjectRegistrations();
            recomputeIdlePower();
            saveChanges(); markForUpdate();
            return true;
        }
        if (connections.size() >= MAX_WIRELESS_CONNECTIONS) {
            return false;
        }
        connections.add(conn);
        invalidConnectionScanCursor = 0;
        invalidateConnectionCache(); refreshEjectRegistrations();
        recomputeIdlePower();
        saveChanges(); markForUpdate();
        return true;
    }

    public boolean removeConnection(ResourceKey<Level> dim, BlockPos pos, Direction face) {
        int index = indexOfConnectionEndpoint(dim, pos, face);
        if (index < 0) {
            return false;
        }
        connections.remove(index);
        invalidConnectionScanCursor = 0;
        invalidateConnectionCache(); refreshEjectRegistrations();
        recomputeIdlePower();
        saveChanges(); markForUpdate();
        return true;
    }

    public boolean removeConnection(ResourceKey<Level> dim, BlockPos pos) {
        int index = WirelessConnectionLists.indexOf(connections, dim, pos);
        if (index < 0) {
            return false;
        }
        connections.remove(index);
        invalidConnectionScanCursor = 0;
        invalidateConnectionCache(); refreshEjectRegistrations();
        recomputeIdlePower();
        saveChanges(); markForUpdate();
        return true;
    }

    private int indexOfConnectionEndpoint(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        return ConnectionEndpoints.indexOfEndpoint(
                connections,
                dimension,
                pos,
                face,
                WirelessConnection::dimension,
                WirelessConnection::pos,
                WirelessConnection::boundFace);
    }

    public int clearInvalidConnections() {
        return pruneInvalidConnections(Integer.MAX_VALUE);
    }

    public int pruneInvalidConnections(int maxChecks) {
        if (!(level instanceof ServerLevel serverLevel) || maxChecks <= 0 || connections.isEmpty()) {
            return 0;
        }

        var result = WirelessConnectionLists.pruneInvalid(
                connections, invalidConnectionScanCursor, maxChecks,
                serverLevel, getBlockPos());
        invalidConnectionScanCursor = result.nextCursor();
        if (result.removed() > 0) {
            invalidateConnectionCache();
            refreshEjectRegistrations();
            recomputeIdlePower();
            saveChanges();
            markForUpdate();
        }
        return result.removed();
    }

    private void tickWirelessConnectionCleanup(ServerLevel level) {
        if (connections.isEmpty()
                || !WirelessConnectionValidator.shouldRunPeriodicPrune(level, getBlockPos())) {
            return;
        }
        pruneInvalidConnections(WirelessConnectionValidator.PERIODIC_PRUNE_MAX_CHECKS);
    }

    private boolean isLocalDimension(ResourceKey<Level> dimension) {
        return WirelessConnectionLists.isLocalDimension(level, dimension);
    }

    // ── Connection state management ──────────────────────────────────────

    private ConnectionState getOrCreateState(WirelessConnection conn) {
        return connectionStates.computeIfAbsent(conn, k -> new ConnectionState());
    }

    private void invalidateConnectionCache() {
        connectionsDirty = true;
        validConnectionsCache = List.of(); validConnectionsCacheTick = -1;
        if (!validEnergyTargetsCache.isEmpty()) {
            validEnergyTargetsCache = List.of();
        }
        validEnergyTargetsVersion++;
        connectionStates.clear();
        normalConnectionStates.clear();
        resetIOWheel();
        wirelessDistributor.clearTickState(true);
        alertGridTicker();
    }

    private void resetIOWheel() {
        for (var slot : ioWheel) {
            slot.clear();
        }
        dueIoEntries.clear();
        ioEntries.clear();
        importBackpressureWaiters.clear();
        lastIOWheelTick = -1;
        lastIOEntryRefreshTick = Long.MIN_VALUE;
        ioScheduleGeneration++;
        ioWheelDirty = true;
    }

    private void wakeWirelessIo() {
        for (var state : connectionStates.values()) {
            state.resetWirelessIo(ioSpeedMode);
        }
        for (var state : normalConnectionStates.values()) {
            state.resetWirelessIo(ioSpeedMode);
        }
        keyTypeLockUntil.clear();
        importBufferFlushState.resetPasses();
        resetIOWheel();
        alertGridTicker();
    }

    private void alertGridTicker() {
        getMainNode().ifPresent((grid, node) ->
                grid.getTickManager().alertDevice(node));
    }

    private void invalidateExportConfigCache() {
        exactImportPlan = null;
        exportConfigCache.clear();
        exportConfigCacheTick = Long.MIN_VALUE;
        exportConfigCacheValid = false;
        exportBlacklistCache = Set.of();
        exportBlacklistTick = -1;
        alertGridTicker();
    }

    private List<WirelessConnection> getOrRefreshValidConnections(
            ServerLevel sl, long gameTick) {
        if (!connectionsDirty
                && gameTick - validConnectionsCacheTick < VALIDATE_INTERVAL)
            return validConnectionsCache;
        clearInvalidConnections();
        var valid = new ArrayList<WirelessConnection>();
        for (var c : connections) {
            if (WirelessConnectionValidator.validate(sl, getBlockPos(), c)
                    == WirelessConnectionValidator.Status.VALID) {
                valid.add(c);
            }
        }
        var newCache = List.copyOf(valid);
        if (!newCache.equals(validConnectionsCache)) {
            validConnectionsCache = newCache;
            rebuildEnergyTargets();
        }
        validConnectionsCacheTick = gameTick; connectionsDirty = false;
        return validConnectionsCache;
    }

    /**
     * Project the current valid wireless connections into a list of
     * {@link WirelessEnergyAPI.Target} records and bump the version stamp so
     * the shared distributor refreshes its per-target caches on the next
     * tick.
     */
    private void rebuildEnergyTargets() {
        if (validConnectionsCache.isEmpty()) {
            validEnergyTargetsCache = List.of();
        } else {
            var snapshot = new ArrayList<WirelessEnergyAPI.Target>(validConnectionsCache.size());
            for (var conn : validConnectionsCache) {
                snapshot.add(new WirelessEnergyAPI.Target(
                        conn.dimension(), conn.pos(), conn.boundFace()));
            }
            validEnergyTargetsCache = List.copyOf(snapshot);
        }
        validEnergyTargetsVersion++;
    }

    @Nullable
    private ServerLevel resolveTargetLevel(
            ServerLevel origin, WirelessConnection conn) {
        if (!conn.dimension().equals(origin.dimension())) return null;
        if (!WirelessConnectionRange.isConnectorLinkInRange(
                origin.dimension(), getBlockPos(), conn.dimension(), conn.pos())) {
            return null;
        }
        var tl = origin.getServer().getLevel(conn.dimension());
        return (tl != null && tl.isLoaded(conn.pos())) ? tl : null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Server tick
    // ══════════════════════════════════════════════════════════════════════

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   OverloadedInterfaceBlockEntity be) {
        if (level == null || level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;
        be.frequencyBinding.serverTick();
        be.tickWirelessConnectionCleanup(sl);
        if (!be.hasServerEnergyWork()) return;

        // Energy stays on the block-entity ticker. Item I/O is driven by the
        // existing AE2 IGridTickable service so it follows node/channel activity.
        be.tickEnergyTransfer(sl);
    }

    private boolean hasServerEnergyWork() {
        boolean wirelessMode = interfaceMode == InterfaceMode.WIRELESS;
        boolean hasConnections = !connections.isEmpty();
        boolean hasEnergyOutput = energyOutputDir != null;
        boolean hasFeKey = AppFluxHelper.FE_KEY != null;
        boolean mayTransferEnergy = (wirelessMode && hasConnections) || hasEnergyOutput;
        boolean hasInduction = mayTransferEnergy && hasFeKey && hasInductionCard();

        return OverloadedInterfaceTickDecider.hasServerEnergyWork(
                wirelessMode,
                hasConnections,
                hasEnergyOutput,
                hasFeKey,
                hasInduction);
    }

    public boolean hasGridItemIoWork() {
        return OverloadedInterfaceTickDecider.hasGridItemIoWork(
                interfaceMode == InterfaceMode.WIRELESS,
                !importBuffer.isEmpty(),
                !connections.isEmpty(),
                hasAutoImportWork(),
                exportMode == ExportMode.AUTO);
    }

    public void tickGridItemIo() {
        if (!(level instanceof ServerLevel sl) || !getMainNode().isActive()) {
            return;
        }
        if (interfaceMode == InterfaceMode.WIRELESS) {
            if (WirelessIoPerformanceProbe.shouldMeasureIoBody()) {
                long started = System.nanoTime();
                try {
                    tickWirelessIO(sl);
                } finally {
                    WirelessIoPerformanceProbe.recordWirelessInterfaceIo(
                            System.nanoTime() - started,
                            connections.size(),
                            ioSpeedMode == IOSpeedMode.FAST);
                }
            } else {
                tickWirelessIO(sl);
            }
        } else {
            tickNormalIO(sl);
        }
    }

    private void tickNormalIO(ServerLevel sl) {
        if (interfaceMode != InterfaceMode.NORMAL) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;

        long now = sl.getGameTime();
        var meStorage = grid.getStorageService().getInventory();
        var source = machineSource;

        flushImportBuffer(meStorage, source, now);

        boolean activeImport = hasAutoImportWork();
        boolean activeExport = exportMode == ExportMode.AUTO;
        if (!activeImport && !activeExport) return;

        for (var direction : normalIoDirections()) {
            var conn = new WirelessConnection(
                    sl.dimension(),
                    getBlockPos().relative(direction),
                    direction.getOpposite());
            var state = normalConnectionStates.computeIfAbsent(
                    direction, ignored -> new ConnectionState());
            var wrappers = state.resolveWrappers(sl, conn);
            if (wrappers == null) continue;

            for (var wrapperEntry : wrappers.entrySet()) {
                var keyType = wrapperEntry.getKey();
                if (!isWirelessIoKeyType(keyType)) continue;
                var wrapper = wrapperEntry.getValue();
                if (activeImport && isImportKeyTypeAllowed(keyType)) {
                    runNormalImportIfDue(state, keyType, wrapper, source, now);
                }
                if (activeExport) {
                    runNormalExportIfDue(state, keyType, wrapper, meStorage, source, now);
                }
            }
        }
    }

    private List<Direction> normalIoDirections() {
        if (OverloadedInterfaceTickDecider.normalIoDirectionCount(energyOutputDir != null) == 1
                && energyOutputDir != null) {
            return List.of(energyOutputDir);
        }
        return ALL_NORMAL_IO_DIRECTIONS;
    }

    private void runNormalImportIfDue(ConnectionState state, AEKeyType keyType,
                                      MEStorage wrapper, IActionSource source, long now) {
        var cd = state.cdFor(keyType, IoDirection.IMPORT);
        if (cd.cooldownUntil() > now) return;

        long locked = lockedUntil(keyType, now);
        if (locked > now) {
            // Keep the due deadline so a successful flush resumes immediately.
            return;
        }

        runExtract(state, keyType, wrapper, source, now, IMPORT_TRANSFER_LIMIT);
    }

    private void runNormalExportIfDue(ConnectionState state, AEKeyType keyType,
                                      MEStorage wrapper, MEStorage meStorage,
                                      IActionSource source, long now) {
        var cd = state.cdFor(keyType, IoDirection.EXPORT);
        if (cd.cooldownUntil() > now) return;

        runExport(state, keyType, wrapper, meStorage, source, now);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Wireless I/O — timing wheel, per-keytype cooldown, persistent buffer
    // ══════════════════════════════════════════════════════════════════════

    private void tickWirelessIO(ServerLevel sl) {
        if (interfaceMode != InterfaceMode.WIRELESS) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;

        long now = sl.getGameTime();
        var meStorage = grid.getStorageService().getInventory();
        var source = machineSource;

        flushImportBuffer(meStorage, source, now);

        boolean activeImport = hasAutoImportWork();
        boolean activeExport = exportMode == ExportMode.AUTO;
        if (!activeImport && !activeExport) return;

        var valid = getOrRefreshValidConnections(sl, now);
        if (valid.isEmpty()) return;

        refreshIOWheel(sl, valid, now, activeImport, activeExport);
        pollIOWheel(now);
        importBackpressureWaiters.resumeReady(keyTypeLockUntil, now, dueIoEntries);

        for (var entry : dueIoEntries) {
            if (!isEntryStillValid(entry)) continue;

            if (entry.direction == IoDirection.IMPORT && lockedUntil(entry.keyType, now) > now) {
                importBackpressureWaiters.park(entry);
                continue;
            }

            var targetLevel = resolveTargetLevel(sl, entry.conn);
            if (targetLevel == null) {
                entry.state.cdFor(entry.keyType, entry.direction).onFail(now, ioSpeedMode);
                rescheduleEntry(entry, now);
                continue;
            }

            var wrappers = entry.state.resolveWrappers(targetLevel, entry.conn);
            var wrapper = wrappers != null ? wrappers.get(entry.keyType) : null;
            if (wrapper == null) {
                entry.state.cdFor(entry.keyType, entry.direction).onFail(now, ioSpeedMode);
                rescheduleEntry(entry, now);
                continue;
            }

            if (entry.direction == IoDirection.IMPORT) {
                runExtract(entry.state, entry.keyType, wrapper, source, now, IMPORT_TRANSFER_LIMIT);
            } else {
                runExport(entry.state, entry.keyType, wrapper, meStorage, source, now);
            }

            rescheduleEntry(entry, now);
        }
        dueIoEntries.clear();
    }

    private void refreshIOWheel(ServerLevel sl, List<WirelessConnection> valid,
                                long now, boolean activeImport, boolean activeExport) {
        if (!ioWheelDirty && lastIOEntryRefreshTick == now) return;
        if (ioWheelDirty || lastIOEntryRefreshTick == Long.MIN_VALUE
                || now < lastIOEntryRefreshTick
                || now - lastIOEntryRefreshTick >= WRAPPER_REFRESH_TICKS) {
            refreshIOEntries(sl, valid, 0, valid.size(), now, activeImport, activeExport);
        } else {
            // Discovery has the same 20-tick bound; ordinary due transfers below
            // still execute every tick. Catch up only the slices actually missed.
            for (long tick = lastIOEntryRefreshTick + 1; tick <= now; tick++) {
                int phase = (int) Math.floorMod(tick, WRAPPER_REFRESH_TICKS);
                int start = (int) ((long) phase * valid.size() / WRAPPER_REFRESH_TICKS);
                int end = (int) ((long) (phase + 1) * valid.size() / WRAPPER_REFRESH_TICKS);
                refreshIOEntries(sl, valid, start, end, now, activeImport, activeExport);
            }
        }
        lastIOEntryRefreshTick = now;
        ioWheelDirty = false;
    }

    private void refreshIOEntries(ServerLevel sl, List<WirelessConnection> valid,
                                   int start, int end, long now,
                                   boolean activeImport, boolean activeExport) {
        for (int index = start; index < end; index++) {
            var conn = valid.get(index);
            var state = getOrCreateState(conn);
            var targetLevel = resolveTargetLevel(sl, conn);
            if (targetLevel == null) continue;
            // Align wrapper refresh and type discovery in the same slice.
            // Separate deadlines could take 20 + 20 ticks to discover a type.
            state.storageWrappers = null;
            var wrappers = state.resolveWrappers(targetLevel, conn);
            if (wrappers == null) continue;
            for (var keyType : wrappers.keySet()) {
                if (!isWirelessIoKeyType(keyType)) continue;
                if (activeImport && isImportKeyTypeAllowed(keyType)) {
                    ensureIOEntry(conn, state, keyType, IoDirection.IMPORT, now);
                }
                if (activeExport) {
                    ensureIOEntry(conn, state, keyType, IoDirection.EXPORT, now);
                }
            }
        }
    }

    private void ensureIOEntry(WirelessConnection conn, ConnectionState state,
                               AEKeyType keyType, IoDirection direction, long now) {
        var key = new IoEntryKey(conn, keyType, direction);
        if (ioEntries.containsKey(key)) return;
        var entry = new IoScheduledEntry(conn, state, keyType, direction, ioScheduleGeneration);
        entry.state.cdFor(keyType, direction).reset(ioSpeedMode);
        ioEntries.put(key, entry);
        scheduleEntryAt(entry, now + 1);
    }

    private static boolean isWirelessIoKeyType(AEKeyType keyType) {
        if (AppFluxHelper.FE_KEY != null && keyType == AppFluxHelper.FE_KEY.getType()) {
            return false;
        }
        return true;
    }

    private void pollIOWheel(long now) {
        dueIoEntries.clear();
        long start;
        if (lastIOWheelTick < 0) {
            start = now;
        } else if (now - lastIOWheelTick >= IO_WHEEL_SLOTS) {
            start = now - IO_WHEEL_SLOTS + 1;
        } else {
            start = lastIOWheelTick + 1;
        }
        for (long tick = start; tick <= now; tick++) {
            var slot = ioWheel[(int) (tick % IO_WHEEL_SLOTS)];
            if (slot.isEmpty()) continue;
            dueIoEntries.addAll(slot);
            slot.clear();
        }
        lastIOWheelTick = now;
    }

    private boolean isEntryStillValid(IoScheduledEntry entry) {
        if (entry.generation != ioScheduleGeneration) return false;
        if (!isWirelessIoKeyType(entry.keyType)) return false;
        if (connectionStates.get(entry.conn) != entry.state) return false;
        if (entry.direction == IoDirection.IMPORT) return hasAutoImportWork() && isImportKeyTypeAllowed(entry.keyType);
        return exportMode == ExportMode.AUTO;
    }

    private void scheduleEntryAt(IoScheduledEntry entry, long dueTick) {
        long target = Math.max(1, dueTick);
        ioWheel[(int) (target % IO_WHEEL_SLOTS)].add(entry);
    }

    private void rescheduleEntry(IoScheduledEntry entry, long now) {
        var cd = entry.state.cdFor(entry.keyType, entry.direction);
        scheduleEntryAt(entry, Math.max(now + 1, cd.cooldownUntil()));
    }

    // ── Import: remote wrapper.extract → persistent import buffer ────────

    private long runExtract(ConnectionState state, AEKeyType keyType, MEStorage wrapper,
                            IActionSource src, long now, long transferLimit) {
        var exactFilterKeys = getExactImportFilterKeys(keyType);
        long moved;
        if (exactFilterKeys != null) {
            moved = extractExactImportKeys(keyType, wrapper, src, transferLimit, exactFilterKeys);
        } else {
            // Custom MEStorage wrappers keep their own transfer contract.
            var handler = keyType == AEKeyType.items() && wrapper instanceof ExternalStorageFacade
                    ? state.resolveItemHandler() : null;
            moved = handler != null
                    ? scanItemHandlerSlots(handler, state.importSlotKeys, transferLimit)
                    : scanImportKeys(keyType, wrapper, src, transferLimit);
        }

        var cd = state.cdFor(keyType, IoDirection.IMPORT);
        if (moved > 0) {
            saveImportBufferChanges(now);
            cd.onSuccess(now, ioSpeedMode);
        } else {
            cd.onFail(now, ioSpeedMode);
        }
        return moved;
    }

    private long extractExactImportKeys(AEKeyType keyType, MEStorage wrapper,
                                                IActionSource src, long transferLimit,
                                                List<AEKey> exactFilterKeys) {
        long budget = transferLimit;
        long moved = 0;
        for (var key : exactFilterKeys) {
            if (budget <= 0) break;
            if (key.getType() != keyType || !isImportAllowed(key)) continue;
            long available = wrapper.extract(key, budget,
                    Actionable.SIMULATE, src);
            if (available <= 0) continue;
            long extracted = importExtractToBuffer(key, Math.min(available, budget), wrapper, src);
            moved += extracted;
            budget -= extracted;
        }
        return moved;
    }

    private long scanImportKeys(AEKeyType keyType, MEStorage wrapper, IActionSource src,
                                long transferLimit) {
        var buffer = scanBuffer.acquire();
        try {
            wrapper.getAvailableStacks(buffer);
            long budget = transferLimit;
            long moved = 0;
            for (var available : buffer) {
                if (budget <= 0) break;
                var key = available.getKey();
                if (key.getType() != keyType || !isImportAllowed(key)) continue;
                long amount = available.getLongValue();
                if (amount <= 0) continue;
                long extracted = importExtractToBuffer(key, Math.min(amount, budget), wrapper, src);
                moved += extracted;
                budget -= extracted;
            }
            return moved;
        } finally {
            scanBuffer.release(buffer);
        }
    }

    private long importExtractToBuffer(AEKey key, long amount, MEStorage wrapper, IActionSource src) {
        if (amount <= 0) return 0;
        var grid = getMainNode().getGrid();
        long affordable = PowerCostUtil.maxAffordable(grid, key, amount);
        if (affordable <= 0) return 0;
        long extracted = wrapper.extract(key, affordable, Actionable.MODULATE, src);
        if (extracted > 0) {
            PowerCostUtil.consume(grid, key, extracted);
            addToImportBuffer(key, extracted);
        }
        return extracted;
    }

    @Nullable
    private ExactImportPlan getExactImportPlan() {
        if (importFilterKeys == null || importFilterInverted || importFilterFuzzyMode != null) return null;
        if (exactImportPlan == null) {
            exactImportPlan = new ExactImportPlan(importFilterKeys, getExportBlacklist());
        }
        return exactImportPlan;
    }

    @Nullable
    private List<AEKey> getExactImportFilterKeys(AEKeyType keyType) {
        var plan = getExactImportPlan();
        return plan != null ? plan.keysFor(keyType) : null;
    }

    private boolean hasAutoImportWork() {
        if (importMode != ImportMode.AUTO) return false;
        var plan = getExactImportPlan();
        return plan == null || plan.hasKeys();
    }

    private boolean isImportKeyTypeAllowed(AEKeyType keyType) {
        var plan = getExactImportPlan();
        return plan == null || plan.allowsType(keyType);
    }

    /** Walk each live item slot once instead of rescanning all slots per key. */
    private long scanItemHandlerSlots(IItemHandler handler, ImportSlotKeyCache cache, long transferLimit) {
        long budget = transferLimit;
        long moved = 0;
        int slots = handler.getSlots();
        cache.prepareSlots(slots);
        for (int slot = 0; slot < slots && budget > 0; slot++) {
            var stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            var key = cache.keyForSlot(slot, stack);
            if (key == null || !isImportAllowed(key)) continue;
            long extracted = importExtractSlotToBuffer(handler, slot, key, Math.min(stack.getCount(), budget));
            moved += extracted;
            budget -= extracted;
        }
        return moved;
    }

    private long importExtractSlotToBuffer(IItemHandler handler, int slot, AEItemKey key, long amount) {
        var grid = getMainNode().getGrid();
        long affordable = PowerCostUtil.maxAffordable(grid, key, amount);
        if (affordable <= 0) return 0;
        long extracted = extractSlotForKey(handler, slot, key, affordable, this::addToImportBuffer);
        if (extracted > 0) {
            PowerCostUtil.consume(grid, key, extracted);
        }
        return extracted;
    }

    static long extractSlotForKey(IItemHandler handler, int slot, AEItemKey key, long amount,
                                  BiConsumer<AEKey, Long> sink) {
        long extracted = 0;
        while (extracted < amount) {
            int request = (int) Math.min(Integer.MAX_VALUE, amount - extracted);
            var taken = handler.extractItem(slot, request, false);
            if (taken.isEmpty()) break;
            int count = Math.min(taken.getCount(), request);
            var takenKey = key.matches(taken) ? key : AEItemKey.of(taken);
            sink.accept(takenKey, (long) count);
            extracted += count;
            // A legitimate handler may cap each call below max stack size.
            // Re-read only for a continuation: do not mistake that cap for an
            // empty slot, or keep draining a replacement key under the old
            // filter decision after a container callback changed the slot.
            if (extracted < amount && !key.matches(handler.getStackInSlot(slot))) break;
        }
        return extracted;
    }

    private Set<AEKey> exportBlacklistCache = Set.of();
    private long exportBlacklistTick = -1;

    private Set<AEKey> getExportBlacklist() {
        if (level != null && level.getGameTime() == exportBlacklistTick) {
            return exportBlacklistCache;
        }
        var config = getInterfaceLogic().getConfig();
        var set = new HashSet<AEKey>();
        for (int i = 0; i < config.size(); i++) {
            var key = config.getKey(i);
            if (key != null) set.add(key);
        }
        exportBlacklistCache = set;
        if (level != null) {
            exportBlacklistTick = level.getGameTime();
        }
        return set;
    }

    private boolean isImportAllowed(AEKey key) {
        if (getExportBlacklist().contains(key)) return false;
        return isInsertAllowedByFilter(key);
    }

    /**
     * Filter-component check alone (no export blacklist — eject may legally
     * return items that are also configured for stocking).
     */
    public boolean isInsertAllowedByFilter(AEKey key) {
        var keys = importFilterKeys;
        if (keys == null || keys.isEmpty()) return true;
        var fuzzyMode = importFilterFuzzyMode;
        boolean matches;
        if (fuzzyMode == null) {
            matches = keys.contains(key);
        } else {
            matches = false;
            for (var filterKey : keys) {
                if (key.equals(filterKey) || key.fuzzyEquals(filterKey, fuzzyMode)) {
                    matches = true;
                    break;
                }
            }
        }
        return matches != importFilterInverted;
    }

    // ── Export: ME.extract → remote wrapper.insert, overflow → buffer ────

    private long runExport(ConnectionState state, AEKeyType keyType, MEStorage wrapper,
                           MEStorage me, IActionSource src, long now) {
        var entries = exportEntriesForType(keyType, now);
        long moved = 0;
        long next = Long.MAX_VALUE;
        for (var entry : entries) {
            // One state lookup per key, including deriving the type's deadline.
            var transfer = state.exportState(entry.key());
            if (now >= transfer.untilTick) {
                moved += exportKey(transfer, entry, wrapper, me, src, now);
            }
            next = Math.min(next, transfer.untilTick);
        }
        var cd = state.cdFor(keyType, IoDirection.EXPORT);
        if (entries.isEmpty()) {
            cd.onFail(now, ioSpeedMode);
        } else {
            cd.cooldownUntil = Math.max(now + 1, next);
        }
        return moved;
    }

    private long exportKey(ExportTransferState transfer, ExportConfigEntry entry,
                           MEStorage wrapper, MEStorage me, IActionSource src, long now) {
        var key = entry.key();
        long available = me.extract(key, entry.maxAmount(), Actionable.SIMULATE, src);
        if (available <= 0) {
            transfer.unavailable(now, ioSpeedMode);
            return 0;
        }
        long requested = Math.min(entry.maxAmount(), available);
        long canAccept = wrapper.insert(key, requested, Actionable.SIMULATE, src);
        if (canAccept <= 0) {
            transfer.rejected(now, ioSpeedMode);
            return 0;
        }
        long target = Math.min(requested, canAccept);
        var grid = getMainNode().getGrid();
        long affordable = PowerCostUtil.maxAffordable(grid, key, target);
        if (affordable <= 0) {
            transfer.unavailable(now, ioSpeedMode);
            return 0;
        }
        long extracted = me.extract(key, affordable, Actionable.MODULATE, src);
        if (extracted <= 0) {
            transfer.unavailable(now, ioSpeedMode);
            return 0;
        }
        long inserted = wrapper.insert(key, extracted, Actionable.MODULATE, src);
        if (inserted > 0) {
            PowerCostUtil.consume(grid, key, inserted);
            // Equal acceptance of a bounded request does not establish fullness.
            // Only target-limited samples may infer a longer refill horizon.
            boolean requestLimited = canAccept >= requested || affordable < target || extracted < affordable;
            transfer.accepted(now, inserted, requestLimited, ioSpeedMode);
        } else {
            transfer.rejected(now, ioSpeedMode);
        }
        long overflow = extracted - inserted;
        if (overflow > 0) {
            addToImportBuffer(key, overflow);
            saveImportBufferChanges(now);
        }
        return inserted;
    }

    private List<ExportConfigEntry> exportEntriesForType(AEKeyType keyType, long now) {
        refreshExportConfigCache(now);
        var entries = exportConfigCache.get(keyType);
        return entries != null ? entries : List.of();
    }

    private void refreshExportConfigCache(long now) {
        if (exportConfigCacheTick == now) {
            return;
        }
        exportConfigCacheTick = now;

        int hash = computeExportConfigHash();
        if (exportConfigCacheValid && hash == exportConfigCacheHash) {
            return;
        }

        exportConfigCacheHash = hash;
        exportConfigCacheValid = true;
        exportConfigCache.clear();

        var config = getInterfaceLogic().getConfig();
        for (int ci = 0; ci < config.size(); ci++) {
            var key = config.getKey(ci);
            if (key == null || !isWirelessIoKeyType(key.getType())) continue;

            long maxAmount = unlimitedSlots[ci] ? Long.MAX_VALUE : config.getAmount(ci);
            if (maxAmount <= 0) maxAmount = Long.MAX_VALUE;
            exportConfigCache
                    .computeIfAbsent(key.getType(), ignored -> new ArrayList<>())
                    .add(new ExportConfigEntry(key, maxAmount));
        }
        exportConfigCache.replaceAll((ignored, entries) -> List.copyOf(entries));
    }

    private int computeExportConfigHash() {
        var config = getInterfaceLogic().getConfig();
        int hash = config.size();
        for (int ci = 0; ci < config.size(); ci++) {
            var key = config.getKey(ci);
            hash = 31 * hash + (key != null ? key.hashCode() : 0);
            hash = 31 * hash + (unlimitedSlots[ci] ? 1 : 0);
            if (key != null && !unlimitedSlots[ci]) {
                hash = 31 * hash + Long.hashCode(config.getAmount(ci));
            }
        }
        return hash;
    }

    // ── Persistent import buffer ─────────────────────────────────────────

    private void addToImportBuffer(AEKey key, long amount) {
        if (amount <= 0) return;
        importBufferFlushState.ensureInitialized(importBuffer);
        Long existingAmount = importBuffer.get(key);
        boolean newKey = existingAmount == null || existingAmount <= 0;
        importBuffer.merge(key, amount, (oldAmount, added) ->
                oldAmount > Long.MAX_VALUE - added ? Long.MAX_VALUE : oldAmount + added);
        importBufferFlushState.onBuffered(key, newKey);
        if (newKey) {
            // A newly observed key is not covered by an earlier type-wide
            // rejection. Do not leave it behind the lock until the next TTL
            // merely because it arrived after the rejection pass closed.
            keyTypeLockUntil.remove(key.getType());
        }
        // All additions originate in the active grid import/export pass.
        // ProxyTicker returns URGENT for that work; AE2 ignores alerts for
        // the currently ticking node, so there is no per-key wakeup to send.
    }

    private void saveImportBufferChanges(long now) {
        if (importBufferLastSaveTick != now) {
            importBufferLastSaveTick = now;
            // AE2 queues setChanged for the end of the tick, but still looks
            // up the chunk on every saveChanges call. One call covers all
            // buffer mutations in this tick, including subsequent targets.
            saveChanges();
        }
    }

    /** Read-only observation used by the self-contained development GameTest fixture. */
    long benchmarkBufferedImportAmount() {
        long total = 0;
        for (long amount : importBuffer.values()) {
            total = total > Long.MAX_VALUE - amount ? Long.MAX_VALUE : total + amount;
        }
        return total;
    }

    /** Read-only observation used by the self-contained development GameTest fixture. */
    int benchmarkBufferedImportKeys() {
        return importBuffer.size();
    }

    /** Read-only scheduler observation used by the development GameTest fixture. */
    String benchmarkWirelessIoState() {
        return "mode=" + interfaceMode + "/" + ioSpeedMode + "/" + importMode
                + ", connections=" + connections.size()
                + ", valid=" + validConnectionsCache.size()
                + ", states=" + connectionStates.size()
                + ", entries=" + ioEntries.size()
                + ", wheelDirty=" + ioWheelDirty
                + ", bufferedKeys=" + importBuffer.size()
                + ", bufferedAmount=" + benchmarkBufferedImportAmount();
    }

    static ImportBufferFlushResult flushImportBufferEntries(
            Map<AEKey, Long> importBuffer,
            Map<AEKeyType, Long> keyTypeLockUntil,
            long importBufferLastFlushTick,
            boolean importBufferFlushLimited,
            int importBufferRemainingKeys,
            ImportBufferFlushState flushState,
            MEStorage me,
            IActionSource src,
            long now,
            Runnable saveChanges) {
        if (importBuffer.isEmpty()) {
            return new ImportBufferFlushResult(
                    importBufferLastFlushTick,
                    importBufferFlushLimited,
                    importBufferRemainingKeys,
                    false,
                    0);
        }
        boolean continueLimitedFlush = importBufferFlushLimited && !importBuffer.isEmpty();
        if (!continueLimitedFlush && importBufferLastFlushTick != Long.MIN_VALUE
                && now - importBufferLastFlushTick < IMPORT_FLUSH_INTERVAL) {
            return new ImportBufferFlushResult(
                    importBufferLastFlushTick,
                    importBufferFlushLimited,
                    importBufferRemainingKeys,
                    false,
                    0);
        }
        flushState.ensureInitialized(importBuffer);
        importBufferLastFlushTick = now;

        var touchedTypes = new IdentityHashMap<AEKeyType, Boolean>();
        boolean changed = false;
        boolean hadRejection = false;

        int flushLimit = IMPORT_FLUSH_MAX_KEYS;
        if (importBufferFlushLimited) {
            int newKeys = Math.max(0, importBuffer.size() - importBufferRemainingKeys);
            int candidateLimit = newKeys > 0
                    ? Math.max(IMPORT_FLUSH_MAX_KEYS, newKeys)
                    : importBuffer.size();
            if (flushState.previousFlushHadRejection()) {
                // Once a slice has rejected anything, keep every following
                // slice bounded as well.  The cross-slice counters decide
                // when a type is fully rejected; they must not turn a small
                // untested count into a one-key-per-flush fairness penalty.
                flushLimit = Math.min(candidateLimit, IMPORT_FLUSH_MAX_KEYS);
            } else {
                flushLimit = candidateLimit;
            }
        }

        var it = importBuffer.entrySet().iterator();
        int attemptedKeys = 0;
        int visitedKeys = 0;
        // A full traversal already leaves survivors in their original order.
        // Only a sliced traversal needs to move survivors behind an untouched tail.
        ArrayList<AEKey> rotated = null;
        boolean mayHaveUntouchedTail = importBuffer.size() > flushLimit;
        while (it.hasNext() && attemptedKeys < flushLimit) {
            attemptedKeys++;
            visitedKeys++;
            var buffered = it.next();
            var key = buffered.getKey();
            var type = key.getType();
            touchedTypes.put(type, true);
            long amount = buffered.getValue();
            if (amount <= 0) {
                it.remove();
                flushState.onAttempt(type, key, true, true);
                changed = true;
                continue;
            }

            long inserted = me.insert(key, amount, Actionable.MODULATE, src);
            if (inserted >= amount) {
                it.remove();
                flushState.onAttempt(type, key, true, true);
                changed = true;
            } else if (inserted > 0) {
                buffered.setValue(amount - inserted);
                flushState.onAttempt(type, key, true, false);
                changed = true;
            } else {
                flushState.onAttempt(type, key, false, false);
                hadRejection = true;
            }

            if (inserted < amount && mayHaveUntouchedTail) {
                if (rotated == null) rotated = new ArrayList<>();
                rotated.add(key);
            }
        }

        // Move unfinished entries behind the untouched tail so a single
        // rejected key cannot monopolize every bounded flush slice.
        boolean hasUntouchedEntries = it.hasNext();
        if (hasUntouchedEntries && rotated != null) {
            for (var key : rotated) {
                var remaining = importBuffer.remove(key);
                if (remaining != null) {
                    importBuffer.put(key, remaining);
                }
            }
        }

        importBufferFlushLimited = changed && hasUntouchedEntries;
        importBufferRemainingKeys = importBufferFlushLimited ? importBuffer.size() : 0;

        for (var type : touchedTypes.keySet()) {
            var decision = flushState.finishType(type);
            if (decision.progressed()) {
                if (keyTypeLockUntil.remove(type) != null) {
                    changed = true;
                }
            } else if (decision.rejectionPassComplete()) {
                keyTypeLockUntil.put(type, now + STOP_IMPORT_TTL);
            }
        }

        flushState.recordFlush(hadRejection);
        if (changed) {
            saveChanges.run();
        }
        return new ImportBufferFlushResult(
                importBufferLastFlushTick,
                importBufferFlushLimited,
                importBufferRemainingKeys,
                changed,
                visitedKeys);
    }

    private void flushImportBuffer(MEStorage me, IActionSource src, long now) {
        var result = flushImportBufferEntries(
                importBuffer,
                keyTypeLockUntil,
                importBufferLastFlushTick,
                importBufferFlushLimited,
                importBufferRemainingKeys,
                importBufferFlushState,
                me,
                src,
                now,
                () -> saveImportBufferChanges(now));
        importBufferLastFlushTick = result.lastFlushTick();
        importBufferFlushLimited = result.flushLimited();
        importBufferRemainingKeys = result.remainingKeys();
    }

    private long lockedUntil(AEKeyType type, long now) {
        long until = keyTypeLockUntil.getOrDefault(type, 0L);
        if (until <= 0) return 0;
        if (now >= until) {
            keyTypeLockUntil.remove(type);
            return 0;
        }
        return until;
    }

    public void addImportBufferDrops(List<ItemStack> drops) {
        if (importBuffer.isEmpty()) {
            importBufferFlushState.clear();
            return;
        }
        for (var buffered : importBuffer.entrySet()) {
            buffered.getKey().addDrops(buffered.getValue(), drops, getLevel(), getBlockPos());
        }
        importBuffer.clear();
        importBufferFlushState.clear();
        importBufferFlushLimited = false;
        importBufferRemainingKeys = 0;
    }

    public void clearImportBuffer() {
        importBuffer.clear();
        importBufferFlushState.clear();
        keyTypeLockUntil.clear();
        importBufferLastFlushTick = Long.MIN_VALUE;
        importBufferLastSaveTick = Long.MIN_VALUE;
        importBufferFlushLimited = false;
        importBufferRemainingKeys = 0;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Energy transfer
    // ══════════════════════════════════════════════════════════════════════

    private boolean hasInductionCard() {
        if (!AppFluxHelper.isAvailable()) return false;
        if (!inductionCardCacheDirty) return inductionCardInstalledCache;
        var u = getInterfaceLogic().getUpgrades();
        boolean installed = false;
        for (int i = 0; i < u.size(); i++) {
            if (AppFluxHelper.isInductionCard(u.getStackInSlot(i).getItem())) {
                installed = true;
                break;
            }
        }
        inductionCardInstalledCache = installed;
        inductionCardCacheDirty = false;
        return installed;
    }

    private void tickEnergyTransfer(ServerLevel sl) {
        if (!hasInductionCard()) return;
        var feKey = AppFluxHelper.FE_KEY; if (feKey == null) return;
        var grid = getMainNode().getGrid(); if (grid == null) return;
        if (interfaceMode == InterfaceMode.WIRELESS) tickWirelessEnergy(sl, feKey);
        else if (energyOutputDir != null)            tickNormalEnergy(sl);
    }

    private void tickNormalEnergy(ServerLevel sl) {
        if (!AppFluxBridge.canUseEnergyHandler()) return;
        var grid = getMainNode().getGrid(); if (grid == null) return;
        var capCache = AppFluxBridge.createCapCache(sl, getBlockPos(), () -> grid);
        var target = WirelessEnergyAPI.resolveEnergyTarget(capCache, energyOutputDir.getOpposite());
        if (target == null) return;
        var storage = grid.getStorageService();
        WirelessEnergyAPI.sendToTarget(target, storage, machineSource, AppFluxBridge.TRANSFER_RATE);
    }

    // ── Wireless energy: timing-wheel scheduler ──────────────────────────

    private void tickWirelessEnergy(ServerLevel sl, AEKey feKey) {
        long gt = sl.getGameTime();
        if (gt == lastEnergyTickGameTime) return;
        lastEnergyTickGameTime = gt;
        distributeWirelessEnergy(sl, gt, feKey);
    }

    private void distributeWirelessEnergy(ServerLevel sl, long tick, AEKey feKey) {
        // Refresh the validated connection list (host-owned: 20-tick sweep
        // + automatic stale-connection removal). rebuildEnergyTargets()
        // inside this call publishes the snapshot the distributor reads
        // through DistributorHost and bumps the version stamp.
        getOrRefreshValidConnections(sl, tick);
        wirelessDistributor.tickNormal(sl);
    }

    private final class DistributorHost implements WirelessEnergyDistributor.Host {
        @Override
        public appeng.api.networking.IManagedGridNode getMainNode() {
            return OverloadedInterfaceBlockEntity.this.getMainNode();
        }

        @Override
        public IActionSource actionSource() {
            return machineSource;
        }

        @Override
        public boolean isHostRemoved() {
            return OverloadedInterfaceBlockEntity.this.isRemoved();
        }

        @Override
        public List<WirelessEnergyAPI.Target> getValidTargets() {
            return validEnergyTargetsCache;
        }

        @Override
        public int getValidTargetsVersion() {
            return validEnergyTargetsVersion;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Eject mode
    // ══════════════════════════════════════════════════════════════════════

    public void refreshEjectRegistrations() {
        // The registry is shared by both logical sides in an integrated server.
        if (level != null && level.isClientSide()) return;
        unregisterEject();
        if (!OverloadedInterfaceTickDecider.shouldRegisterEjectPorts(
                interfaceMode == InterfaceMode.WIRELESS,
                importMode == ImportMode.EJECT)
                || level==null) return;
        var srv = level.getServer(); if (srv==null) return;
        for (var c : connections) {
            if (!c.dimension().equals(level.dimension())) continue;
            registerEjectAt(srv, c.dimension(),
                    c.pos().relative(c.boundFace()), c.boundFace().getOpposite());
        }
    }

    private void registerEjectAt(net.minecraft.server.MinecraftServer srv,
                                  ResourceKey<Level> dim, BlockPos ip, Direction iface) {
        var tl = srv.getLevel(dim); if (tl==null) return;
        var ghost = new GhostOutputBlockEntity(ip); ghost.setLevel(tl);
        EjectModeRegistry.register(dim, ip.asLong(), iface,
                new EjectModeRegistry.EjectEntry(
                        new java.lang.ref.WeakReference<>(this), ghost,
                        level.dimension(), getBlockPos()));
        if (tl instanceof ServerLevel s) s.invalidateCapabilities(ip);
    }

    private void unregisterEject() {
        if (level==null || level.isClientSide()) return;
        var removed = EjectModeRegistry.unregisterAll(this, true);
        if (level instanceof ServerLevel sl) {
            var srv = sl.getServer();
            for (var dp : removed) {
                var t = srv.getLevel(dp.dimension());
                if (t!=null) t.invalidateCapabilities(dp.pos());
            }
        }
    }

    @Override
    public void setRemoved() {
        frequencyBinding.setRemoved();
        if (!unloadingChunk) {
            unregisterEject();
        }
        // Flush any FE the wireless distributor still has buffered back to
        // the ME network — there is no persistent storage on this side and
        // BufferedMEStorage discards on GC.
        wirelessDistributor.flushBufferToNetwork();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        frequencyBinding.clearRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        frequencyBinding.onChunkUnloaded();
        unloadingChunk = true;
        wirelessDistributor.flushBufferToNetwork();
        super.onChunkUnloaded();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NBT
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(interfaceMode.ordinal());
        data.writeByte(ioSpeedMode.ordinal());
        data.writeByte(exportMode.ordinal());
        data.writeByte(importMode.ordinal());
        data.writeByte(energyOutputDir != null ? energyOutputDir.get3DDataValue() : -1);

        long bits = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (unlimitedSlots[i]) {
                bits |= (1L << i);
            }
        }
        data.writeLong(bits);

        data.writeVarInt(connections.size());
        for (var c : connections) {
            data.writeResourceLocation(c.dimension().location());
            data.writeBlockPos(c.pos());
            data.writeByte(c.boundFace().get3DDataValue());
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);

        int interfaceOrd = data.readByte();
        var newInterfaceMode = interfaceOrd >= 0 && interfaceOrd < InterfaceMode.values().length
                ? InterfaceMode.values()[interfaceOrd] : InterfaceMode.NORMAL;

        int speedOrd = data.readByte();
        var newIoSpeedMode = speedOrd >= 0 && speedOrd < IOSpeedMode.values().length
                ? IOSpeedMode.values()[speedOrd] : IOSpeedMode.NORMAL;

        int exportOrd = data.readByte();
        var newExportMode = exportOrd >= 0 && exportOrd < ExportMode.values().length
                ? ExportMode.values()[exportOrd] : ExportMode.OFF;

        int importOrd = data.readByte();
        var newImportMode = importOrd >= 0 && importOrd < ImportMode.values().length
                ? ImportMode.values()[importOrd] : ImportMode.OFF;

        int energyOrd = data.readByte();
        Direction newEnergyDir = energyOrd >= 0 && energyOrd < 6
                ? Direction.from3DDataValue(energyOrd) : null;

        long newBits = data.readLong();
        boolean[] newUnlimitedSlots = new boolean[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            newUnlimitedSlots[i] = (newBits & (1L << i)) != 0;
        }

        int count = data.readVarInt();
        var newConnections = new ArrayList<WirelessConnection>(Math.min(count, MAX_WIRELESS_CONNECTIONS));
        for (int i = 0; i < count; i++) {
            var dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    data.readResourceLocation());
            var pos = data.readBlockPos();
            var face = Direction.from3DDataValue(data.readByte());
            var connection = new WirelessConnection(dim, pos, face);
            if (ConnectionEndpoints.indexOfEndpoint(
                    newConnections,
                    connection.dimension(),
                    connection.pos(),
                    connection.boundFace(),
                    WirelessConnection::dimension,
                    WirelessConnection::pos,
                    WirelessConnection::boundFace) < 0
                    && newConnections.size() < MAX_WIRELESS_CONNECTIONS) {
                newConnections.add(connection);
            }
        }

        boolean unlimitedChanged = false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (unlimitedSlots[i] != newUnlimitedSlots[i]) {
                unlimitedChanged = true;
                break;
            }
        }

        if (newInterfaceMode != interfaceMode
                || newIoSpeedMode != ioSpeedMode
                || newExportMode != exportMode
                || newImportMode != importMode
                || newEnergyDir != energyOutputDir
                || unlimitedChanged
                || !newConnections.equals(connections)) {
            interfaceMode = newInterfaceMode;
            ioSpeedMode = newIoSpeedMode;
            exportMode = newExportMode;
            importMode = newImportMode;
            energyOutputDir = newEnergyDir;
            System.arraycopy(newUnlimitedSlots, 0, unlimitedSlots, 0, SLOT_COUNT);
            invalidateExportConfigCache();
            connections.clear();
            connections.addAll(newConnections);
            invalidConnectionScanCursor = 0;
            changed = true;
        }

        return changed;
    }

    @Override
    public void saveAdditional(CompoundTag d, HolderLookup.Provider r) {
        super.saveAdditional(d, r);
        d.putString(TAG_INTERFACE_MODE, interfaceMode.name());
        d.putString(TAG_IO_SPEED_MODE, ioSpeedMode.name());
        d.putString(TAG_EXPORT_MODE, exportMode.name());
        d.putString(TAG_IMPORT_MODE, importMode.name());
        d.putInt(TAG_ENERGY_DIR, energyOutputDir!=null ? energyOutputDir.get3DDataValue() : -1);
        long bits = 0;
        for (int i = 0; i < SLOT_COUNT; i++) if (unlimitedSlots[i]) bits |= (1L << i);
        d.putLong(TAG_UNLIMITED_SLOTS, bits);
        d.put(TAG_CONNECTIONS, WirelessConnectionLists.writeTagList(connections));
        filterInv.writeToNBT(d, TAG_FILTER_INV, r);
        if (!importBuffer.isEmpty()) {
            var buffered = new ListTag();
            for (var entry : importBuffer.entrySet()) {
                buffered.add(GenericStack.writeTag(r, new GenericStack(entry.getKey(), entry.getValue())));
            }
            d.put(TAG_IMPORT_BUFFER, buffered);
        }
        d.putLong(TAG_IMPORT_FLUSH_TICK, importBufferLastFlushTick);
        frequencyBinding.save(d);
    }

    @Override
    public void loadTag(CompoundTag d, HolderLookup.Provider r) {
        super.loadTag(d, r);
        if (d.contains(TAG_INTERFACE_MODE)) {
            try { interfaceMode = InterfaceMode.valueOf(d.getString(TAG_INTERFACE_MODE)); }
            catch (IllegalArgumentException e) { interfaceMode = InterfaceMode.NORMAL; }
        }
        if (d.contains(TAG_IO_SPEED_MODE)) {
            try { ioSpeedMode = IOSpeedMode.valueOf(d.getString(TAG_IO_SPEED_MODE)); }
            catch (IllegalArgumentException e) { ioSpeedMode = IOSpeedMode.NORMAL; }
        }
        if (d.contains(TAG_EXPORT_MODE)) {
            try { exportMode = ExportMode.valueOf(d.getString(TAG_EXPORT_MODE)); }
            catch (IllegalArgumentException e) { exportMode = ExportMode.OFF; }
        }
        if (d.contains(TAG_IMPORT_MODE)) {
            try { importMode = ImportMode.valueOf(d.getString(TAG_IMPORT_MODE)); }
            catch (IllegalArgumentException e) { importMode = ImportMode.OFF; }
        }
        long bits = d.getLong(TAG_UNLIMITED_SLOTS);
        for (int i = 0; i < SLOT_COUNT; i++) unlimitedSlots[i] = (bits & (1L << i)) != 0;
        int ev = d.contains(TAG_ENERGY_DIR) ? d.getInt(TAG_ENERGY_DIR) : -1;
        energyOutputDir = ev>=0 && ev<6 ? Direction.from3DDataValue(ev) : null;
        WirelessConnectionLists.readTagList(
                d, TAG_CONNECTIONS, connections, MAX_WIRELESS_CONNECTIONS, WirelessConnection::fromTag);
        invalidConnectionScanCursor = 0;
        filterInv.readFromNBT(d, TAG_FILTER_INV, r);
        rebuildFilter();
        importBuffer.clear();
        importBufferLastSaveTick = Long.MIN_VALUE;
        importBufferFlushLimited = false;
        importBufferRemainingKeys = 0;
        if (d.contains(TAG_IMPORT_BUFFER, Tag.TAG_LIST)) {
            var buffered = d.getList(TAG_IMPORT_BUFFER, Tag.TAG_COMPOUND);
            for (int i = 0; i < buffered.size(); i++) {
                var stack = GenericStack.readTag(r, buffered.getCompound(i));
                if (stack != null && stack.amount() > 0) {
                    importBuffer.merge(stack.what(), stack.amount(), (oldAmount, added) ->
                            oldAmount > Long.MAX_VALUE - added ? Long.MAX_VALUE : oldAmount + added);
                }
            }
        }
        importBufferLastFlushTick = d.contains(TAG_IMPORT_FLUSH_TICK)
                ? d.getLong(TAG_IMPORT_FLUSH_TICK)
                : Long.MIN_VALUE;
        importBufferFlushState.rebuildFrom(importBuffer);
        keyTypeLockUntil.clear();
        invalidateConnectionCache();
        refreshEjectRegistrations();
        frequencyBinding.load(d);
        recomputeIdlePower();
    }

    // ── Memory card copy/paste (machine-specific fields only) ───────────────
    // AE2's generic export only walks IUpgradeable / IConfigurableObject /
    // IPriorityHost / IConfigInvHost — none of our custom mode enums, the
    // energy output direction, or the unlimited-slot bitset live there.

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.exportMemoryCardSettings(mode, builder, tag -> {
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_INTERFACE_MODE, interfaceMode);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_IO_SPEED_MODE, ioSpeedMode);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_EXPORT_MODE, exportMode);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_IMPORT_MODE, importMode);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeDirection(tag, TAG_ENERGY_DIR, energyOutputDir);
            long bits = 0;
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (unlimitedSlots[i]) bits |= (1L << i);
            }
            tag.putLong(TAG_UNLIMITED_SLOTS, bits);
            FrequencyBindingHelper.writeMemoryFrequency(tag, getFrequencyId());
        });
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @Nullable Player player) {
        super.importSettings(mode, input, player);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.importMemoryCardSettings(mode, input, tag -> {
            this.interfaceMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_INTERFACE_MODE, InterfaceMode.class, this.interfaceMode);
            this.ioSpeedMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_IO_SPEED_MODE, IOSpeedMode.class, this.ioSpeedMode);
            this.exportMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_EXPORT_MODE, ExportMode.class, this.exportMode);
            this.importMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_IMPORT_MODE, ImportMode.class, this.importMode);
            if (tag.contains(TAG_ENERGY_DIR)) {
                this.energyOutputDir = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readDirection(tag, TAG_ENERGY_DIR);
            }
            if (tag.contains(TAG_UNLIMITED_SLOTS)) {
                long bits = tag.getLong(TAG_UNLIMITED_SLOTS);
                for (int i = 0; i < SLOT_COUNT; i++) {
                    unlimitedSlots[i] = (bits & (1L << i)) != 0;
                }
            }
            FrequencyBindingHelper.importMemoryFrequency(tag, this::setFrequency);
            invalidateConnectionCache();
            // Unconditional: eject registration depends on interfaceMode AND
            // importMode, both possibly changed above (idempotent re-register)
            refreshEjectRegistrations();
            recomputeIdlePower();
            saveChanges();
            markForUpdate();
        });
    }

}
