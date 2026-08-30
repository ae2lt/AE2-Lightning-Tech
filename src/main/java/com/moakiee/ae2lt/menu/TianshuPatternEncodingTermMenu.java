package com.moakiee.ae2lt.menu;

import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.client.gui.Icon;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.moakiee.ae2lt.util.SlotPositionAccess;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.AdvancedAECompat;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopDiscoveryService;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopDiscoveryCandidate;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAuthoringService;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternUploadService;
import com.moakiee.ae2lt.logic.tianshu.loop.TianshuSeedRefillService;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopDraftStatus;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopDraftSync;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopResultPage;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.ExtendedAEPlusEncodingCompat;
import com.moakiee.ae2lt.logic.tianshu.terminal.SeedRefillSync;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternMultiplier;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalTarget;
import com.moakiee.ae2lt.logic.tianshu.terminal.MaintenanceEditorData;
import com.moakiee.ae2lt.logic.tianshu.terminal.PatternEncodingDuplicateFilter;
import com.moakiee.ae2lt.me.GridNodeAccess;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceRule;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.MaintenanceTopologyService;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceService;
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
import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;
import com.moakiee.ae2lt.overload.runtime.pattern.Ae2PlainPatternResolver;
import com.moakiee.ae2lt.overload.runtime.pattern.ParsedPatternDefinition;
import net.minecraft.server.level.ServerPlayer;
import com.moakiee.ae2lt.network.PacketSender;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.config.Settings;
import appeng.api.config.ViewItems;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import com.moakiee.ae2lt.network.tianshu.ClosedLoopResultPagePacket;
import com.moakiee.ae2lt.network.tianshu.RequestClosedLoopResultPagePacket;
import com.moakiee.ae2lt.network.tianshu.RequestUploadTargetsPacket;
import com.moakiee.ae2lt.network.tianshu.UploadPatternToTargetPacket;
import com.moakiee.ae2lt.network.tianshu.UploadTargetsSyncPacket;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.EnumMap;
import java.util.Map;
import com.moakiee.ae2lt.network.tianshu.SaveGlobalReservePacket;
import com.moakiee.ae2lt.network.tianshu.TianshuPacketLimits;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import net.minecraft.world.entity.player.Player;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import net.minecraft.core.RegistryAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@IPNIgnore
public class TianshuPatternEncodingTermMenu extends PatternEncodingTermMenu {
    private static final Logger DUPLICATE_LOG =
            LoggerFactory.getLogger("ae2lt/TianshuDuplicate");
    public static final int CLOSED_LOOP_MEMBER_SLOTS = ClosedLoopDraftSync.MEMBER_SLOTS;
    public static final int CLOSED_LOOP_OUTPUT_SLOTS = ClosedLoopDraftSync.OUTPUT_SLOTS;
    public static final int CLOSED_LOOP_RESULT_SLOTS = ClosedLoopResultPage.MAX_RESULTS;
    private static final int CLOSED_LOOP_OFFSCREEN = -10000;
    private static final MenuTypeBuilder.MenuFactory<
            TianshuPatternEncodingTermMenu, TianshuPatternTerminalHost> FACTORY =
            TianshuPatternEncodingTermMenu::new;
    public static final MenuType<TianshuPatternEncodingTermMenu> TYPE = Ae2ltMenuBuilder
            .buildUnregistered(
                    MenuTypeBuilder.create(FACTORY, TianshuPatternTerminalHost.class),
                    new ResourceLocation(AE2LightningTech.MODID, "tianshu_pattern_encoding_terminal"));

    @GuiSync(110)
    public TianshuEncodingMode tianshuMode = TianshuEncodingMode.CRAFTING;

    @GuiSync(111)
    public ProcessingPatternEncodingType processingEncodingType = ProcessingPatternEncodingType.NORMAL;
    @GuiSync(113)
    public int closedLoopCandidateCount;
    @GuiSync(114)
    public int closedLoopCandidateIndex;
    @GuiSync(115)
    public int closedLoopExecutionSeedMultiplier = 1;
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
    @GuiSync(130)
    public int tianshuSelectionRevision;
    @GuiSync(131)
    public int closedLoopStoredTaskMultiplier = 1;
    /** Read-only compatibility mirror for integrations compiled against the single multiplier. */
    @Deprecated
    @GuiSync(132)
    public int closedLoopSeedMultiplier = 1;
    @GuiSync(133)
    public ClosedLoopDraftSync closedLoopDraftSync = ClosedLoopDraftSync.empty();
    @GuiSync(134)
    public ClosedLoopDraftStatus closedLoopDraftStatus = ClosedLoopDraftStatus.EMPTY;
    @GuiSync(135)
    public int closedLoopExternalInputCount;
    @GuiSync(136)
    public int closedLoopSeedInputCount;
    @GuiSync(137)
    public boolean seedRefillAvailable;
    @GuiSync(138)
    public SeedRefillSync seedRefillSync = SeedRefillSync.none();
    @GuiSync(139)
    public ProcessingPatternTerminalDraft processingDraftSync =
            ProcessingPatternTerminalDraft.empty();
    @GuiSync(140)
    public int closedLoopResultRevision;

    protected final TianshuPatternTerminalHost tianshuHost;
    @Nullable private TianshuTerminalTarget boundTianshuTarget;
    private final PatternConversionService conversionService = new PatternConversionService();
    private ItemStack configuredSource = ItemStack.EMPTY;
    private List<ClosedLoopDiscoveryCandidate> closedLoopCandidates = List.of();
    private List<ClosedLoopMemberPattern> closedLoopDraftMembers = List.of();
    @Nullable private AEKey closedLoopMainOutput;
    private final AppEngInternalInventory closedLoopMemberInventory;
    private final AppEngInternalInventory closedLoopOutputInventory;
    private final AppEngInternalInventory globalReserveMarkInventory;
    private final FakeSlot globalReserveMarkSlot;
    private final List<AppEngSlot> closedLoopMemberSlots = new ArrayList<>();
    private final List<AppEngSlot> closedLoopOutputSlots = new ArrayList<>();
    private List<GenericStack> closedLoopExternalInputs = List.of();
    private List<GenericStack> closedLoopSeeds = List.of();
    private final Map<ClosedLoopResultPage.Kind, ClosedLoopResultPage> closedLoopResultPages =
            new EnumMap<>(ClosedLoopResultPage.Kind.class);
    private final long[] closedLoopMemberCopies = new long[CLOSED_LOOP_MEMBER_SLOTS];
    private final int[] closedLoopOutputRoles = new int[CLOSED_LOOP_OUTPUT_SLOTS];
    private boolean closedLoopBulkUpdating;
    private boolean closedLoopDraftDirty;
    private boolean closedLoopDraftRepresentsEncoded;
    @Nullable private ClosedLoopPatternPayload closedLoopPreparedPayload;
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
    private boolean pendingDirectUpload;
    private int pendingTriggeredUploadUntil;
    private int expectedTriggeredUploadAck;
    private boolean directUploadTargetsRequested;
    private int expectedDirectUploadTargetRevision;
    /** Prevents a committed encoding result from being mistaken for a manually inserted pattern. */
    private boolean ae2EncodingInProgress;
    /** Exact encoded stack that still represents one blank pattern extracted by this menu. */
    private ItemStack refundableEncodedPattern = ItemStack.EMPTY;

    public TianshuPatternEncodingTermMenu(
            int id, Inventory inventory, TianshuPatternTerminalHost host) {
        this(TYPE, id, inventory, host);
    }

