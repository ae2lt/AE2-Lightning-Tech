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
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAuthoringService;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternUploadService;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternMultiplier;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalTarget;
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
import appeng.helpers.patternprovider.PatternContainer;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.config.Settings;
import appeng.api.config.ViewItems;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import com.moakiee.ae2lt.network.tianshu.RequestUploadTargetsPacket;
import com.moakiee.ae2lt.network.tianshu.UploadPatternToTargetPacket;
import com.moakiee.ae2lt.network.tianshu.UploadTargetsSyncPacket;
import java.util.LinkedHashMap;
import java.util.Map;
import com.moakiee.ae2lt.network.tianshu.SaveGlobalReservePacket;
import com.moakiee.ae2lt.network.tianshu.TianshuPacketLimits;

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
    public int closedLoopSeedMultiplier = 1;
    @GuiSync(116)
    public int uploadState;
    @GuiSync(117)
    public boolean maintenanceAvailable;
    @GuiSync(122)
    public boolean maintainableView;
    @GuiSync(123)
    public boolean encodedClosedLoop;
    @GuiSync(124)
    public int triggeredUploadAck;
    /** 0=none, 1=member cannot be decoded, 2=other invalid closed-loop declaration. */
    @GuiSync(125)
    public int closedLoopEncodeState;
    @GuiSync(126)
    public String selectedTianshuMachine = "";
    @GuiSync(127)
    public String selectedTianshuLocation = "";
    @GuiSync(128)
    public int selectedTianshuIndex = -1;
    @GuiSync(129)
    public int availableTianshuCount;
    @GuiSync(130)
    public int tianshuSelectionRevision;

    protected final TianshuPatternTerminalHost tianshuHost;
    @Nullable private TianshuTerminalTarget boundTianshuTarget;
    private final PatternConversionService conversionService = new PatternConversionService();
    private ItemStack configuredSource = ItemStack.EMPTY;
    @Nullable private ParsedPatternDefinition overloadSource;
    private List<ClosedLoopDiscoveryCandidate> closedLoopCandidates = List.of();
    private List<ClosedLoopMemberPattern> closedLoopDraftMembers = List.of();
    @Nullable private appeng.api.stacks.AEKey closedLoopMainOutput;
    @Nullable private MaintenanceEditorData maintenanceEditorData;
    private int maintenanceEditorRevision;
    private int maintenanceEditorSelectionRevision = Integer.MIN_VALUE;
    private List<PatternContainer> uploadTargets = List.of();
    private List<TianshuUploadTargetData> uploadTargetGroups = List.of();
    private int uploadTargetsRevision;
    private int lastMaintenanceSummaryTick = Integer.MIN_VALUE;
    @Nullable private List<MaintenanceSummarySyncPacket.Entry> lastSentMaintenanceSummary;
    private boolean lastSentMaintenanceSummaryOverflow;
    private long maintenanceSummaryRevision;
    private long receivedMaintenanceSummaryRevision = Long.MIN_VALUE;
    private int maintenanceSummarySelectionRevision = Integer.MIN_VALUE;
    private boolean maintenanceSummaryOverflow;
    private List<MaintenanceSummarySyncPacket.Entry> maintenanceSummary = List.of();
    private boolean pendingTriggeredUpload;
    private int pendingTriggeredUploadUntil;
    private int expectedTriggeredUploadAck;
    private boolean tianshuSelectionPending;

    public TianshuPatternEncodingTermMenu(
            int id, Inventory inventory, TianshuPatternTerminalHost host) {
        this(TYPE, id, inventory, host);
    }

    protected TianshuPatternEncodingTermMenu(
            MenuType<?> type, int id, Inventory inventory, TianshuPatternTerminalHost host) {
        super(type, id, inventory, host, true);
        this.tianshuHost = host;
        this.boundTianshuTarget = inventory.player.level().isClientSide
                ? null : host.selectTianshuTarget();
        if (boundTianshuTarget != null) tianshuSelectionRevision = 1;
        this.tianshuMode = host.getTianshuEncodingMode();
        registerClientAction("setTianshuMode", TianshuEncodingMode.class, this::setTianshuModeServer);
        registerClientAction("multiplyProcessing", Integer.class, this::multiplyProcessingServer);
        registerClientAction("cycleAdvancedDirection", Integer.class, this::cycleAdvancedDirectionServer);
        registerClientAction("toggleOverloadInput", Integer.class, this::toggleOverloadInputServer);
        registerClientAction("toggleOverloadOutput", Integer.class, this::toggleOverloadOutputServer);
        registerClientAction("selectClosedLoopCandidate", Integer.class, this::selectClosedLoopCandidateServer);
        registerClientAction("changeClosedLoopSeedMultiplier", Integer.class,
                this::changeClosedLoopSeedMultiplierServer);
        registerClientAction("uploadTianshuPattern", Integer.class, this::uploadTianshuPatternServer);
        registerClientAction("uploadTianshuCraftingPattern", this::uploadTianshuCraftingPatternServer);
        registerClientAction("setMaintainableView", Boolean.class, this::setMaintainableViewServer);
        registerClientAction("cycleTianshuTarget", Integer.class, this::cycleTianshuTargetServer);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            tianshuMode = tianshuHost.getTianshuEncodingMode();
            var selected = resolveBoundTianshu();
            refreshTianshuSelectionSync(selected);
            maintenanceAvailable = selected != null
                    && selected.getFunctionProfile().supportsInventoryMaintenance();
            refreshDerivedConfiguration();
        }
        super.broadcastChanges();
        if (isServerSide()) sendMaintenanceSummaryIfNeeded();
    }

    /** Resolves only the machine captured when this server menu opened. */
    @Nullable
    private com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity resolveBoundTianshu() {
        return tianshuHost.resolveTianshuTarget(boundTianshuTarget);
    }

    private void refreshTianshuSelectionSync(
            @Nullable com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity selected) {
        var available = tianshuHost.getAvailableTianshu();
        availableTianshuCount = available.size();
        selectedTianshuIndex = -1;
        if (boundTianshuTarget == null) {
            selectedTianshuMachine = "";
            selectedTianshuLocation = "";
            return;
        }
        selectedTianshuMachine = boundTianshuTarget.machineId().toString();
        var pos = boundTianshuTarget.controllerPos();
        selectedTianshuLocation = boundTianshuTarget.dimension().location()
                + " @ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        if (selected == null) return;
        for (int i = 0; i < available.size(); i++) {
            if (boundTianshuTarget.matches(available.get(i))) {
                selectedTianshuIndex = i;
                break;
            }
        }
    }

    public void cycleTianshuTarget(int delta) {
        if (delta == 0) return;
        if (isClientSide()) {
            if (tianshuSelectionPending) return;
            tianshuSelectionPending = true;
            sendClientAction("cycleTianshuTarget", delta);
        } else {
            cycleTianshuTargetServer(delta);
        }
    }

    private void cycleTianshuTargetServer(int delta) {
        if (!isServerSide() || delta == 0) return;
        var available = tianshuHost.getAvailableTianshu();
        if (!available.isEmpty()) {
            int current = -1;
            for (int i = 0; i < available.size(); i++) {
                if (boundTianshuTarget != null && boundTianshuTarget.matches(available.get(i))) {
                    current = i;
                    break;
                }
            }
            int step = Integer.signum(delta);
            int next = current < 0
                    ? (step < 0 ? available.size() - 1 : 0)
                    : Math.floorMod(current + step, available.size());
            boundTianshuTarget = TianshuTerminalTarget.from(available.get(next));
        }
        // Also acknowledge a selection attempt if the topology changed between the
        // client click and server handling. This releases the client's pending gate
        // without ever falling back to a different machine implicitly.
        tianshuSelectionRevision++;
        maintenanceEditorData = null;
        lastSentMaintenanceSummary = null;
        lastMaintenanceSummaryTick = Integer.MIN_VALUE;
        uploadState = 0;
        broadcastChanges();
    }

    public void resetClientTianshuScopedState() {
        if (!isClientSide()) return;
        tianshuSelectionPending = false;
        if (maintenanceSummarySelectionRevision != tianshuSelectionRevision) {
            maintenanceSummary = List.of();
            maintenanceSummaryOverflow = false;
        }
        if (maintenanceEditorSelectionRevision != tianshuSelectionRevision) {
            maintenanceEditorData = null;
            maintenanceEditorRevision++;
        }
    }

    public boolean isTianshuSelectionPending() {
        return tianshuSelectionPending;
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

    public boolean consumeTriggeredUpload() {
        if (!isClientSide() || !pendingTriggeredUpload) return false;
        if (pendingTriggeredUpload && getPlayer().tickCount > pendingTriggeredUploadUntil) {
            pendingTriggeredUpload = false;
            return false;
        }
        if (triggeredUploadAck == expectedTriggeredUploadAck) return false;
        pendingTriggeredUpload = false;
        return true;
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
        fillClosedLoopDraftFromSelectedCandidate();
        broadcastChanges();
    }

    public void changeClosedLoopSeedMultiplier(int delta) {
        if (isClientSide()) sendClientAction("changeClosedLoopSeedMultiplier", delta);
        else changeClosedLoopSeedMultiplierServer(delta);
    }

    private void changeClosedLoopSeedMultiplierServer(int delta) {
        if (!isServerSide() || delta == 0) return;
        closedLoopSeedMultiplier = (int) Math.max(1L, Math.min(
                Integer.MAX_VALUE, (long) closedLoopSeedMultiplier + delta));
        broadcastChanges();
    }

    public void uploadTianshuPattern() {
        if (isClientSide()) {
            if (tianshuSelectionPending) return;
            sendClientAction("uploadTianshuPattern", tianshuSelectionRevision);
        }
        else uploadTianshuPatternServer(tianshuSelectionRevision);
    }

    private void uploadTianshuPatternServer(int expectedSelectionRevision) {
        if (!isServerSide() || expectedSelectionRevision != tianshuSelectionRevision) return;
        uploadState = 2;
        var stack = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (TianshuPatternUploadRouting.classify(stack, getPlayer().level())
                != TianshuPatternUploadRouting.Route.CLOSED_LOOP_STORAGE
                || !(stack.getItem() instanceof ClosedLoopPatternItem item)) {
            uploadState = 3;
            broadcastChanges();
            return;
        }
        var target = resolveBoundTianshu();
        var payload = item.readPayload(stack, getPlayer().level()).orElse(null);
        var result = ClosedLoopPatternUploadService.upload(target, payload);
        uploadState = switch (result) {
            case ADDED, UPDATED -> 1;
            default -> 3;
        };
        broadcastChanges();
    }

    public void requestUploadTargets() {
        if (isClientSide()) {
            PacketDistributor.sendToServer(new RequestUploadTargetsPacket(containerId));
        }
    }

    public void sendUploadTargets(ServerPlayer player) {
        if (!isServerSide() || player == null) return;
        refreshUploadTargetsNow();
        PacketDistributor.sendToPlayer(player,
                new UploadTargetsSyncPacket(containerId, uploadTargetGroups));
    }

    public void receiveUploadTargets(List<TianshuUploadTargetData> targets) {
        if (!isClientSide()) return;
        uploadTargetGroups = targets == null ? List.of() : List.copyOf(targets);
        uploadTargetsRevision++;
    }

    public List<TianshuUploadTargetData> getUploadTargets() {
        return uploadTargetGroups;
    }

    public int getUploadTargetsRevision() {
        return uploadTargetsRevision;
    }

    public void uploadTianshuPatternToTarget(PatternContainerGroup group) {
        if (!isClientSide() || group == null) return;
        uploadState = 2;
        PacketDistributor.sendToServer(new UploadPatternToTargetPacket(containerId, group));
    }

    public void uploadTianshuPatternToTarget(ServerPlayer player, PatternContainerGroup group) {
        if (!isServerSide() || player == null || group == null) return;
        uploadState = 2;
        var stack = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (TianshuPatternUploadRouting.classify(stack, getPlayer().level())
                != TianshuPatternUploadRouting.Route.PROCESSING_PROVIDER) {
            finishProviderUpload(player, false);
            return;
        }
        refreshUploadTargetsNow();
        PatternContainer selected = null;
        int selectedSlot = -1;
        for (var target : uploadTargets) {
            if (!group.equals(target.getTerminalGroup())) continue;
            int free = firstFreePatternSlot(target.getTerminalPatternInventory(), stack);
            if (free >= 0) {
                selected = target;
                selectedSlot = free;
                break;
            }
        }
        if (selected == null) {
            finishProviderUpload(player, false);
            return;
        }

        uploadToProvider(player, selected, selectedSlot, stack);
    }

    public void uploadTianshuCraftingPattern() {
        if (!isClientSide()) return;
        uploadState = 2;
        sendClientAction("uploadTianshuCraftingPattern");
    }

    private void uploadTianshuCraftingPatternServer() {
        if (!isServerSide() || !(getPlayer() instanceof ServerPlayer player)) return;
        uploadState = 2;
        var stack = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (TianshuPatternUploadRouting.classify(stack, getPlayer().level())
                != TianshuPatternUploadRouting.Route.CRAFTING_ASSEMBLER) {
            finishProviderUpload(player, false);
            return;
        }
        refreshUploadTargetsNow();
        for (var target : uploadTargets) {
            int free = firstFreePatternSlot(target.getTerminalPatternInventory(), stack);
            if (free >= 0) {
                uploadToProvider(player, target, free, stack);
                return;
            }
        }
        finishProviderUpload(player, false);
    }

    private void uploadToProvider(ServerPlayer player, PatternContainer selected,
                                  int selectedSlot, ItemStack stack) {
        var sourceInventory = tianshuHost.getLogic().getEncodedPatternInv();
        var removed = sourceInventory.extractItem(0, 1, false);
        if (removed.isEmpty() || !ItemStack.isSameItemSameComponents(stack, removed)) {
            if (!removed.isEmpty()) sourceInventory.addItems(removed);
            finishProviderUpload(player, false);
            return;
        }
        var targetInventory = selected.getTerminalPatternInventory();
        try {
            var remaining = targetInventory.insertItem(selectedSlot, removed, false);
            if (!remaining.isEmpty()) {
                sourceInventory.addItems(remaining);
                finishProviderUpload(player, false);
                return;
            }
            if (selected instanceof PatternProviderLogicHost logicHost) logicHost.saveChanges();
            finishProviderUpload(player, true);
        } catch (RuntimeException failure) {
            // insertItem is the provider-owned write path. Only return the source stack if
            // the provider demonstrably did not take ownership, otherwise avoid duplication.
            try {
                if (!ItemStack.isSameItemSameComponents(
                        targetInventory.getStackInSlot(selectedSlot), removed)) {
                    sourceInventory.addItems(removed);
                }
            } catch (RuntimeException ignored) {
            }
            finishProviderUpload(player, false);
        }
    }

    private void finishProviderUpload(ServerPlayer player, boolean success) {
        uploadState = success ? 1 : 3;
        refreshUploadTargetsNow();
        PacketDistributor.sendToPlayer(player,
                new UploadTargetsSyncPacket(containerId, uploadTargetGroups));
        broadcastChanges();
    }

    private void refreshUploadTargetsNow() {
        var node = tianshuHost.getActionableNode();
        var grid = node != null ? node.getGrid() : null;
        if (grid == null) {
            uploadTargets = List.of();
            uploadTargetGroups = List.of();
            return;
        }
        var found = new ArrayList<PatternContainer>();
        for (var machineClass : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) continue;
            @SuppressWarnings("unchecked")
            var containerClass = (Class<? extends PatternContainer>) machineClass;
            for (var container : grid.getActiveMachines(containerClass)) {
                if (!container.isVisibleInTerminal() || container.getGrid() != grid) continue;
                var inv = container.getTerminalPatternInventory();
                if (inv != null && inv.size() > 0) found.add(container);
            }
        }
        found.sort(java.util.Comparator
                .comparing((PatternContainer host) -> host.getTerminalGroup().name().getString())
                .thenComparingLong(PatternContainer::getTerminalSortOrder));
        uploadTargets = List.copyOf(found);
        var stack = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        var groups = new LinkedHashMap<PatternContainerGroup, MutableUploadGroup>();
        for (var target : uploadTargets) {
            var group = target.getTerminalGroup();
            var summary = groups.computeIfAbsent(group, ignored -> new MutableUploadGroup());
            summary.providers++;
            summary.availableSlots += countFreePatternSlots(
                    target.getTerminalPatternInventory(), stack);
        }
        uploadTargetGroups = groups.entrySet().stream()
                .map(entry -> new TianshuUploadTargetData(
                        entry.getKey(), entry.getValue().providers, entry.getValue().availableSlots))
                .toList();
    }

    private static int countFreePatternSlots(
            appeng.api.inventories.InternalInventory inventory, ItemStack stack) {
        if (inventory == null || stack == null || stack.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStackInSlot(i).isEmpty() && inventory.isItemValid(i, stack)) count++;
        }
        return count;
    }

    private static int firstFreePatternSlot(
            appeng.api.inventories.InternalInventory inventory, ItemStack stack) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStackInSlot(i).isEmpty() && inventory.isItemValid(i, stack)) return i;
        }
        return -1;
    }

    private static final class MutableUploadGroup {
        int providers;
        int availableSlots;
    }

    private void refreshDerivedConfiguration() {
        var source = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (ItemStack.matches(configuredSource, source)) return;
        configuredSource = source.copy();
        encodedClosedLoop = source.getItem() instanceof ClosedLoopPatternItem;
        advancedDirections = 0;
        // Preserve the result when a successful provider upload empties the source slot.
        // A newly inserted/encoded pattern starts a fresh upload state.
        if (!source.isEmpty()) uploadState = 0;
        overloadSource = null;
        overloadState = OverloadPatternEditState.empty();
        closedLoopCandidates = List.of();
        closedLoopDraftMembers = List.of();
        closedLoopMainOutput = null;
        closedLoopCandidateCount = 0;
        closedLoopCandidateIndex = 0;
        closedLoopEncodeState = 0;
        if (source.isEmpty()) return;
        var savedDirections = AdvancedAECompat.readDirections(source, getPlayer().level());
        for (int i = 0; i < Math.min(9, savedDirections.size()); i++) {
            advancedDirections |= (savedDirections.get(i) & 7) << (i * 3);
        }
        if (source.getItem() instanceof ClosedLoopPatternItem closedLoopItem) {
            var payload = closedLoopItem.readPayload(source, getPlayer().level()).orElse(null);
            if (payload != null) {
                closedLoopSeedMultiplier = payload.seedMultiplier();
                closedLoopDraftMembers = payload.memberPatterns();
                if (!payload.netOutputs().isEmpty()) {
                    closedLoopMainOutput = payload.netOutputs().getFirst().what();
                }
                var validation = com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternValidator
                        .validate(payload, getPlayer().level());
                if (!validation.valid()) {
                    closedLoopEncodeState = validation.status()
                            == com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopValidationResult.Status.MEMBER_UNDECODABLE
                            ? 1 : 2;
                }
            }
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
        if (details == null) {
            closedLoopEncodeState = 1;
            return;
        }
        if (details.getOutputs().isEmpty()) return;
        var output = details.getOutputs().getFirst();
        var node = tianshuHost.getActionableNode();
        var grid = node != null ? node.getGrid() : null;
        if (output == null || output.what() == null || grid == null) return;
        closedLoopMainOutput = output.what();
        var discovery = ClosedLoopDiscoveryService.discoverDetailed(
                grid.getCraftingService(), getPlayer().level(), output.what());
        closedLoopCandidates = discovery.candidates();
        closedLoopCandidateCount = closedLoopCandidates.size();
        if (closedLoopCandidates.isEmpty() && discovery.rejectedUndecodablePattern()) {
            closedLoopEncodeState = 1;
        }
        fillClosedLoopDraftFromSelectedCandidate();
    }

    /** Automatic discovery only fills the same member list that manual editing owns. */
    private void fillClosedLoopDraftFromSelectedCandidate() {
        if (closedLoopCandidates.isEmpty()) return;
        int index = Math.max(0, Math.min(
                closedLoopCandidateIndex, closedLoopCandidates.size() - 1));
        closedLoopDraftMembers = List.copyOf(
                closedLoopCandidates.get(index).payload().memberPatterns());
    }

    public void requestMaintenanceEditor(appeng.api.stacks.AEKey key) {
        if (!isClientSide() || tianshuSelectionPending) return;
        PacketDistributor.sendToServer(new OpenMaintenanceEditorPacket(
                containerId, tianshuSelectionRevision, key));
    }

    public void openMaintenanceEditor(int expectedSelectionRevision, appeng.api.stacks.AEKey key) {
        if (!isServerSide() || expectedSelectionRevision != tianshuSelectionRevision
                || !(getPlayer() instanceof ServerPlayer serverPlayer)) return;
        var target = resolveBoundTianshu();
        if (key == null || target == null
                || !target.getFunctionProfile().supportsInventoryMaintenance()) return;
        sendMaintenanceEditorData(serverPlayer, key);
    }

    public void setMaintainableView(boolean enabled) {
        maintainableView = enabled;
        if (isClientSide()) {
            sendClientAction("setMaintainableView", enabled);
        } else setMaintainableViewServer(enabled);
    }

    private void setMaintainableViewServer(boolean enabled) {
        if (!isServerSide()) return;
        maintainableView = enabled;
        getConfigManager().putSetting(Settings.VIEW_MODE, ViewItems.ALL);
        broadcastChanges();
    }

    @Override
    public boolean isKeyVisible(appeng.api.stacks.AEKey key) {
        if (!maintainableView) return super.isKeyVisible(key);
        var target = resolveBoundTianshu();
        var maintenance = target != null ? target.getInventoryMaintenance() : null;
        return maintenance != null && maintenance.repository().get(key) != null;
    }

    @Override
    protected boolean showsCraftables() {
        return maintainableView || super.showsCraftables();
    }

    private void sendMaintenanceSummaryIfNeeded() {
        if (!(getPlayer() instanceof ServerPlayer player)
                || getPlayer().tickCount - lastMaintenanceSummaryTick < 20) return;
        lastMaintenanceSummaryTick = getPlayer().tickCount;
        var target = resolveBoundTianshu();
        var summaries = new LinkedHashMap<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry>();
        boolean overflow = false;
        if (target != null && target.getFunctionProfile().supportsInventoryMaintenance()) {
            var service = target.getInventoryMaintenance();
            if (service != null) {
                if (service.repository().size() > TianshuPacketLimits.MAX_LIST_ENTRIES
                        || service.reservedStock().size() > TianshuPacketLimits.MAX_LIST_ENTRIES) {
                    overflow = true;
                }
                for (var rule : service.repository().rules(TianshuPacketLimits.MAX_LIST_ENTRIES)) {
                    if (summaries.size() >= TianshuPacketLimits.MAX_LIST_ENTRIES
                            && !summaries.containsKey(rule.key())) {
                        overflow = true;
                        break;
                    }
                    boolean ruleReserveOverflow = service.reservedStock(rule.id()).size()
                            > TianshuPacketLimits.MAX_LIST_ENTRIES;
                    summaries.put(rule.key(), new MaintenanceSummarySyncPacket.Entry(
                            rule.key(), service.status(rule.id()),
                            service.reservedStock().reserve(rule.key()),
                            service.reservedStock().matchMode(rule.key()),
                            ruleReserveOverflow));
                }
                for (var reserve : service.reservedStock().reservations(TianshuPacketLimits.MAX_LIST_ENTRIES)) {
                    if (summaries.size() >= TianshuPacketLimits.MAX_LIST_ENTRIES
                            && !summaries.containsKey(reserve.key())) {
                        overflow = true;
                        break;
                    }
                    summaries.putIfAbsent(reserve.key(), new MaintenanceSummarySyncPacket.Entry(
                            reserve.key(), InventoryMaintenanceStatus.IDLE,
                            reserve.amount(), reserve.mode(), false));
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
                containerId, tianshuSelectionRevision,
                maintenanceSummaryRevision, overflow, snapshot));
    }

    public void receiveMaintenanceSummary(
            int selectionRevision, long revision, boolean overflow,
            List<MaintenanceSummarySyncPacket.Entry> entries) {
        if (!isClientSide() || revision <= receivedMaintenanceSummaryRevision) return;
        if (selectionRevision < tianshuSelectionRevision) return;
        receivedMaintenanceSummaryRevision = revision;
        maintenanceSummarySelectionRevision = selectionRevision;
        maintenanceSummaryOverflow = overflow;
        maintenanceSummary = entries != null ? List.copyOf(entries) : List.of();
    }

    public boolean isMaintenanceSummaryOverflow() { return maintenanceSummaryOverflow; }

    public Map<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry> getMaintenanceSummary() {
        var result = new LinkedHashMap<appeng.api.stacks.AEKey, MaintenanceSummarySyncPacket.Entry>();
        for (var entry : maintenanceSummary) result.put(entry.key(), entry);
        return Map.copyOf(result);
    }

    public void sendGlobalReserve(appeng.api.stacks.AEKey key, long amount,
                                  com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode mode) {
        if (isClientSide() && !tianshuSelectionPending && key != null && mode != null) PacketDistributor.sendToServer(
                new SaveGlobalReservePacket(
                        containerId, tianshuSelectionRevision, key, amount, mode));
    }

    public void saveGlobalReserve(SaveGlobalReservePacket packet) {
        if (!isServerSide() || packet == null || packet.amount() < -1
                || packet.selectionRevision() != tianshuSelectionRevision) return;
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
        maintenance.setMaintenanceWideReservedStock(
                packet.key(), packet.mode(), packet.amount());
        lastMaintenanceSummaryTick = Integer.MIN_VALUE;
        broadcastChanges();
    }

    private void sendMaintenanceEditorData(ServerPlayer player, appeng.api.stacks.AEKey key) {
        var target = resolveBoundTianshu();
        if (target == null) return;
        var maintenance = target.getInventoryMaintenance();
        if (maintenance == null) return;
        var rule = maintenance.repository().get(key);
        var grid = tianshuHost.getActionableNode() != null
                ? tianshuHost.getActionableNode().getGrid() : null;
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
                        saved.key(), topologyEntry, global, local));
            }
        }
        for (var entry : topology) {
            if (topologyData.containsKey(entry.key())) continue;
            if (topologyData.size() >= TianshuPacketLimits.MAX_LIST_ENTRIES) {
                recoveryPage = true;
                break;
            }
            topologyData.put(entry.key(), maintenanceEditorEntry(
                    entry.key(), entry, global, local));
        }

        var allVariants = maintenance.variants(key);
        if (allVariants.size() > TianshuPacketLimits.MAX_LIST_ENTRIES) recoveryPage = true;
        var variants = allVariants.stream()
                .limit(TianshuPacketLimits.MAX_LIST_ENTRIES)
                .map(variant -> new MaintenanceEditorData.VariantEntry(
                        variant.key(), variant.storedAmount(), variant.craftable()))
                .toList();
        var data = new MaintenanceEditorData(key, rule != null ? rule.id() : null,
                rule != null ? rule.lowerThreshold() : 0L,
                rule != null ? rule.upperThreshold() : 64L,
                rule != null ? rule.amountPerJob() : 64L,
                rule == null || rule.enabled(),
                rule != null ? maintenance.status(rule.id()) : InventoryMaintenanceStatus.IDLE,
                recoveryPage, List.copyOf(topologyData.values()), variants);
        PacketDistributor.sendToPlayer(player, new MaintenanceEditorSyncPacket(
                containerId, tianshuSelectionRevision, data));
    }

    private static MaintenanceEditorData.TopologyEntry maintenanceEditorEntry(
            appeng.api.stacks.AEKey key,
            @Nullable MaintenanceTopologyService.Entry topology,
            com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockRepository global,
            @Nullable com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockRepository local) {
        return new MaintenanceEditorData.TopologyEntry(
                key, topology != null ? topology.depth() : 0,
                topology != null && topology.craftable(),
                global.reserve(key), global.matchMode(key),
                local != null ? local.reserve(key) : 0L,
                local != null ? local.matchMode(key)
                        : com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode.EXACT);
    }

    public void receiveMaintenanceEditorData(int selectionRevision, MaintenanceEditorData data) {
        if (!isClientSide() || data == null || selectionRevision < tianshuSelectionRevision) return;
        maintenanceEditorSelectionRevision = selectionRevision;
        maintenanceEditorData = data;
        maintenanceEditorRevision++;
    }

    @Nullable public MaintenanceEditorData getMaintenanceEditorData() { return maintenanceEditorData; }
    public int getMaintenanceEditorRevision() { return maintenanceEditorRevision; }

    public void sendMaintenanceSave(SaveMaintenanceRulePacket packet) {
        if (isClientSide() && !tianshuSelectionPending && packet != null) {
            PacketDistributor.sendToServer(packet);
        }
    }

    public void saveMaintenanceRule(SaveMaintenanceRulePacket packet) {
        if (!isServerSide() || packet == null
                || packet.selectionRevision() != tianshuSelectionRevision
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
            if (existing != null) service.removeRule(existing.id());
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        if (service.repository().size() > TianshuPacketLimits.MAX_LIST_ENTRIES) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "ae2lt.tianshu.maintenance.too_large",
                    TianshuPacketLimits.MAX_LIST_ENTRIES), true);
            sendMaintenanceEditorData(player, packet.target());
            return;
        }
        if (packet.lower() < 0 || packet.upper() <= packet.lower() || packet.amountPerJob() <= 0) {
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
            for (var edit : packet.reserves()) {
                service.setMaintenanceWideReservedStock(edit.key(), edit.globalMode(), edit.globalAmount());
                service.setReservedStock(ruleId, edit.key(), edit.ruleMode(), edit.ruleAmount());
            }
        }
        sendMaintenanceEditorData(player, packet.target());
    }

    @Override
    public void encode() {
        if (isClientSide()) {
            pendingTriggeredUpload = com.moakiee.ae2lt.client.TianshuUploadTriggerClient.shouldTrigger();
            pendingTriggeredUploadUntil = getPlayer().tickCount + 200;
            expectedTriggeredUploadAck = triggeredUploadAck;
        }
        if (tianshuMode.isAe2Mode()) {
            super.encode();
            var encoded = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
            if (isServerSide() && TianshuPatternUploadRouting.isValidEncodingResult(
                    encoded, getPlayer().level())) {
                triggeredUploadAck++;
                broadcastChanges();
            }
            return;
        }
        if (isClientSide()) {
            sendClientAction("encode");
            return;
        }
        var result = encodeDerivedPattern();
        if (result != null && !result.isEmpty()) {
            if (tianshuMode == TianshuEncodingMode.CLOSED_LOOP) closedLoopEncodeState = 0;
            tianshuHost.getLogic().getEncodedPatternInv().setItemDirect(0, result);
            if (TianshuPatternUploadRouting.isValidEncodingResult(result, getPlayer().level())) {
                triggeredUploadAck++;
            }
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
        if (closedLoopDraftMembers.isEmpty() || closedLoopMainOutput == null) {
            if (closedLoopEncodeState == 0) closedLoopEncodeState = 2;
            return ItemStack.EMPTY;
        }
        var authored = ClosedLoopPatternAuthoringService.createFromDraft(
                closedLoopDraftMembers, closedLoopMainOutput,
                closedLoopSeedMultiplier, getPlayer().level());
        if (!authored.valid()) {
            closedLoopEncodeState = authored.status()
                    == ClosedLoopPatternAuthoringService.Status.MEMBER_UNDECODABLE ? 1 : 2;
            broadcastChanges();
            return ItemStack.EMPTY;
        }
        return ((ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get()).createStack(
                authored.payload(), registryAccess());
    }
}
