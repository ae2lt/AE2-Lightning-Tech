package com.moakiee.ae2lt.logic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;

import appeng.api.config.Actionable;
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

import java.util.List;

public class OverloadedInterfaceLogic extends InterfaceLogic {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static final long OVERLOADED_CAP = 1024L * AEKeyType.items().getAmountPerByte();

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

    public OverloadedInterfaceLogic(IManagedGridNode gridNode,
                                    OverloadedInterfaceBlockEntity host,
                                    Item is, int slots) {
        super(gridNode, host, is, slots);
        this.owner = host;
        this.slotCount = slots;

        try {
            this.craftingTrackerRef = (appeng.helpers.MultiCraftingTracker) F_CRAFTING_TRACKER.get(this);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read craftingTracker", e);
        }

        var newConfig = new OverloadedConfigInv(
                AEKeyTypes.getAll(), null,
                GenericStackInv.Mode.CONFIG_STACKS, slots,
                () -> invokeQuietly(M_ON_CONFIG_CHANGED, this));
        newConfig.owner = host;
        setField(F_CONFIG, newConfig);
        newConfig.useRegisteredCapacities();
        newConfig.setCapacity(AEKeyType.items(), OVERLOADED_CAP);

        proxiedStorage = new ProxiedStorageInv(
                this, AEKeyTypes.getAll(),
                (slot, key) -> invokeSlotFilter(M_IS_ALLOWED_IN_SLOT, this, slot, key),
                slots,
                () -> invokeQuietly(M_ON_STORAGE_CHANGED, this));
        setField(F_STORAGE, proxiedStorage);
        proxiedStorage.useRegisteredCapacities();
        proxiedStorage.setCapacity(AEKeyType.items(), OVERLOADED_CAP);

        var newUpgrades = UpgradeInventories.forMachine(is, 4, () -> {
            invokeQuietly(M_ON_UPGRADES_CHANGED, this);
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
            if (key == null || !key.equals(what)) continue;
            long cap = owner.isSlotUnlimited(i) ? OVERLOADED_CAP : cfg.getAmount(i);
            invokeCrafting(i, what, cap);
            break;
        }
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

            var cache = grid.getStorageService().getCachedInventory();
            var cfg = getConfig();
            boolean didWork = false;
            for (int i = 0; i < slotCount; i++) {
                var cfgStack = cfg.getStack(i);
                if (cfgStack == null) continue;
                long cap = owner.isSlotUnlimited(i) ? Long.MAX_VALUE : cfgStack.amount();
                if (cap == Long.MAX_VALUE) {
                    didWork |= invokeCrafting(i, cfgStack.what(), OVERLOADED_CAP);
                } else {
                    long deficit = cap - cache.get(cfgStack.what());
                    if (deficit > 0) {
                        didWork |= invokeCrafting(i, cfgStack.what(), deficit);
                    }
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
        private final KeyCounter availableStacksCache = new KeyCounter();
        private long availableStacksCacheTick = Long.MIN_VALUE;

        ProxiedStorageInv(OverloadedInterfaceLogic logic,
                          Set<AEKeyType> supportedTypes,
                          @Nullable AEKeySlotFilter slotFilter,
                          int size, @Nullable Runnable listener) {
            super(supportedTypes, slotFilter, GenericStackInv.Mode.STORAGE, size, listener);
            this.logic = logic;
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

        private long networkAmount(AEKey key) {
            var grid = logic.mainNode.getGrid();
            if (grid == null) return 0;
            return grid.getStorageService().getCachedInventory().get(key);
        }

        private long displayAmount(int slot, AEKey key) {
            long netAmt = networkAmount(key);
            long cap = capForSlot(slot);
            return (cap == Long.MAX_VALUE) ? netAmt : Math.min(cap, netAmt);
        }

        private boolean matchesConfiguredSlot(int slot, AEKey what) {
            var configured = cfg().getKey(slot);
            if (configured == null) return false;
            if (configured.equals(what)) return true;
            if (!logic.ourUpgrades.isInstalled(AEItems.FUZZY_CARD)) return false;
            if (!configured.supportsFuzzyRangeSearch()) return false;
            var fuzzyMode = logic.getConfigManager().getSetting(Settings.FUZZY_MODE);
            return configured.fuzzyEquals(what, fuzzyMode);
        }

        private long exposedAmount(AEKey what) {
            var grid = logic.mainNode.getGrid();
            if (grid == null) return 0;
            var cache = grid.getStorageService().getCachedInventory();
            long total = 0;
            for (int i = 0; i < size(); i++) {
                if (!matchesConfiguredSlot(i, what)) continue;
                long netAmt = cache.get(what);
                long cap = capForSlot(i);
                long amount = (cap == Long.MAX_VALUE) ? netAmt : Math.min(cap, netAmt);
                if (Long.MAX_VALUE - total < amount) return Long.MAX_VALUE;
                total += amount;
            }
            return total;
        }

        // ── Display: server queries ME network & syncs stacks[]; client uses synced stacks[] ─

        @Override
        public @Nullable GenericStack getStack(int slot) {
            if (logic.mainNode.getGrid() == null) {
                return super.getStack(slot);
            }
            var key = cfg().getKey(slot);
            if (key == null) {
                stacks[slot] = null;
                return null;
            }
            long amt = displayAmount(slot, key);
            var result = amt > 0 ? new GenericStack(key, amt) : null;
            stacks[slot] = result;
            return result;
        }

        @Override
        public @Nullable AEKey getKey(int slot) {
            if (logic.mainNode.getGrid() == null) {
                return super.getKey(slot);
            }
            return cfg().getKey(slot);
        }

        @Override
        public long getAmount(int slot) {
            if (logic.mainNode.getGrid() == null) {
                return super.getAmount(slot);
            }
            var key = cfg().getKey(slot);
            if (key == null) return 0;
            return displayAmount(slot, key);
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
            var key = cfg().getKey(slot);
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
                availableStacksCache.reset();
                var cache = grid.getStorageService().getCachedInventory();
                boolean fuzzy = logic.ourUpgrades.isInstalled(AEItems.FUZZY_CARD);
                var fuzzyMode = fuzzy ? logic.getConfigManager().getSetting(Settings.FUZZY_MODE) : null;
                for (int slot = 0; slot < size(); slot++) {
                    var key = cfg().getKey(slot);
                    if (key == null) continue;

                    long cap = capForSlot(slot);
                    if (fuzzy && key.supportsFuzzyRangeSearch()) {
                        for (var entry : cache.findFuzzy(key, fuzzyMode)) {
                            long amount = cap == Long.MAX_VALUE
                                    ? entry.getLongValue()
                                    : Math.min(cap, entry.getLongValue());
                            if (amount > 0) availableStacksCache.add(entry.getKey(), amount);
                        }
                    } else {
                        long amount = displayAmount(slot, key);
                        if (amount > 0) availableStacksCache.add(key, amount);
                    }
                }
                availableStacksCacheTick = now;
                for (var entry : availableStacksCache) {
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
