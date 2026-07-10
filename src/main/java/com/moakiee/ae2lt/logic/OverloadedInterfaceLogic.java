package com.moakiee.ae2lt.logic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeySlotFilter;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class OverloadedInterfaceLogic extends InterfaceLogic {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private static final long OVERLOADED_BYTES = 1024L;

    private static final Field F_CONFIG;
    private static final Field F_STORAGE;
    private static final Field F_UPGRADES;
    private static final Field F_CRAFTING_TRACKER;
    private static final Method M_ON_CONFIG_CHANGED;
    private static final Method M_IS_ALLOWED_IN_SLOT;
    private static final Method M_ON_STORAGE_CHANGED;
    private static final Method M_ON_UPGRADES_CHANGED;

    static {
        try {
            F_CONFIG = InterfaceLogic.class.getDeclaredField("config");
            F_CONFIG.setAccessible(true);
            F_STORAGE = InterfaceLogic.class.getDeclaredField("storage");
            F_STORAGE.setAccessible(true);
            F_UPGRADES = InterfaceLogic.class.getDeclaredField("upgrades");
            F_UPGRADES.setAccessible(true);
            F_CRAFTING_TRACKER = InterfaceLogic.class.getDeclaredField("craftingTracker");
            F_CRAFTING_TRACKER.setAccessible(true);
            M_ON_CONFIG_CHANGED = InterfaceLogic.class.getDeclaredMethod("onConfigRowChanged");
            M_ON_CONFIG_CHANGED.setAccessible(true);
            M_IS_ALLOWED_IN_SLOT = InterfaceLogic.class.getDeclaredMethod(
                    "isAllowedInStorageSlot", int.class, AEKey.class);
            M_IS_ALLOWED_IN_SLOT.setAccessible(true);
            M_ON_STORAGE_CHANGED = InterfaceLogic.class.getDeclaredMethod("onStorageChanged");
            M_ON_STORAGE_CHANGED.setAccessible(true);
            M_ON_UPGRADES_CHANGED = InterfaceLogic.class.getDeclaredMethod("onUpgradesChanged");
            M_ON_UPGRADES_CHANGED.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to init reflection for OverloadedInterfaceLogic", e);
        }
    }

    private final OverloadedInterfaceBlockEntity owner;
    private final ProxiedStorageInv proxiedStorage;
    private final int slotCount;
    private final appeng.api.upgrades.IUpgradeInventory ourUpgrades;
    private final appeng.helpers.MultiCraftingTracker craftingTrackerRef;
    private final AEKey[] limitedSelections;
    private final List<AEKey>[] orderedCandidateCache;
    private long candidateCacheTick = Long.MIN_VALUE;
    private boolean candidateCacheFuzzyEnabled;
    private FuzzyMode candidateCacheFuzzyMode = FuzzyMode.IGNORE_ALL;

    @SuppressWarnings("unchecked")
    public OverloadedInterfaceLogic(IManagedGridNode gridNode,
                                    OverloadedInterfaceBlockEntity host,
                                    Item is, int slots) {
        super(gridNode, host, is, slots);
        this.owner = host;
        this.slotCount = slots;
        this.limitedSelections = new AEKey[slots];
        this.orderedCandidateCache = (List<AEKey>[]) new List<?>[slots];

        try {
            this.craftingTrackerRef = (appeng.helpers.MultiCraftingTracker) F_CRAFTING_TRACKER.get(this);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read craftingTracker", e);
        }

        var newConfig = new OverloadedConfigInv(
                AEKeyTypes.getAll(), null,
                GenericStackInv.Mode.CONFIG_STACKS, slots,
                this::onOverloadedConfigChanged);
        newConfig.owner = host;
        setField(F_CONFIG, newConfig);
        newConfig.useRegisteredCapacities();
        for (var type : AEKeyTypes.getAll()) {
            newConfig.setCapacity(type, overloadedCap(type));
        }

        proxiedStorage = new ProxiedStorageInv(
                this, AEKeyTypes.getAll(),
                (slot, key) -> invokeSlotFilter(M_IS_ALLOWED_IN_SLOT, this, slot, key),
                slots,
                () -> invokeQuietly(M_ON_STORAGE_CHANGED, this));
        setField(F_STORAGE, proxiedStorage);
        proxiedStorage.useRegisteredCapacities();

        var newUpgrades = UpgradeInventories.forMachine(is, 4, () -> {
            invokeQuietly(M_ON_UPGRADES_CHANGED, this);
            invalidateFuzzyState();
            host.invalidateInductionCardCache();
        });
        setField(F_UPGRADES, newUpgrades);
        this.ourUpgrades = newUpgrades;

        mainNode.addService(IGridTickable.class, new ProxyTicker());
    }

    OverloadedInterfaceBlockEntity getOwner() {
        return owner;
    }

    public ProxiedStorageInv getProxiedStorage() {
        return proxiedStorage;
    }

    private void onOverloadedConfigChanged() {
        invokeQuietly(M_ON_CONFIG_CHANGED, this);
        invalidateFuzzyState();
    }

    private boolean isFuzzyEnabled() {
        return ourUpgrades.isInstalled(AEItems.FUZZY_CARD);
    }

    private FuzzyMode fuzzyMode() {
        return getConfigManager().getSetting(Settings.FUZZY_MODE);
    }

    private void prepareCandidateCache() {
        var level = host.getBlockEntity().getLevel();
        long now = level != null ? level.getGameTime() : Long.MIN_VALUE;
        boolean fuzzyEnabled = isFuzzyEnabled();
        FuzzyMode fuzzyMode = fuzzyMode();
        boolean fuzzyStateChanged = fuzzyEnabled != candidateCacheFuzzyEnabled
                || fuzzyMode != candidateCacheFuzzyMode;
        if (now != candidateCacheTick || fuzzyStateChanged) {
            Arrays.fill(orderedCandidateCache, null);
            candidateCacheTick = now;
        }
        if (fuzzyStateChanged) {
            Arrays.fill(limitedSelections, null);
            candidateCacheFuzzyEnabled = fuzzyEnabled;
            candidateCacheFuzzyMode = fuzzyMode;
            proxiedStorage.invalidateCaches();
        }
    }

    private void invalidateFuzzyState() {
        Arrays.fill(limitedSelections, null);
        Arrays.fill(orderedCandidateCache, null);
        candidateCacheTick = Long.MIN_VALUE;
        if (proxiedStorage != null) {
            proxiedStorage.invalidateCaches();
        }
    }

    public void onSlotModeChanged(int slot) {
        if (slot >= 0 && slot < slotCount) {
            limitedSelections[slot] = null;
            orderedCandidateCache[slot] = null;
        }
        proxiedStorage.invalidateCaches();
    }

    public boolean matchesConfiguredSlot(int slot, AEKey candidate) {
        if (slot < 0 || slot >= slotCount || candidate == null) {
            return false;
        }
        var configured = getConfig().getKey(slot);
        return configured != null && OverloadedInterfaceFuzzyResolver.matches(
                configured, candidate, isFuzzyEnabled(), fuzzyMode());
    }

    private List<AEKey> orderedCandidates(int slot) {
        prepareCandidateCache();
        if (slot < 0 || slot >= slotCount) {
            return List.of();
        }
        var configured = getConfig().getKey(slot);
        var grid = mainNode.getGrid();
        if (configured == null || grid == null) {
            limitedSelections[slot] = null;
            return List.of();
        }
        var cached = orderedCandidateCache[slot];
        if (cached == null) {
            cached = OverloadedInterfaceFuzzyResolver.orderedCandidates(
                    configured,
                    grid.getStorageService().getCachedInventory(),
                    isFuzzyEnabled(),
                    fuzzyMode());
            orderedCandidateCache[slot] = cached;
        }
        return cached;
    }

    /**
     * Resolves the real network keys represented by a configuration slot.
     * Limited slots return at most one sticky key; unlimited slots return all
     * currently available matching keys.
     */
    public List<AEKey> resolveCandidatesForSlot(int slot, boolean unlimited) {
        var ordered = orderedCandidates(slot);
        if (ordered.isEmpty()) {
            return List.of();
        }
        var configured = getConfig().getKey(slot);
        if (configured == null) {
            return List.of();
        }
        if (unlimited) {
            return OverloadedInterfaceFuzzyResolver.selectUnlimited(
                    ordered, key -> proxiedStorage.visibleNetworkAmount(key, 1));
        }

        var selected = OverloadedInterfaceFuzzyResolver.selectLimited(
                limitedSelections[slot], configured, ordered,
                isFuzzyEnabled(), fuzzyMode(),
                key -> proxiedStorage.visibleNetworkAmount(key, 1));
        limitedSelections[slot] = selected;
        return selected != null ? List.of(selected) : List.of();
    }

    /**
     * Sets config stack while suppressing the automatic unlimited-cancel in OverloadedConfigInv.
     * Used by toggleUnlimited to set amount=1 without cancelling the unlimited flag it just set.
     */
    public void setConfigStackSuppressed(int slot, @Nullable GenericStack stack) {
        var cfg = getConfig();
        if (cfg instanceof OverloadedConfigInv oci) {
            oci.suppressUnlimitedCancel = true;
            try { oci.setStack(slot, stack); }
            finally { oci.suppressUnlimitedCancel = false; }
        } else {
            cfg.setStack(slot, stack);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Drops / clear — storage is virtual, MUST NOT drop or clear ME items
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void addDrops(List<ItemStack> drops) {
        for (var is : getUpgrades()) {
            if (!is.isEmpty()) drops.add(is);
        }
        var filterStack = owner.getFilterInv().getStackInSlot(0);
        if (!filterStack.isEmpty()) drops.add(filterStack);
        owner.addImportBufferDrops(drops);
    }

    @Override
    public void clearContent() {
        getUpgrades().clear();
        owner.getFilterInv().setItemDirect(0, ItemStack.EMPTY);
        owner.clearImportBuffer();
    }

    private static final long REACTIVE_COOLDOWN_TICKS = 5;
    private long lastReactiveTick = -1;

    private boolean invokeCrafting(int slot, AEKey what, long amount) {
        var grid = mainNode.getGrid();
        if (grid == null || what == null) return false;
        return craftingTrackerRef.handleCrafting(slot, what, amount,
                host.getBlockEntity().getLevel(),
                grid.getCraftingService(), actionSource);
    }

    void onExtractDeficit(AEKey what) {
        if (!ourUpgrades.isInstalled(AEItems.CRAFTING_CARD)) return;
        var level = host.getBlockEntity().getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        if (now - lastReactiveTick < REACTIVE_COOLDOWN_TICKS) return;
        lastReactiveTick = now;

        var cfg = getConfig();
        for (int i = 0; i < slotCount; i++) {
            var key = cfg.getKey(i);
            if (key == null || !matchesConfiguredSlot(i, what)) continue;
            boolean unlimited = owner.isSlotUnlimited(i);
            if (!resolveCandidatesForSlot(i, unlimited).isEmpty()) return;
            long cap = unlimited ? overloadedCap(key) : cfg.getAmount(i);
            if (cap > 0) {
                invokeCrafting(i, key, cap);
            }
            break;
        }
    }

    private static long overloadedCap(AEKey what) {
        return overloadedCap(what.getType());
    }

    private static long overloadedCap(AEKeyType type) {
        return OVERLOADED_BYTES * type.getAmountPerByte();
    }

    private void setField(Field f, Object value) {
        try { f.set(this, value); } catch (IllegalAccessException e) {
            throw new IllegalStateException("Reflection set failed", e);
        }
    }

    private static void invokeQuietly(Method m, Object target) {
        try {
            m.invoke(target);
        } catch (Exception e) {
            LOG.warn("Reflection invoke failed: {}.{}", m.getDeclaringClass().getSimpleName(), m.getName(), e);
        }
    }

    private static boolean invokeSlotFilter(Method m, Object target, int slot, AEKey key) {
        try { return (boolean) m.invoke(target, slot, key); } catch (Exception e) { return false; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Ticker — no stocking, only handles crafting card deficit requests
    // ══════════════════════════════════════════════════════════════════════

    private class ProxyTicker implements IGridTickable {
        // 自定义 1-5 tick 档:有活 URGENT → 1 tick,空转 SLOWER → 最慢 5 tick
        // (AE2 默认 TickRates.Interface 是 5-120,空转太慢、响应滞后;这里覆盖为响应优先)
        private static final int MIN_TICKS = 1;
        private static final int MAX_TICKS = 5;

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(MIN_TICKS, MAX_TICKS, false);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!mainNode.isActive()) return TickRateModulation.IDLE;
            if (!ourUpgrades.isInstalled(AEItems.CRAFTING_CARD)) return TickRateModulation.IDLE;
            var grid = mainNode.getGrid();
            if (grid == null) return TickRateModulation.IDLE;

            var cfg = getConfig();
            boolean didWork = false;
            for (int i = 0; i < slotCount; i++) {
                var cfgStack = cfg.getStack(i);
                if (cfgStack == null) continue;
                boolean unlimited = owner.isSlotUnlimited(i);
                if (!resolveCandidatesForSlot(i, unlimited).isEmpty()) continue;
                long batch = unlimited ? overloadedCap(cfgStack.what()) : cfgStack.amount();
                if (batch > 0) {
                    didWork |= invokeCrafting(i, cfgStack.what(), batch);
                }
            }
            return didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OverloadedConfigInv — bypasses getMaxStackSize() clamp
    // ══════════════════════════════════════════════════════════════════════

    static class OverloadedConfigInv extends ConfigInventory {
        @Nullable OverloadedInterfaceBlockEntity owner;
        boolean suppressUnlimitedCancel;

        OverloadedConfigInv(Set<AEKeyType> supportedTypes,
                            @Nullable AEKeySlotFilter slotFilter,
                            GenericStackInv.Mode mode, int size,
                            @Nullable Runnable listener) {
            super(supportedTypes, slotFilter, mode, size, listener, false);
        }

        @Override
        public long getMaxAmount(AEKey key) {
            return getCapacity(key.getType());
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack stack) {
            super.setStack(slot, stack);
            if (!suppressUnlimitedCancel && owner != null && owner.isSlotUnlimited(slot)) {
                var level = owner.getLevel();
                if (level != null && !level.isClientSide()) {
                    owner.setSlotUnlimited(slot, false);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ProxiedStorageInv — ME network proxy
    //
    //  • getStack/getAmount return REAL display values:
    //      unlimited  → actual ME network quantity
    //      limited    → min(configAmount, networkQuantity)
    //  • extract() proxies to the ME network (capped per config)
    //  • insert()  proxies to the ME network (crafted items, pipe input)
    //  • getAvailableStacks() exposes only configured keys to avoid grid cache inflation
    //  • Re-entrancy guard prevents recursion through InterfaceInventory
    // ══════════════════════════════════════════════════════════════════════

    public static class ProxiedStorageInv extends OverloadedConfigInv {
        private final OverloadedInterfaceLogic logic;
        private boolean proxying = false;
        /** Rebuilt (not reset) each refresh: KeyCounter.reset() keeps zeroed keys, which would leak 0-amount entries to callers. */
        private KeyCounter availableStacksCache = new KeyCounter();
        private long availableStacksCacheTick = Long.MIN_VALUE;
        /** Per-slot tick cache for the getStack/getAmount display path. */
        private final GenericStack[] displayStackCache;
        private final boolean[] displayStackComputed;
        private long displayAmountCacheTick = Long.MIN_VALUE;

        ProxiedStorageInv(OverloadedInterfaceLogic logic,
                          Set<AEKeyType> supportedTypes,
                          @Nullable AEKeySlotFilter slotFilter,
                          int size, @Nullable Runnable listener) {
            super(supportedTypes, slotFilter, GenericStackInv.Mode.STORAGE, size, listener);
            this.logic = logic;
            this.displayStackCache = new GenericStack[size];
            this.displayStackComputed = new boolean[size];
        }

        void invalidateCaches() {
            availableStacksCacheTick = Long.MIN_VALUE;
            displayAmountCacheTick = Long.MIN_VALUE;
            Arrays.fill(displayStackComputed, false);
            Arrays.fill(displayStackCache, null);
        }

        @Override
        public long getCapacity(AEKeyType keyType) {
            // AE2 adapts this to IItemHandler slot limit; keep unbounded so external
            // automation does not re-impose the configured stack cap on unlimited slots.
            if (keyType == AEKeyType.items()) return Integer.MAX_VALUE;
            return super.getCapacity(keyType);
        }

        private IActionSource src() {
            return IActionSource.ofMachine(logic.owner);
        }

        public ConfigInventory cfg() {
            return logic.getConfig();
        }

        public long capForSlot(int slot) {
            if (logic.owner.isSlotUnlimited(slot)) return Long.MAX_VALUE;
            long amt = cfg().getAmount(slot);
            if (amt > 0) return amt;
            var key = cfg().getKey(slot);
            return key != null ? key.getType().getAmountPerUnit() : Long.MAX_VALUE;
        }

        private @Nullable GenericStack displayStack(int slot) {
            var be = logic.host.getBlockEntity();
            var level = be != null ? be.getLevel() : null;
            long now = level != null ? level.getGameTime() : Long.MIN_VALUE;
            boolean cacheable = level != null && slot >= 0 && slot < displayStackCache.length;
            if (cacheable) {
                if (now != displayAmountCacheTick) {
                    Arrays.fill(displayStackComputed, false);
                    Arrays.fill(displayStackCache, null);
                    displayAmountCacheTick = now;
                }
                if (displayStackComputed[slot]) return displayStackCache[slot];
            }

            var candidates = logic.resolveCandidatesForSlot(
                    slot, logic.owner.isSlotUnlimited(slot));
            var key = candidates.isEmpty() ? null : candidates.getFirst();
            long amount = key != null ? visibleNetworkAmount(key, capForSlot(slot)) : 0;
            var result = key != null && amount > 0 ? new GenericStack(key, amount) : null;
            if (cacheable) {
                displayStackComputed[slot] = true;
                displayStackCache[slot] = result;
            }
            return result;
        }

        private long visibleNetworkAmount(AEKey key, long cap) {
            var grid = logic.mainNode.getGrid();
            if (grid == null || key == null) return 0;
            var storageService = grid.getStorageService();
            long reported = storageService.getCachedInventory().get(key);
            long simulated = simulateNetworkExtraction(storageService.getInventory(), key, cap);
            return OverloadedAmountMath.mergeReportedAndSimulatedAmount(reported, simulated, cap);
        }

        private long simulateNetworkExtraction(MEStorage network, AEKey key, long cap) {
            if (network == null || key == null || cap <= 0) return 0;
            boolean wasProxying = proxying;
            proxying = true;
            try {
                return network.extract(key, cap, Actionable.SIMULATE, src());
            } finally {
                proxying = wasProxying;
            }
        }

        private long exposedAmount(AEKey what) {
            var grid = logic.mainNode.getGrid();
            if (grid == null) return 0;
            long totalCap = 0;
            for (int i = 0; i < size(); i++) {
                boolean unlimited = logic.owner.isSlotUnlimited(i);
                if (unlimited) {
                    if (logic.matchesConfiguredSlot(i, what)) {
                        totalCap = Long.MAX_VALUE;
                        break;
                    }
                    continue;
                }
                var candidates = logic.resolveCandidatesForSlot(i, false);
                if (!candidates.isEmpty() && candidates.getFirst().equals(what)) {
                    long slotCap = capForSlot(i);
                    totalCap = saturatingAdd(totalCap, slotCap);
                }
            }
            return totalCap > 0 ? visibleNetworkAmount(what, totalCap) : 0;
        }

        private static long saturatingAdd(long left, long right) {
            if (left == Long.MAX_VALUE || right == Long.MAX_VALUE
                    || Long.MAX_VALUE - left < right) {
                return Long.MAX_VALUE;
            }
            return left + right;
        }

        // ── Display: server queries ME network & syncs stacks[]; client uses synced stacks[] ─

        @Override
        public @Nullable GenericStack getStack(int slot) {
            if (logic.mainNode.getGrid() == null) {
                return super.getStack(slot);
            }
            var result = displayStack(slot);
            stacks[slot] = result;
            return result;
        }

        @Override
        public @Nullable AEKey getKey(int slot) {
            if (logic.mainNode.getGrid() == null) {
                return super.getKey(slot);
            }
            var stack = displayStack(slot);
            return stack != null ? stack.what() : null;
        }

        @Override
        public long getAmount(int slot) {
            if (logic.mainNode.getGrid() == null) {
                return super.getAmount(slot);
            }
            var stack = displayStack(slot);
            return stack != null ? stack.amount() : 0;
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey what) {
            return what != null && isSupportedType(what);
        }

        @Override
        public ConfigMenuInventory createMenuWrapper() {
            return new ProxiedMenuWrapper(this);
        }

        // ── Per-slot insert: proxy to ME (crafted items / InterfaceLogic) ─

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            if (what == null || amount <= 0) return 0;
            return proxyInsert(what, amount, mode);
        }

        // ── Per-slot extract: proxy to ME, cap by config ────────────────

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            if (what == null || amount <= 0) return 0;
            var key = getKey(slot);
            if (key == null || !key.equals(what)) return 0;
            long capped = Math.min(amount, capForSlot(slot));
            long extracted = proxyExtract(what, capped, mode);
            if (extracted > 0 && mode == Actionable.MODULATE) {
                logic.onExtractDeficit(what);
            }
            return extracted;
        }

        // ── MEStorage extract: direct pass-through to ME network ────────

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (proxying) return 0;
            if (what == null || amount <= 0) return 0;
            long exposed = exposedAmount(what);
            if (exposed <= 0) return 0;
            long extracted = proxyExtract(what, Math.min(amount, exposed), mode);
            if (extracted > 0 && mode == Actionable.MODULATE) {
                logic.onExtractDeficit(what);
            }
            return extracted;
        }

        // ── MEStorage insert: proxy to ME ───────────────────────────────

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (proxying) return 0;
            if (what == null || amount <= 0) return 0;
            return proxyInsert(what, amount, mode);
        }

        // ── Re-entrant-safe proxies (public for Menu-level click handling) ─

        public long proxyExtract(AEKey what, long amount, Actionable mode) {
            if (proxying) return 0;
            proxying = true;
            try {
                var grid = logic.mainNode.getGrid();
                if (grid == null) return 0;
                var network = grid.getStorageService().getInventory();
                long affordable = PowerCostUtil.maxAffordable(grid, what, amount);
                if (affordable <= 0) return 0;
                long extracted = network.extract(what, affordable, mode, src());
                if (extracted > 0 && mode == Actionable.MODULATE) {
                    PowerCostUtil.consume(grid, what, extracted);
                }
                return extracted;
            } finally {
                proxying = false;
            }
        }

        public long proxyInsert(AEKey what, long amount, Actionable mode) {
            if (proxying) return 0;
            proxying = true;
            try {
                var grid = logic.mainNode.getGrid();
                if (grid == null) return 0;
                var network = grid.getStorageService().getInventory();
                long affordable = PowerCostUtil.maxAffordable(grid, what, amount);
                if (affordable <= 0) return 0;
                long inserted = network.insert(what, affordable, mode, src());
                if (inserted > 0 && mode == Actionable.MODULATE) {
                    PowerCostUtil.consume(grid, what, inserted);
                }
                return inserted;
            } finally {
                proxying = false;
            }
        }

        public void setDisplayStack(int slot, @Nullable GenericStack stack) {
            stacks[slot] = stack;
        }

        // ── Grid cache: expose a configured view of the ME network ───────
        // InterfaceInventory (registered on our grid) iterates stacks[] via
        // per-slot getStack(), so this override only affects external MEStorage
        // callers (e.g. storage bus from another network). The proxying guard
        // prevents recursion if the same grid re-enters.

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (proxying) return;
            var grid = logic.mainNode.getGrid();
            if (grid == null) return;
            var be = logic.host.getBlockEntity();
            var level = be != null ? be.getLevel() : null;
            long now = level != null ? level.getGameTime() : Long.MIN_VALUE;
            if (level != null && now == availableStacksCacheTick) {
                for (var entry : availableStacksCache) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
                return;
            }
            proxying = true;
            try {
                var fresh = new KeyCounter();
                var candidateCaps = new HashMap<AEKey, Long>();
                for (int slot = 0; slot < size(); slot++) {
                    boolean unlimited = logic.owner.isSlotUnlimited(slot);
                    long cap = unlimited ? Long.MAX_VALUE : capForSlot(slot);
                    for (var key : logic.resolveCandidatesForSlot(slot, unlimited)) {
                        candidateCaps.merge(key, cap, ProxiedStorageInv::saturatingAdd);
                    }
                }
                for (var entry : candidateCaps.entrySet()) {
                    long amount = visibleNetworkAmount(entry.getKey(), entry.getValue());
                    if (amount > 0) fresh.add(entry.getKey(), amount);
                }
                availableStacksCache = fresh;
                availableStacksCacheTick = now;
                for (var entry : fresh) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            } finally {
                proxying = false;
            }
        }

        // ── NBT: no persistent state ────────────────────────────────────

        @Override
        public void writeToChildTag(CompoundTag tag, String name,
                                    HolderLookup.Provider registries) {
            tag.remove(name);
        }

        @Override
        public void readFromChildTag(CompoundTag tag, String name,
                                     HolderLookup.Provider registries) {
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ProxiedMenuWrapper — display-only wrapper
    //
    //  All mutation methods are no-ops; actual GUI interaction is handled
    //  entirely by OverloadedInterfaceMenu.clicked() which directly
    //  operates on the ME network (ME Terminal pattern).
    // ══════════════════════════════════════════════════════════════════════

    static class ProxiedMenuWrapper extends ConfigMenuInventory {
        private final ProxiedStorageInv proxy;

        ProxiedMenuWrapper(ProxiedStorageInv proxy) {
            super(proxy);
            this.proxy = proxy;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (stack.isEmpty()) {
                proxy.setDisplayStack(slotIndex, null);
            } else {
                proxy.setDisplayStack(slotIndex, convertToSuitableStack(stack));
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
