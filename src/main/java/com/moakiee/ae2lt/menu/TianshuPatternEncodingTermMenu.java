package com.moakiee.ae2lt.menu;

import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.api.crafting.PatternDetailsHelper;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.AdvancedAECompat;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopDiscoveryService;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopDiscoveryCandidate;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternUploadService;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternMultiplier;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.MaintenanceEditorData;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceRule;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.MaintenanceTopologyService;
import com.moakiee.ae2lt.network.tianshu.MaintenanceEditorSyncPacket;
import com.moakiee.ae2lt.network.tianshu.OpenMaintenanceEditorPacket;
import com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import com.moakiee.ae2lt.overload.pattern.PatternConversionService;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.thunderbolt.ae2.overload.pattern.Ae2PlainPatternResolver;
import com.moakiee.thunderbolt.ae2.overload.pattern.EditableOverloadPatternState;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternEditState;
import com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.api.config.Settings;
import appeng.api.config.ViewItems;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import java.util.LinkedHashMap;
import java.util.Map;
import com.moakiee.ae2lt.network.tianshu.SaveGlobalReservePacket;

public class TianshuPatternEncodingTermMenu extends PatternEncodingTermMenu {
    private static final MenuTypeBuilder.MenuFactory<
            TianshuPatternEncodingTermMenu, TianshuPatternTerminalHost> FACTORY =
            TianshuPatternEncodingTermMenu::new;
    public static final MenuType<TianshuPatternEncodingTermMenu> TYPE = MenuTypeBuilder
            .create(FACTORY, TianshuPatternTerminalHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "tianshu_pattern_encoding_terminal"));

    @GuiSync(110)
    public TianshuEncodingMode tianshuMode = TianshuEncodingMode.CRAFTING;

    @GuiSync(111)
    public OverloadPatternEditState overloadState = OverloadPatternEditState.empty();
    @GuiSync(112)
    public int advancedDirections;
    @GuiSync(113)
    public int closedLoopCandidateCount;
    @GuiSync(114)
    public int closedLoopCandidateIndex;
    @GuiSync(115)
    public int closedLoopParallelism = 1;
    @GuiSync(116)
    public int uploadState;
    @GuiSync(117)
    public boolean maintenanceAvailable;
    @GuiSync(118)
    public int uploadTargetCount;
    @GuiSync(119)
    public int uploadTargetIndex;
    @GuiSync(120)
    public String uploadTargetName = "";
    @GuiSync(121)
    public int uploadTargetFreeSlots;
    @GuiSync(122)
    public boolean maintainableView;
    @GuiSync(123)
    public boolean encodedClosedLoop;

    protected final TianshuPatternTerminalHost tianshuHost;
    private final PatternConversionService conversionService = new PatternConversionService();
    private ItemStack configuredSource = ItemStack.EMPTY;
    @Nullable private ParsedPatternDefinition overloadSource;
    private List<ClosedLoopDiscoveryCandidate> closedLoopCandidates = List.of();
    @Nullable private MaintenanceEditorData maintenanceEditorData;
    private int maintenanceEditorRevision;
    private List<PatternProviderLogicHost> uploadTargets = List.of();
    private int lastUploadTargetScan = Integer.MIN_VALUE;
    private int lastMaintenanceSummaryTick = Integer.MIN_VALUE;
    private List<MaintenanceSummarySyncPacket.Entry> maintenanceSummary = List.of();

    public TianshuPatternEncodingTermMenu(
            int id, Inventory inventory, TianshuPatternTerminalHost host) {
        this(TYPE, id, inventory, host);
    }

    protected TianshuPatternEncodingTermMenu(
            MenuType<?> type, int id, Inventory inventory, TianshuPatternTerminalHost host) {
        super(type, id, inventory, host, true);
        this.tianshuHost = host;
        this.tianshuMode = host.getTianshuEncodingMode();
        registerClientAction("setTianshuMode", TianshuEncodingMode.class, this::setTianshuModeServer);
        registerClientAction("multiplyProcessing", Integer.class, this::multiplyProcessingServer);
        registerClientAction("cycleAdvancedDirection", Integer.class, this::cycleAdvancedDirectionServer);
        registerClientAction("toggleOverloadInput", Integer.class, this::toggleOverloadInputServer);
        registerClientAction("toggleOverloadOutput", Integer.class, this::toggleOverloadOutputServer);
        registerClientAction("selectClosedLoopCandidate", Integer.class, this::selectClosedLoopCandidateServer);
        registerClientAction("changeClosedLoopParallelism", Integer.class, this::changeClosedLoopParallelismServer);
        registerClientAction("uploadTianshuPattern", this::uploadTianshuPatternServer);
        registerClientAction("cycleUploadTarget", Integer.class, this::cycleUploadTargetServer);
        registerClientAction("setMaintainableView", Boolean.class, this::setMaintainableViewServer);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            tianshuMode = tianshuHost.getTianshuEncodingMode();
            var selected = tianshuHost.getSelectedTianshu();
            maintenanceAvailable = selected != null
                    && selected.getFunctionProfile().supportsInventoryMaintenance();
            refreshUploadTargets();
            sendMaintenanceSummaryIfNeeded();
            refreshDerivedConfiguration();
        }
        super.broadcastChanges();
    }

    @Override
    public void setMode(EncodingMode mode) {
        if (mode != null) {
            var extended = TianshuEncodingMode.fromAe2(mode);
            if (isClientSide()) sendClientAction("setTianshuMode", extended);
            else setTianshuModeServer(extended);
        }
        super.setMode(mode);
    }

    public void setTianshuMode(TianshuEncodingMode mode) {
        if (mode == null) return;
        if (isClientSide()) {
            tianshuMode = mode;
            sendClientAction("setTianshuMode", mode);
        } else {
            setTianshuModeServer(mode);
        }
    }

    private void setTianshuModeServer(TianshuEncodingMode mode) {
        if (!isServerSide() || mode == null) return;
        if (mode == TianshuEncodingMode.ADVANCED && !AdvancedAECompat.isLoaded()) return;
        tianshuMode = mode;
        tianshuHost.setTianshuEncodingMode(mode);
        if (mode.ae2Mode() != null && getMode() != mode.ae2Mode()) super.setMode(mode.ae2Mode());
        broadcastChanges();
    }

    public void multiplyProcessing(int factor) {
        if (isClientSide()) sendClientAction("multiplyProcessing", factor);
        else multiplyProcessingServer(factor);
    }

    private void multiplyProcessingServer(int factor) {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.PROCESSING || !validFactor(factor)) return;
        var logic = tianshuHost.getLogic();
        if (ProcessingPatternMultiplier.apply(
                logic.getEncodedInputInv(), logic.getEncodedOutputInv(), factor)) {
            broadcastChanges();
        }
    }

    private static boolean validFactor(int factor) {
        return factor == 2 || factor == 4 || factor == 5 || factor == 10
                || factor == -2 || factor == -4 || factor == -5 || factor == -10;
    }

    public void cycleAdvancedDirection(int slot) {
        if (isClientSide()) sendClientAction("cycleAdvancedDirection", slot);
        else cycleAdvancedDirectionServer(slot);
    }

    private void cycleAdvancedDirectionServer(int slot) {
        if (!isServerSide() || slot < 0 || slot >= 9 || tianshuMode != TianshuEncodingMode.ADVANCED) return;
        int shift = slot * 3;
        int next = (((advancedDirections >>> shift) & 7) + 1) % 7;
        advancedDirections = (advancedDirections & ~(7 << shift)) | (next << shift);
        broadcastChanges();
    }

    public int getAdvancedDirection(int slot) {
        return slot >= 0 && slot < 9 ? (advancedDirections >>> (slot * 3)) & 7 : 0;
    }

    public void toggleOverloadInput(int slot) {
        if (isClientSide()) sendClientAction("toggleOverloadInput", slot);
        else toggleOverloadInputServer(slot);
    }

    public void toggleOverloadOutput(int slot) {
        if (isClientSide()) sendClientAction("toggleOverloadOutput", slot);
        else toggleOverloadOutputServer(slot);
    }

    private void toggleOverloadInputServer(int slot) {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.OVERLOAD || overloadSource == null) return;
        overloadState = overloadState.toggleInputMode(slot);
        broadcastChanges();
    }

    private void toggleOverloadOutputServer(int slot) {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.OVERLOAD || overloadSource == null) return;
        overloadState = overloadState.toggleOutputMode(slot);
        broadcastChanges();
    }

    public void selectClosedLoopCandidate(int delta) {
        if (isClientSide()) sendClientAction("selectClosedLoopCandidate", delta);
        else selectClosedLoopCandidateServer(delta);
    }

    private void selectClosedLoopCandidateServer(int delta) {
        if (!isServerSide() || closedLoopCandidates.isEmpty()) return;
        closedLoopCandidateIndex = Math.floorMod(closedLoopCandidateIndex + Integer.signum(delta),
                closedLoopCandidates.size());
        broadcastChanges();
    }

    public void changeClosedLoopParallelism(int delta) {
        if (isClientSide()) sendClientAction("changeClosedLoopParallelism", delta);
        else changeClosedLoopParallelismServer(delta);
    }

    private void changeClosedLoopParallelismServer(int delta) {
        if (!isServerSide() || delta == 0) return;
        closedLoopParallelism = Math.max(1, Math.min(1024, closedLoopParallelism + delta));
        broadcastChanges();
    }

    public void uploadTianshuPattern() {
        if (isClientSide()) sendClientAction("uploadTianshuPattern");
        else uploadTianshuPatternServer();
    }

    private void uploadTianshuPatternServer() {
        if (!isServerSide()) return;
        uploadState = 2;
        var stack = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (stack.isEmpty()) {
            uploadState = 3;
            broadcastChanges();
            return;
        }
        if (tianshuMode == TianshuEncodingMode.CLOSED_LOOP
                && !(stack.getItem() instanceof ClosedLoopPatternItem)) {
            uploadState = 3;
            broadcastChanges();
            return;
        }
        if (stack.getItem() instanceof ClosedLoopPatternItem item) {
            var target = tianshuHost.getSelectedTianshu();
            var payload = item.readPayload(stack, getPlayer().level()).orElse(null);
            var result = ClosedLoopPatternUploadService.upload(target, payload);
            uploadState = switch (result) {
                case ADDED, UPDATED -> 1;
                default -> 3;
            };
        } else {
            refreshUploadTargetsNow();
            if (uploadTargets.isEmpty()) {
                uploadState = 3;
            } else {
                int index = Math.max(0, Math.min(uploadTargetIndex, uploadTargets.size() - 1));
                var target = uploadTargets.get(index);
                var inventory = target.getTerminalPatternInventory();
                int free = firstFreePatternSlot(inventory, stack);
                if (free < 0) uploadState = 3;
                else {
                    var sourceInventory = tianshuHost.getLogic().getEncodedPatternInv();
                    var removed = sourceInventory.extractItem(0, 1, false);
                    if (removed.isEmpty() || !ItemStack.isSameItemSameComponents(stack, removed)) {
                        if (!removed.isEmpty()) sourceInventory.addItems(removed);
                        uploadState = 3;
                    } else try {
                        inventory.setItemDirect(free, removed);
                        target.saveChanges();
                        uploadState = 1;
                        refreshUploadTargetsNow();
                    } catch (RuntimeException failure) {
                        sourceInventory.addItems(removed);
                        uploadState = 3;
                    }
                }
            }
        }
        broadcastChanges();
    }

    public void cycleUploadTarget(int delta) {
        if (isClientSide()) sendClientAction("cycleUploadTarget", delta);
        else cycleUploadTargetServer(delta);
    }

    private void cycleUploadTargetServer(int delta) {
        if (!isServerSide()) return;
        refreshUploadTargetsNow();
        if (!uploadTargets.isEmpty()) {
            uploadTargetIndex = Math.floorMod(uploadTargetIndex + Integer.signum(delta), uploadTargets.size());
            updateUploadTargetSync();
        }
        broadcastChanges();
    }

    private void refreshUploadTargets() {
        if (getPlayer().tickCount - lastUploadTargetScan >= 20) refreshUploadTargetsNow();
    }

    private void refreshUploadTargetsNow() {
        lastUploadTargetScan = getPlayer().tickCount;
        var node = tianshuHost.getActionableNode();
        var grid = node != null ? node.getGrid() : null;
        if (grid == null) {
            uploadTargets = List.of();
            updateUploadTargetSync();
            return;
        }
        var found = new ArrayList<PatternProviderLogicHost>();
        for (var gridNode : grid.getNodes()) {
            if (!gridNode.isActive() || !(gridNode.getOwner() instanceof PatternProviderLogicHost host)
                    || !host.isVisibleInTerminal()) continue;
            var inv = host.getTerminalPatternInventory();
            if (inv != null && inv.size() > 0) found.add(host);
        }
        found.sort(java.util.Comparator
                .comparing((PatternProviderLogicHost host) -> host.getTerminalGroup().name().getString())
                .thenComparingLong(PatternProviderLogicHost::getTerminalSortOrder));
        uploadTargets = List.copyOf(found);
        if (uploadTargetIndex >= uploadTargets.size()) uploadTargetIndex = 0;
        updateUploadTargetSync();
    }

    private void updateUploadTargetSync() {
        uploadTargetCount = uploadTargets.size();
        if (uploadTargets.isEmpty()) {
            uploadTargetName = "";
            uploadTargetFreeSlots = 0;
            return;
        }
        var target = uploadTargets.get(Math.max(0, Math.min(uploadTargetIndex, uploadTargets.size() - 1)));
        uploadTargetName = target.getTerminalGroup().name().getString();
        uploadTargetFreeSlots = countFreePatternSlots(target.getTerminalPatternInventory());
    }

    private static int countFreePatternSlots(appeng.api.inventories.InternalInventory inventory) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStackInSlot(i).isEmpty()) count++;
        return count;
    }

    private static int firstFreePatternSlot(
            appeng.api.inventories.InternalInventory inventory, ItemStack stack) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStackInSlot(i).isEmpty() && inventory.isItemValid(i, stack)) return i;
        }
        return -1;
    }

    private void refreshDerivedConfiguration() {
        var source = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (ItemStack.matches(configuredSource, source)) return;
        configuredSource = source.copy();
        encodedClosedLoop = source.getItem() instanceof ClosedLoopPatternItem;
        advancedDirections = 0;
        uploadState = 0;
        overloadSource = null;
        overloadState = OverloadPatternEditState.empty();
        closedLoopCandidates = List.of();
        closedLoopCandidateCount = 0;
        closedLoopCandidateIndex = 0;
        if (source.isEmpty()) return;
        var savedDirections = AdvancedAECompat.readDirections(source, getPlayer().level());
        for (int i = 0; i < Math.min(9, savedDirections.size()); i++) {
            advancedDirections |= (savedDirections.get(i) & 7) << (i * 3);
        }
        if (source.getItem() instanceof ClosedLoopPatternItem closedLoopItem) {
            var payload = closedLoopItem.readPayload(source, getPlayer().level()).orElse(null);
            if (payload != null) closedLoopParallelism = payload.parallelism();
            return;
        }
        refreshOverload(source);
        refreshClosedLoops(source);
    }

    private void refreshOverload(ItemStack source) {
        try {
            EditableOverloadPatternState editable = conversionService.resolveEditableSource(
                    source, new Ae2PlainPatternResolver(getPlayer().level()), registryAccess()).orElse(null);
            if (editable == null) return;
            overloadSource = editable.parsedPattern();
            overloadState = conversionService.createEditState(
                    editable.parsedPattern(), editable.encodedPattern(),
                    source.getItem() instanceof OverloadPatternItem);
        } catch (RuntimeException ignored) {
        }
    }

    private void refreshClosedLoops(ItemStack source) {
        var details = PatternDetailsHelper.decodePattern(source, getPlayer().level());
        if (details == null || details.getOutputs().isEmpty()) return;
        var output = details.getOutputs().getFirst();
        var node = tianshuHost.getActionableNode();
        var grid = node != null ? node.getGrid() : null;
        if (output == null || output.what() == null || grid == null) return;
        closedLoopCandidates = ClosedLoopDiscoveryService.discover(
                grid.getCraftingService(), getPlayer().level(), output.what());
        closedLoopCandidateCount = closedLoopCandidates.size();
    }

    public void requestMaintenanceEditor(appeng.api.stacks.AEKey key) {
        if (!isClientSide()) return;
        PacketDistributor.sendToServer(new OpenMaintenanceEditorPacket(containerId, key));
    }

    public void openMaintenanceEditor(appeng.api.stacks.AEKey key) {
        if (!isServerSide() || !(getPlayer() instanceof ServerPlayer serverPlayer)) return;
        var target = tianshuHost.getSelectedTianshu();
        if (key == null || target == null
                || !target.getFunctionProfile().supportsInventoryMaintenance()) return;
        sendMaintenanceEditorData(serverPlayer, key);
    }

    public void setMaintainableView(boolean enabled) {
        maintainableView = enabled;
        if (isClientSide()) {
            sendClientAction("setMaintainableView", enabled);
            applyMaintenanceSyntheticEntries();
        } else setMaintainableViewServer(enabled);
    }

    private void setMaintainableViewServer(boolean enabled) {
        if (!isServerSide()) return;
        maintainableView = enabled && maintenanceAvailable;
        getConfigManager().putSetting(Settings.VIEW_MODE, ViewItems.ALL);
        broadcastChanges();
    }

    @Override
    public boolean isKeyVisible(appeng.api.stacks.AEKey key) {
        if (!maintainableView) return super.isKeyVisible(key);
        var target = tianshuHost.getSelectedTianshu();
        return target != null && target.getInventoryMaintenance().repository().get(key) != null;
    }

    @Override
    protected boolean showsCraftables() {
        return maintainableView || super.showsCraftables();
    }

    private void sendMaintenanceSummaryIfNeeded() {
        if (!(getPlayer() instanceof ServerPlayer player)
                || getPlayer().tickCount - lastMaintenanceSummaryTick < 20) return;
        lastMaintenanceSummaryTick = getPlayer().tickCount;
        var target = tianshuHost.getSelectedTianshu();
        var summaries = new LinkedHashMap<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry>();
        if (target != null && target.getFunctionProfile().supportsInventoryMaintenance()) {
            var service = target.getInventoryMaintenance();
            for (var rule : service.repository().rules()) summaries.put(rule.key(), new MaintenanceSummarySyncPacket.Entry(
                    rule.key(), service.status(rule.id()), service.reservedStock().reserve(rule.key()),
                    service.reservedStock().matchMode(rule.key())));
            for (var reserve : service.reservedStock().reservations()) summaries.putIfAbsent(
                    reserve.key(), new MaintenanceSummarySyncPacket.Entry(
                            reserve.key(), InventoryMaintenanceStatus.IDLE,
                            reserve.amount(), reserve.mode()));
        }
        PacketDistributor.sendToPlayer(player, new MaintenanceSummarySyncPacket(
                containerId, List.copyOf(summaries.values())));
    }

    public void receiveMaintenanceSummary(List<MaintenanceSummarySyncPacket.Entry> entries) {
        if (!isClientSide()) return;
        maintenanceSummary = entries != null ? List.copyOf(entries) : List.of();
        applyMaintenanceSyntheticEntries();
    }

    private void applyMaintenanceSyntheticEntries() {
        if (!isClientSide() || !maintainableView || getClientRepo() == null) return;
        var existingKeys = new java.util.HashSet<appeng.api.stacks.AEKey>();
        for (var entry : getClientRepo().getAllEntries()) existingKeys.add(entry.getWhat());
        var synthetic = new ArrayList<appeng.menu.me.common.GridInventoryEntry>();
        long serial = Long.MIN_VALUE;
        for (var entry : maintenanceSummary) {
            if (!existingKeys.contains(entry.key())) synthetic.add(new appeng.menu.me.common.GridInventoryEntry(
                    serial++, entry.key(), 0L, 0L, false));
        }
        if (!synthetic.isEmpty()) getClientRepo().handleUpdate(false, synthetic);
    }

    public Map<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry> getMaintenanceSummary() {
        var result = new LinkedHashMap<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry>();
        for (var entry : maintenanceSummary) result.put(entry.key(), entry);
        return Map.copyOf(result);
    }

    public void sendGlobalReserve(appeng.api.stacks.AEKey key, long amount,
                                  com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode mode) {
        if (isClientSide() && key != null && mode != null) PacketDistributor.sendToServer(
                new SaveGlobalReservePacket(containerId, key, amount, mode));
    }

    public void saveGlobalReserve(SaveGlobalReservePacket packet) {
        if (!isServerSide() || packet == null || packet.amount() < -1) return;
        var target = tianshuHost.getSelectedTianshu();
        if (target == null || !target.getFunctionProfile().supportsInventoryMaintenance()) return;
        target.getInventoryMaintenance().setMaintenanceWideReservedStock(
                packet.key(), packet.mode(), packet.amount());
        lastMaintenanceSummaryTick = Integer.MIN_VALUE;
        broadcastChanges();
    }

    private void sendMaintenanceEditorData(ServerPlayer player, appeng.api.stacks.AEKey key) {
        var target = tianshuHost.getSelectedTianshu();
        if (target == null) return;
        var maintenance = target.getInventoryMaintenance();
        var rule = maintenance.repository().get(key);
        var grid = tianshuHost.getActionableNode() != null
                ? tianshuHost.getActionableNode().getGrid() : null;
        var topology = grid != null
                ? MaintenanceTopologyService.build(grid.getCraftingService(), key) : List.<MaintenanceTopologyService.Entry>of();
        var topologyData = new ArrayList<MaintenanceEditorData.TopologyEntry>(topology.size());
        for (var entry : topology) {
            var global = maintenance.reservedStock();
            var local = rule != null ? maintenance.reservedStock(rule.id()) : null;
            topologyData.add(new MaintenanceEditorData.TopologyEntry(
                    entry.key(), entry.depth(), entry.craftable(),
                    global.reserve(entry.key()), global.matchMode(entry.key()),
                    local != null ? local.reserve(entry.key()) : 0L,
                    local != null ? local.matchMode(entry.key())
                            : com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode.EXACT));
        }
        var variants = maintenance.variants(key).stream()
                .map(variant -> new MaintenanceEditorData.VariantEntry(
                        variant.key(), variant.storedAmount(), variant.craftable()))
                .toList();
        var data = new MaintenanceEditorData(key, rule != null ? rule.id() : null,
                rule != null ? rule.lowerThreshold() : 0L,
                rule != null ? rule.upperThreshold() : 64L,
                rule != null ? rule.amountPerJob() : 64L,
                rule == null || rule.enabled(),
                rule != null ? maintenance.status(rule.id()) : InventoryMaintenanceStatus.IDLE,
                topologyData, variants);
        PacketDistributor.sendToPlayer(player, new MaintenanceEditorSyncPacket(containerId, data));
    }

    public void receiveMaintenanceEditorData(MaintenanceEditorData data) {
        if (!isClientSide() || data == null) return;
        maintenanceEditorData = data;
        maintenanceEditorRevision++;
    }

    @Nullable public MaintenanceEditorData getMaintenanceEditorData() { return maintenanceEditorData; }
    public int getMaintenanceEditorRevision() { return maintenanceEditorRevision; }

    public void sendMaintenanceSave(SaveMaintenanceRulePacket packet) {
        if (isClientSide() && packet != null) PacketDistributor.sendToServer(packet);
    }

    public void saveMaintenanceRule(SaveMaintenanceRulePacket packet) {
        if (!isServerSide() || packet == null || !(getPlayer() instanceof ServerPlayer player)) return;
        var target = tianshuHost.getSelectedTianshu();
        if (target == null || !target.getFunctionProfile().supportsInventoryMaintenance()) return;
        var service = target.getInventoryMaintenance();
        var existing = service.repository().get(packet.target());
        if ((existing == null && packet.expectedRuleId() != null)
                || (existing != null && !existing.id().equals(packet.expectedRuleId()))) {
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        if (packet.delete()) {
            if (existing != null) service.removeRule(existing.id());
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        if (packet.lower() < 0 || packet.upper() <= packet.lower() || packet.amountPerJob() <= 0) {
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
            for (var edit : packet.reserves()) {
                service.setMaintenanceWideReservedStock(edit.key(), edit.globalMode(), edit.globalAmount());
                service.setReservedStock(ruleId, edit.key(), edit.ruleMode(), edit.ruleAmount());
            }
        }
        sendMaintenanceEditorData(player, packet.target());
    }

    @Override
    public void encode() {
        if (tianshuMode.isAe2Mode()) {
            super.encode();
            return;
        }
        if (isClientSide()) {
            sendClientAction("encode");
            return;
        }
        var result = encodeDerivedPattern();
        if (result != null && !result.isEmpty()) {
            tianshuHost.getLogic().getEncodedPatternInv().setItemDirect(0, result);
            broadcastChanges();
        }
    }

    private ItemStack encodeDerivedPattern() {
        var source = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (source.isEmpty()) return ItemStack.EMPTY;
        return switch (tianshuMode) {
            case ADVANCED -> AdvancedAECompat.encodeWithDirections(
                    source, getPlayer().level(), advancedDirectionList());
            case OVERLOAD -> encodeConfiguredOverload();
            case CLOSED_LOOP -> encodeSelectedClosedLoopCandidate();
            default -> ItemStack.EMPTY;
        };
    }

    private List<Integer> advancedDirectionList() {
        var result = new ArrayList<Integer>(9);
        for (int i = 0; i < 9; i++) result.add(getAdvancedDirection(i));
        return result;
    }

    private ItemStack encodeConfiguredOverload() {
        if (overloadSource == null || !overloadState.canEncode()) return ItemStack.EMPTY;
        return conversionService.createOverloadPatternStack(
                (OverloadPatternItem) ModItems.OVERLOAD_PATTERN.get(), overloadSource, overloadState);
    }

    private ItemStack encodeSelectedClosedLoopCandidate() {
        if (closedLoopCandidates.isEmpty()) return ItemStack.EMPTY;
        int index = Math.max(0, Math.min(closedLoopCandidateIndex, closedLoopCandidates.size() - 1));
        ClosedLoopPatternPayload payload = closedLoopCandidates.get(index).payload()
                .withParallelism(closedLoopParallelism);
        return ((ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get()).createStack(
                payload, registryAccess());
    }
}
