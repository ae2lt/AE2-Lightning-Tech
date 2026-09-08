package com.moakiee.ae2lt.menu;

import appeng.api.stacks.AEKey;
import appeng.api.config.Settings;
import appeng.api.config.ViewItems;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.maintenance.*;
import com.moakiee.ae2lt.logic.tianshu.terminal.MaintenanceEditorData;
import com.moakiee.ae2lt.network.tianshu.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Shared terminal session; the bound supercomputer owns all rules and running jobs. */
public final class TianshuMaintenanceSession {
    private final MEStorageMenu menu;
    private final Supplier<TianshuSupercomputerPortBlockEntity> target;
    private final IntSupplier selectionRevision;
    private final Consumer<MaintenanceAction> actionSender;
    @Nullable private MaintenanceEditorData maintenanceEditorData;
    private int maintenanceEditorRevision;
    private int maintenanceEditorSelectionRevision = Integer.MIN_VALUE;
    private int lastMaintenanceSummaryTick = Integer.MIN_VALUE;
    @Nullable private List<MaintenanceSummarySyncPacket.Entry> lastSentMaintenanceSummary;
    private boolean lastSentMaintenanceSummaryOverflow;
    private long maintenanceSummaryRevision;
    private long receivedMaintenanceSummaryRevision = Long.MIN_VALUE;
    private int maintenanceSummarySelectionRevision = Integer.MIN_VALUE;
    private boolean maintenanceSummaryOverflow;
    private List<MaintenanceSummarySyncPacket.Entry> maintenanceSummary = List.of();

    public TianshuMaintenanceSession(MEStorageMenu menu,
            Supplier<TianshuSupercomputerPortBlockEntity> target, IntSupplier selectionRevision,
            Consumer<MaintenanceAction> actionSender) {
        this.menu = menu;
        this.target = target;
        this.selectionRevision = selectionRevision;
        this.actionSender = actionSender;
    }

    private Player getPlayer() { return menu.getPlayer(); }
    private boolean isClientSide() { return menu.isClientSide(); }
    private boolean isServerSide() { return !menu.isClientSide(); }
    private void broadcastChanges() { menu.broadcastChanges(); }
    @Nullable private TianshuSupercomputerPortBlockEntity resolveBoundTianshu() { return target.get(); }

    public void invalidateTarget() {
        maintenanceEditorData = null;
        lastSentMaintenanceSummary = null;
        lastMaintenanceSummaryTick = Integer.MIN_VALUE;
    }

    public void resetClientTianshuScopedState() {
        if (!isClientSide()) return;
        if (maintenanceSummarySelectionRevision != selectionRevision.getAsInt()) {
            maintenanceSummary = List.of();
            maintenanceSummaryOverflow = false;
        }
        if (maintenanceEditorSelectionRevision != selectionRevision.getAsInt()) {
            maintenanceEditorData = null;
            maintenanceEditorRevision++;
        }
    }

    public void requestMaintenanceEditor(appeng.api.stacks.AEKey key) {
        if (!isClientSide()) return;
        PacketDistributor.sendToServer(new OpenMaintenanceEditorPacket(
                menu.containerId, selectionRevision.getAsInt(), key));
    }

