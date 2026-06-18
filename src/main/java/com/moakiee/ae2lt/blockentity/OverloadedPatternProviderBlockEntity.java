package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AECableType;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;
import com.moakiee.ae2lt.logic.WirelessConnectionLists;
import com.moakiee.ae2lt.logic.WirelessConnectionRef;
import com.moakiee.ae2lt.logic.WirelessConnectionValidator;
import com.moakiee.ae2lt.logic.WirelessPatternContainerGroupSelector;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * BlockEntity for the Overloaded Pattern Provider.
 * <p>
 * Extends vanilla PatternProviderBlockEntity — behaves identically in NORMAL mode.
 * Three extra persisted / synced fields provide the skeleton for future wireless mode.
 * <p>
 * PUSH_DIRECTION (block orientation) is always kept and never repurposed:
 * in NORMAL mode it drives adjacent-machine interaction (vanilla semantics);
 * in WIRELESS mode it is purely visual / grid-connectivity and does NOT affect
 * wireless dispatch or auto-return — those use wireless connector records instead.
 */
public class OverloadedPatternProviderBlockEntity extends PatternProviderBlockEntity
        implements FrequencyBindingHost {

    /** Pattern slots displayed per GUI page. */
    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_WIRELESS_CONNECTIONS = 1024;

    // ── Idle power (recomputed on mode/connection changes) ───────────────
    // Mirrors the overloaded interface: wireless dispatch is far more
    // expensive than vanilla adjacent push, so wireless mode and FAST speed
    // both add cost on top of the base upkeep.
    private static final double IDLE_BASE = 5.0;
    private static final double IDLE_WIRELESS_BONUS = 5.0;
    private static final double IDLE_PER_CONNECTION = 1.0;
    private static final double IDLE_FAST_MULTIPLIER = 1.5;

    // -- Custom fields --

    /** Operating mode: NORMAL (adjacent) or WIRELESS (remote). */
    public enum ProviderMode { NORMAL, WIRELESS }

    /** Return mode: OFF (no auto-return), AUTO (active extraction), EJECT (virtual output hatch). */
    public enum ReturnMode { OFF, AUTO, EJECT }

    /** Wireless dispatch strategy. */
    public enum WirelessDispatchMode { SINGLE_TARGET, EVEN_DISTRIBUTION }

    /** Wireless speed mode: NORMAL = standard cooldown, FAST = probe-based early detection. */
    public enum WirelessSpeedMode { NORMAL, FAST }

    private ProviderMode providerMode = ProviderMode.NORMAL;
    private ReturnMode returnMode = ReturnMode.OFF;
    private WirelessDispatchMode wirelessDispatchMode = WirelessDispatchMode.EVEN_DISTRIBUTION;
    private WirelessSpeedMode wirelessSpeedMode = WirelessSpeedMode.NORMAL;
    private boolean filteredImport = false;

    /** Active wireless connection records. */
    private final List<WirelessConnection> connections = new ArrayList<>();
    private int invalidConnectionScanCursor;
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);

    // -- Wireless connection record --

    /**
     * A single wireless connection to a remote machine face.
     * Identity is determined by (dimension, pos) — only one connection per machine.
     */
    public record WirelessConnection(
            ResourceKey<Level> dimension,
            BlockPos pos,
            Direction boundFace
    ) implements WirelessConnectionRef {
        private static final String TAG_DIM = "Dim";
        private static final String TAG_POS = "Pos";
        private static final String TAG_FACE = "Face";

        public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString(TAG_DIM, dimension.location().toString());
            tag.putLong(TAG_POS, pos.asLong());
            tag.putInt(TAG_FACE, boundFace.get3DDataValue());
            return tag;
        }

        public static WirelessConnection fromTag(CompoundTag tag) {
            var dim = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString(TAG_DIM)));
            var pos = BlockPos.of(tag.getLong(TAG_POS));
            var face = Direction.from3DDataValue(tag.getInt(TAG_FACE));
            return new WirelessConnection(dim, pos, face);
        }
    }

    // -- Constructor --

    public OverloadedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OVERLOADED_PATTERN_PROVIDER.get(), pos, blockState);
    }

    protected OverloadedPatternProviderBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                                    BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OverloadedPatternProviderBlockEntity be) {
        if (!level.isClientSide()) {
            be.frequencyBinding.serverTick();
            if (level instanceof ServerLevel serverLevel) {
                be.tickWirelessConnectionCleanup(serverLevel);
            }
        }
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
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        frequencyBinding.onMainNodeStateChanged(reason);
    }

    public int getTotalPatternCapacity() {
        return SLOTS_PER_PAGE;
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new OverloadedPatternProviderLogic(this.getMainNode(), this, getTotalPatternCapacity());
    }

    @Override
    public void onReady() {
        var logic = getOverloadedLogic();
        if (logic != null) {
            logic.onBlockEntityReady();
        }
        super.onReady();
        frequencyBinding.onReady();
        recomputeIdlePower();
    }

    void recomputeIdlePower() {
        double idle = IDLE_BASE;
        if (providerMode == ProviderMode.WIRELESS) {
            idle += IDLE_WIRELESS_BONUS;
            idle += connections.size() * IDLE_PER_CONNECTION;
        }
        if (wirelessSpeedMode == WirelessSpeedMode.FAST) {
            idle *= IDLE_FAST_MULTIPLIER;
        }
        getMainNode().setIdlePowerUsage(idle);
    }

    @Nullable
    private OverloadedPatternProviderLogic getOverloadedLogic() {
        var logic = getLogic();
        return logic instanceof OverloadedPatternProviderLogic overloadedLogic ? overloadedLogic : null;
    }

    private void notifyLogicStateChanged() {
        var logic = getOverloadedLogic();
        if (logic != null) {
            logic.onHostStateChanged();
        }
    }

    public void onNeighborChanged() {
        var logic = getOverloadedLogic();
        if (logic != null) {
            logic.onNeighborChanged();
        }
    }

    @Override
    public void saveChanges() {
        super.saveChanges();
        var level = getLevel();
        if (level != null && !level.isClientSide) {
            var logic = getOverloadedLogic();
            if (logic != null) {
                logic.onPersistentStateChanged();
            }
        }
    }

    /**
     * In WIRELESS mode return an empty set so the vanilla adjacent-block dispatch
     * path (in PatternProviderLogic) finds no targets.
     */
    @Override
    public EnumSet<Direction> getTargets() {
        if (providerMode == ProviderMode.WIRELESS) {
            return EnumSet.noneOf(Direction.class);
        }
        return super.getTargets();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    // -- Getters / Setters --

    public ProviderMode getProviderMode() {
        return providerMode;
    }

    public void setProviderMode(ProviderMode providerMode) {
        if (this.providerMode == providerMode) {
            return;
        }
        this.providerMode = providerMode;
        recomputeIdlePower();
        notifyLogicStateChanged();
        saveChanges();
        markForClientUpdate();
    }

    public ReturnMode getReturnMode() {
        return returnMode;
    }

    public void setReturnMode(ReturnMode mode) {
        if (this.returnMode == mode) {
            return;
        }
        this.returnMode = mode;
        notifyLogicStateChanged();
        saveChanges();
        markForClientUpdate();
    }

    public boolean isAutoReturn() {
        return returnMode != ReturnMode.OFF;
    }

    public WirelessDispatchMode getWirelessDispatchMode() {
        return wirelessDispatchMode;
    }

    public void setWirelessDispatchMode(WirelessDispatchMode wirelessDispatchMode) {
        if (this.wirelessDispatchMode == wirelessDispatchMode) {
            return;
        }
        this.wirelessDispatchMode = wirelessDispatchMode;
        notifyLogicStateChanged();
        saveChanges();
        markForClientUpdate();
    }

    public WirelessSpeedMode getWirelessSpeedMode() {
        return wirelessSpeedMode;
    }

    public void setWirelessSpeedMode(WirelessSpeedMode wirelessSpeedMode) {
        if (this.wirelessSpeedMode == wirelessSpeedMode) {
            return;
        }
        this.wirelessSpeedMode = wirelessSpeedMode;
        recomputeIdlePower();
        saveChanges();
        markForClientUpdate();
    }

    public boolean isFilteredImport() {
        return filteredImport;
    }

    public void setFilteredImport(boolean filteredImport) {
        if (this.filteredImport == filteredImport) {
            return;
        }
        this.filteredImport = filteredImport;
        saveChanges();
        markForClientUpdate();
    }

    /**
     * Exposes the pattern slots as a standard item inventory for external automation
     * and addon compatibility.
     */
    public InternalInventory getExposedPatternInventory() {
        return getLogic().getPatternInv();
    }

    // -- Connection management --

    /**
     * Add or update a wireless connection. If a connection to the same (dimension, pos)
     * already exists, the bound face is updated; otherwise a new record is added.
     */
    public boolean addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction boundFace) {
        if (!isLocalDimension(dimension)) {
            return false;
        }
        int index = WirelessConnectionLists.indexOf(connections, dimension, pos);
        if (index >= 0) {
            var updated = new WirelessConnection(dimension, pos, boundFace);
            if (connections.get(index).equals(updated)) {
                return true;
            }
            connections.set(index, updated);
            invalidConnectionScanCursor = 0;
            notifyLogicStateChanged();
            saveChanges();
            markForClientUpdate();
            return true;
        }
        if (connections.size() >= MAX_WIRELESS_CONNECTIONS) {
            return false;
        }
        connections.add(new WirelessConnection(dimension, pos, boundFace));
        invalidConnectionScanCursor = 0;
        recomputeIdlePower();
        notifyLogicStateChanged();
        saveChanges();
        markForClientUpdate();
        return true;
    }

    /**
     * Remove the connection to the specified machine, if present.
     *
     * @return true if a connection was removed
     */
    public boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos) {
        int index = WirelessConnectionLists.indexOf(connections, dimension, pos);
        if (index < 0) {
            return false;
        }
        connections.remove(index);
        invalidConnectionScanCursor = 0;
        recomputeIdlePower();
        notifyLogicStateChanged();
        saveChanges();
        markForClientUpdate();
        return true;
    }

    /** Returns an unmodifiable view of the current connections. */
    public List<WirelessConnection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    /**
     * Remove connections whose target block or BlockEntity no longer exists.
     * Only checks loaded chunks — unloaded targets are kept.
     *
     * @return number of connections removed
     */
    public int clearInvalidConnections() {
        return pruneInvalidConnections(Integer.MAX_VALUE);
    }

    public int pruneInvalidConnections(int maxChecks) {
        var hostLevel = getLevel();
        if (!(hostLevel instanceof ServerLevel serverLevel) || maxChecks <= 0 || connections.isEmpty()) {
            return 0;
        }

        var result = WirelessConnectionLists.pruneInvalid(
                connections, invalidConnectionScanCursor, maxChecks,
                serverLevel, worldPosition, this::canRemoveInvalidConnection);
        invalidConnectionScanCursor = result.nextCursor();
        if (result.removed() > 0) {
            recomputeIdlePower();
            notifyLogicStateChanged();
            saveChanges();
            markForClientUpdate();
        }
        return result.removed();
    }

    private void tickWirelessConnectionCleanup(ServerLevel level) {
        if (connections.isEmpty()
                || !WirelessConnectionValidator.shouldRunPeriodicPrune(level, worldPosition)) {
            return;
        }
        pruneInvalidConnections(WirelessConnectionValidator.PERIODIC_PRUNE_MAX_CHECKS);
    }

    private boolean canRemoveInvalidConnection(WirelessConnection conn) {
        var logic = getOverloadedLogic();
        return logic == null || logic.prepareInvalidConnectionRemoval(conn);
    }

    private boolean isLocalDimension(ResourceKey<Level> dimension) {
        return WirelessConnectionLists.isLocalDimension(level, dimension);
    }

    // -- Client sync (writeToStream / readFromStream) --

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(providerMode.ordinal());
        data.writeByte(returnMode.ordinal());
        data.writeByte(wirelessDispatchMode.ordinal());
        data.writeByte(wirelessSpeedMode.ordinal());
        data.writeBoolean(filteredImport);
        data.writeVarInt(connections.size());
        for (var conn : connections) {
            data.writeResourceLocation(conn.dimension().location());
            data.writeBlockPos(conn.pos());
            data.writeByte(conn.boundFace().get3DDataValue());
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        var modeOrd = data.readByte();
        var newMode = modeOrd >= 0 && modeOrd < ProviderMode.values().length
                ? ProviderMode.values()[modeOrd] : ProviderMode.NORMAL;
        var rmOrd = data.readByte();
        var newReturnMode = rmOrd >= 0 && rmOrd < ReturnMode.values().length
                ? ReturnMode.values()[rmOrd] : ReturnMode.OFF;
        var dispatchOrd = data.readByte();
        var newDispatchMode = dispatchOrd >= 0 && dispatchOrd < WirelessDispatchMode.values().length
                ? WirelessDispatchMode.values()[dispatchOrd] : WirelessDispatchMode.EVEN_DISTRIBUTION;
        var speedOrd = data.readByte();
        var newSpeedMode = speedOrd >= 0 && speedOrd < WirelessSpeedMode.values().length
                ? WirelessSpeedMode.values()[speedOrd] : WirelessSpeedMode.NORMAL;
        var newFilteredImport = data.readBoolean();
        int count = data.readVarInt();
        var newConns = new ArrayList<WirelessConnection>(Math.min(count, MAX_WIRELESS_CONNECTIONS));
        for (int i = 0; i < count; i++) {
            var dim = ResourceKey.create(Registries.DIMENSION, data.readResourceLocation());
            var pos = data.readBlockPos();
            var face = Direction.from3DDataValue(data.readByte());
            WirelessConnectionLists.addOrReplace(
                    newConns, new WirelessConnection(dim, pos, face), MAX_WIRELESS_CONNECTIONS);
        }
        if (newMode != providerMode || newReturnMode != returnMode
                || newDispatchMode != wirelessDispatchMode
                || newSpeedMode != wirelessSpeedMode
                || newFilteredImport != filteredImport
                || !newConns.equals(connections)) {
            providerMode = newMode;
            returnMode = newReturnMode;
            wirelessDispatchMode = newDispatchMode;
            wirelessSpeedMode = newSpeedMode;
            filteredImport = newFilteredImport;
            connections.clear();
            connections.addAll(newConns);
            invalidConnectionScanCursor = 0;
            recomputeIdlePower();
            notifyLogicStateChanged();
            changed = true;
        }
        return changed;
    }

    // -- NBT persistence --

    private static final String TAG_PROVIDER_MODE = "OverloadMode";
    private static final String TAG_AUTO_RETURN = "AutoReturn";
    private static final String TAG_RETURN_MODE = "ReturnMode";
    private static final String TAG_WIRELESS_DISPATCH_MODE = "WirelessDispatchMode";
    private static final String TAG_WIRELESS_SPEED_MODE = "WirelessSpeedMode";
    private static final String TAG_FILTERED_IMPORT = "FilteredImport";
    private static final String TAG_CONNECTIONS = "WirelessConnections";

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putString(TAG_PROVIDER_MODE, providerMode.name());
        data.putString(TAG_RETURN_MODE, returnMode.name());
        data.putString(TAG_WIRELESS_DISPATCH_MODE, wirelessDispatchMode.name());
        data.putString(TAG_WIRELESS_SPEED_MODE, wirelessSpeedMode.name());
        data.putBoolean(TAG_FILTERED_IMPORT, filteredImport);

        data.put(TAG_CONNECTIONS, WirelessConnectionLists.writeTagList(connections));
        frequencyBinding.save(data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        if (data.contains(TAG_PROVIDER_MODE)) {
            try {
                providerMode = ProviderMode.valueOf(data.getString(TAG_PROVIDER_MODE));
            } catch (IllegalArgumentException ignored) {
                providerMode = ProviderMode.NORMAL;
            }
        }
        if (data.contains(TAG_RETURN_MODE)) {
            try {
                returnMode = ReturnMode.valueOf(data.getString(TAG_RETURN_MODE));
            } catch (IllegalArgumentException ignored) {
                returnMode = ReturnMode.OFF;
            }
        } else if (data.contains(TAG_AUTO_RETURN)) {
            returnMode = data.getBoolean(TAG_AUTO_RETURN) ? ReturnMode.AUTO : ReturnMode.OFF;
        }
        if (data.contains(TAG_WIRELESS_DISPATCH_MODE)) {
            try {
                wirelessDispatchMode = WirelessDispatchMode.valueOf(data.getString(TAG_WIRELESS_DISPATCH_MODE));
            } catch (IllegalArgumentException ignored) {
                wirelessDispatchMode = WirelessDispatchMode.EVEN_DISTRIBUTION;
            }
        }
        if (data.contains(TAG_WIRELESS_SPEED_MODE)) {
            try {
                wirelessSpeedMode = WirelessSpeedMode.valueOf(data.getString(TAG_WIRELESS_SPEED_MODE));
            } catch (IllegalArgumentException ignored) {
                wirelessSpeedMode = WirelessSpeedMode.NORMAL;
            }
        }
        filteredImport = data.getBoolean(TAG_FILTERED_IMPORT);
        WirelessConnectionLists.readTagList(
                data, TAG_CONNECTIONS, connections, MAX_WIRELESS_CONNECTIONS, WirelessConnection::fromTag);
        invalidConnectionScanCursor = 0;
        frequencyBinding.load(data);
        recomputeIdlePower();
        notifyLogicStateChanged();
    }

    // -- Memory-card copy/paste (machine-specific fields only) --

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.exportMemoryCardSettings(mode, builder, tag -> {
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_PROVIDER_MODE, providerMode);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_RETURN_MODE, returnMode);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_WIRELESS_DISPATCH_MODE, wirelessDispatchMode);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.writeEnum(tag, TAG_WIRELESS_SPEED_MODE, wirelessSpeedMode);
            tag.putBoolean(TAG_FILTERED_IMPORT, filteredImport);
            FrequencyBindingHelper.writeMemoryFrequency(tag, getFrequencyId());
        });
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @Nullable Player player) {
        super.importSettings(mode, input, player);
        com.moakiee.ae2lt.logic.MemoryCardConfigSupport.importMemoryCardSettings(mode, input, tag -> {
            this.providerMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_PROVIDER_MODE, ProviderMode.class, this.providerMode);
            this.returnMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_RETURN_MODE, ReturnMode.class, this.returnMode);
            this.wirelessDispatchMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_WIRELESS_DISPATCH_MODE, WirelessDispatchMode.class, this.wirelessDispatchMode);
            this.wirelessSpeedMode = com.moakiee.ae2lt.logic.MemoryCardConfigSupport.readEnum(
                    tag, TAG_WIRELESS_SPEED_MODE, WirelessSpeedMode.class, this.wirelessSpeedMode);
            com.moakiee.ae2lt.logic.MemoryCardConfigSupport.ifBoolean(tag, TAG_FILTERED_IMPORT,
                    v -> this.filteredImport = v);
            FrequencyBindingHelper.importMemoryFrequency(tag, this::setFrequency);
            recomputeIdlePower();
            notifyLogicStateChanged();
            saveChanges();
            markForUpdate();
        });
    }

    // -- Cleanup --

    private boolean unloadingChunk = false;

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unloadingChunk = true;
        var logic = getOverloadedLogic();
        if (logic != null) {
            logic.flushWirelessEnergyBuffer();
        }
    }

    @Override
    public void setRemoved() {
        frequencyBinding.setRemoved();
        if (!unloadingChunk && level instanceof net.minecraft.server.level.ServerLevel sl) {
            var removed = com.moakiee.ae2lt.logic.EjectModeRegistry.unregisterAll(this, true);
            var server = sl.getServer();
            for (var dp : removed) {
                var targetLevel = server.getLevel(dp.dimension());
                if (targetLevel != null) {
                    targetLevel.invalidateCapabilities(dp.pos());
                }
            }
        }
        var logic = getOverloadedLogic();
        if (logic != null) {
            logic.flushWirelessEnergyBuffer();
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        frequencyBinding.clearRemoved();
    }

    // -- Menu binding --

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        if (level instanceof ServerLevel) {
            clearInvalidConnections();
        }
        MenuOpener.open(OverloadedPatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(OverloadedPatternProviderMenu.TYPE, player, subMenu.getLocator());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModBlocks.OVERLOADED_PATTERN_PROVIDER.get());
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (providerMode != ProviderMode.WIRELESS || hasCustomTerminalName()) {
            return super.getTerminalGroup();
        }

        var wirelessGroup = findMostFrequentWirelessTerminalGroup();
        return wirelessGroup != null ? wirelessGroup : super.getTerminalGroup();
    }

    private boolean hasCustomTerminalName() {
        return this instanceof Nameable nameable && nameable.hasCustomName();
    }

    @Nullable
    private PatternContainerGroup findMostFrequentWirelessTerminalGroup() {
        if (!(level instanceof ServerLevel hostLevel) || connections.isEmpty()) {
            return null;
        }

        var groups = new ArrayList<PatternContainerGroup>(connections.size());
        for (var connection : connections) {
            var targetLevel = resolveTerminalGroupTargetLevel(hostLevel, connection);
            if (targetLevel == null) {
                continue;
            }
            var group = PatternContainerGroup.fromMachine(
                    targetLevel, connection.pos(), connection.boundFace());
            if (group != null) {
                groups.add(group);
            }
        }
        return WirelessPatternContainerGroupSelector.selectMostFrequent(groups).orElse(null);
    }

    @Nullable
    private ServerLevel resolveTerminalGroupTargetLevel(ServerLevel hostLevel, WirelessConnection connection) {
        if (WirelessConnectionValidator.validate(hostLevel, worldPosition, connection)
                != WirelessConnectionValidator.Status.VALID) {
            return null;
        }
        var targetLevel = hostLevel.getServer().getLevel(connection.dimension());
        return targetLevel != null && targetLevel.isLoaded(connection.pos()) ? targetLevel : null;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        // Mirror the overloaded controller fix: the grid node's visual representation
        // defaults to this representative item, and the controller network-status UI
        // groups machines by that representation.
        return ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().asItem();
    }
}
