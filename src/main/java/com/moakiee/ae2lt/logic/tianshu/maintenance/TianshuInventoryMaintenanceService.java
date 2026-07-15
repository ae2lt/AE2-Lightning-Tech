package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.me.service.CraftingService;
import com.google.common.collect.ImmutableSet;
import com.moakiee.thunderbolt.ae2.crafting.ReservedStockCraftingRequester;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class TianshuInventoryMaintenanceService
        implements ICraftingRequester, ICraftingSimulationRequester {
    private static final String TAG_REPOSITORY = "Repository";
    private static final String TAG_LINKS = "Links";
    private static final String TAG_RESERVED_STOCK = "ReservedStock";
    private static final String TAG_RULE_RESERVED_STOCK = "RuleReservedStock";
    private static final long CHECK_INTERVAL = 10L;
    private static final long RETRY_INTERVAL = 100L;

    private final TianshuInventoryMaintenanceHost host;
    private final InventoryMaintenanceRepository repository;
    private final ReservedStockRepository reservedStock;
    private final Map<UUID, ReservedStockRepository> ruleReservedStock = new HashMap<>();
    private final Map<UUID, PendingCalculation> calculations = new HashMap<>();
    private final Map<UUID, InventoryMaintenanceStatus> statuses = new HashMap<>();
    private final Map<UUID, Long> retryAfter = new HashMap<>();
    private final LinkedHashSet<CraftingLink> links = new LinkedHashSet<>();
    private long nextCheckTick;
    private boolean linksRestored;
    private IGrid restoredGrid;

    public TianshuInventoryMaintenanceService(TianshuInventoryMaintenanceHost host) {
        this.host = host;
        this.repository = new InventoryMaintenanceRepository(
                () -> host.getFunctionProfile().maintenanceRuleCapacity());
        this.reservedStock = new ReservedStockRepository(
                () -> host.getFunctionProfile().maintenanceRuleCapacity());
    }

    public InventoryMaintenanceRepository repository() { return repository; }
    public ReservedStockRepository reservedStock() { return reservedStock; }
    public ReservedStockRepository reservedStock(UUID ruleId) {
        if (ruleId == null) return reservedStock;
        return ruleReservedStock.computeIfAbsent(ruleId,
                ignored -> new ReservedStockRepository(
                        () -> host.getFunctionProfile().maintenanceRuleCapacity()));
    }

    public ReservedStockRepository.PutResult setMaintenanceWideReservedStock(
            AEKey key, ReservedStockMatchMode mode, long amount) {
        if (!canConfigure()) return ReservedStockRepository.PutResult.UNAVAILABLE;
        var result = reservedStock.set(key, mode, amount);
        if (result != ReservedStockRepository.PutResult.INVALID
                && result != ReservedStockRepository.PutResult.FULL
                && result != ReservedStockRepository.PutResult.UNAVAILABLE) {
            host.maintenanceStateChanged();
        }
        return result;
    }

    public InventoryMaintenanceStatus status(UUID ruleId) {
        return statuses.getOrDefault(ruleId, InventoryMaintenanceStatus.IDLE);
    }

    /** Stored and craftable exact variants shown by the reserve-stock editor. */
    public java.util.List<MaintenanceVariantService.Variant> variants(AEKey selected) {
        var grid = host.getGrid();
        if (grid == null || selected == null) return java.util.List.of();
        return MaintenanceVariantService.list(
                grid.getStorageService().getInventory(), grid.getCraftingService(), selected);
    }

    public InventoryMaintenanceRepository.PutResult putRule(InventoryMaintenanceRule rule) {
        if (!canConfigure() || rule == null) return InventoryMaintenanceRepository.PutResult.UNAVAILABLE;
        var previous = repository.get(rule.key());
        if (previous != null && !previous.id().equals(rule.id())) {
            return InventoryMaintenanceRepository.PutResult.INVALID;
        }
        var result = repository.put(rule);
        if ((result == InventoryMaintenanceRepository.PutResult.ADDED
                || result == InventoryMaintenanceRepository.PutResult.UPDATED)) {
            nextCheckTick = 0L;
            host.maintenanceStateChanged();
        }
        return result;
    }

    public boolean removeRule(UUID ruleId) {
        var rule = repository.getById(ruleId);
        if (!canConfigure() || rule == null) return false;
        cancelRuleTask(ruleId);
        cancelCalculation(ruleId);
        boolean removed = repository.remove(rule.key());
        ruleReservedStock.remove(ruleId);
        retryAfter.remove(ruleId);
        statuses.remove(ruleId);
        if (removed) host.maintenanceStateChanged();
        return removed;
    }

    public ReservedStockRepository.PutResult setReservedStock(
            UUID ruleId, AEKey key, long amount) {
        return setReservedStock(ruleId, key, ReservedStockMatchMode.EXACT, amount);
    }

    public ReservedStockRepository.PutResult setReservedStock(
            UUID ruleId, AEKey key, ReservedStockMatchMode mode, long amount) {
        if (!canConfigure() || repository.getById(ruleId) == null) {
            return ReservedStockRepository.PutResult.UNAVAILABLE;
        }
        var result = reservedStock(ruleId).set(key, mode, amount);
        if (result != ReservedStockRepository.PutResult.INVALID
                && result != ReservedStockRepository.PutResult.FULL
                && result != ReservedStockRepository.PutResult.UNAVAILABLE) {
            host.maintenanceStateChanged();
        }
        return result;
    }

    /** First call waits for closed-loop seeds; a second call hard-cancels the same time-wheel job. */
    public boolean cancelRuleTask(UUID ruleId) {
        var rule = repository.getById(ruleId);
        if (rule == null || rule.activeCraftingId() == null) return false;
        UUID craftingId = rule.activeCraftingId();
        for (var cpu : host.getTimeWheelCraftingCpuPool().getActiveCpus()) {
            var link = cpu.getCraftingLogic().getLastLink();
            if (link != null && craftingId.equals(link.getCraftingID())) {
                cpu.cancelJob();
                statuses.put(ruleId, InventoryMaintenanceStatus.CANCELLING);
                return true;
            }
        }
        var link = findLink(craftingId);
        if (link != null) {
            link.cancel();
            statuses.put(ruleId, InventoryMaintenanceStatus.CANCELLING);
            return true;
        }
        return false;
    }

    public boolean retryNow(UUID ruleId) {
        if (repository.getById(ruleId) == null) return false;
        retryAfter.remove(ruleId);
        nextCheckTick = 0L;
        statuses.put(ruleId, InventoryMaintenanceStatus.IDLE);
        return true;
    }

    private boolean canConfigure() {
        return host.isFormed() && host.getFunctionProfile().supportsInventoryMaintenance();
    }

    public void tick() {
        var level = host.getLevel();
        var grid = host.getGrid();
        if (level == null || level.isClientSide || grid == null || !host.isCpuActive()
                || !host.getFunctionProfile().supportsInventoryMaintenance()) return;
        restoreLinks((CraftingService) grid.getCraftingService());
        long now = level.getGameTime();
        if (now < nextCheckTick) return;
        nextCheckTick = now + CHECK_INTERVAL;

        var crafting = (CraftingService) grid.getCraftingService();
        var activeRuleIds = new HashSet<UUID>();
        for (var activeRule : repository.activeRules()) activeRuleIds.add(activeRule.id());
        pollCalculations(crafting, activeRuleIds);
        for (var original : repository.rules()) {
            var rule = repository.get(original.key());
            if (rule == null) continue;
            var link = findLink(rule.activeCraftingId());
            boolean activeLink = link != null && !link.isDone() && !link.isCanceled();
            if (link != null && !activeLink) {
                links.remove(link);
                rule = rule.withRuntime(rule.replenishing(), null);
                repository.put(rule);
                host.maintenanceStateChanged();
            }
            if (!activeRuleIds.contains(rule.id())) {
                cancelCalculation(rule.id());
                statuses.put(rule.id(), activeLink
                        ? InventoryMaintenanceStatus.CRAFTING : InventoryMaintenanceStatus.DISABLED);
                continue;
            }
            if (!rule.enabled()) {
                statuses.put(rule.id(), InventoryMaintenanceStatus.DISABLED);
                continue;
            }

            long stock = grid.getStorageService().getInventory()
                    .extract(rule.key(), Long.MAX_VALUE, Actionable.SIMULATE, host.getActionSource());
            boolean calculationActive = calculations.containsKey(rule.id());
            boolean otherCalculationActive = InventoryMaintenanceCalculationClaims.claimedByOther(
                    grid, rule.key(), rule.id());
            boolean networkTaskActive = activeLink || calculationActive || crafting.isRequesting(rule.key())
                    || otherCalculationActive;
            var decision = InventoryMaintenanceDecision.evaluate(rule, stock, networkTaskActive);
            if (decision.replenishing() != rule.replenishing()) {
                rule = rule.withRuntime(decision.replenishing(), rule.activeCraftingId());
                repository.put(rule);
                host.maintenanceStateChanged();
            }
            if (activeLink) {
                statuses.put(rule.id(), InventoryMaintenanceStatus.CRAFTING);
            } else if (calculationActive) {
                statuses.put(rule.id(), InventoryMaintenanceStatus.CALCULATING);
            } else if (!decision.replenishing()) {
                retryAfter.remove(rule.id());
                statuses.put(rule.id(), stock >= rule.upperThreshold()
                        ? InventoryMaintenanceStatus.SATISFIED : InventoryMaintenanceStatus.IDLE);
            } else if (now < retryAfter.getOrDefault(rule.id(), 0L)) {
                statuses.putIfAbsent(rule.id(), InventoryMaintenanceStatus.WAITING_RETRY);
            } else if (otherCalculationActive) {
                statuses.put(rule.id(), InventoryMaintenanceStatus.CALCULATING);
            } else if (networkTaskActive) {
                statuses.put(rule.id(), InventoryMaintenanceStatus.CRAFTING);
            } else if (decision.requestAmount() > 0) {
                beginCalculation(crafting, rule, decision.requestAmount());
            }
        }
    }

    private void beginCalculation(CraftingService crafting, InventoryMaintenanceRule rule, long amount) {
        var grid = host.getGrid();
        var level = host.getLevel();
        if (!InventoryMaintenanceCalculationClaims.tryClaim(grid, rule.key(), rule.id())) {
            statuses.put(rule.id(), InventoryMaintenanceStatus.CALCULATING);
            return;
        }
        if (!crafting.isCraftable(rule.key())) {
            statuses.put(rule.id(), InventoryMaintenanceStatus.MISSING_PATTERN);
            scheduleRetry(rule.id());
            InventoryMaintenanceCalculationClaims.release(grid, rule.key(), rule.id());
            return;
        }
        Future<ICraftingPlan> future;
        try {
            future = crafting.beginCraftingCalculation(
                    host.getLevel(), new RuleCalculationRequester(rule.id()), rule.key(), amount,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
        } catch (RuntimeException failure) {
            InventoryMaintenanceCalculationClaims.release(grid, rule.key(), rule.id());
            statuses.put(rule.id(), InventoryMaintenanceStatus.WAITING_RETRY);
            scheduleRetry(rule.id());
            return;
        }
        calculations.put(rule.id(), new PendingCalculation(grid, rule.key(), amount, future));
        statuses.put(rule.id(), InventoryMaintenanceStatus.CALCULATING);
        host.maintenanceStateChanged();
    }

    private void pollCalculations(CraftingService crafting, java.util.Set<UUID> activeRuleIds) {
        var iterator = calculations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var pending = entry.getValue();
            if (pending.grid() != host.getGrid()) {
                iterator.remove();
                pending.future().cancel(false);
                InventoryMaintenanceCalculationClaims.release(
                        pending.grid(), pending.key(), entry.getKey());
                statuses.put(entry.getKey(), InventoryMaintenanceStatus.IDLE);
                continue;
            }
            if (!pending.future().isDone()) continue;
            iterator.remove();
            InventoryMaintenanceCalculationClaims.release(
                    pending.grid(), pending.key(), entry.getKey());
            var rule = repository.get(pending.key());
            if (rule == null || !rule.id().equals(entry.getKey()) || !rule.enabled()
                    || !activeRuleIds.contains(rule.id())) continue;
            try {
                var plan = pending.future().get();
                if (plan == null || plan.simulation() || !plan.missingItems().isEmpty()) {
                    statuses.put(rule.id(), InventoryMaintenanceStatus.MISSING_INGREDIENTS);
                    scheduleRetry(rule.id());
                    continue;
                }
                if (crafting.isRequesting(rule.key())) {
                    statuses.put(rule.id(), InventoryMaintenanceStatus.IDLE);
                    continue;
                }
                if (!respectsCurrentReservedStock(rule.id(), plan)) {
                    statuses.put(rule.id(), InventoryMaintenanceStatus.WAITING_RETRY);
                    scheduleRetry(rule.id());
                    continue;
                }
                var submitted = crafting.submitJob(plan, this, null, false, host.getActionSource());
                if (!submitted.successful() || submitted.link() == null) {
                    statuses.put(rule.id(), InventoryMaintenanceStatus.WAITING_CPU);
                    scheduleRetry(rule.id());
                    continue;
                }
                var link = (CraftingLink) submitted.link();
                links.add(link);
                repository.put(rule.withRuntime(true, link.getCraftingID()));
                statuses.put(rule.id(), InventoryMaintenanceStatus.CRAFTING);
                retryAfter.remove(rule.id());
                host.maintenanceStateChanged();
            } catch (Exception ignored) {
                statuses.put(rule.id(), InventoryMaintenanceStatus.MISSING_INGREDIENTS);
                scheduleRetry(rule.id());
            }
        }
    }

    private void scheduleRetry(UUID ruleId) {
        var level = host.getLevel();
        retryAfter.put(ruleId, (level != null ? level.getGameTime() : 0L) + RETRY_INTERVAL);
    }

    private boolean respectsCurrentReservedStock(UUID ruleId, ICraftingPlan plan) {
        var grid = host.getGrid();
        if (grid == null || plan == null) return false;
        var policy = reservedStockPolicy(ruleId);
        if (policy.isEmpty()) return true;
        var inventory = grid.getStorageService().getInventory();
        var available = inventory.getAvailableStacks();
        for (var used : plan.usedItems()) {
            if (used.getLongValue() <= 0L) continue;
            var key = used.getKey();
            long current = Math.max(0L, available.get(key));
            long usable;
            if (policy.groupsSecondaryVariants(key)) {
                var group = new HashMap<AEKey, Long>();
                for (var entry : available) {
                    if (entry.getKey().dropSecondary().equals(key.dropSecondary())) {
                        group.put(entry.getKey(), Math.max(0L, entry.getLongValue()));
                    }
                }
                usable = policy.usablePreexistingStock(key, current, group);
            } else {
                usable = policy.usablePreexistingStock(key, current);
            }
            if (used.getLongValue() > usable) return false;
        }
        return true;
    }

    private LayeredReservedStockPolicy reservedStockPolicy(UUID ruleId) {
        return new LayeredReservedStockPolicy(reservedStock, ruleReservedStock.get(ruleId));
    }

    private void cancelCalculation(UUID ruleId) {
        var pending = calculations.remove(ruleId);
        if (pending != null) {
            pending.future().cancel(false);
            InventoryMaintenanceCalculationClaims.release(
                    pending.grid(), pending.key(), ruleId);
        }
    }

    /** Cancels non-persistent calculations and releases their requester-style lifecycle claims. */
    public void shutdownCalculations() {
        for (var entry : calculations.entrySet()) {
            entry.getValue().future().cancel(false);
            InventoryMaintenanceCalculationClaims.release(
                    entry.getValue().grid(), entry.getValue().key(), entry.getKey());
        }
        calculations.clear();
    }

    /** Applies changed multiblock capacity without deleting overflow configuration. */
    public void functionCapacityChanged() {
        var activeRuleIds = new HashSet<UUID>();
        for (var rule : repository.activeRules()) activeRuleIds.add(rule.id());
        for (var ruleId : java.util.List.copyOf(calculations.keySet())) {
            if (!activeRuleIds.contains(ruleId)) cancelCalculation(ruleId);
        }
        nextCheckTick = 0L;
    }

    private CraftingLink findLink(UUID id) {
        if (id == null) return null;
        for (var link : links) if (id.equals(link.getCraftingID())) return link;
        return null;
    }

    private void restoreLinks(CraftingService crafting) {
        var grid = host.getGrid();
        if (linksRestored && restoredGrid == grid) return;
        linksRestored = true;
        restoredGrid = grid;
        for (var link : links) crafting.addLink(link);
    }

    @Override public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.copyOf(links);
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        var grid = host.getGrid();
        if (grid == null || what == null || amount <= 0) return 0L;
        return grid.getStorageService().getInventory().insert(what, amount, mode, host.getActionSource());
    }

    @Override
    public void jobStateChange(ICraftingLink changed) {
        if (changed == null || (!changed.isDone() && !changed.isCanceled())) return;
        links.removeIf(link -> link.getCraftingID().equals(changed.getCraftingID()));
        for (var rule : repository.rules()) {
            if (changed.getCraftingID().equals(rule.activeCraftingId())) {
                repository.put(rule.withRuntime(rule.replenishing(), null));
                statuses.put(rule.id(), InventoryMaintenanceStatus.IDLE);
                if (changed.isCanceled()) scheduleRetry(rule.id());
            }
        }
        host.maintenanceStateChanged();
    }

    @Override public IGridNode getActionableNode() { return host.getActionableNode(); }
    @Override public IActionSource getActionSource() { return host.getActionSource(); }

    public void writeTo(CompoundTag parent, HolderLookup.Provider registries) {
        var repoTag = new CompoundTag();
        repository.writeTo(repoTag, registries);
        parent.put(TAG_REPOSITORY, repoTag);
        var reservedTag = new CompoundTag();
        reservedStock.writeTo(reservedTag, registries);
        parent.put(TAG_RESERVED_STOCK, reservedTag);
        var ruleReserves = new ListTag();
        for (var entry : ruleReservedStock.entrySet()) {
            if (entry.getValue().size() <= 0) continue;
            var tag = new CompoundTag();
            tag.putUUID("RuleId", entry.getKey());
            entry.getValue().writeTo(tag, registries);
            ruleReserves.add(tag);
        }
        parent.put(TAG_RULE_RESERVED_STOCK, ruleReserves);
        var linkTags = new ListTag();
        for (var link : links) {
            var tag = new CompoundTag();
            link.writeToNBT(tag);
            linkTags.add(tag);
        }
        parent.put(TAG_LINKS, linkTags);
    }

    public void readFrom(CompoundTag parent, HolderLookup.Provider registries) {
        shutdownCalculations();
        statuses.clear();
        retryAfter.clear();
        ruleReservedStock.clear();
        links.clear();
        linksRestored = false;
        restoredGrid = null;
        repository.readFrom(parent.getCompound(TAG_REPOSITORY), registries);
        reservedStock.readFrom(parent.getCompound(TAG_RESERVED_STOCK), registries);
        var ruleReserves = parent.getList(TAG_RULE_RESERVED_STOCK, Tag.TAG_COMPOUND);
        for (int i = 0; i < ruleReserves.size(); i++) {
            var tag = ruleReserves.getCompound(i);
            if (!tag.hasUUID("RuleId")) continue;
            var profile = new ReservedStockRepository(
                    () -> host.getFunctionProfile().maintenanceRuleCapacity());
            profile.readFrom(tag, registries);
            ruleReservedStock.put(tag.getUUID("RuleId"), profile);
        }
        var linkTags = parent.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < linkTags.size(); i++) {
            try { links.add(new CraftingLink(linkTags.getCompound(i), this)); }
            catch (RuntimeException ignored) { }
        }
    }

    private record PendingCalculation(
            IGrid grid, AEKey key, long amount, Future<ICraftingPlan> future) { }

    private final class RuleCalculationRequester
            implements ICraftingSimulationRequester, ReservedStockCraftingRequester {
        private final UUID ruleId;

        private RuleCalculationRequester(UUID ruleId) { this.ruleId = ruleId; }
        @Override public IActionSource getActionSource() { return host.getActionSource(); }
        @Override public IGridNode getGridNode() { return host.getActionableNode(); }
        @Override public long usablePreexistingStock(AEKey key, long snapshotAmount) {
            return reservedStockPolicy(ruleId).usablePreexistingStock(key, snapshotAmount);
        }
        @Override public boolean groupsSecondaryVariants(AEKey key) {
            return reservedStockPolicy(ruleId).groupsSecondaryVariants(key);
        }
        @Override public long usablePreexistingStock(
                AEKey key, long snapshotAmount, Map<AEKey, Long> groupSnapshot) {
            return reservedStockPolicy(ruleId)
                    .usablePreexistingStock(key, snapshotAmount, groupSnapshot);
        }
    }
}
