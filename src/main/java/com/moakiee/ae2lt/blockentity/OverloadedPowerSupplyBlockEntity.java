package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.block.OverloadedPowerSupplyBlock;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.logic.OverloadedPowerSupplyLogic;
import com.moakiee.ae2lt.logic.WirelessConnectionLists;
import com.moakiee.ae2lt.logic.WirelessConnectionRef;
import com.moakiee.ae2lt.logic.WirelessConnectionValidator;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.menu.OverloadedPowerSupplyMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

public class OverloadedPowerSupplyBlockEntity extends AENetworkedBlockEntity
        implements InternalInventoryHost, FrequencyBindingHost, OverloadedGridNodeOwner {

    public static final int MAX_WIRELESS_CONNECTIONS = 64;

    private static final String TAG_MODE = "Mode";
    private static final String TAG_CONNECTIONS = "WirelessConnections";
    private static final String TAG_CELL_INV = "CellInv";

    public enum PowerMode {
        NORMAL,
        OVERLOAD;

        public PowerMode next() {
            return this == NORMAL ? OVERLOAD : NORMAL;
        }
    }

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
            var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(TAG_DIM)));
            var pos = BlockPos.of(tag.getLong(TAG_POS));
            int rawFace = tag.getInt(TAG_FACE);
            var face = (rawFace >= 0 && rawFace < Direction.values().length)
                    ? Direction.from3DDataValue(rawFace) : Direction.DOWN;
            return new WirelessConnection(dim, pos, face);
        }
    }

    public record ConnectionEditResult(
            List<BlockPos> disconnected,
            List<BlockPos> updated,
            List<BlockPos> connected,
            int skippedDueToLimit
    ) {
        public boolean hasChanges() {
            return !disconnected.isEmpty() || !updated.isEmpty() || !connected.isEmpty();
        }
    }

    private final AppEngInternalInventory cellInv = new AppEngInternalInventory(this, 1, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AppFluxBridge.isFluxCell(stack);
        }
    };

    private final List<WirelessConnection> connections = new ArrayList<>();
    private final List<WirelessConnection> readOnlyConnections = Collections.unmodifiableList(connections);
    private int invalidConnectionScanCursor;
    /**
     * Monotonic version stamp bumped whenever {@link #connections} is mutated.
     * Lets the supply logic skip an O(n) per-tick {@code List.equals} when the
     * connection list is unchanged — it just compares an int.
     */
    private int connectionVersion;
    private final OverloadedPowerSupplyLogic logic;
    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);

    private PowerMode mode = PowerMode.NORMAL;
    @Nullable
    private StorageCell cachedCellView;
    private boolean cellViewDirty = true;
    private boolean cellCapacityDirty = true;
    private long cachedCellCapacity;

    public OverloadedPowerSupplyBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.OVERLOADED_POWER_SUPPLY.get(), pos, blockState);
        this.logic = new OverloadedPowerSupplyLogic(this);
        getMainNode()
                .setIdlePowerUsage(0.0D)
                .addService(appeng.api.networking.ticking.IGridTickable.class, logic);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OverloadedPowerSupplyBlockEntity be) {
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

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("overloaded_power_supply")
                .setVisualRepresentation(ModBlocks.OVERLOADED_POWER_SUPPLY.get());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.OVERLOADED_POWER_SUPPLY.get().asItem();
    }

    public AppEngInternalInventory getCellInventory() {
        return cellInv;
    }

    public ItemStack getInstalledCell() {
        return cellInv.getStackInSlot(0);
    }

    public long getBufferCapacity() {
        if (cellCapacityDirty) {
            cachedCellCapacity = AppFluxBridge.getFluxCellCapacity(getInstalledCell());
            cellCapacityDirty = false;
        }
        return cachedCellCapacity;
    }

    /**
     * Resolve the installed Flux Cell's {@link MEStorage} view, properly
     * bound to a {@link ISaveProvider} so mutations are persisted back to
     * the ItemStack and this BE is marked dirty.
     *
     * <p>The result is cached to avoid re-creating the cell inventory view
     * on every energy operation (dozens of times per tick in overload mode).
     * The cache is invalidated when the cell slot changes.
     *
     * @return the cell storage, or {@code null} when no valid Flux Cell is
     *         installed.
     */
    @Nullable
    public MEStorage getInstalledCellStorage() {
        if (!cellViewDirty && cachedCellView != null) {
            return cachedCellView;
        }

        cachedCellView = null;
        cellViewDirty = false;
        ItemStack stack = getInstalledCell();
        if (stack.isEmpty() || !AppFluxBridge.isFluxCell(stack)) {
            return null;
        }

        cachedCellView = StorageCells.getCellInventory(stack, this::onCellInventoryChanged);
        return cachedCellView;
    }

    /**
     * AE2 ME Chest pattern, deferred-persist variant: every cell mutation
     * (extract/insert) routes through {@link FluxCellInventory#saveChanges()},
     * which fires this callback. We do NOT call {@code persist()} here —
     * doing so on every mutation would write the ItemStack data component
     * 64+ times per OVERLOAD tick, AND would briefly flash the post-extract
     * value (often 0) into the ItemStack between distribution and refill.
     *
     * <p>Instead, the host logic invokes {@link OverloadedPowerSupplyLogic
     * #endTick(net.minecraft.server.level.ServerLevel)} once per tick, which
     * batches all in-tick mutations into a single {@code FluxCellInventory.persist()}
     * call. The ItemStack therefore only ever sees the post-tick equilibrium
     * value, exactly matching the in-memory {@code storedEnergy}.
     *
     * <p>The {@link #saveChanges()} call here only marks the chunk dirty so
     * Minecraft's autosave will eventually serialise this BE to disk; it
     * does not touch the cell's ItemStack.
     */
    private void onCellInventoryChanged() {
        saveChanges();
    }

    /**
     * Externally-invoked persist (lifecycle hooks: cell removed, BE saved,
     * chunk unloaded). Forces the latest in-memory cell state to be flushed
     * into the ItemStack data component before the stack leaves the BE.
     */
    public void persistCellStorage() {
        AppFluxBridge.persistCellStorage(cachedCellView);
        saveChanges();
    }

    public PowerMode getMode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode.next();
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
        // Always pair OVERLOADED with the current mode; POWERED stays where
        // it is (the next tick will refresh it). Clearing it here would
        // produce a one-tick "off" flicker every time the player toggles.
        BlockState state = getBlockState();
        boolean currentlyPowered = state.hasProperty(OverloadedPowerSupplyBlock.POWERED)
                && state.getValue(OverloadedPowerSupplyBlock.POWERED);
        updateVisualState(currentlyPowered, mode == PowerMode.OVERLOAD);
    }

    /**
     * Push the (powered, overloaded) pair onto the world block state so the
     * three Blockbench models swap in line with what the supply is actually
     * doing. {@code overloaded} is always the player-selected mode — it does
     * not require an active transfer to be true — but the blockstate JSON
     * collapses {@code powered=false,overloaded=true} back to the "off"
     * model so we never show the overloaded crystal while idle.
     */
    public void updateVisualState(boolean powered, boolean overloaded) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(OverloadedPowerSupplyBlock.POWERED)
                || !state.hasProperty(OverloadedPowerSupplyBlock.OVERLOADED)) {
            return;
        }
        boolean curPowered = state.getValue(OverloadedPowerSupplyBlock.POWERED);
        boolean curOverloaded = state.getValue(OverloadedPowerSupplyBlock.OVERLOADED);
        if (curPowered == powered && curOverloaded == overloaded) {
            return;
        }
        level.setBlock(worldPosition,
                state.setValue(OverloadedPowerSupplyBlock.POWERED, powered)
                        .setValue(OverloadedPowerSupplyBlock.OVERLOADED, overloaded),
                Block.UPDATE_ALL);
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
        // Saved BlockState may already be POWERED=true from before unload, but
        // the supply is not actually transferring until the first server tick
        // proves it can. Reset POWERED to false on (re)load so the visuals
        // always start in the off state and let the logic light it up again.
        updateVisualState(false, mode == PowerMode.OVERLOAD);
    }

    public OverloadedPowerSupplyLogic getSupplyLogic() {
        return logic;
    }

    public boolean addOrUpdateConnection(ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        if (!isLocalDimension(dimension)) {
            return false;
        }
        int index = WirelessConnectionLists.indexOf(connections, dimension, pos);
        if (index >= 0) {
            var updated = new WirelessConnection(dimension, pos.immutable(), face);
            if (connections.get(index).equals(updated)) {
                return false;
            }
            connections.set(index, updated);
            invalidConnectionScanCursor = 0;
            notifyConnectionsChanged();
            return true;
        }

        if (connections.size() >= MAX_WIRELESS_CONNECTIONS) {
            return false;
        }
        connections.add(new WirelessConnection(dimension, pos.immutable(), face));
        invalidConnectionScanCursor = 0;
        notifyConnectionsChanged();
        return true;
    }

    public ConnectionEditResult editConnections(
            ResourceKey<Level> dimension,
            Collection<BlockPos> positions,
            Direction face) {
        if (positions.isEmpty()) {
            return new ConnectionEditResult(List.of(), List.of(), List.of(), 0);
        }
        if (!isLocalDimension(dimension)) {
            return new ConnectionEditResult(List.of(), List.of(), List.of(), 0);
        }

        var disconnected = new ArrayList<BlockPos>();
        var updated = new ArrayList<BlockPos>();
        var connected = new ArrayList<BlockPos>();
        int skippedDueToLimit = 0;
        boolean changed = false;

        for (var rawPos : positions) {
            var targetPos = rawPos.immutable();
            int index = WirelessConnectionLists.indexOf(connections, dimension, targetPos);
            if (index >= 0) {
                var existing = connections.get(index);
                if (existing.boundFace() == face) {
                    connections.remove(index);
                    disconnected.add(targetPos);
                    invalidConnectionScanCursor = 0;
                    changed = true;
                } else {
                    connections.set(index, new WirelessConnection(dimension, targetPos, face));
                    updated.add(targetPos);
                    invalidConnectionScanCursor = 0;
                    changed = true;
                }
                continue;
            }

            if (connections.size() >= MAX_WIRELESS_CONNECTIONS) {
                skippedDueToLimit++;
                continue;
            }

            connections.add(new WirelessConnection(dimension, targetPos, face));
            connected.add(targetPos);
            invalidConnectionScanCursor = 0;
            changed = true;
        }

        if (changed) {
            notifyConnectionsChanged();
        }

        return new ConnectionEditResult(
                List.copyOf(disconnected),
                List.copyOf(updated),
                List.copyOf(connected),
                skippedDueToLimit);
    }

    public boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos) {
        int index = WirelessConnectionLists.indexOf(connections, dimension, pos);
        if (index >= 0) {
            connections.remove(index);
            invalidConnectionScanCursor = 0;
            notifyConnectionsChanged();
            return true;
        }
        return false;
    }

    public boolean removeConnections(Collection<WirelessConnection> removedConnections) {
        if (removedConnections.isEmpty()) {
            return false;
        }

        boolean removed = connections.removeIf(removedConnections::contains);
        if (removed) {
            invalidConnectionScanCursor = 0;
            notifyConnectionsChanged();
        }
        return removed;
    }

    public List<WirelessConnection> getConnections() {
        return readOnlyConnections;
    }

    /**
     * Monotonic version stamp; the value changes iff the wireless connection
     * list has been mutated since the last read. Called once per server tick
     * by {@link OverloadedPowerSupplyLogic#getValidTargets} to short-circuit
     * the per-tick rebuild path.
     */
    public int getConnectionVersion() {
        return connectionVersion;
    }

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
                serverLevel, worldPosition);
        invalidConnectionScanCursor = result.nextCursor();
        if (result.removed() > 0) {
            notifyConnectionsChanged();
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

    private void notifyConnectionsChanged() {
        connectionVersion++;
        saveChanges();
        markForClientUpdate();
        logic.onStateChanged();
    }

    private boolean isLocalDimension(ResourceKey<Level> dimension) {
        return WirelessConnectionLists.isLocalDimension(level, dimension);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == cellInv) {
            // The cell slot just changed (player took out / inserted / swapped).
            // Flush any FE BufferedMEStorage is still holding (transient buffer
            // AND staged cell cache) BEFORE we drop the cached view, so the
            // ItemStack the player just removed is fully up-to-date and we
            // never carry stale FE forward to whatever cell appears next.
            // {@code flushBufferToNetwork} ends an active overload batch which
            // already persists the cell; the explicit persist here covers the
            // NORMAL-mode path where no batch is active but cached delta might
            // still be queued in the FluxCellInventory.
            logic.flushBufferToNetwork();
            AppFluxBridge.persistCellStorage(cachedCellView);
            cellViewDirty = true;
            cellCapacityDirty = true;
            cachedCellView = null;
            logic.onStateChanged();
        }
    }

    @Override
    public boolean isClientSide() {
        return level != null && level.isClientSide();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        logic.flushBufferToNetwork();
        AppFluxBridge.persistCellStorage(cachedCellView);
        super.addAdditionalDrops(level, pos, drops);
        ItemStack cell = cellInv.getStackInSlot(0);
        if (!cell.isEmpty()) {
            drops.add(cell.copy());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        cellInv.clear();
        connections.clear();
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap.Builder builder,
                               @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        FrequencyBindingHelper.exportMemorySettings(mode, builder, getFrequencyId());
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.core.component.DataComponentMap input,
                               @Nullable Player player) {
        super.importSettings(mode, input, player);
        FrequencyBindingHelper.importMemorySettings(mode, input, this::setFrequency);
    }

    @Override
    public void setRemoved() {
        frequencyBinding.setRemoved();
        logic.flushBufferToNetwork();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        frequencyBinding.clearRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        logic.flushBufferToNetwork();
        super.onChunkUnloaded();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        // Only copy the cell's in-memory state into its ItemStack before the
        // inventory is serialized. Do not call saveChanges() from this path:
        // the chunk is already being saved, and re-marking it dirty here makes
        // /save-all flush pick it up again in the next dirty pass.
        AppFluxBridge.persistCellStorage(cachedCellView);
        super.saveAdditional(data, registries);
        data.putString(TAG_MODE, mode.name());
        cellInv.writeToNBT(data, TAG_CELL_INV, registries);

        data.put(TAG_CONNECTIONS, WirelessConnectionLists.writeTagList(connections));
        frequencyBinding.save(data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);

        if (data.contains(TAG_MODE, Tag.TAG_STRING)) {
            try {
                mode = PowerMode.valueOf(data.getString(TAG_MODE));
            } catch (IllegalArgumentException ignored) {
                mode = PowerMode.NORMAL;
            }
        } else {
            mode = PowerMode.NORMAL;
        }

        cellInv.readFromNBT(data, TAG_CELL_INV, registries);
        cellViewDirty = true;
        cellCapacityDirty = true;
        cachedCellView = null;
        WirelessConnectionLists.readTagList(
                data, TAG_CONNECTIONS, connections, MAX_WIRELESS_CONNECTIONS, WirelessConnection::fromTag);
        invalidConnectionScanCursor = 0;
        connectionVersion++;

        frequencyBinding.load(data);
        logic.onStateChanged();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(mode.ordinal());
        data.writeVarInt(connections.size());
        for (var connection : connections) {
            data.writeResourceLocation(connection.dimension().location());
            data.writeBlockPos(connection.pos());
            data.writeByte(connection.boundFace().get3DDataValue());
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);

        int modeOrdinal = data.readByte();
        PowerMode newMode = modeOrdinal >= 0 && modeOrdinal < PowerMode.values().length
                ? PowerMode.values()[modeOrdinal]
                : PowerMode.NORMAL;

        int count = data.readVarInt();
        var newConnections = new ArrayList<WirelessConnection>(Math.min(count, MAX_WIRELESS_CONNECTIONS));
        for (int i = 0; i < count; i++) {
            var dim = ResourceKey.create(Registries.DIMENSION, data.readResourceLocation());
            var pos = data.readBlockPos();
            int rawFace = data.readByte();
            var face = (rawFace >= 0 && rawFace < Direction.values().length)
                    ? Direction.from3DDataValue(rawFace) : Direction.DOWN;
            var connection = new WirelessConnection(dim, pos, face);
            WirelessConnectionLists.addOrReplace(newConnections, connection, MAX_WIRELESS_CONNECTIONS);
        }

        if (newMode != mode || !newConnections.equals(connections)) {
            mode = newMode;
            connections.clear();
            connections.addAll(newConnections);
            invalidConnectionScanCursor = 0;
            connectionVersion++;
            logic.onStateChanged();
            changed = true;
        }

        return changed;
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        if (level instanceof ServerLevel) {
            clearInvalidConnections();
        }
        MenuOpener.open(OverloadedPowerSupplyMenu.TYPE, player, locator);
    }
}