    public void openMaintenanceEditor(int expectedSelectionRevision, appeng.api.stacks.AEKey key) {
        if (!isServerSide() || expectedSelectionRevision != selectionRevision.getAsInt()
                || !(getPlayer() instanceof ServerPlayer serverPlayer)) return;
        var target = resolveBoundTianshu();
        if (key == null || target == null
                || !target.getFunctionProfile().supportsInventoryMaintenance()) return;
        var maintenance = target.getInventoryMaintenance();
        var grid = target.getGrid();
        if (maintenance == null) return;
        if (maintenance.repository().get(key) == null
                && (grid == null || !MaintenanceRequestability.isRequestable(grid.getCraftingService(), key))) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "ae2lt.tianshu.maintenance.unsupported"), true);
            return;
        }
        sendMaintenanceEditorData(serverPlayer, key);
    }

    public void sendMaintenanceSummaryIfNeeded() {
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        if (lastMaintenanceSummaryTick != Integer.MIN_VALUE
                && player.tickCount - lastMaintenanceSummaryTick < 20) return;
        lastMaintenanceSummaryTick = getPlayer().tickCount;
        var target = resolveBoundTianshu();
        var summaries = new LinkedHashMap<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry>();
        boolean overflow = false;
        if (target != null && target.getFunctionProfile().supportsInventoryMaintenance()) {
            var service = target.getInventoryMaintenance();
            if (service != null) {
                var grid = target.getGrid();
                var available = grid != null
                        ? grid.getStorageService().getInventory().getAvailableStacks() : null;
                var crafting = grid != null ? grid.getCraftingService() : null;
                if (service.repository().size() > TianshuPacketLimits.MAX_LIST_ENTRIES
                        || service.reservedStock().size() > TianshuPacketLimits.MAX_LIST_ENTRIES) {
                    overflow = true;
                }
                var globalReservations = service.reservedStock()
                        .reservations(TianshuPacketLimits.MAX_LIST_ENTRIES);
                var directlyReservedKeys = new LinkedHashSet<appeng.api.stacks.AEKey>();
                for (var reserve : globalReservations) directlyReservedKeys.add(reserve.key());
                for (var rule : service.repository().rules(TianshuPacketLimits.MAX_LIST_ENTRIES)) {
                    if (summaries.size() >= TianshuPacketLimits.MAX_LIST_ENTRIES
                            && !summaries.containsKey(rule.key())) {
                        overflow = true;
                        break;
                    }
                    boolean ruleReserveOverflow = service.reservedStock(rule.id()).size()
                            > TianshuPacketLimits.MAX_LIST_ENTRIES;
                    long storedAmount = available != null ? Math.max(0L, available.get(rule.key())) : 0L;
                    boolean craftable = MaintenanceRequestability.isRequestable(crafting, rule.key());
                    summaries.put(rule.key(), new MaintenanceSummarySyncPacket.Entry(
                            rule.key(), true,
                            maintenanceSummaryStatus(rule, service.status(rule.id()), grid != null, craftable),
                            storedAmount, rule.lowerThreshold(), rule.upperThreshold(), rule.amountPerJob(),
                            service.reservedStock().reserve(rule.key()),
                            service.reservedStock().matchMode(rule.key()),
                            directlyReservedKeys.contains(rule.key()), craftable, ruleReserveOverflow));
                }
                for (var reserve : globalReservations) {
                    if (summaries.size() >= TianshuPacketLimits.MAX_LIST_ENTRIES
                            && !summaries.containsKey(reserve.key())) {
                        overflow = true;
                        break;
                    }
                    long storedAmount = available != null ? Math.max(0L, available.get(reserve.key())) : 0L;
                    boolean craftable = MaintenanceRequestability.isRequestable(crafting, reserve.key());
                    var existing = summaries.get(reserve.key());
                    summaries.put(reserve.key(), existing == null
                            ? new MaintenanceSummarySyncPacket.Entry(
                                    reserve.key(), false, InventoryMaintenanceStatus.IDLE,
                                    storedAmount, 0L, 0L, 0L,
                                    reserve.amount(), reserve.mode(), true, craftable, false)
                            : new MaintenanceSummarySyncPacket.Entry(
                                    existing.key(), existing.ruleConfigured(), existing.status(),
                                    existing.storedAmount(), existing.lowerThreshold(),
                                    existing.upperThreshold(), existing.amountPerJob(),
                                    reserve.amount(), reserve.mode(), true,
                                    existing.craftable(), existing.ruleReserveOverflow()));
                }
            }
        }
        // An explicit overflow marker makes this a recovery page, rather than a
        // silently-truncated authoritative snapshot. Deleting one of the visible
        // entries exposes the next persisted entry on the following revision.
        var snapshot = List.copyOf(summaries.values());
        if (lastSentMaintenanceSummary != null
                && lastSentMaintenanceSummaryOverflow == overflow
                && lastSentMaintenanceSummary.equals(snapshot)) return;
        lastSentMaintenanceSummary = snapshot;
        lastSentMaintenanceSummaryOverflow = overflow;
        maintenanceSummaryRevision++;
        PacketDistributor.sendToPlayer(player, new MaintenanceSummarySyncPacket(
                menu.containerId, selectionRevision.getAsInt(),
                maintenanceSummaryRevision, overflow, snapshot));
    }

    public void receiveMaintenanceSummary(
            int selectionRevision, long revision, boolean overflow,
            List<MaintenanceSummarySyncPacket.Entry> entries) {
        if (!isClientSide() || revision <= receivedMaintenanceSummaryRevision) return;
        if (selectionRevision < this.selectionRevision.getAsInt()) return;
        receivedMaintenanceSummaryRevision = revision;
        maintenanceSummarySelectionRevision = selectionRevision;
        maintenanceSummaryOverflow = overflow;
        maintenanceSummary = entries != null ? List.copyOf(entries) : List.of();
    }

    public boolean isMaintenanceSummaryOverflow() { return maintenanceSummaryOverflow; }

    public long getMaintenanceSummaryRevision() { return receivedMaintenanceSummaryRevision; }

    public Map<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry> getMaintenanceSummary() {
        var result = new LinkedHashMap<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry>();
        for (var entry : maintenanceSummary) result.put(entry.key(), entry);
        return Map.copyOf(result);
    }

    @Nullable
    public MaintenanceSummarySyncPacket.Entry getMaintenanceSummaryEntry(appeng.api.stacks.AEKey key) {
        if (key == null) return null;
        for (var entry : maintenanceSummary) {
            if (key.equals(entry.key())) return entry;
        }
        return null;
    }

    public void runMaintenanceAction(UUID ruleId, boolean cancel) {
        if (isClientSide() && ruleId != null) {
            actionSender.accept(new MaintenanceAction(selectionRevision.getAsInt(), ruleId, cancel));
        }
    }

    public void maintenanceActionServer(MaintenanceAction action) {
        if (!isServerSide() || action == null
                || action.selectionRevision() != selectionRevision.getAsInt()) return;
        var target = resolveBoundTianshu();
        var service = target != null ? target.getInventoryMaintenance() : null;
        if (service == null || service.repository().getById(action.ruleId()) == null) return;
        if (action.cancel()) service.cancelRuleTask(action.ruleId());
        else service.retryNow(action.ruleId());
        lastMaintenanceSummaryTick = Integer.MIN_VALUE;
        broadcastChanges();
    }

    public void sendGlobalReserve(appeng.api.stacks.AEKey key, long amount,
                                  com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode mode) {
        if (isClientSide() && key != null && mode != null) PacketDistributor.sendToServer(
                new SaveGlobalReservePacket(
                        menu.containerId, selectionRevision.getAsInt(), key, amount, mode));
    }

    public void saveGlobalReserve(SaveGlobalReservePacket packet) {
        if (!isServerSide() || packet == null || packet.amount() < -1
                || packet.selectionRevision() != selectionRevision.getAsInt()) return;
        var target = resolveBoundTianshu();
        if (target == null || !target.getFunctionProfile().supportsInventoryMaintenance()) return;
        var maintenance = target.getInventoryMaintenance();
        if (maintenance == null) return;
        if (packet.amount() != 0
                && maintenance.reservedStock().size() > TianshuPacketLimits.MAX_LIST_ENTRIES) {
            getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "ae2lt.tianshu.maintenance.too_large",
                    TianshuPacketLimits.MAX_LIST_ENTRIES), true);
            return;
        }
        if (packet.amount() != 0 && maintenance.reservedStock().reserve(packet.key()) == 0
                && maintenance.reservedStock().size() >= TianshuPacketLimits.MAX_LIST_ENTRIES) {
            return;
        }
        setGlobalReserveFromEditor(maintenance, packet.key(), packet.mode(), packet.amount());
        lastMaintenanceSummaryTick = Integer.MIN_VALUE;
        broadcastChanges();
    }

    private static void setGlobalReserveFromEditor(
            TianshuInventoryMaintenanceService maintenance,
            appeng.api.stacks.AEKey key,
            ReservedStockMatchMode mode,
            long amount) {
        var direct = maintenance.reservedStock().reservations().stream()
                .filter(entry -> entry.key().equals(key))
                .findFirst().orElse(null);
        // Switching an exact entry to grouped matching must remove that exact override first;
        // otherwise ReservedStockRepository correctly finds the existing group and would leave
        // the old exact entry shadowing the edit. Deletion likewise follows the persisted mode,
        // not a mode the player may have toggled immediately before pressing 0.
        if (direct != null && (amount == 0L || direct.mode() != mode)) {
            maintenance.setMaintenanceWideReservedStock(key, direct.mode(), 0L);
        }
        if (amount != 0L || direct == null) {
            maintenance.setMaintenanceWideReservedStock(key, mode, amount);
        }
    }

    private static void setRuleReserveFromEditor(
            TianshuInventoryMaintenanceService maintenance,
            UUID ruleId,
            appeng.api.stacks.AEKey key,
            ReservedStockMatchMode mode,
            long amount) {
        var repository = maintenance.reservedStock(ruleId);
        var direct = repository.reservations().stream()
                .filter(entry -> entry.key().equals(key))
                .findFirst().orElse(null);
        if (direct != null && (amount == 0L || direct.mode() != mode)) {
            maintenance.setReservedStock(ruleId, key, direct.mode(), 0L);
        }
        if (amount != 0L || direct == null) {
            maintenance.setReservedStock(ruleId, key, mode, amount);
        }
    }

    private void sendMaintenanceEditorData(ServerPlayer player, appeng.api.stacks.AEKey key) {
        var target = resolveBoundTianshu();
        if (target == null) return;
        var maintenance = target.getInventoryMaintenance();
        if (maintenance == null) return;
        var rule = maintenance.repository().get(key);
        var grid = target.getGrid();
        var available = grid != null
                ? grid.getStorageService().getInventory().getAvailableStacks() : null;
        var topology = grid != null
                ? MaintenanceTopologyService.build(grid.getCraftingService(), key) : List.<MaintenanceTopologyService.Entry>of();
        boolean recoveryPage = topology.size() > TianshuPacketLimits.MAX_LIST_ENTRIES;
        var global = maintenance.reservedStock();
        var local = rule != null ? maintenance.reservedStock(rule.id()) : null;
        var topologyByKey = new LinkedHashMap<appeng.api.stacks.AEKey, MaintenanceTopologyService.Entry>();
        for (var entry : topology) topologyByKey.putIfAbsent(entry.key(), entry);
        var topologyData = new LinkedHashMap<appeng.api.stacks.AEKey, MaintenanceEditorData.TopologyEntry>();

        // Persisted per-rule reserves come first. This keeps old entries that are no
        // longer part of the current crafting topology visible and lets amount=0
        // remove them even while the legacy repository remains oversized/read-only.
        if (local != null) {
            if (local.size() > TianshuPacketLimits.MAX_LIST_ENTRIES) recoveryPage = true;
            for (var saved : local.reservations(TianshuPacketLimits.MAX_LIST_ENTRIES)) {
                var topologyEntry = topologyByKey.get(saved.key());
                topologyData.put(saved.key(), maintenanceEditorEntry(
                        saved.key(), topologyEntry, available, global, local));
            }
        }
        for (var entry : topology) {
            if (topologyData.containsKey(entry.key())) continue;
            if (topologyData.size() >= TianshuPacketLimits.MAX_LIST_ENTRIES) {
                recoveryPage = true;
                break;
            }
            topologyData.put(entry.key(), maintenanceEditorEntry(
                    entry.key(), entry, available, global, local));
        }

        var allVariants = maintenance.variants(key);
        if (allVariants.size() > TianshuPacketLimits.MAX_LIST_ENTRIES) recoveryPage = true;
        var variants = allVariants.stream()
                .limit(TianshuPacketLimits.MAX_LIST_ENTRIES)
                .map(variant -> new MaintenanceEditorData.VariantEntry(
                        variant.key(), variant.storedAmount(), variant.craftable()))
                .toList();
        long currentStock = available != null ? Math.max(0L, available.get(key)) : 0L;
        boolean craftable = grid != null && MaintenanceRequestability.isRequestable(grid.getCraftingService(), key);
        var editorStatus = rule != null
                ? maintenanceSummaryStatus(rule, maintenance.status(rule.id()), grid != null, craftable)
                : InventoryMaintenanceStatus.IDLE;
        var data = new MaintenanceEditorData(key, rule != null ? rule.id() : null,
                rule != null ? rule.lowerThreshold() : 0L,
                rule != null ? rule.upperThreshold() : 64L,
                rule != null ? rule.amountPerJob() : 64L,
                rule == null || rule.enabled(),
                editorStatus, currentStock, craftable,
                recoveryPage, List.copyOf(topologyData.values()), variants);
        PacketDistributor.sendToPlayer(player, new MaintenanceEditorSyncPacket(
                menu.containerId, selectionRevision.getAsInt(), data));
    }

    private static MaintenanceEditorData.TopologyEntry maintenanceEditorEntry(
            appeng.api.stacks.AEKey key,
            @Nullable MaintenanceTopologyService.Entry topology,
            @Nullable appeng.api.stacks.KeyCounter available,
            com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockRepository global,
            @Nullable com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockRepository local) {
        return new MaintenanceEditorData.TopologyEntry(
                key, topology != null ? topology.depth() : 0,
                topology != null && topology.craftable(),
                available != null ? Math.max(0L, available.get(key)) : 0L,
                global.reserve(key), global.matchMode(key),
                local != null ? local.reserve(key) : 0L,
                local != null ? local.matchMode(key)
                        : com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode.EXACT);
    }

    private static InventoryMaintenanceStatus maintenanceSummaryStatus(
            InventoryMaintenanceRule rule,
            InventoryMaintenanceStatus runtimeStatus,
            boolean online,
            boolean craftable) {
        if (!rule.enabled()) return InventoryMaintenanceStatus.DISABLED;
        if (!online) return InventoryMaintenanceStatus.OFFLINE;
        var status = runtimeStatus != null ? runtimeStatus : InventoryMaintenanceStatus.IDLE;
        if (!craftable && status != InventoryMaintenanceStatus.CRAFTING
                && status != InventoryMaintenanceStatus.CANCELLING) {
            return InventoryMaintenanceStatus.MISSING_PATTERN;
        }
        return status;
    }

    public void receiveMaintenanceEditorData(int selectionRevision, MaintenanceEditorData data) {
        if (!isClientSide() || data == null || selectionRevision < this.selectionRevision.getAsInt()) return;
        maintenanceEditorSelectionRevision = selectionRevision;
        maintenanceEditorData = data;
        maintenanceEditorRevision++;
    }

    @Nullable public MaintenanceEditorData getMaintenanceEditorData() { return maintenanceEditorData; }
    public int getMaintenanceEditorRevision() { return maintenanceEditorRevision; }

    public void sendMaintenanceSave(SaveMaintenanceRulePacket packet) {
        if (isClientSide() && packet != null) {
            PacketDistributor.sendToServer(packet);
        }
    }

    public void saveMaintenanceRule(SaveMaintenanceRulePacket packet) {
        if (!isServerSide() || packet == null
                || packet.selectionRevision() != selectionRevision.getAsInt()
                || !(getPlayer() instanceof ServerPlayer player)) return;
        var target = resolveBoundTianshu();
        if (target == null || !target.getFunctionProfile().supportsInventoryMaintenance()) return;
        var service = target.getInventoryMaintenance();
        if (service == null) return;
        var existing = service.repository().get(packet.target());
        if ((existing == null && packet.expectedRuleId() != null)
                || (existing != null && !existing.id().equals(packet.expectedRuleId()))) {
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        if (packet.delete()) {
            if (existing != null && service.removeRule(existing.id())) {
                lastMaintenanceSummaryTick = Integer.MIN_VALUE;
            }
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        var editedReserveKeys = new LinkedHashSet<appeng.api.stacks.AEKey>();
        for (var edit : packet.reserves()) {
            if (edit == null || edit.key() == null
                    || edit.globalMode() == null || edit.ruleMode() == null
                    || edit.globalAmount() < -1L || edit.ruleAmount() < -1L
                    || !editedReserveKeys.add(edit.key())) {
                sendMaintenanceEditorData(player, packet.target());
                return;
            }
        }
        if (existing == null) {
            var grid = target.getGrid();
            if (grid == null || !MaintenanceRequestability.isRequestable(grid.getCraftingService(), packet.target())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "ae2lt.tianshu.maintenance.unsupported"), true);
                return;
            }
        }
        if (service.repository().size() > TianshuPacketLimits.MAX_LIST_ENTRIES) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "ae2lt.tianshu.maintenance.too_large",
                    TianshuPacketLimits.MAX_LIST_ENTRIES), true);
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        if (packet.lower() < 0 || packet.upper() < packet.lower() || packet.amountPerJob() <= 0) {
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        if (existing == null
                && service.repository().size() >= TianshuPacketLimits.MAX_LIST_ENTRIES) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "ae2lt.tianshu.maintenance.too_large",
                    TianshuPacketLimits.MAX_LIST_ENTRIES), true);
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        UUID ruleId = existing != null ? existing.id() : UUID.randomUUID();
        var rule = new InventoryMaintenanceRule(ruleId, packet.target(), packet.lower(), packet.upper(),
                packet.amountPerJob(), packet.enabled(),
                existing != null && existing.replenishing(),
                existing != null ? existing.activeCraftingId() : null);
        var result = service.putRule(rule);
        if (result == com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceRepository.PutResult.ADDED
                || result == com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceRepository.PutResult.UPDATED) {
            lastMaintenanceSummaryTick = Integer.MIN_VALUE;
            for (var edit : packet.reserves()) {
                setGlobalReserveFromEditor(
                        service, edit.key(), edit.globalMode(), edit.globalAmount());
                setRuleReserveFromEditor(
                        service, ruleId, edit.key(), edit.ruleMode(), edit.ruleAmount());
            }
        }
        sendMaintenanceEditorData(player, packet.target());
    }

    public record MaintenanceAction(int selectionRevision, UUID ruleId, boolean cancel) {}
}
