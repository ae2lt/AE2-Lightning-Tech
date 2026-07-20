package com.moakiee.ae2lt.menu;

import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
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
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopDraftStatus;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopDraftSync;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternMultiplier;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
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
import com.moakiee.thunderbolt.ae2.overload.model.EncodedOverloadPattern;
import com.moakiee.thunderbolt.ae2.overload.model.MatchMode;
import com.moakiee.thunderbolt.ae2.overload.pattern.Ae2PlainPatternResolver;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.moakiee.ae2lt.network.tianshu.SaveGlobalReservePacket;
import com.moakiee.ae2lt.network.tianshu.TianshuPacketLimits;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import net.minecraft.world.entity.player.Player;

public class TianshuPatternEncodingTermMenu extends PatternEncodingTermMenu {
    public static final int CLOSED_LOOP_MEMBER_SLOTS = ClosedLoopDraftSync.MEMBER_SLOTS;
    public static final int CLOSED_LOOP_OUTPUT_SLOTS = ClosedLoopDraftSync.OUTPUT_SLOTS;
    public static final int CLOSED_LOOP_RESULT_SLOTS = ClosedLoopPatternAnalyzer.MAX_MEMBERS * 9;
    private static final int CLOSED_LOOP_OFFSCREEN = -10000;
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