    protected TianshuPatternEncodingTermMenu(
            MenuType<?> type, int id, Inventory inventory, TianshuPatternTerminalHost host) {
        super(type, id, inventory, host, true);
        this.tianshuHost = host;
        var inheritedBlankPatternSlot = (AppEngSlot) getSlots(SlotSemantics.BLANK_PATTERN).get(0);
        inheritedBlankPatternSlot.setSlotEnabled(false);
        this.closedLoopMemberInventory = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void onChangeInventory(InternalInventory inv, int slot) {
                if (!closedLoopBulkUpdating) {
                    closedLoopDraftRepresentsEncoded = false;
                    closedLoopDraftDirty = true;
                }
            }

            @Override
            public boolean isClientSide() {
                return TianshuPatternEncodingTermMenu.this.isClientSide();
            }

            @Override
            public void saveChanges() {
            }
        }, CLOSED_LOOP_MEMBER_SLOTS, 1);
        this.closedLoopOutputInventory = new AppEngInternalInventory(null, CLOSED_LOOP_OUTPUT_SLOTS, 1);
        this.globalReserveMarkInventory = new AppEngInternalInventory(null, 1, 1);
        this.globalReserveMarkSlot = new FakeSlot(globalReserveMarkInventory, 0);
        SlotPositionAccess.set(globalReserveMarkSlot, CLOSED_LOOP_OFFSCREEN, CLOSED_LOOP_OFFSCREEN);
        globalReserveMarkSlot.setIcon(Icon.BACKGROUND_PRIMARY_OUTPUT);
        addSlot(globalReserveMarkSlot, Ae2ltSlotSemantics.TIANSHU_GLOBAL_RESERVE_MARK);
        for (int i = 0; i < CLOSED_LOOP_MEMBER_SLOTS; i++) {
            var slot = new ClosedLoopMemberSlot(closedLoopMemberInventory, i);
            SlotPositionAccess.set(slot, CLOSED_LOOP_OFFSCREEN, CLOSED_LOOP_OFFSCREEN);
            addSlot(slot, Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_MEMBER);
            closedLoopMemberSlots.add(slot);
        }
        for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
            AppEngSlot slot;
            if (i == 0) {
                slot = new ClosedLoopOutputSlot(closedLoopOutputInventory);
                slot.setIcon(Icon.BACKGROUND_PRIMARY_OUTPUT);
                slot.setEmptyTooltip(() -> List.of(net.minecraft.network.chat.Component.translatable(
                        "ae2lt.tianshu.closed_loop.primary_output_mark.tooltip")));
            } else {
                slot = new ClosedLoopReadonlySlot(closedLoopOutputInventory, i);
                slot.setEmptyTooltip(() -> List.of(net.minecraft.network.chat.Component.translatable(
                        "ae2lt.tianshu.closed_loop.byproduct_output.tooltip")));
            }
            SlotPositionAccess.set(slot, CLOSED_LOOP_OFFSCREEN, CLOSED_LOOP_OFFSCREEN);
            addSlot(slot, Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_OUTPUT_MARK);
            closedLoopOutputSlots.add(slot);
        }
        this.boundTianshuTarget = inventory.player.level().isClientSide
                ? null : host.selectTianshuTarget();
        if (boundTianshuTarget != null) tianshuSelectionRevision = 1;
        this.tianshuMode = host.getTianshuEncodingMode();
        this.maintainableView = host.isMaintainableView();
        if (maintainableView && !inventory.player.level().isClientSide) {
            getConfigManager().putSetting(Settings.VIEW_MODE, ViewItems.ALL);
        }
        if (!inventory.player.level().isClientSide) {
            restoreProcessingDraft(host.getProcessingPatternTerminalDraft());
            restoreClosedLoopDraft(host.getClosedLoopTerminalDraft());
        }
        registerClientAction("setTianshuMode", TianshuEncodingMode.class, this::setTianshuModeServer);
        registerClientAction("multiplyProcessing", Integer.class, this::multiplyProcessingServer);
        registerClientAction("armAdvancedEncoding",
                ProcessingPatternEncodingType.AdvancedConfig.class, this::armAdvancedEncodingServer);
        registerClientAction("armOverloadEncoding",
                ProcessingPatternEncodingType.OverloadConfig.class, this::armOverloadEncodingServer);
        registerClientAction("resetProcessingEncoding", this::resetProcessingEncodingServer);
        registerClientAction("selectClosedLoopCandidate", Integer.class, this::selectClosedLoopCandidateServer);
        registerClientAction("changeClosedLoopExecutionSeedMultiplier", Integer.class,
                this::changeClosedLoopExecutionSeedMultiplierServer);
        registerClientAction("changeClosedLoopSeedMultiplier", Integer.class,
                this::changeClosedLoopSeedMultiplierServer);
        registerClientAction("changeClosedLoopStoredTaskMultiplier", Integer.class,
                this::changeClosedLoopStoredTaskMultiplierServer);
        registerClientAction("setClosedLoopMemberCopies", ClosedLoopMemberEdit.class,
                this::setClosedLoopMemberCopiesServer);
        registerClientAction("moveClosedLoopMember", ClosedLoopMemberMove.class,
                this::moveClosedLoopMemberServer);
        registerClientAction("setClosedLoopMultipliers", ClosedLoopMultiplierEdit.class,
                this::setClosedLoopMultipliersServer);
        registerClientAction("autoFillClosedLoop", this::autoFillClosedLoopServer);
        registerClientAction("cycleClosedLoopOutput", this::cycleClosedLoopOutputServer);
        registerClientAction("refillClosedLoopSeeds", this::refillClosedLoopSeedsServer);
        registerClientAction("clearClosedLoopDraft", this::clearClosedLoopDraftServer);
        registerClientAction("encodeTianshu", Boolean.class, this::encodeServerWithOptions);
        registerClientAction("uploadEncodedPattern", Integer.class, this::uploadEncodedPatternServer);
        registerClientAction("setMaintainableView", Boolean.class, this::setMaintainableViewServer);
        registerClientAction("setMaintainableViewTemporarily", Boolean.class,
                this::setMaintainableViewTemporarilyServer);
        registerClientAction("maintenanceAction", MaintenanceAction.class, this::maintenanceActionServer);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && GridNodeAccess.getGridIfPresent(getNetworkNode()) == null) {
            setValidMenu(false);
            return;
        }
        if (isServerSide()) {
            returnLegacyBlankPatternsToNetwork();
            tianshuMode = tianshuHost.getTianshuEncodingMode();
            var selected = resolveOrBindTianshu();
            maintenanceAvailable = selected != null
                    && selected.getFunctionProfile().supportsInventoryMaintenance();
            seedRefillAvailable = selected != null && selected.isFormed()
                    && selected.getFunctionProfile().supportsClosedLoopSeeds();
            if (!ae2EncodingInProgress) refreshDerivedConfiguration();
            refreshProcessingDraftBinding();
            if (closedLoopDraftDirty) rebuildClosedLoopDraft();
            closedLoopSeedMultiplier = closedLoopExecutionSeedMultiplier;
            refreshClosedLoopDraftSync();
            persistClosedLoopDraft();
            // CLOSED_LOOP has no AE2 EncodingMode of its own. Keep the inherited menu field
            // aligned with the logic without routing the old native mode through our override.
            if (tianshuMode == TianshuEncodingMode.CLOSED_LOOP
                    && getMode() != tianshuHost.getLogic().getMode()) {
                super.setMode(tianshuHost.getLogic().getMode());
            }
        }
        broadcastParentChanges();
        if (isServerSide()) sendMaintenanceSummaryIfNeeded();
    }

    private void broadcastParentChanges() {
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        if (isServerSide()) {
            if (closedLoopDraftDirty && tianshuMode == TianshuEncodingMode.CLOSED_LOOP) {
                rebuildClosedLoopDraft();
            }
            persistClosedLoopDraft();
        }
        super.removed(player);
    }

    /** Resolves only the machine captured when this server menu opened. */
    @Nullable
    private com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity resolveBoundTianshu() {
        return tianshuHost.resolveTianshuTarget(boundTianshuTarget);
    }

    public void resetClientTianshuScopedState() {
        if (!isClientSide()) return;
        if (maintenanceSummarySelectionRevision != tianshuSelectionRevision) {
            maintenanceSummary = List.of();
            maintenanceSummaryOverflow = false;
        }
        if (maintenanceEditorSelectionRevision != tianshuSelectionRevision) {
            maintenanceEditorData = null;
            maintenanceEditorRevision++;
        }
    }

    @Override
    public void setMode(EncodingMode mode) {
        if (mode == null) {
            super.setMode(null);
            return;
        }
        var extended = TianshuEncodingMode.fromAe2(mode);
        if (isClientSide()) {
            // AE2's recipe-viewer helper immediately follows this mode change with fake-slot
            // updates and may encode in the same input event. Send its authoritative logic-mode
            // action before the Tianshu mirror so the server never observes a stale native mode.
            super.setMode(mode);
            sendClientAction("setTianshuMode", extended);
        } else {
            alignNativeModeServer(extended, mode);
        }
    }

    public boolean consumeTriggeredUpload() {
        if (!isClientSide() || !pendingTriggeredUpload) return false;
        expirePendingTriggeredUpload();
        if (triggeredUploadAck == expectedTriggeredUploadAck) return false;
        pendingTriggeredUpload = false;
        return true;
    }

    public boolean hasPendingDirectUpload() {
        if (!isClientSide()) return false;
        expirePendingTriggeredUpload();
        return pendingTriggeredUpload && pendingDirectUpload;
    }

    public boolean hasTriggeredUploadAck() {
        if (!isClientSide() || !pendingTriggeredUpload) return false;
        expirePendingTriggeredUpload();
        return pendingTriggeredUpload && triggeredUploadAck != expectedTriggeredUploadAck;
    }

    public boolean hasFreshDirectUploadTargets() {
        return isClientSide()
                && directUploadTargetsRequested
                && uploadTargetsRevision != expectedDirectUploadTargetRevision;
    }

    /**
     * Requests provider availability only after the newly encoded pattern has reached the client.
     * Availability depends on the encoded stack, so a request sent before encoding would report
     * every provider as unwritable when the result slot was initially empty.
     */
    public boolean requestDirectUploadTargetsAfterEncoding() {
        if (!isClientSide() || !hasPendingDirectUpload() || !hasTriggeredUploadAck()) return false;
        if (!directUploadTargetsRequested) {
            expectedDirectUploadTargetRevision = uploadTargetsRevision;
            directUploadTargetsRequested = true;
            requestUploadTargets();
        }
        return true;
    }

    public boolean consumeDirectUploadRequest() {
        boolean direct = pendingDirectUpload;
        pendingDirectUpload = false;
        directUploadTargetsRequested = false;
        return direct;
    }

    public void clearClientUploadSelectionState() {
        if (!isClientSide()) return;
        pendingTriggeredUpload = false;
        pendingDirectUpload = false;
        directUploadTargetsRequested = false;
        uploadTargetGroups = List.of();
        uploadTargetsRevision++;
    }

    private void expirePendingTriggeredUpload() {
        if (pendingTriggeredUpload && getPlayer().tickCount > pendingTriggeredUploadUntil) {
            pendingTriggeredUpload = false;
            pendingDirectUpload = false;
            directUploadTargetsRequested = false;
        }
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
        if (mode.ae2Mode() != null) {
            alignNativeModeServer(mode, mode.ae2Mode());
        } else {
            applyTianshuModeState(mode);
        }
        broadcastChanges();
    }

    private void alignNativeModeServer(TianshuEncodingMode mode, EncodingMode nativeMode) {
        applyTianshuModeState(mode);
        var logic = tianshuHost.getLogic();
        if (logic.getMode() != nativeMode) logic.setMode(nativeMode);
        if (getMode() != nativeMode) super.setMode(nativeMode);
    }

    private void applyTianshuModeState(TianshuEncodingMode mode) {
        if (tianshuMode != mode) resetProcessingEncodingType();
        tianshuMode = mode;
        tianshuHost.setTianshuEncodingMode(mode);
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

    public void armAdvancedEncoding(ProcessingPatternEncodingType.AdvancedConfig config) {
        if (config == null) return;
        if (isClientSide()) {
            updateAdvancedEncodingConfig(config);
            sendClientAction("armAdvancedEncoding", config);
        } else {
            armAdvancedEncodingServer(config);
        }
    }

    private void armAdvancedEncodingServer(ProcessingPatternEncodingType.AdvancedConfig config) {
        if (!isServerSide() || config == null || config.directions() == null
                || config.directions().length > getProcessingInputSlots().length
                || !validDirections(config.directions())
                || !AdvancedAECompat.canEncode()
                || tianshuMode != TianshuEncodingMode.PROCESSING) return;
        updateAdvancedEncodingConfig(config);
        persistProcessingDraft();
        broadcastChanges();
    }

    public void armOverloadEncoding(ProcessingPatternEncodingType.OverloadConfig config) {
        if (config == null) return;
        if (isClientSide()) {
            updateOverloadEncodingConfig(config);
            sendClientAction("armOverloadEncoding", config);
        } else {
            armOverloadEncodingServer(config);
        }
    }

    private void armOverloadEncodingServer(ProcessingPatternEncodingType.OverloadConfig config) {
        if (!isServerSide() || config == null
                || config.inputIdOnly() == null || config.outputIdOnly() == null
                || config.inputIdOnly().length > getProcessingInputSlots().length
                || config.outputIdOnly().length > getProcessingOutputSlots().length
                || !validSlots(config.inputIdOnly(), getProcessingInputSlots().length)
                || !validSlots(config.outputIdOnly(), getProcessingOutputSlots().length)
                || tianshuMode != TianshuEncodingMode.PROCESSING) return;
        updateOverloadEncodingConfig(config);
        persistProcessingDraft();
        broadcastChanges();
    }

    private void updateAdvancedEncodingConfig(
            ProcessingPatternEncodingType.AdvancedConfig config) {
        var inputs = snapshotProcessingInputs();
        var outputs = snapshotProcessingOutputs();
        var overload = processingDraftSync.matches(inputs, outputs)
                ? processingDraftSync.overloadConfig() : null;
        setProcessingDraft(inputs, outputs, config, overload);
    }

    private void updateOverloadEncodingConfig(
            ProcessingPatternEncodingType.OverloadConfig config) {
        var inputs = snapshotProcessingInputs();
        var outputs = snapshotProcessingOutputs();
        var advanced = processingDraftSync.matches(inputs, outputs)
                ? processingDraftSync.advancedConfig() : null;
        setProcessingDraft(inputs, outputs, advanced, config);
    }

    private void setProcessingDraft(
            List<GenericStack> inputs,
            List<GenericStack> outputs,
            @Nullable ProcessingPatternEncodingType.AdvancedConfig advanced,
            @Nullable ProcessingPatternEncodingType.OverloadConfig overload) {
        processingDraftSync = ProcessingPatternTerminalDraft.configured(
                inputs, outputs, advanced, overload);
        processingEncodingType = processingDraftSync.type();
    }

    @Nullable
    public ProcessingPatternEncodingType.AdvancedConfig getAdvancedEncodingConfig() {
        return processingDraftSync.advancedConfig();
    }

    @Nullable
    public ProcessingPatternEncodingType.OverloadConfig getOverloadEncodingConfig() {
        return processingDraftSync.overloadConfig();
    }

    private void resetProcessingEncodingType() {
        processingEncodingType = ProcessingPatternEncodingType.NORMAL;
        processingDraftSync = ProcessingPatternTerminalDraft.empty();
        if (isServerSide()) tianshuHost.setProcessingPatternTerminalDraft(null);
    }

    public void resetProcessingEncoding() {
        if (isClientSide()) {
            resetProcessingEncodingType();
            sendClientAction("resetProcessingEncoding");
        } else {
            resetProcessingEncodingServer();
        }
    }

    private void resetProcessingEncodingServer() {
        if (!isServerSide()) return;
        resetProcessingEncodingType();
        broadcastChanges();
    }

    private void refreshProcessingDraftBinding() {
        if (processingEncodingType == ProcessingPatternEncodingType.NORMAL) return;
        if (tianshuMode != TianshuEncodingMode.PROCESSING
                || processingDraftSync.type() != processingEncodingType
                || !processingDraftSync.matches(
                        snapshotProcessingInputs(), snapshotProcessingOutputs())) {
            resetProcessingEncodingType();
        }
    }

    private void restoreProcessingDraft(@Nullable ProcessingPatternTerminalDraft draft) {
        if (draft == null) return;
        boolean supported = draft.type() != ProcessingPatternEncodingType.NORMAL
                && (!draft.type().hasAdvanced()
                        || AdvancedAECompat.canEncode());
        if (tianshuMode == TianshuEncodingMode.PROCESSING
                && supported
                && draft.matches(snapshotProcessingInputs(), snapshotProcessingOutputs())) {
            processingDraftSync = draft;
            processingEncodingType = draft.type();
        } else {
            tianshuHost.setProcessingPatternTerminalDraft(null);
        }
    }

    private void persistProcessingDraft() {
        tianshuHost.setProcessingPatternTerminalDraft(
                processingEncodingType == ProcessingPatternEncodingType.NORMAL
                        ? null : processingDraftSync);
    }

    private List<GenericStack> snapshotProcessingInputs() {
        return snapshotProcessingInventory(tianshuHost.getLogic().getEncodedInputInv());
    }

    private List<GenericStack> snapshotProcessingOutputs() {
        return snapshotProcessingInventory(tianshuHost.getLogic().getEncodedOutputInv());
    }

    private static List<GenericStack> snapshotProcessingInventory(
            appeng.util.ConfigInventory inventory) {
        var result = new ArrayList<GenericStack>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) result.add(inventory.getStack(i));
        return result;
    }

    private static boolean validDirections(int[] directions) {
        for (int direction : directions) {
            if (direction < 0 || direction > 6) return false;
        }
        return true;
    }

    private static boolean validSlots(int[] slots, int slotCount) {
        var seen = new boolean[slotCount];
        for (int slot : slots) {
            if (slot < 0 || slot >= slotCount || seen[slot]) return false;
            seen[slot] = true;
        }
        return true;
    }

    @Override
    public void clear() {
        if (isClientSide()) {
            com.moakiee.ae2lt.client.TianshuRecipeTransferContext.clear(this);
            clearClientUploadSelectionState();
        }
        resetProcessingEncodingType();
        super.clear();
    }

    public List<AppEngSlot> getClosedLoopMemberSlots() {
        return closedLoopMemberSlots;
    }

    public List<AppEngSlot> getClosedLoopOutputSlots() {
        return closedLoopOutputSlots;
    }

    /**
     * Marks the first right-hand output slot from a client-side recipe-viewer transfer.
     * The regular fake-slot packet keeps the server authoritative.
     */
    public boolean markClosedLoopPrimaryOutput(ItemStack stack) {
        if (!isClientSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP
                || stack == null || stack.isEmpty() || closedLoopOutputSlots.isEmpty()
                || GenericStack.fromItemStack(stack) == null) {
            return false;
        }
        ((ClosedLoopOutputSlot) closedLoopOutputSlots.get(0)).setFilterTo(stack);
        return true;
    }

    public boolean hasClosedLoopPrimaryOutputMark() {
        return getMarkedClosedLoopPrimaryOutput() != null;
    }

    public FakeSlot getGlobalReserveMarkSlot() {
        return globalReserveMarkSlot;
    }

    public void requestClosedLoopResultPage(ClosedLoopResultPage.Kind kind, int offset) {
        if (!isClientSide() || kind == null) return;
        PacketSender.sendToServer(new RequestClosedLoopResultPagePacket(
                containerId, kind, offset));
    }

    public void sendClosedLoopResultPage(
            ServerPlayer player, ClosedLoopResultPage.Kind kind, int offset) {
        if (!isServerSide() || player == null || kind == null) return;
        var source = kind == ClosedLoopResultPage.Kind.EXTERNAL_INPUTS
                ? closedLoopExternalInputs : closedLoopSeeds;
        PacketSender.sendToPlayer(player, new ClosedLoopResultPagePacket(
                containerId,
                ClosedLoopResultPage.from(closedLoopResultRevision, kind, source, offset)));
    }

    public void receiveClosedLoopResultPage(ClosedLoopResultPage page) {
        if (!isClientSide() || page == null || page.revision() < closedLoopResultRevision) return;
        closedLoopResultPages.put(page.kind(), page);
    }

    @Nullable
    public ClosedLoopResultPage getClosedLoopResultPage(
            ClosedLoopResultPage.Kind kind, int offset) {
        var page = closedLoopResultPages.get(kind);
        return page != null
                        && page.revision() == closedLoopResultRevision
                        && page.offset() == offset
                ? page : null;
    }

    public long getClosedLoopMemberCopies(int slot) {
        return slot >= 0 && slot < closedLoopMemberCopies.length
                ? closedLoopMemberCopies[slot] : 0L;
    }

    public int getClosedLoopOutputRole(int slot) {
        return slot >= 0 && slot < closedLoopOutputRoles.length
                ? closedLoopOutputRoles[slot] : 0;
    }

    public void setClosedLoopMemberCopies(int slot, long copies) {
        if (isClientSide()) {
            sendClientAction("setClosedLoopMemberCopies", new ClosedLoopMemberEdit(slot, copies));
        } else {
            setClosedLoopMemberCopiesServer(new ClosedLoopMemberEdit(slot, copies));
        }
    }

    private void setClosedLoopMemberCopiesServer(ClosedLoopMemberEdit edit) {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP
                || edit == null || edit.slot() < 0 || edit.slot() >= CLOSED_LOOP_MEMBER_SLOTS
                || edit.copies() < 1L || edit.copies() == Long.MAX_VALUE) return;
        if (closedLoopMemberInventory.getStackInSlot(edit.slot()).isEmpty()) return;
        closedLoopMemberCopies[edit.slot()] = edit.copies();
        closedLoopDraftRepresentsEncoded = false;
        closedLoopDraftDirty = true;
        broadcastChanges();
    }

    public void moveClosedLoopMember(int slot, int direction) {
        if (isClientSide()) {
            sendClientAction("moveClosedLoopMember", new ClosedLoopMemberMove(slot, direction));
        } else {
            moveClosedLoopMemberServer(new ClosedLoopMemberMove(slot, direction));
        }
    }

    private void moveClosedLoopMemberServer(ClosedLoopMemberMove move) {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP
                || move == null || (move.direction() != -1 && move.direction() != 1)) return;
        int source = move.slot();
        int target = source + move.direction();
        if (source < 0 || source >= CLOSED_LOOP_MEMBER_SLOTS
                || target < 0 || target >= CLOSED_LOOP_MEMBER_SLOTS) return;
        closedLoopBulkUpdating = true;
        try {
            var left = closedLoopMemberInventory.getStackInSlot(source).copy();
            var right = closedLoopMemberInventory.getStackInSlot(target).copy();
            closedLoopMemberInventory.setItemDirect(source, right);
            closedLoopMemberInventory.setItemDirect(target, left);
            long copies = closedLoopMemberCopies[source];
            closedLoopMemberCopies[source] = closedLoopMemberCopies[target];
            closedLoopMemberCopies[target] = copies;
        } finally {
            closedLoopBulkUpdating = false;
        }
        closedLoopDraftRepresentsEncoded = false;
        closedLoopDraftDirty = true;
        broadcastChanges();
    }

    public void setClosedLoopMultipliers(int execution, int stored) {
        if (isClientSide()) {
            sendClientAction("setClosedLoopMultipliers", new ClosedLoopMultiplierEdit(execution, stored));
        } else {
            setClosedLoopMultipliersServer(new ClosedLoopMultiplierEdit(execution, stored));
        }
    }

    private void setClosedLoopMultipliersServer(ClosedLoopMultiplierEdit edit) {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP || edit == null
                || edit.execution() < 1 || edit.stored() < 1) return;
        closedLoopExecutionSeedMultiplier = edit.execution();
        closedLoopStoredTaskMultiplier = edit.stored();
        closedLoopSeedMultiplier = closedLoopExecutionSeedMultiplier;
        closedLoopDraftRepresentsEncoded = false;
        closedLoopDraftDirty = true;
        broadcastChanges();
    }

    public void selectClosedLoopCandidate(int delta) {
        if (isClientSide()) sendClientAction("selectClosedLoopCandidate", delta);
        else selectClosedLoopCandidateServer(delta);
    }

    /** Discovers members from the marked primary output, or cycles candidates once discovered. */
    public void autoFillClosedLoop() {
        if (isClientSide()) sendClientAction("autoFillClosedLoop");
        else autoFillClosedLoopServer();
    }

    /** Cycles the computed outputs so the next byproduct becomes the primary output. */
    public void cycleClosedLoopOutput() {
        if (isClientSide()) sendClientAction("cycleClosedLoopOutput");
        else cycleClosedLoopOutputServer();
    }

    public boolean canCycleClosedLoopOutputs() {
        if (tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return false;
        int outputCount = 0;
        for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
            if (!closedLoopOutputInventory.getStackInSlot(i).isEmpty()) outputCount++;
        }
        return outputCount > 1;
    }

    /** Clears the editable members and output marks. */
    public void clearClosedLoopDraft() {
        if (isClientSide()) sendClientAction("clearClosedLoopDraft");
        else clearClosedLoopDraftServer();
    }

    private void clearClosedLoopDraftServer() {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return;
        resetClosedLoopDraft();
        uploadState = 0;
        seedRefillSync = SeedRefillSync.none();
        broadcastChanges();
    }

    private void autoFillClosedLoopServer() {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return;
        if (!closedLoopCandidates.isEmpty()) {
            selectClosedLoopCandidateServer(1);
            return;
        }
        var markedOutput = getMarkedClosedLoopPrimaryOutput();
        if (markedOutput == null) return;
        resetClosedLoopDraft();
        setClosedLoopPrimaryOutputMarker(markedOutput);
        refreshClosedLoops(markedOutput.what());
        broadcastChanges();
    }

    private void cycleClosedLoopOutputServer() {
        if (!isServerSide() || !canCycleClosedLoopOutputs()) return;
        var rotated = new ItemStack[CLOSED_LOOP_OUTPUT_SLOTS];
        for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
            rotated[i] = ItemStack.EMPTY;
            if (closedLoopOutputInventory.getStackInSlot(i).isEmpty()) continue;
            for (int offset = 1; offset < CLOSED_LOOP_OUTPUT_SLOTS; offset++) {
                var next = closedLoopOutputInventory.getStackInSlot(
                        (i + offset) % CLOSED_LOOP_OUTPUT_SLOTS);
                if (!next.isEmpty()) {
                    rotated[i] = next.copy();
                    break;
                }
            }
        }
        closedLoopBulkUpdating = true;
        try {
            java.util.Arrays.fill(closedLoopOutputRoles, 0);
            for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
                closedLoopOutputInventory.setItemDirect(i, rotated[i]);
                if (!rotated[i].isEmpty()) closedLoopOutputRoles[i] = i == 0 ? 1 : 2;
            }
        } finally {
            closedLoopBulkUpdating = false;
        }
        var primary = getMarkedClosedLoopPrimaryOutput();
        closedLoopMainOutput = primary != null ? primary.what() : null;
        closedLoopCandidates = List.of();
        closedLoopCandidateCount = 0;
        closedLoopCandidateIndex = 0;
        closedLoopDraftRepresentsEncoded = false;
        closedLoopDraftDirty = true;
        uploadState = 0;
        seedRefillSync = SeedRefillSync.none();
        broadcastChanges();
    }

    /** Tops up stored seeds for all enabled closed-loop patterns of the bound Tianshu. */
    public void refillClosedLoopSeeds() {
        if (isClientSide()) sendClientAction("refillClosedLoopSeeds");
        else refillClosedLoopSeedsServer();
    }

    private void refillClosedLoopSeedsServer() {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return;
        var result = TianshuSeedRefillService.refillAll(resolveOrBindTianshu());
        seedRefillSync = SeedRefillSync.of(result);
        // The refill outcome is the newest status; stop showing the last upload result.
        uploadState = 0;
        broadcastChanges();
    }

    private void selectClosedLoopCandidateServer(int delta) {
        if (!isServerSide() || closedLoopCandidates.isEmpty()) return;
        closedLoopCandidateIndex = Math.floorMod(closedLoopCandidateIndex + Integer.signum(delta),
                closedLoopCandidates.size());
        fillClosedLoopDraftFromSelectedCandidate();
        broadcastChanges();
    }

    public void changeClosedLoopExecutionSeedMultiplier(int delta) {
        if (isClientSide()) sendClientAction("changeClosedLoopExecutionSeedMultiplier", delta);
        else changeClosedLoopExecutionSeedMultiplierServer(delta);
    }

    private void changeClosedLoopExecutionSeedMultiplierServer(int delta) {
        if (!isServerSide() || delta == 0) return;
        closedLoopExecutionSeedMultiplier = adjustPositiveMultiplier(
                closedLoopExecutionSeedMultiplier, delta);
        closedLoopSeedMultiplier = closedLoopExecutionSeedMultiplier;
        closedLoopDraftRepresentsEncoded = false;
        closedLoopDraftDirty = true;
        broadcastChanges();
    }

    /** Legacy action name; changes only the per-job borrowed seed multiplier. */
    @Deprecated
    public void changeClosedLoopSeedMultiplier(int delta) {
        if (isClientSide()) sendClientAction("changeClosedLoopSeedMultiplier", delta);
        else changeClosedLoopSeedMultiplierServer(delta);
    }

    private void changeClosedLoopSeedMultiplierServer(int delta) {
        changeClosedLoopExecutionSeedMultiplierServer(delta);
    }

    public void changeClosedLoopStoredTaskMultiplier(int delta) {
        if (isClientSide()) sendClientAction("changeClosedLoopStoredTaskMultiplier", delta);
        else changeClosedLoopStoredTaskMultiplierServer(delta);
    }

    private void changeClosedLoopStoredTaskMultiplierServer(int delta) {
        if (!isServerSide() || delta == 0) return;
        closedLoopStoredTaskMultiplier = adjustPositiveMultiplier(
                closedLoopStoredTaskMultiplier, delta);
        closedLoopDraftRepresentsEncoded = false;
        closedLoopDraftDirty = true;
        broadcastChanges();
    }

    private static int adjustPositiveMultiplier(int value, int delta) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (long) value + delta));
    }

    /** Uploads the encoded pattern through the shared terminal upload action. */
    public void uploadEncodedPattern() {
        if (isClientSide()) {
            uploadState = 2;
            sendClientAction("uploadEncodedPattern", tianshuSelectionRevision);
        }
        else uploadEncodedPatternServer(tianshuSelectionRevision);
    }

    private void uploadEncodedPatternServer(int expectedSelectionRevision) {
        if (!isServerSide() || expectedSelectionRevision != tianshuSelectionRevision) return;
        uploadState = 2;
        var stack = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        switch (TianshuPatternUploadRouting.classify(stack, getPlayer().level())) {
            case CLOSED_LOOP_STORAGE -> uploadClosedLoopPatternServer(stack);
            case CRAFTING_ASSEMBLER -> {
                if (getPlayer() instanceof ServerPlayer player) {
                    uploadCraftingPatternServer(player, stack);
                } else {
                    finishUpload(false);
                }
            }
            case PROCESSING_PROVIDER, INVALID -> finishUpload(false);
        }
    }

    private void uploadClosedLoopPatternServer(ItemStack stack) {
        if (!(stack.getItem() instanceof ClosedLoopPatternItem item)) {
            finishUpload(false);
            return;
        }
        var sourceInventory = tianshuHost.getLogic().getEncodedPatternInv();
        var removed = sourceInventory.extractItem(0, 1, false);
        if (removed.isEmpty() || !ItemStack.isSameItemSameTags(stack, removed)) {
            if (!removed.isEmpty()) sourceInventory.addItems(removed);
            finishUpload(false);
            return;
        }
        var target = resolveOrBindTianshu();
        var payload = item.readPayload(removed, getPlayer().level()).orElse(null);
        var result = ClosedLoopPatternUploadService.upload(target, payload);
        var success = result == ClosedLoopPatternRepository.PutResult.ADDED
                || result == ClosedLoopPatternRepository.PutResult.UPDATED;
        if (!success) sourceInventory.addItems(removed);
        finishUpload(success);
    }

    /**
     * Binds the first available machine only when this menu has never had a target. A previously
     * captured target that disappeared is never replaced implicitly with a different machine.
     */
    @Nullable
    private com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity resolveOrBindTianshu() {
        var resolved = resolveBoundTianshu();
        if (resolved != null || boundTianshuTarget != null) return resolved;
        var available = tianshuHost.getAvailableTianshu();
        if (available.isEmpty()) return null;
        var selected = available.get(0);
        boundTianshuTarget = TianshuTerminalTarget.from(selected);
        tianshuSelectionRevision++;
        maintenanceEditorData = null;
        lastSentMaintenanceSummary = null;
        lastMaintenanceSummaryTick = Integer.MIN_VALUE;
        uploadState = 0;
        seedRefillSync = SeedRefillSync.none();
        return selected;
    }

    private void finishUpload(boolean success) {
        settleNetworkBlankCharge(success);
        uploadState = success ? 1 : 3;
        broadcastChanges();
    }

    public void requestUploadTargets() {
        if (isClientSide()) {
            PacketSender.sendToServer(new RequestUploadTargetsPacket(containerId));
        }
    }

    public void sendUploadTargets(ServerPlayer player) {
        if (!isServerSide() || player == null) return;
        refreshUploadTargetsNow();
        PacketSender.sendToPlayer(player,
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
        PacketSender.sendToServer(new UploadPatternToTargetPacket(containerId, group));
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

    private void uploadCraftingPatternServer(ServerPlayer player, ItemStack stack) {
        refreshUploadTargetsNow();
        if (uploadCraftingPatternToFirstTarget(player, stack, true)) return;
        if (uploadCraftingPatternToFirstTarget(player, stack, false)) return;
        finishProviderUpload(player, false);
    }

    private boolean uploadCraftingPatternToFirstTarget(
            ServerPlayer player, ItemStack stack, boolean matrixTarget) {
        for (var target : uploadTargets) {
            var group = target.getTerminalGroup();
            if (!TianshuPatternUploadRouting.isCraftingUploadGroup(group)
                    || TianshuPatternUploadRouting.isMatterWarpingMatrixGroup(group) != matrixTarget) {
                continue;
            }
            int free = firstFreePatternSlot(target.getTerminalPatternInventory(), stack);
            if (free >= 0) {
                uploadToProvider(player, target, free, stack);
                return true;
            }
        }
        return false;
    }

    private void uploadToProvider(ServerPlayer player, PatternContainer selected,
                                  int selectedSlot, ItemStack stack) {
        var sourceInventory = tianshuHost.getLogic().getEncodedPatternInv();
        var removed = sourceInventory.extractItem(0, 1, false);
        if (removed.isEmpty() || !ItemStack.isSameItemSameTags(stack, removed)) {
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
                if (!ItemStack.isSameItemSameTags(
                        targetInventory.getStackInSlot(selectedSlot), removed)) {
                    sourceInventory.addItems(removed);
                }
            } catch (RuntimeException ignored) {
            }
            finishProviderUpload(player, false);
        }
    }

    private void finishProviderUpload(ServerPlayer player, boolean success) {
        settleNetworkBlankCharge(success);
        uploadState = success ? 1 : 3;
        refreshUploadTargetsNow();
        PacketSender.sendToPlayer(player,
                new UploadTargetsSyncPacket(containerId, uploadTargetGroups));
        broadcastChanges();
    }

    private void refreshUploadTargetsNow() {
        refreshUploadTargetsNow(
                tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0));
    }

    private void refreshUploadTargetsNow(ItemStack stack) {
        uploadTargets = discoverUploadTargets();
        if (uploadTargets.isEmpty()) {
            uploadTargetGroups = List.of();
            return;
        }
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

    private List<PatternContainer> discoverUploadTargets() {
        return discoverUploadTargets(false);
    }

    private List<PatternContainer> discoverUploadTargets(boolean diagnostics) {
        var node = tianshuHost.getActionableNode();
        var grid = GridNodeAccess.getActiveGrid(node);
        if (grid == null) {
            if (diagnostics) {
                DUPLICATE_LOG.warn("Target scan found no grid (nodePresent={})", node != null);
            }
            return List.of();
        }
        var found = new ArrayList<PatternContainer>();
        int machineClasses = 0;
        int patternContainerClasses = 0;
        int activeContainers = 0;
        int hiddenContainers = 0;
        int foreignGridContainers = 0;
        int emptyInventories = 0;
        for (var machineClass : grid.getMachineClasses()) {
            machineClasses++;
            if (!PatternContainer.class.isAssignableFrom(machineClass)) continue;
            patternContainerClasses++;
            @SuppressWarnings("unchecked")
            var containerClass = (Class<? extends PatternContainer>) machineClass;
            for (var container : grid.getActiveMachines(containerClass)) {
                activeContainers++;
                if (!container.isVisibleInTerminal()) {
                    hiddenContainers++;
                    continue;
                }
                if (container.getGrid() != grid) {
                    foreignGridContainers++;
                    continue;
                }
                var inv = container.getTerminalPatternInventory();
                if (inv != null && inv.size() > 0) {
                    found.add(container);
                } else {
                    emptyInventories++;
                }
            }
        }
        found.sort(java.util.Comparator
                .comparing((PatternContainer host) -> host.getTerminalGroup().name().getString())
                .thenComparingLong(PatternContainer::getTerminalSortOrder));
        if (diagnostics) {
            DUPLICATE_LOG.debug("Target scan: machineClasses={}, patternContainerClasses={}, "
                            + "activeContainers={}, eligibleTargets={}, hidden={}, foreignGrid={}, "
                            + "missingOrEmptyInventory={}",
                    machineClasses, patternContainerClasses, activeContainers, found.size(),
                    hiddenContainers, foreignGridContainers, emptyInventories);
        }
        return List.copyOf(found);
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
        if (!refundableEncodedPattern.isEmpty()
                && !ItemStack.isSameItemSameTags(refundableEncodedPattern, source)
                && (!source.isEmpty() || uploadState != 2)) {
            refundableEncodedPattern = ItemStack.EMPTY;
        }
        if (ItemStack.matches(configuredSource, source)) return;
        boolean wasEncodedClosedLoop = configuredSource.getItem() instanceof ClosedLoopPatternItem;
        configuredSource = source.copy();
        encodedClosedLoop = source.getItem() instanceof ClosedLoopPatternItem;
        // Preserve the result when a successful provider upload empties the source slot.
        // A newly inserted/encoded pattern starts a fresh upload state.
        if (!source.isEmpty()) {
            uploadState = 0;
            seedRefillSync = SeedRefillSync.none();
            // The encoded item is authoritative. Never retain advanced/overload flags merely
            // because the newly loaded pattern happens to have the same fake-slot contents.
            resetProcessingEncodingType();
        }
        if (source.isEmpty() || !(source.getItem() instanceof ClosedLoopPatternItem)) {
            if (wasEncodedClosedLoop) {
                closedLoopDraftRepresentsEncoded = false;
                closedLoopDraftDirty = true;
            }
            if (!source.isEmpty()) restoreInsertedProcessingPattern(source);
            return;
        }
        if (source.getItem() instanceof ClosedLoopPatternItem closedLoopItem) {
            selectInsertedPatternMode(TianshuEncodingMode.CLOSED_LOOP);
            resetClosedLoopDraft();
            var payload = closedLoopItem.readPayload(source, getPlayer().level()).orElse(null);
            if (payload != null) {
                closedLoopExecutionSeedMultiplier = payload.executionSeedMultiplier();
                closedLoopSeedMultiplier = closedLoopExecutionSeedMultiplier;
                closedLoopStoredTaskMultiplier = payload.storedTaskMultiplier();
                fillClosedLoopDraft(payload);
                closedLoopDraftRepresentsEncoded = true;
            } else {
                closedLoopDraftStatus = ClosedLoopDraftStatus.MEMBER_UNDECODABLE;
                closedLoopEncodeState = 1;
            }
        }
    }

    private void restoreInsertedProcessingPattern(ItemStack source) {
        try {
            var details = PatternDetailsHelper.decodePattern(source, getPlayer().level());
            if (details == null) return;
            var advanced = AdvancedAECompat.restoreForEditing(
                    details, getProcessingInputSlots().length, getProcessingOutputSlots().length);
            if (source.getItem() instanceof OverloadPatternItem overloadItem) {
                var restored = conversionService.restoreEditableState(
                        overloadItem,
                        source,
                        new Ae2PlainPatternResolver(getPlayer().level())).orElse(null);
                if (restored == null
                        || !replaceProcessingInventories(restored.parsedPattern())) {
                    return;
                }
                var advancedConfig = advanced == null ? null
                        : new ProcessingPatternEncodingType.AdvancedConfig(advanced.directions());
                var overloadConfig = restoreOverloadConfig(
                        restored.encodedPattern(),
                        getProcessingInputSlots().length,
                        getProcessingOutputSlots().length);
                selectInsertedPatternMode(TianshuEncodingMode.PROCESSING);
                setProcessingDraft(
                        snapshotProcessingInputs(), snapshotProcessingOutputs(),
                        advancedConfig, overloadConfig);
                persistProcessingDraft();
                return;
            }
            if (advanced != null
                    && replaceProcessingInventories(advanced.inputs(), advanced.outputs())) {
                selectInsertedPatternMode(TianshuEncodingMode.PROCESSING);
                setProcessingDraft(
                        snapshotProcessingInputs(), snapshotProcessingOutputs(),
                        new ProcessingPatternEncodingType.AdvancedConfig(advanced.directions()),
                        null);
                persistProcessingDraft();
            }
        } catch (RuntimeException ignored) {
            // A malformed or unsupported third-party pattern must not poison the terminal draft.
        }
    }

    private boolean replaceProcessingInventories(ParsedPatternDefinition pattern) {
        var inputs = nullableStackList(tianshuHost.getLogic().getEncodedInputInv().size());
        var outputs = nullableStackList(tianshuHost.getLogic().getEncodedOutputInv().size());
        for (var input : pattern.inputs()) {
            if (input.slotIndex() >= inputs.size()) return false;
            var stack = GenericStack.fromItemStack(input.stack());
            if (stack == null) return false;
            inputs.set(input.slotIndex(), stack);
        }
        for (var output : pattern.outputs()) {
            if (output.slotIndex() >= outputs.size()) return false;
            var stack = GenericStack.fromItemStack(output.stack());
            if (stack == null) return false;
            outputs.set(output.slotIndex(), stack);
        }
        return replaceProcessingInventories(inputs, outputs);
    }

    private boolean replaceProcessingInventories(
            List<GenericStack> inputs, List<GenericStack> outputs) {
        var inputInventory = tianshuHost.getLogic().getEncodedInputInv();
        var outputInventory = tianshuHost.getLogic().getEncodedOutputInv();
        if (inputs.size() > inputInventory.size() || outputs.size() > outputInventory.size()) {
            return false;
        }
        inputInventory.clear();
        outputInventory.clear();
        for (int i = 0; i < inputs.size(); i++) inputInventory.setStack(i, inputs.get(i));
        for (int i = 0; i < outputs.size(); i++) outputInventory.setStack(i, outputs.get(i));
        return true;
    }

    private static List<GenericStack> nullableStackList(int size) {
        return new ArrayList<>(java.util.Collections.nCopies(Math.max(0, size), null));
    }

    private static ProcessingPatternEncodingType.OverloadConfig restoreOverloadConfig(
            EncodedOverloadPattern pattern, int inputSlots, int outputSlots) {
        for (var slot : pattern.inputSlots()) {
            checkedPatternSlot(slot.slotIndex(), inputSlots);
        }
        for (var slot : pattern.outputSlots()) {
            checkedPatternSlot(slot.slotIndex(), outputSlots);
        }
        var idOnlyInputs = pattern.inputSlots().stream()
                .filter(slot -> slot.matchMode() == MatchMode.ID_ONLY)
                .mapToInt(slot -> checkedPatternSlot(slot.slotIndex(), inputSlots))
                .toArray();
        var idOnlyOutputs = pattern.outputSlots().stream()
                .filter(slot -> slot.matchMode() == MatchMode.ID_ONLY)
                .mapToInt(slot -> checkedPatternSlot(slot.slotIndex(), outputSlots))
                .toArray();
        return new ProcessingPatternEncodingType.OverloadConfig(
                idOnlyInputs, idOnlyOutputs);
    }

    private static int checkedPatternSlot(int slot, int slotCount) {
        if (slot < 0 || slot >= slotCount) {
            throw new IllegalArgumentException("overload slot is outside the terminal draft");
        }
        return slot;
    }

    private void selectInsertedPatternMode(TianshuEncodingMode mode) {
        tianshuMode = mode;
        tianshuHost.setTianshuEncodingMode(mode);
        if (mode.ae2Mode() != null) {
            tianshuHost.getLogic().setMode(mode.ae2Mode());
            super.setMode(mode.ae2Mode());
        }
    }

    private void refreshClosedLoops(AEKey primaryOutput) {
        var node = tianshuHost.getActionableNode();
        var grid = GridNodeAccess.getActiveGrid(node);
        if (primaryOutput == null) return;
        closedLoopMainOutput = primaryOutput;
        if (grid == null) {
            closedLoopDraftStatus = ClosedLoopDraftStatus.NO_CANDIDATE;
            return;
        }
        var discovery = ClosedLoopDiscoveryService.discoverDetailed(
                grid.getCraftingService(), getPlayer().level(), primaryOutput);
        closedLoopCandidates = discovery.candidates();
        closedLoopCandidateCount = closedLoopCandidates.size();
        if (closedLoopCandidates.isEmpty() && discovery.rejectedUndecodablePattern()) {
            closedLoopDraftStatus = ClosedLoopDraftStatus.MEMBER_UNDECODABLE;
            closedLoopEncodeState = 1;
        } else if (closedLoopCandidates.isEmpty()) {
            closedLoopDraftStatus = ClosedLoopDraftStatus.NO_CANDIDATE;
        }
        fillClosedLoopDraftFromSelectedCandidate();
    }

    /** Automatic discovery only fills the same member list that manual editing owns. */
    private void fillClosedLoopDraftFromSelectedCandidate() {
        if (closedLoopCandidates.isEmpty()) return;
        int index = Math.max(0, Math.min(
                closedLoopCandidateIndex, closedLoopCandidates.size() - 1));
        fillClosedLoopDraft(closedLoopCandidates.get(index).payload());
        closedLoopDraftRepresentsEncoded = false;
    }

    @Nullable
    private GenericStack getMarkedClosedLoopPrimaryOutput() {
        var output = GenericStack.fromItemStack(closedLoopOutputInventory.getStackInSlot(0));
        return output != null && output.what() != null ? output : null;
    }

    private void setClosedLoopPrimaryOutputMarker(GenericStack output) {
        if (output == null || output.what() == null) return;
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopOutputInventory);
            java.util.Arrays.fill(closedLoopOutputRoles, 0);
            closedLoopOutputInventory.setItemDirect(0, GenericStack.wrapInItemStack(output));
            closedLoopOutputRoles[0] = 1;
            closedLoopMainOutput = output.what();
        } finally {
            closedLoopBulkUpdating = false;
        }
    }

    private void onClosedLoopPrimaryOutputMarked() {
        if (!isServerSide() || closedLoopBulkUpdating
                || tianshuMode != TianshuEncodingMode.CLOSED_LOOP) {
            return;
        }
        var marked = GenericStack.fromItemStack(closedLoopOutputInventory.getStackInSlot(0));
        closedLoopBulkUpdating = true;
        try {
            for (int i = 1; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
                closedLoopOutputInventory.setItemDirect(i, ItemStack.EMPTY);
            }
        } finally {
            closedLoopBulkUpdating = false;
        }
        java.util.Arrays.fill(closedLoopOutputRoles, 0);
        closedLoopMainOutput = marked != null ? marked.what() : null;
        if (closedLoopMainOutput != null) closedLoopOutputRoles[0] = 1;
        closedLoopCandidates = List.of();
        closedLoopCandidateCount = 0;
        closedLoopCandidateIndex = 0;
        closedLoopDraftRepresentsEncoded = false;
        closedLoopDraftDirty = true;
        uploadState = 0;
        seedRefillSync = SeedRefillSync.none();
    }

    private void resetClosedLoopDraft() {
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopMemberInventory);
            clearInventory(closedLoopOutputInventory);
        } finally {
            closedLoopBulkUpdating = false;
        }
        java.util.Arrays.fill(closedLoopMemberCopies, 0L);
        java.util.Arrays.fill(closedLoopOutputRoles, 0);
        closedLoopCandidates = List.of();
        closedLoopDraftMembers = List.of();
        closedLoopMainOutput = null;
        closedLoopCandidateCount = 0;
        closedLoopCandidateIndex = 0;
        closedLoopEncodeState = 0;
        closedLoopDraftStatus = ClosedLoopDraftStatus.EMPTY;
        closedLoopPreparedPayload = null;
        setClosedLoopComputedResults(List.of(), List.of());
        closedLoopDraftDirty = false;
        closedLoopDraftRepresentsEncoded = false;
    }

    /** Atomically replaces the editable draft and candidate marks. */
    private void fillClosedLoopDraft(ClosedLoopPatternPayload payload) {
        if (payload == null) return;
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopMemberInventory);
            clearInventory(closedLoopOutputInventory);
            java.util.Arrays.fill(closedLoopMemberCopies, 0L);
            java.util.Arrays.fill(closedLoopOutputRoles, 0);
            int memberCount = Math.min(CLOSED_LOOP_MEMBER_SLOTS, payload.memberPatterns().size());
            for (int i = 0; i < memberCount; i++) {
                var member = payload.memberPatterns().get(i);
                var stack = member.pattern().toItemStack();
                if (stack.isEmpty()) continue;
                closedLoopMemberInventory.setItemDirect(i, stack.copyWithCount(1));
                closedLoopMemberCopies[i] = member.copiesPerCycle();
            }
            int outputCount = Math.min(CLOSED_LOOP_OUTPUT_SLOTS, payload.netOutputs().size());
            for (int i = 0; i < outputCount; i++) {
                closedLoopOutputInventory.setItemDirect(
                        i, GenericStack.wrapInItemStack(payload.netOutputs().get(i)));
                closedLoopOutputRoles[i] = i == 0 ? 1 : 2;
            }
            closedLoopMainOutput = payload.netOutputs().isEmpty()
                    ? null : payload.netOutputs().get(0).what();
            closedLoopDraftMembers = List.copyOf(payload.memberPatterns());
        } finally {
            closedLoopBulkUpdating = false;
        }
        closedLoopPreparedPayload = null;
        setClosedLoopComputedResults(List.of(), List.of());
        closedLoopDraftDirty = true;
    }

    private static void clearInventory(appeng.api.inventories.InternalInventory inventory) {
        if (inventory == null) return;
        for (int i = 0; i < inventory.size(); i++) inventory.setItemDirect(i, ItemStack.EMPTY);
    }

    private void rebuildClosedLoopDraft() {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return;
        closedLoopDraftDirty = false;
        var draft = new ArrayList<ClosedLoopMemberPattern>();
        for (int i = 0; i < CLOSED_LOOP_MEMBER_SLOTS; i++) {
            var stack = closedLoopMemberInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                closedLoopMemberCopies[i] = 0L;
                continue;
            }
            if (!PatternDetailsHelper.isEncodedPattern(stack)
                    || isExecutionMemberReference(stack)) {
                setClosedLoopInvalid(ClosedLoopDraftStatus.MEMBER_UNDECODABLE);
                return;
            }
            long copies = closedLoopMemberCopies[i];
            if (copies < 1L) copies = 1L;
            closedLoopMemberCopies[i] = copies;
            try {
                draft.add(new ClosedLoopMemberPattern(
                        SourcePatternSnapshot.fromItemStack(stack), copies));
            } catch (RuntimeException ignored) {
                setClosedLoopInvalid(ClosedLoopDraftStatus.MEMBER_UNDECODABLE);
                return;
            }
        }
        closedLoopDraftMembers = List.copyOf(draft);
        if (draft.isEmpty()) {
            clearClosedLoopComputedResults();
            closedLoopDraftStatus = closedLoopCandidates.isEmpty()
                    ? ClosedLoopDraftStatus.NO_CANDIDATE : ClosedLoopDraftStatus.EMPTY;
            return;
        }
        if (!ClosedLoopPatternAnalyzer.isMinimalIntegerRatio(
                draft.stream().mapToLong(ClosedLoopMemberPattern::copiesPerCycle).toArray())) {
            setClosedLoopInvalid(ClosedLoopDraftStatus.NON_MINIMAL_COPIES);
            return;
        }

        var markedPrimary = getMarkedClosedLoopPrimaryOutput();
        if (markedPrimary == null) {
            clearClosedLoopComputedResults();
            closedLoopDraftStatus = ClosedLoopDraftStatus.MISSING_PRIMARY_OUTPUT;
            closedLoopEncodeState = 2;
            return;
        }
        var preferredOutputOrder = snapshotClosedLoopOutputKeys();
        closedLoopMainOutput = markedPrimary.what();
        var authored = ClosedLoopPatternAuthoringService.createFromDraft(
                draft, closedLoopMainOutput, closedLoopExecutionSeedMultiplier,
                closedLoopStoredTaskMultiplier, getPlayer().level());
        if (!authored.valid()) {
            clearClosedLoopComputedResults();
            setClosedLoopInvalid(mapAuthoringStatus(authored));
            return;
        }

        var payload = authored.payload();
        var orderedOutputs = orderClosedLoopOutputs(
                payload.netOutputs(), preferredOutputOrder);
        if (!orderedOutputs.equals(payload.netOutputs())) {
            payload = new ClosedLoopPatternPayload(
                    payload.memberPatterns(), payload.seeds(), payload.externalInputs(),
                    orderedOutputs, payload.executionSeedMultiplier(),
                    payload.storedTaskMultiplier(), payload.enabled());
        }
        writeOutputCandidates(payload.netOutputs());
        closedLoopPreparedPayload = payload;
        fillClosedLoopComputedResults(payload);
        closedLoopDraftStatus = closedLoopDraftRepresentsEncoded
                ? ClosedLoopDraftStatus.ENCODED : ClosedLoopDraftStatus.VALID;
        closedLoopEncodeState = 0;
    }

    private List<AEKey> snapshotClosedLoopOutputKeys() {
        var result = new ArrayList<AEKey>(CLOSED_LOOP_OUTPUT_SLOTS);
        for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
            var output = GenericStack.fromItemStack(closedLoopOutputInventory.getStackInSlot(i));
            if (output != null && output.what() != null && !result.contains(output.what())) {
                result.add(output.what());
            }
        }
        return result;
    }

    private static List<GenericStack> orderClosedLoopOutputs(
            List<GenericStack> analyzedOutputs,
            List<AEKey> preferredOrder) {
        if (analyzedOutputs.isEmpty()) return List.of();
        var remaining = new LinkedHashMap<AEKey, GenericStack>();
        for (var output : analyzedOutputs) remaining.put(output.what(), output);
        var ordered = new ArrayList<GenericStack>(analyzedOutputs.size());
        var primary = analyzedOutputs.get(0);
        ordered.add(primary);
        remaining.remove(primary.what());
        for (var key : preferredOrder) {
            var output = remaining.remove(key);
            if (output != null) ordered.add(output);
        }
        ordered.addAll(remaining.values());
        return List.copyOf(ordered);
    }

    private void writeOutputCandidates(List<GenericStack> outputs) {
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopOutputInventory);
            java.util.Arrays.fill(closedLoopOutputRoles, 0);
            int count = Math.min(CLOSED_LOOP_OUTPUT_SLOTS, outputs.size());
            for (int i = 0; i < count; i++) {
                var output = outputs.get(i);
                closedLoopOutputInventory.setItemDirect(i, GenericStack.wrapInItemStack(output));
                closedLoopOutputRoles[i] = i == 0 ? 1 : 2;
            }
            closedLoopMainOutput = count == 0 ? null : outputs.get(0).what();
        } finally {
            closedLoopBulkUpdating = false;
        }
    }

    private void fillClosedLoopComputedResults(ClosedLoopPatternPayload payload) {
        setClosedLoopComputedResults(payload.externalInputs(), payload.seeds());
    }

    private void clearClosedLoopComputedResults() {
        closedLoopPreparedPayload = null;
        setClosedLoopComputedResults(List.of(), List.of());
    }

    private void setClosedLoopComputedResults(
            List<GenericStack> externalInputs, List<GenericStack> seeds) {
        var nextExternalInputs = copyClosedLoopResults(externalInputs);
        var nextSeeds = copyClosedLoopResults(seeds);
        boolean changed = !nextExternalInputs.equals(closedLoopExternalInputs)
                || !nextSeeds.equals(closedLoopSeeds);
        closedLoopExternalInputs = nextExternalInputs;
        closedLoopSeeds = nextSeeds;
        closedLoopExternalInputCount = nextExternalInputs.size();
        closedLoopSeedInputCount = nextSeeds.size();
        if (changed) closedLoopResultRevision++;
    }

    private static List<GenericStack> copyClosedLoopResults(List<GenericStack> source) {
        if (source == null || source.isEmpty()) return List.of();
        var result = new ArrayList<GenericStack>(Math.min(CLOSED_LOOP_RESULT_SLOTS, source.size()));
        for (var entry : source) {
            if (entry != null && entry.what() != null && entry.amount() > 0L) {
                result.add(entry);
                if (result.size() == CLOSED_LOOP_RESULT_SLOTS) break;
            }
        }
        return List.copyOf(result);
    }

    private void setClosedLoopInvalid(ClosedLoopDraftStatus status) {
        clearClosedLoopComputedResults();
        closedLoopDraftStatus = status == null ? ClosedLoopDraftStatus.INVALID_OUTPUT_MARKING : status;
        closedLoopEncodeState = 2;
    }

    private static ClosedLoopDraftStatus mapAuthoringStatus(
            @Nullable ClosedLoopPatternAuthoringService.Result result) {
        if (result == null) return ClosedLoopDraftStatus.NOT_BALANCED;
        return switch (result.status()) {
            case MEMBER_UNDECODABLE -> ClosedLoopDraftStatus.MEMBER_UNDECODABLE;
            case TOO_MANY_MEMBERS -> ClosedLoopDraftStatus.TOO_MANY_MEMBERS;
            case NON_MINIMAL_COPIES -> ClosedLoopDraftStatus.NON_MINIMAL_COPIES;
            case INVALID_SEED_ROUTING -> ClosedLoopDraftStatus.INVALID_SEED_ROUTING;
            case INVALID_MARKING -> ClosedLoopDraftStatus.INVALID_OUTPUT_MARKING;
            case NOT_BALANCED -> ClosedLoopDraftStatus.NOT_BALANCED;
            case VALID -> ClosedLoopDraftStatus.NOT_BALANCED;
        };
    }

    private void refreshClosedLoopDraftSync() {
        var copies = new ArrayList<Long>(CLOSED_LOOP_MEMBER_SLOTS);
        for (long value : closedLoopMemberCopies) copies.add(value);
        var roles = new ArrayList<Integer>(CLOSED_LOOP_OUTPUT_SLOTS);
        for (int value : closedLoopOutputRoles) roles.add(value);
        closedLoopDraftSync = new com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopDraftSync(
                copies, roles);
    }

    private void restoreClosedLoopDraft(@Nullable ClosedLoopTerminalDraft draft) {
        var source = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (draft == null || (source.getItem() instanceof ClosedLoopPatternItem
                && !ItemStack.matches(source, draft.source()))) {
            return;
        }
        configuredSource = source.copy();
        encodedClosedLoop = source.getItem() instanceof ClosedLoopPatternItem;
        closedLoopBulkUpdating = true;
        try {
            for (int i = 0; i < CLOSED_LOOP_MEMBER_SLOTS; i++) {
                closedLoopMemberInventory.setItemDirect(i, draft.members().get(i).copy());
                closedLoopMemberCopies[i] = draft.memberCopies().get(i);
            }
            int primaryIndex = -1;
            for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
                if (draft.outputRoles().get(i) == 1 && !draft.outputs().get(i).isEmpty()) {
                    primaryIndex = i;
                    break;
                }
            }
            if (primaryIndex < 0 && !draft.outputs().get(0).isEmpty()) primaryIndex = 0;
            clearInventory(closedLoopOutputInventory);
            java.util.Arrays.fill(closedLoopOutputRoles, 0);
            int outputSlot = 0;
            if (primaryIndex >= 0) {
                closedLoopOutputInventory.setItemDirect(
                        outputSlot, draft.outputs().get(primaryIndex).copy());
                closedLoopOutputRoles[outputSlot++] = 1;
            }
            for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS
                    && outputSlot < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
                if (i == primaryIndex || draft.outputs().get(i).isEmpty()) continue;
                closedLoopOutputInventory.setItemDirect(outputSlot, draft.outputs().get(i).copy());
                closedLoopOutputRoles[outputSlot++] = 2;
            }
        } finally {
            closedLoopBulkUpdating = false;
        }
        closedLoopExecutionSeedMultiplier = draft.executionSeedMultiplier();
        closedLoopSeedMultiplier = closedLoopExecutionSeedMultiplier;
        closedLoopStoredTaskMultiplier = draft.storedTaskMultiplier();
        closedLoopDraftRepresentsEncoded = draft.representsEncodedPattern();
        var primary = getMarkedClosedLoopPrimaryOutput();
        closedLoopMainOutput = primary != null ? primary.what() : null;
        closedLoopDraftDirty = draft.members().stream().anyMatch(stack -> !stack.isEmpty());
    }

    private void persistClosedLoopDraft() {
        var source = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        boolean hasMember = false;
        for (int i = 0; i < CLOSED_LOOP_MEMBER_SLOTS; i++) {
            if (!closedLoopMemberInventory.getStackInSlot(i).isEmpty()) {
                hasMember = true;
                break;
            }
        }
        boolean hasOutputMark = getMarkedClosedLoopPrimaryOutput() != null;
        if (!hasMember && !hasOutputMark
                && !(source.getItem() instanceof ClosedLoopPatternItem)) {
            tianshuHost.setClosedLoopTerminalDraft(null);
            return;
        }
        var members = new ArrayList<ItemStack>(CLOSED_LOOP_MEMBER_SLOTS);
        for (int i = 0; i < CLOSED_LOOP_MEMBER_SLOTS; i++) {
            members.add(closedLoopMemberInventory.getStackInSlot(i).copy());
        }
        var copies = new ArrayList<Long>(CLOSED_LOOP_MEMBER_SLOTS);
        for (long copiesPerCycle : closedLoopMemberCopies) copies.add(copiesPerCycle);
        var outputs = new ArrayList<ItemStack>(CLOSED_LOOP_OUTPUT_SLOTS);
        for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
            outputs.add(closedLoopOutputInventory.getStackInSlot(i).copy());
        }
        var roles = new ArrayList<Integer>(CLOSED_LOOP_OUTPUT_SLOTS);
        for (int role : closedLoopOutputRoles) roles.add(role);
        tianshuHost.setClosedLoopTerminalDraft(new ClosedLoopTerminalDraft(
                source, members, copies, outputs, roles,
                closedLoopExecutionSeedMultiplier, closedLoopStoredTaskMultiplier,
                closedLoopDraftRepresentsEncoded));
    }

    private boolean isExecutionMemberReference(ItemStack stack) {
        return stack.getItem() instanceof ClosedLoopPatternItem item
                && item.readExecutionMember(stack) >= 0;
    }

    public void requestMaintenanceEditor(appeng.api.stacks.AEKey key) {
        if (!isClientSide()) return;
        PacketSender.sendToServer(new OpenMaintenanceEditorPacket(
                containerId, tianshuSelectionRevision, key));
    }

    public void openMaintenanceEditor(int expectedSelectionRevision, appeng.api.stacks.AEKey key) {
        if (!isServerSide() || expectedSelectionRevision != tianshuSelectionRevision
                || !(getPlayer() instanceof ServerPlayer serverPlayer)) return;
        var target = resolveBoundTianshu();
        if (key == null || target == null
                || !target.getFunctionProfile().supportsInventoryMaintenance()) return;
        var maintenance = target.getInventoryMaintenance();
        var grid = target.getGrid();
        if (maintenance == null) return;
        if (maintenance.repository().get(key) == null
                && (grid == null || !grid.getCraftingService().isCraftable(key))) {
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "ae2lt.tianshu.maintenance.unsupported"), true);
            return;
        }
        sendMaintenanceEditorData(serverPlayer, key);
    }

    public void setMaintainableView(boolean enabled) {
        maintainableView = enabled;
        if (isClientSide()) {
            sendClientAction("setMaintainableView", enabled);
        } else setMaintainableViewServer(enabled);
    }

    public void setMaintainableViewTemporarily(boolean enabled) {
        maintainableView = enabled;
        if (isClientSide()) {
            sendClientAction("setMaintainableViewTemporarily", enabled);
        } else setMaintainableViewTemporarilyServer(enabled);
    }

    private void setMaintainableViewServer(boolean enabled) {
        applyMaintainableViewServer(enabled, true);
    }

    private void setMaintainableViewTemporarilyServer(boolean enabled) {
        applyMaintainableViewServer(enabled, false);
    }

    private void applyMaintainableViewServer(boolean enabled, boolean persist) {
        if (!isServerSide()) return;
        maintainableView = enabled;
        if (persist) tianshuHost.setMaintainableView(enabled);
        getConfigManager().putSetting(Settings.VIEW_MODE, ViewItems.ALL);
        broadcastChanges();
    }

    @Override
    protected boolean showsCraftables() {
        return maintainableView || super.showsCraftables();
    }

    private void sendMaintenanceSummaryIfNeeded() {
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
                    boolean craftable = crafting != null && crafting.isCraftable(rule.key());
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
                    boolean craftable = crafting != null && crafting.isCraftable(reserve.key());
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
        PacketSender.sendToPlayer(player, new MaintenanceSummarySyncPacket(
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
            sendClientAction("maintenanceAction",
                    new MaintenanceAction(tianshuSelectionRevision, ruleId, cancel));
        }
    }

    private void maintenanceActionServer(MaintenanceAction action) {
        if (!isServerSide() || action == null
                || action.selectionRevision() != tianshuSelectionRevision) return;
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
        if (isClientSide() && key != null && mode != null) PacketSender.sendToServer(
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
        var grid = GridNodeAccess.getActiveGrid(tianshuHost.getActionableNode());
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
        boolean craftable = grid != null && grid.getCraftingService().isCraftable(key);
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
        PacketSender.sendToPlayer(player, new MaintenanceEditorSyncPacket(
                containerId, tianshuSelectionRevision, data));
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
        if (!isClientSide() || data == null || selectionRevision < tianshuSelectionRevision) return;
        maintenanceEditorSelectionRevision = selectionRevision;
        maintenanceEditorData = data;
        maintenanceEditorRevision++;
    }

    @Nullable public MaintenanceEditorData getMaintenanceEditorData() { return maintenanceEditorData; }
    public int getMaintenanceEditorRevision() { return maintenanceEditorRevision; }

    public void sendMaintenanceSave(SaveMaintenanceRulePacket packet) {
        if (isClientSide() && packet != null) {
            PacketSender.sendToServer(packet);
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
            if (grid == null || !grid.getCraftingService().isCraftable(packet.target())) {
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

    @Override
    public void encode() {
        if (isClientSide()) {
            beginClientEncoding(
                    com.moakiee.ae2lt.client.TianshuUploadTriggerClient.shouldTrigger(), false);
            return;
        }
        encodeServerWithOptions(false);
    }

    /** Used only after a recipe viewer confirms that its slot transfer succeeded. */
    public void encodeAndUploadDirectly() {
        if (!isClientSide()) return;
        beginClientEncoding(true, true);
    }

    /**
     * Requests the output refresh action registered by Polymorphic Energistics' menu mixin.
     * Called only by the optional client widget when both compatibility mods are loaded.
     */
    public void refreshPolymorphRecipe() {
        if (isClientSide()) sendClientAction("polyeng$selectRecipe");
    }

    private void beginClientEncoding(boolean triggerUpload, boolean directUpload) {
        com.moakiee.ae2lt.client.TianshuRecipeTransferContext.beginEncoding(
                this, tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0));
        pendingTriggeredUpload = triggerUpload;
        pendingDirectUpload = triggerUpload && directUpload;
        pendingTriggeredUploadUntil = getPlayer().tickCount + 200;
        expectedTriggeredUploadAck = triggeredUploadAck;
        directUploadTargetsRequested = false;
        // Duplicate interception belongs to the upload flow. In the default NO_SHIFT mode,
        // holding Shift therefore bypasses both upload and duplicate interception while still
        // allowing the pattern to be encoded normally.
        sendClientAction("encodeTianshu", triggerUpload
                && com.moakiee.ae2lt.config.AE2LTClientConfig.interceptDuplicatePatternEncoding());
    }

    private void encodeServerWithOptions(Boolean interceptDuplicateUpload) {
        if (!isServerSide()) return;
        boolean interceptDuplicates = Boolean.TRUE.equals(interceptDuplicateUpload);
        if (tianshuMode.isAe2Mode()) {
            var encodedInventory = tianshuHost.getLogic().getEncodedPatternInv();
            boolean carriesNetworkBlank = isRefundableEncodedPattern(
                    encodedInventory.getStackInSlot(0));
            boolean stagedNetworkBlank = false;
            ae2EncodingInProgress = true;
            try {
                stagedNetworkBlank = stageNetworkBlankPattern();
                try (var ignored = ExtendedAEPlusEncodingCompat.suppressAutomaticUpload(this)) {
                    super.encode();
                }
                applyConfiguredProcessingConversion();
            } finally {
                ae2EncodingInProgress = false;
                if (stagedNetworkBlank) returnStagedBlankPatternToNetwork();
            }
            var encoded = encodedInventory.getStackInSlot(0);
            if (TianshuPatternUploadRouting.isValidEncodingResult(encoded, getPlayer().level())) {
                if (stagedNetworkBlank || carriesNetworkBlank) {
                    refundableEncodedPattern = encoded.copy();
                }
                if (shouldInterceptDuplicateEncoding(encoded, interceptDuplicates)) {
                    rollbackRefundableEncodedPattern();
                    notifyDuplicateEncodingIntercepted();
                    broadcastChanges();
                    return;
                }
                triggeredUploadAck++;
            }
            broadcastChanges();
            return;
        }
        var result = encodeDerivedPattern();
        if (result != null && !result.isEmpty()) {
            if (shouldInterceptDuplicateEncoding(result, interceptDuplicates)) {
                notifyDuplicateEncodingIntercepted();
                return;
            }
            var encodedInventory = tianshuHost.getLogic().getEncodedPatternInv();
            boolean carriesNetworkBlank = isRefundableEncodedPattern(
                    encodedInventory.getStackInSlot(0));
            boolean stagedNetworkBlank = false;
            if (encodedInventory.getStackInSlot(0).isEmpty()) {
                stagedNetworkBlank = stageNetworkBlankPattern();
                if (!stagedNetworkBlank) return;
            }
            if (tianshuMode == TianshuEncodingMode.CLOSED_LOOP) closedLoopEncodeState = 0;
            encodedInventory.setItemDirect(0, result);
            if (stagedNetworkBlank || carriesNetworkBlank) {
                refundableEncodedPattern = result.copy();
            }
            if (TianshuPatternUploadRouting.isValidEncodingResult(result, getPlayer().level())) {
                triggeredUploadAck++;
            }
            broadcastChanges();
        }
    }

    private boolean shouldInterceptDuplicateEncoding(ItemStack candidate, boolean enabled) {
        if (!enabled || candidate == null || candidate.isEmpty()) {
            return false;
        }
        var route = TianshuPatternUploadRouting.classify(candidate, getPlayer().level());
        if (route == TianshuPatternUploadRouting.Route.INVALID) {
            return false;
        }
        var candidateDescription = PatternEncodingDuplicateFilter.describeStack(candidate);
        DUPLICATE_LOG.debug("Check start: player={}, route={}, candidate={}",
                getPlayer().getGameProfile().getName(), route, candidateDescription);
        uploadTargets = discoverUploadTargets(true);
        var level = getPlayer().level();
        for (int targetIndex = 0; targetIndex < uploadTargets.size(); targetIndex++) {
            var target = uploadTargets.get(targetIndex);
            var inventory = target.getTerminalPatternInventory();
            var result = PatternEncodingDuplicateFilter.checkEquivalentPattern(
                    inventory, candidate, level);
            if (DUPLICATE_LOG.isDebugEnabled()) {
                DUPLICATE_LOG.debug("Target {}: type={}, group={}, slots={}, occupiedScanned={}, "
                                + "undecodable={}, duplicate={}, matchedSlot={}, method={}, stored={}",
                        targetIndex, target.getClass().getName(),
                        target.getTerminalGroup().name().getString(), inventory.size(),
                        result.occupiedSlots(), result.undecodableSlots(), result.duplicate(),
                        result.matchedSlot(), result.matchMethod(),
                        result.duplicate() ? "matched"
                                : PatternEncodingDuplicateFilter.describeOccupiedStacks(inventory, 8));
            }
            if (result.duplicate()) {
                DUPLICATE_LOG.debug("Check result: BLOCK candidate={} target={} slot={} method={}",
                        candidateDescription, targetIndex, result.matchedSlot(), result.matchMethod());
                return true;
            }
        }
        DUPLICATE_LOG.debug("Check result: ALLOW candidate={} because no equivalent pattern was "
                        + "found across {} eligible targets",
                candidateDescription, uploadTargets.size());
        return false;
    }

    private void notifyDuplicateEncodingIntercepted() {
        if (getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "ae2lt.tianshu.encode.duplicate_blocked"), false);
        }
    }

    /** @return whether this menu is attached to an active AE2 grid (1.20.1 API replacement). */
    private boolean isConnectedToNetwork() {
        return GridNodeAccess.getActiveGrid(getNetworkNode()) != null;
    }

    private boolean stageNetworkBlankPattern() {
        var encodedInventory = tianshuHost.getLogic().getEncodedPatternInv();
        if (!encodedInventory.getStackInSlot(0).isEmpty() || !isConnectedToNetwork()) return false;
        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        var actionSource = getActionSource();
        long available = storage.extract(
                blankPatternKey, 1, Actionable.SIMULATE, actionSource);
        if (available <= 0) {
            notifyEncodingFailure("ae2lt.tianshu.encode.missing_blank");
            return false;
        }
        long poweredAvailable = StorageHelper.poweredExtraction(
                powerSource, storage, blankPatternKey, 1, actionSource, Actionable.SIMULATE);
        if (poweredAvailable <= 0) {
            notifyEncodingFailure("ae2lt.tianshu.encode.insufficient_power");
            return false;
        }
        long extracted = StorageHelper.poweredExtraction(
                powerSource, storage, blankPatternKey, 1, actionSource);
        if (extracted <= 0) {
            notifyEncodingFailure("ae2lt.tianshu.encode.extraction_failed");
            return false;
        }
        encodedInventory.setItemDirect(0, AEItems.BLANK_PATTERN.stack((int) extracted));
        return true;
    }

    private void notifyEncodingFailure(String translationKey) {
        if (getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(translationKey), false);
        }
    }

    private void returnStagedBlankPatternToNetwork() {
        if (!isServerSide() || !isConnectedToNetwork()) return;
        var encodedInventory = tianshuHost.getLogic().getEncodedPatternInv();
        var stack = encodedInventory.getStackInSlot(0);
        if (!AEItems.BLANK_PATTERN.isSameAs(stack)) return;
        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        long inserted = StorageHelper.poweredInsert(
                powerSource, storage, blankPatternKey, stack.getCount(), getActionSource());
        if (inserted <= 0) return;
        var remainder = stack.copy();
        remainder.shrink((int) inserted);
        encodedInventory.setItemDirect(0, remainder);
    }

    private boolean isRefundableEncodedPattern(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !refundableEncodedPattern.isEmpty()
                && ItemStack.isSameItemSameTags(refundableEncodedPattern, stack);
    }

    /**
     * Compensates a failed encode/upload by replacing exactly this menu's generated pattern with
     * one blank pattern in network storage. If the network cannot accept the refund, the encoded
     * pattern is restored to the terminal so no item is lost.
     */
    private boolean rollbackRefundableEncodedPattern() {
        if (!isServerSide() || !isConnectedToNetwork()) return false;
        var encodedInventory = tianshuHost.getLogic().getEncodedPatternInv();
        var current = encodedInventory.getStackInSlot(0);
        if (!isRefundableEncodedPattern(current)) {
            refundableEncodedPattern = ItemStack.EMPTY;
            return false;
        }

        var debt = refundableEncodedPattern.copy();
        var removed = encodedInventory.extractItem(0, 1, false);
        if (removed.isEmpty() || !ItemStack.isSameItemSameTags(debt, removed)) {
            if (!removed.isEmpty()) encodedInventory.addItems(removed);
            refundableEncodedPattern = ItemStack.EMPTY;
            return false;
        }

        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        long inserted = StorageHelper.poweredInsert(
                powerSource, storage, blankPatternKey, 1, getActionSource());
        if (inserted == 1) {
            refundableEncodedPattern = ItemStack.EMPTY;
            return true;
        }

        encodedInventory.addItems(removed);
        refundableEncodedPattern = debt;
        return false;
    }

    private void settleNetworkBlankCharge(boolean uploadSucceeded) {
        if (uploadSucceeded) {
            refundableEncodedPattern = ItemStack.EMPTY;
        } else {
            rollbackRefundableEncodedPattern();
        }
    }

    /** Clears a blank left by an older menu version that staged it in the hidden input inventory. */
    private void returnLegacyBlankPatternsToNetwork() {
        if (!isServerSide() || !isConnectedToNetwork()) return;
        var blankInventory = tianshuHost.getLogic().getBlankPatternInv();
        var stack = blankInventory.getStackInSlot(0);
        if (!AEItems.BLANK_PATTERN.isSameAs(stack)) return;
        var blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        long inserted = StorageHelper.poweredInsert(
                powerSource, storage, blankPatternKey, stack.getCount(), getActionSource());
        if (inserted <= 0) return;
        var remainder = stack.copy();
        remainder.shrink((int) inserted);
        blankInventory.setItemDirect(0, remainder);
    }

    private ItemStack encodeDerivedPattern() {
        if (tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return ItemStack.EMPTY;
        return encodeSelectedClosedLoopCandidate();
    }

    /** Applies the persistent processing configuration to the freshly encoded pattern. */
    private void applyConfiguredProcessingConversion() {
        if (tianshuMode != TianshuEncodingMode.PROCESSING
                || processingEncodingType == ProcessingPatternEncodingType.NORMAL) return;
        var inventory = tianshuHost.getLogic().getEncodedPatternInv();
        var source = inventory.getStackInSlot(0);
        if (source.isEmpty()) return;
        var converted = convertConfiguredProcessingPattern(
                source, getAdvancedEncodingConfig(), getOverloadEncodingConfig());
        if (converted != null && !converted.isEmpty()) {
            inventory.setItemDirect(0, converted);
        }
    }

    @Nullable
    private ItemStack convertConfiguredProcessingPattern(
            ItemStack source,
            @Nullable ProcessingPatternEncodingType.AdvancedConfig advancedConfig,
            @Nullable ProcessingPatternEncodingType.OverloadConfig overloadConfig) {
        ItemStack converted = source;
        if (advancedConfig != null) {
            converted = convertToAdvanced(converted, advancedConfig);
            if (converted == null || converted.isEmpty()) return null;
        }
        if (overloadConfig != null) {
            converted = convertToOverload(converted, overloadConfig);
            if (converted == null || converted.isEmpty()) return null;
        }
        return converted;
    }

    @Nullable
    private ItemStack convertToAdvanced(
            ItemStack source, @Nullable ProcessingPatternEncodingType.AdvancedConfig config) {
        if (config == null) return null;
        int slotCount = getProcessingInputSlots().length;
        var sides = new ArrayList<Integer>(slotCount);
        for (int i = 0; i < slotCount; i++) sides.add(config.direction(i));
        return AdvancedAECompat.encodeWithDirections(source, getPlayer().level(), sides);
    }

    @Nullable
    private ItemStack convertToOverload(
            ItemStack source, @Nullable ProcessingPatternEncodingType.OverloadConfig config) {
        if (config == null) return null;
        try {
            var editable = conversionService.resolveEditableSource(
                    source, new Ae2PlainPatternResolver(getPlayer().level()))
                    .orElse(null);
            if (editable == null) return null;
            var parsed = editable.parsedPattern();
            var builder = EncodedOverloadPattern.builder();
            for (var input : parsed.inputs()) {
                builder.input(input.slotIndex(), config.isInputIdOnly(input.slotIndex())
                        ? MatchMode.ID_ONLY : MatchMode.STRICT);
            }
            for (var output : parsed.outputs()) {
                builder.output(output.slotIndex(), config.isOutputIdOnly(output.slotIndex())
                        ? MatchMode.ID_ONLY : MatchMode.STRICT);
            }
            return conversionService.createOverloadPatternStack(
                    (OverloadPatternItem) ModItems.OVERLOAD_PATTERN.get(), parsed, builder.build());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ItemStack encodeSelectedClosedLoopCandidate() {
        if (closedLoopDraftDirty) rebuildClosedLoopDraft();
        if (closedLoopPreparedPayload == null
                || closedLoopDraftStatus != ClosedLoopDraftStatus.VALID
                && closedLoopDraftStatus != ClosedLoopDraftStatus.ENCODED) {
            closedLoopEncodeState = closedLoopDraftStatus == ClosedLoopDraftStatus.MEMBER_UNDECODABLE
                    ? 1 : 2;
            broadcastChanges();
            return ItemStack.EMPTY;
        }
        return ((ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get()).createStack(
                closedLoopPreparedPayload, getPlayer().level().registryAccess());
    }

    public record ClosedLoopMemberEdit(int slot, long copies) {
    }

    public record ClosedLoopMemberMove(int slot, int direction) {
    }

    public record ClosedLoopMultiplierEdit(int execution, int stored) {
    }

    public record MaintenanceAction(int selectionRevision, UUID ruleId, boolean cancel) {
    }

    private final class ClosedLoopMemberSlot extends FakeSlot {
        private ClosedLoopMemberSlot(AppEngInternalInventory inventory, int slot) {
            super(inventory, slot);
            setHideAmount(true);
        }

        @Override
        public void set(ItemStack stack) {
            super.set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return canUseAsClosedLoopMember(stack);
        }

        @Override
        public boolean canSetFilterTo(ItemStack stack) {
            return stack.isEmpty() || canUseAsClosedLoopMember(stack);
        }

        @Override
        public void setFilterTo(ItemStack stack) {
            if (canSetFilterTo(stack)) super.setFilterTo(stack);
        }

        private boolean canUseAsClosedLoopMember(ItemStack stack) {
            return stack != null && !stack.isEmpty()
                    && PatternDetailsHelper.isEncodedPattern(stack)
                    && !isExecutionMemberReference(stack);
        }
    }

    private final class ClosedLoopOutputSlot extends FakeSlot {
        private ClosedLoopOutputSlot(AppEngInternalInventory inventory) {
            super(inventory, 0);
        }

        @Override
        public void set(ItemStack stack) {
            if (!canSetFilterTo(stack)) return;
            var marked = stack == null ? null : GenericStack.fromItemStack(stack);
            super.set(marked == null
                    ? ItemStack.EMPTY : GenericStack.wrapInItemStack(marked.what(), 1));
            onClosedLoopPrimaryOutputMarked();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return canSetFilterTo(stack);
        }

        @Override
        public boolean canSetFilterTo(ItemStack stack) {
            return stack == null || stack.isEmpty() || GenericStack.fromItemStack(stack) != null;
        }
    }

    private static final class ClosedLoopReadonlySlot extends AppEngSlot {
        private ClosedLoopReadonlySlot(AppEngInternalInventory inventory, int slot) {
            super(inventory, slot);
            setNotDraggable();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }
    }
}