    protected final TianshuPatternTerminalHost tianshuHost;
    @Nullable private TianshuTerminalTarget boundTianshuTarget;
    private final PatternConversionService conversionService = new PatternConversionService();
    private ItemStack configuredSource = ItemStack.EMPTY;
    @Nullable private ProcessingPatternEncodingType.AdvancedConfig advancedEncodingConfig;
    @Nullable private ProcessingPatternEncodingType.OverloadConfig overloadEncodingConfig;
    private List<ClosedLoopDiscoveryCandidate> closedLoopCandidates = List.of();
    private List<ClosedLoopMemberPattern> closedLoopDraftMembers = List.of();
    @Nullable private AEKey closedLoopMainOutput;
    private final AppEngInternalInventory closedLoopMemberInventory;
    private final AppEngInternalInventory closedLoopOutputInventory;
    private final AppEngInternalInventory closedLoopExternalInputInventory;
    private final AppEngInternalInventory closedLoopSeedInventory;
    private final List<AppEngSlot> closedLoopMemberSlots = new ArrayList<>();
    private final List<AppEngSlot> closedLoopOutputSlots = new ArrayList<>();
    private final List<AppEngSlot> closedLoopExternalInputSlots = new ArrayList<>();
    private final List<AppEngSlot> closedLoopSeedSlots = new ArrayList<>();
    private final long[] closedLoopMemberCopies = new long[CLOSED_LOOP_MEMBER_SLOTS];
    private final int[] closedLoopOutputRoles = new int[CLOSED_LOOP_OUTPUT_SLOTS];
    private boolean closedLoopBulkUpdating;
    private boolean closedLoopDraftDirty;
    private boolean closedLoopDraftRepresentsEncoded;
    @Nullable private ClosedLoopPatternPayload closedLoopPreparedPayload;
    @Nullable private UUID closedLoopOriginalPatternId;
    private long closedLoopOriginalPatternVersion;
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
        this.closedLoopMemberInventory = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory inv) {
                if (!closedLoopBulkUpdating) {
                    closedLoopDraftRepresentsEncoded = false;
                    closedLoopDraftDirty = true;
                }
            }

            @Override
            public boolean isClientSide() {
                return TianshuPatternEncodingTermMenu.this.isClientSide();
            }
        }, CLOSED_LOOP_MEMBER_SLOTS, 1);
        this.closedLoopOutputInventory = new AppEngInternalInventory(null, CLOSED_LOOP_OUTPUT_SLOTS, 1);
        this.closedLoopExternalInputInventory = new AppEngInternalInventory(null, CLOSED_LOOP_RESULT_SLOTS, 1);
        this.closedLoopSeedInventory = new AppEngInternalInventory(null, CLOSED_LOOP_RESULT_SLOTS, 1);
        for (int i = 0; i < CLOSED_LOOP_MEMBER_SLOTS; i++) {
            var slot = new ClosedLoopMemberSlot(closedLoopMemberInventory, i);
            slot.x = CLOSED_LOOP_OFFSCREEN;
            slot.y = CLOSED_LOOP_OFFSCREEN;
            addSlot(slot, Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_MEMBER);
            closedLoopMemberSlots.add(slot);
        }
        for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
            var slot = new ClosedLoopReadonlySlot(closedLoopOutputInventory, i);
            slot.x = CLOSED_LOOP_OFFSCREEN;
            slot.y = CLOSED_LOOP_OFFSCREEN;
            addSlot(slot, Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_OUTPUT_MARK);
            closedLoopOutputSlots.add(slot);
        }
        for (int i = 0; i < CLOSED_LOOP_RESULT_SLOTS; i++) {
            var external = new ClosedLoopReadonlySlot(closedLoopExternalInputInventory, i);
            external.x = CLOSED_LOOP_OFFSCREEN;
            external.y = CLOSED_LOOP_OFFSCREEN;
            addSlot(external, Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_EXTERNAL_INPUT);
            closedLoopExternalInputSlots.add(external);
            var seed = new ClosedLoopReadonlySlot(closedLoopSeedInventory, i);
            seed.x = CLOSED_LOOP_OFFSCREEN;
            seed.y = CLOSED_LOOP_OFFSCREEN;
            addSlot(seed, Ae2ltSlotSemantics.TIANSHU_CLOSED_LOOP_SEED_INPUT);
            closedLoopSeedSlots.add(seed);
        }
        this.boundTianshuTarget = inventory.player.level().isClientSide
                ? null : host.selectTianshuTarget();
        if (boundTianshuTarget != null) tianshuSelectionRevision = 1;
        this.tianshuMode = host.getTianshuEncodingMode();
        registerClientAction("setTianshuMode", TianshuEncodingMode.class, this::setTianshuModeServer);
        registerClientAction("multiplyProcessing", Integer.class, this::multiplyProcessingServer);
        registerClientAction("armAdvancedEncoding",
                ProcessingPatternEncodingType.AdvancedConfig.class, this::armAdvancedEncodingServer);
        registerClientAction("armOverloadEncoding",
                ProcessingPatternEncodingType.OverloadConfig.class, this::armOverloadEncodingServer);
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
        registerClientAction("setClosedLoopOutputRole", ClosedLoopOutputEdit.class,
                this::setClosedLoopOutputRoleServer);
        registerClientAction("setClosedLoopMultipliers", ClosedLoopMultiplierEdit.class,
                this::setClosedLoopMultipliersServer);
        registerClientAction("autoFillClosedLoop", this::autoFillClosedLoopServer);
        registerClientAction("clearClosedLoopDraft", this::clearClosedLoopDraftServer);
        registerClientAction("uploadEncodedPattern", Integer.class, this::uploadEncodedPatternServer);
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
            if (closedLoopDraftDirty) rebuildClosedLoopDraft();
            closedLoopSeedMultiplier = closedLoopExecutionSeedMultiplier;
            refreshClosedLoopDraftSync();
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
        if (tianshuMode != mode) resetProcessingEncodingType();
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

    public void armAdvancedEncoding(ProcessingPatternEncodingType.AdvancedConfig config) {
        if (config == null) return;
        if (isClientSide()) {
            advancedEncodingConfig = config;
            sendClientAction("armAdvancedEncoding", config);
        } else {
            armAdvancedEncodingServer(config);
        }
    }

    private void armAdvancedEncodingServer(ProcessingPatternEncodingType.AdvancedConfig config) {
        if (!isServerSide() || config == null || config.directions() == null
                || config.directions().length > getProcessingInputSlots().length
                || !AdvancedAECompat.isLoaded()
                || tianshuMode != TianshuEncodingMode.PROCESSING) return;
        advancedEncodingConfig = config;
        overloadEncodingConfig = null;
        processingEncodingType = ProcessingPatternEncodingType.ADVANCED;
        broadcastChanges();
    }

    public void armOverloadEncoding(ProcessingPatternEncodingType.OverloadConfig config) {
        if (config == null) return;
        if (isClientSide()) {
            overloadEncodingConfig = config;
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
                || tianshuMode != TianshuEncodingMode.PROCESSING) return;
        overloadEncodingConfig = config;
        advancedEncodingConfig = null;
        processingEncodingType = ProcessingPatternEncodingType.OVERLOAD;
        broadcastChanges();
    }

    @Nullable
    public ProcessingPatternEncodingType.AdvancedConfig getAdvancedEncodingConfig() {
        return advancedEncodingConfig;
    }

    @Nullable
    public ProcessingPatternEncodingType.OverloadConfig getOverloadEncodingConfig() {
        return overloadEncodingConfig;
    }

    private void resetProcessingEncodingType() {
        processingEncodingType = ProcessingPatternEncodingType.NORMAL;
        advancedEncodingConfig = null;
        overloadEncodingConfig = null;
    }

    @Override
    public void clear() {
        resetProcessingEncodingType();
        super.clear();
    }

    public List<AppEngSlot> getClosedLoopMemberSlots() {
        return closedLoopMemberSlots;
    }

    public List<AppEngSlot> getClosedLoopOutputSlots() {
        return closedLoopOutputSlots;
    }

    public List<AppEngSlot> getClosedLoopExternalInputSlots() {
        return closedLoopExternalInputSlots;
    }

    public List<AppEngSlot> getClosedLoopSeedSlots() {
        return closedLoopSeedSlots;
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

    public void setClosedLoopOutputRole(int slot, int role) {
        if (isClientSide()) {
            sendClientAction("setClosedLoopOutputRole", new ClosedLoopOutputEdit(slot, role));
        } else {
            setClosedLoopOutputRoleServer(new ClosedLoopOutputEdit(slot, role));
        }
    }

    private void setClosedLoopOutputRoleServer(ClosedLoopOutputEdit edit) {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP
                || edit == null || edit.slot() < 0 || edit.slot() >= CLOSED_LOOP_OUTPUT_SLOTS
                || edit.role() < 0 || edit.role() > 2) return;
        if (closedLoopOutputInventory.getStackInSlot(edit.slot()).isEmpty()) return;
        if (edit.role() == 1) {
            for (int i = 0; i < closedLoopOutputRoles.length; i++) {
                if (closedLoopOutputRoles[i] == 1) closedLoopOutputRoles[i] = 0;
            }
            closedLoopOutputRoles[edit.slot()] = 1;
        } else {
            closedLoopOutputRoles[edit.slot()] = edit.role();
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

    /** Re-runs closed-loop discovery from the encoded source, or cycles candidates once discovered. */
    public void autoFillClosedLoop() {
        if (isClientSide()) sendClientAction("autoFillClosedLoop");
        else autoFillClosedLoopServer();
    }

    /** Clears the editable closed-loop draft while keeping the encoded source available for refilling. */
    public void clearClosedLoopDraft() {
        if (isClientSide()) sendClientAction("clearClosedLoopDraft");
        else clearClosedLoopDraftServer();
    }

    private void clearClosedLoopDraftServer() {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return;
        resetClosedLoopDraft();
        uploadState = 0;
        broadcastChanges();
    }

    private void autoFillClosedLoopServer() {
        if (!isServerSide() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return;
        if (!closedLoopCandidates.isEmpty()) {
            selectClosedLoopCandidateServer(1);
            return;
        }
        var source = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
        if (source.isEmpty() || source.getItem() instanceof ClosedLoopPatternItem) return;
        resetClosedLoopDraft();
        refreshClosedLoops(source);
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
            if (tianshuSelectionPending) return;
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
        var target = resolveOrBindTianshuForUpload();
        var payload = item.readPayload(stack, getPlayer().level()).orElse(null);
        var result = ClosedLoopPatternUploadService.upload(target, payload);
        finishUpload(result == ClosedLoopPatternRepository.PutResult.ADDED
                || result == ClosedLoopPatternRepository.PutResult.UPDATED);
    }

    /**
     * Binds the first available machine only when this menu has never had a target. A previously
     * captured target that disappeared is never replaced implicitly with a different machine.
     */
    @Nullable
    private com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity
            resolveOrBindTianshuForUpload() {
        var resolved = resolveBoundTianshu();
        if (resolved != null || boundTianshuTarget != null) return resolved;
        var available = tianshuHost.getAvailableTianshu();
        if (available.isEmpty()) return null;
        var selected = available.getFirst();
        boundTianshuTarget = TianshuTerminalTarget.from(selected);
        tianshuSelectionRevision++;
        return selected;
    }

    private void finishUpload(boolean success) {
        uploadState = success ? 1 : 3;
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

    private void uploadCraftingPatternServer(ServerPlayer player, ItemStack stack) {
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
        // Preserve the result when a successful provider upload empties the source slot.
        // A newly inserted/encoded pattern starts a fresh upload state.
        if (!source.isEmpty()) uploadState = 0;
        resetClosedLoopDraft();
        if (source.isEmpty()) return;
        if (source.getItem() instanceof ClosedLoopPatternItem closedLoopItem) {
            var payload = closedLoopItem.readPayload(source, getPlayer().level()).orElse(null);
            if (payload != null) {
                closedLoopExecutionSeedMultiplier = payload.executionSeedMultiplier();
                closedLoopSeedMultiplier = closedLoopExecutionSeedMultiplier;
                closedLoopStoredTaskMultiplier = payload.storedTaskMultiplier();
                closedLoopOriginalPatternId = payload.patternId();
                closedLoopOriginalPatternVersion = payload.version();
                fillClosedLoopDraft(payload, true);
                closedLoopDraftRepresentsEncoded = true;
            } else {
                closedLoopDraftStatus = ClosedLoopDraftStatus.MEMBER_UNDECODABLE;
                closedLoopEncodeState = 1;
            }
            return;
        }
        refreshClosedLoops(source);
    }

    private void refreshClosedLoops(ItemStack source) {
        var details = PatternDetailsHelper.decodePattern(source, getPlayer().level());
        if (details == null) {
            closedLoopDraftStatus = ClosedLoopDraftStatus.MEMBER_UNDECODABLE;
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
        fillClosedLoopDraft(closedLoopCandidates.get(index).payload(), true);
        closedLoopDraftRepresentsEncoded = false;
    }

    private void resetClosedLoopDraft() {
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopMemberInventory);
            clearInventory(closedLoopOutputInventory);
            clearInventory(closedLoopExternalInputInventory);
            clearInventory(closedLoopSeedInventory);
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
        closedLoopOriginalPatternId = null;
        closedLoopOriginalPatternVersion = 0L;
        closedLoopExternalInputCount = 0;
        closedLoopSeedInputCount = 0;
        closedLoopDraftDirty = false;
        closedLoopDraftRepresentsEncoded = false;
    }

    /** Atomically replaces the editable draft and candidate marks. */
    private void fillClosedLoopDraft(ClosedLoopPatternPayload payload, boolean markOutputs) {
        if (payload == null) return;
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopMemberInventory);
            clearInventory(closedLoopOutputInventory);
            clearInventory(closedLoopExternalInputInventory);
            clearInventory(closedLoopSeedInventory);
            java.util.Arrays.fill(closedLoopMemberCopies, 0L);
            java.util.Arrays.fill(closedLoopOutputRoles, 0);
            int memberCount = Math.min(CLOSED_LOOP_MEMBER_SLOTS, payload.memberPatterns().size());
            for (int i = 0; i < memberCount; i++) {
                var member = payload.memberPatterns().get(i);
                var stack = member.pattern().toItemStack(registryAccess());
                if (stack.isEmpty()) continue;
                closedLoopMemberInventory.setItemDirect(i, stack.copyWithCount(1));
                closedLoopMemberCopies[i] = member.copiesPerCycle();
            }
            int outputCount = Math.min(CLOSED_LOOP_OUTPUT_SLOTS, payload.netOutputs().size());
            for (int i = 0; i < outputCount; i++) {
                closedLoopOutputInventory.setItemDirect(
                        i, GenericStack.wrapInItemStack(payload.netOutputs().get(i)));
                if (markOutputs) closedLoopOutputRoles[i] = i == 0 ? 1 : 2;
            }
            closedLoopMainOutput = payload.netOutputs().isEmpty()
                    ? null : payload.netOutputs().getFirst().what();
            closedLoopDraftMembers = List.copyOf(payload.memberPatterns());
        } finally {
            closedLoopBulkUpdating = false;
        }
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
                        SourcePatternSnapshot.fromItemStack(stack, registryAccess()), copies));
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

        var priorRoles = new LinkedHashMap<AEKey, Integer>();
        AEKey priorPrimary = null;
        for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
            var stack = closedLoopOutputInventory.getStackInSlot(i);
            var generic = GenericStack.fromItemStack(stack);
            if (generic == null || generic.what() == null) continue;
            int role = closedLoopOutputRoles[i];
            priorRoles.put(generic.what(), role);
            if (role == 1) priorPrimary = generic.what();
        }
        var requestedKeys = new LinkedHashSet<AEKey>();
        if (priorPrimary != null) requestedKeys.add(priorPrimary);
        if (closedLoopMainOutput != null) requestedKeys.add(closedLoopMainOutput);
        collectDraftOutputKeys(draft, requestedKeys);

        ClosedLoopPatternAuthoringService.Result authored = null;
        ClosedLoopPatternAuthoringService.Result firstFailure = null;
        for (var key : requestedKeys) {
            var attempt = ClosedLoopPatternAuthoringService.createFromDraft(
                    draft, key, closedLoopExecutionSeedMultiplier,
                    closedLoopStoredTaskMultiplier, getPlayer().level());
            if (attempt.valid()) {
                authored = attempt;
                break;
            }
            if (firstFailure == null) firstFailure = attempt;
        }
        if (authored == null || !authored.valid()) {
            clearClosedLoopComputedResults();
            setClosedLoopInvalid(mapAuthoringStatus(firstFailure));
            return;
        }

        var candidatePayload = authored.payload();
        writeOutputCandidates(candidatePayload.netOutputs(), priorRoles);
        if (priorPrimary == null) {
            clearClosedLoopComputedResults();
            closedLoopPreparedPayload = null;
            closedLoopDraftStatus = ClosedLoopDraftStatus.MISSING_PRIMARY_OUTPUT;
            closedLoopEncodeState = 2;
            return;
        }
        var declared = new ArrayList<AEKey>();
        declared.add(priorPrimary);
        for (int i = 0; i < CLOSED_LOOP_OUTPUT_SLOTS; i++) {
            if (closedLoopOutputRoles[i] != 2) continue;
            var generic = GenericStack.fromItemStack(closedLoopOutputInventory.getStackInSlot(i));
            if (generic != null && generic.what() != null && !declared.contains(generic.what())) {
                declared.add(generic.what());
            }
        }
        var selected = ClosedLoopPatternAuthoringService.createFromDraft(
                draft, declared, closedLoopExecutionSeedMultiplier,
                closedLoopStoredTaskMultiplier, getPlayer().level());
        if (!selected.valid()) {
            clearClosedLoopComputedResults();
            setClosedLoopInvalid(mapAuthoringStatus(selected));
            return;
        }
        var payload = selected.payload();
        if (closedLoopOriginalPatternId != null) {
            long version = closedLoopOriginalPatternVersion == Long.MAX_VALUE
                    ? Long.MAX_VALUE : Math.max(1L, closedLoopOriginalPatternVersion + 1L);
            payload = new ClosedLoopPatternPayload(
                    closedLoopOriginalPatternId, version, payload.memberPatterns(), payload.seeds(),
                    payload.externalInputs(), payload.netOutputs(), payload.executionSeedMultiplier(),
                    payload.storedTaskMultiplier(), payload.enabled());
        }
        closedLoopPreparedPayload = payload;
        fillClosedLoopComputedResults(payload);
        closedLoopDraftStatus = closedLoopDraftRepresentsEncoded
                ? ClosedLoopDraftStatus.ENCODED : ClosedLoopDraftStatus.VALID;
        closedLoopEncodeState = 0;
    }

    private void collectDraftOutputKeys(
            List<ClosedLoopMemberPattern> draft, Set<AEKey> target) {
        for (var member : draft) {
            try {
                var details = PatternDetailsHelper.decodePattern(
                        member.pattern().toItemStack(registryAccess()), getPlayer().level());
                if (details == null) continue;
                for (var output : details.getOutputs()) {
                    if (output != null && output.what() != null) target.add(output.what());
                }
            } catch (RuntimeException ignored) {
                // The authoritative authoring call below reports undecodable members.
            }
            if (target.size() >= CLOSED_LOOP_RESULT_SLOTS) return;
        }
    }

    private void writeOutputCandidates(
            List<GenericStack> outputs, Map<AEKey, Integer> priorRoles) {
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopOutputInventory);
            java.util.Arrays.fill(closedLoopOutputRoles, 0);
            int count = Math.min(CLOSED_LOOP_OUTPUT_SLOTS, outputs.size());
            int primary = -1;
            for (int i = 0; i < count; i++) {
                var output = outputs.get(i);
                closedLoopOutputInventory.setItemDirect(i, GenericStack.wrapInItemStack(output));
                int role = priorRoles.getOrDefault(output.what(), 0);
                closedLoopOutputRoles[i] = role;
                if (role == 1 && primary < 0) primary = i;
            }
            for (int i = 0; i < count; i++) {
                if (i != primary && closedLoopOutputRoles[i] == 1) closedLoopOutputRoles[i] = 0;
            }
        } finally {
            closedLoopBulkUpdating = false;
        }
    }

    private void fillClosedLoopComputedResults(ClosedLoopPatternPayload payload) {
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopExternalInputInventory);
            clearInventory(closedLoopSeedInventory);
            closedLoopExternalInputCount = payload.externalInputs().size();
            closedLoopSeedInputCount = payload.seeds().size();
            for (int i = 0; i < Math.min(CLOSED_LOOP_RESULT_SLOTS, payload.externalInputs().size()); i++) {
                closedLoopExternalInputInventory.setItemDirect(
                        i, GenericStack.wrapInItemStack(payload.externalInputs().get(i)));
            }
            for (int i = 0; i < Math.min(CLOSED_LOOP_RESULT_SLOTS, payload.seeds().size()); i++) {
                closedLoopSeedInventory.setItemDirect(
                        i, GenericStack.wrapInItemStack(payload.seeds().get(i)));
            }
        } finally {
            closedLoopBulkUpdating = false;
        }
    }

    private void clearClosedLoopComputedResults() {
        closedLoopPreparedPayload = null;
        closedLoopExternalInputCount = 0;
        closedLoopSeedInputCount = 0;
        closedLoopBulkUpdating = true;
        try {
            clearInventory(closedLoopExternalInputInventory);
            clearInventory(closedLoopSeedInventory);
        } finally {
            closedLoopBulkUpdating = false;
        }
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

    private boolean isExecutionMemberReference(ItemStack stack) {
        return stack.getItem() instanceof ClosedLoopPatternItem item
                && item.readExecutionMember(stack) >= 0;
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
            if (isServerSide()) {
                applyOneShotProcessingConversion();
                var encoded = tianshuHost.getLogic().getEncodedPatternInv().getStackInSlot(0);
                if (TianshuPatternUploadRouting.isValidEncodingResult(encoded, getPlayer().level())) {
                    triggeredUploadAck++;
                }
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
        if (source.isEmpty() || tianshuMode != TianshuEncodingMode.CLOSED_LOOP) return ItemStack.EMPTY;
        return encodeSelectedClosedLoopCandidate();
    }

    /** Consumes the armed one-shot configuration and converts the freshly encoded pattern. */
    private void applyOneShotProcessingConversion() {
        if (tianshuMode != TianshuEncodingMode.PROCESSING
                || processingEncodingType == ProcessingPatternEncodingType.NORMAL) return;
        var type = processingEncodingType;
        var advancedConfig = advancedEncodingConfig;
        var overloadConfig = overloadEncodingConfig;
        resetProcessingEncodingType();
        var inventory = tianshuHost.getLogic().getEncodedPatternInv();
        var source = inventory.getStackInSlot(0);
        if (source.isEmpty()) return;
        var converted = switch (type) {
            case ADVANCED -> convertToAdvanced(source, advancedConfig);
            case OVERLOAD -> convertToOverload(source, overloadConfig);
            case NORMAL -> null;
        };
        if (converted != null && !converted.isEmpty()) {
            inventory.setItemDirect(0, converted);
        }
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
                    source, new Ae2PlainPatternResolver(getPlayer().level()), registryAccess())
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
                closedLoopPreparedPayload, registryAccess());
    }

    public record ClosedLoopMemberEdit(int slot, long copies) {
    }

    public record ClosedLoopMemberMove(int slot, int direction) {
    }

    public record ClosedLoopOutputEdit(int slot, int role) {
    }

    public record ClosedLoopMultiplierEdit(int execution, int stored) {
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
