package com.moakiee.ae2lt.blockentity;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.me.GridConnection;

import com.moakiee.ae2lt.grid.WirelessConnectionCapProvider;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * Wireless Overloaded Controller: an overloaded controller that also acts as a
 * wireless transmitter. Select a frequency via the GUI to register this
 * controller in the global {@link WirelessFrequencyManager}.
 * Receivers with the matching frequency connect directly to this controller's grid node.
 */
public class WirelessOverloadedControllerBlockEntity extends OverloadedControllerBlockEntity
        implements WirelessFrequencyManager.WirelessTransmitterNodeProvider, WirelessConnectionCapProvider {

    private int frequencyId = -1;

    public WirelessOverloadedControllerBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.WIRELESS_OVERLOADED_CONTROLLER.get(), pos, blockState);
    }

    protected WirelessOverloadedControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("wireless_overloaded_controller")
                .setVisualRepresentation(ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get());
    }

    /**
     * Whether this is an advanced wireless controller (cross-dimension, unlimited channels).
     * Overridden in the advanced subclass.
     */
    public boolean isAdvanced() {
        return false;
    }

    @Override
    public int getWirelessChannelCap(ChannelMode mode) {
        return isAdvanced() ? Integer.MAX_VALUE / 2 : 32 * mode.getCableCapacityFactor();
    }

    // ── WirelessTransmitterNodeProvider ──

    @Override
    @Nullable
    public IGridNode getWirelessGridNode() {
        return getMainNode().getNode();
    }

    @Override
    public int getTransmitterFrequencyId() {
        return frequencyId;
    }

    // ── Frequency Management ──

    public int getFrequencyId() {
        return frequencyId;
    }

    /**
     * Channels currently allocated across the grid.
     * Uses our helper which de-duplicates multiblock clusters. AE2's
     * {@code pathingService.getUsedChannels()} is also not reliable for
     * overloaded-only networks because vanilla pathing indexes controller
     * machines by exact class.
     */
    public int getGridUsedChannels() {
        var grid = getMainNode().getGrid();
        if (grid == null) return 0;
        return OverloadedChannelOwnerHelper.countUsedChannels(grid);
    }

    /**
     * Maximum channels the grid could support.
     * Returns -1 as a sentinel when the grid is in INFINITE channel mode.
     */
    public int getGridMaxChannels() {
        var grid = getMainNode().getGrid();
        if (grid == null) return 0;

        var channelMode = grid.getPathingService().getChannelMode();
        if (channelMode == appeng.api.networking.pathing.ChannelMode.INFINITE) {
            return -1;
        }

        int overloadedCount = 0;
        int vanillaCount = 0;
        for (var node : com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper.getAllControllerNodes(grid)) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) {
                overloadedCount++;
            } else {
                vanillaCount++;
            }
        }
        int factor = Math.max(1, channelMode.getCableCapacityFactor());
        long cap = (long) overloadedCount
                * com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper.channelsPerController() * factor
                + (long) vanillaCount * 32L * factor;
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    public boolean isFrequencyActive() {
        if (frequencyId <= 0 || level == null) {
            return false;
        }

        var manager = WirelessFrequencyManager.get();
        if (manager == null) {
            return false;
        }

        var entry = manager.findTransmitter(frequencyId);
        return entry != null
                && entry.dimension().equals(level.dimension())
                && entry.pos().equals(worldPosition)
                && getMainNode().getNode() != null;
    }

    public void setFrequency(int newFreqId) {
        if (newFreqId == frequencyId) return;

        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;

        // validate new frequency is free BEFORE releasing the old one
        if (newFreqId > 0 && level != null
                && !manager.canRegisterTransmitter(newFreqId, level.dimension(), worldPosition)) {
            markForUpdate();
            return;
        }

        // release old
        if (frequencyId > 0) {
            destroyAllVirtualConnections();
            manager.unregisterTransmitter(frequencyId);
            if (level != null) {
                manager.unregisterDevice(frequencyId, level.dimension(), worldPosition);
            }
        }

        int oldFreqId = frequencyId;
        frequencyId = newFreqId;

        // register new
        if (frequencyId > 0 && level != null) {
            if (!manager.registerTransmitter(frequencyId, level.dimension(), worldPosition,
                    getMainNode().getNode(), isAdvanced())) {
                // lost race: restore old binding (canRegister check above is best-effort)
                frequencyId = oldFreqId;
                if (frequencyId > 0) {
                    manager.registerTransmitter(frequencyId, level.dimension(), worldPosition,
                            getMainNode().getNode(), isAdvanced());
                    manager.registerDevice(frequencyId, new WirelessFrequencyManager.DeviceEntry(
                            level.dimension(), worldPosition, true, isAdvanced()));
                }
            } else {
                manager.registerDevice(frequencyId, new WirelessFrequencyManager.DeviceEntry(
                        level.dimension(), worldPosition, true, isAdvanced()));
            }
        }

        saveChanges();
        markForUpdate();
    }

    public void clearFrequency() {
        setFrequency(-1);
    }

    /**
     * Destroys all virtual (direction-less) connections on this controller's
     * grid node.
     */
    private void destroyAllVirtualConnections() {
        IGridNode node = getMainNode().getNode();
        if (node == null) return;

        for (var conn : new java.util.ArrayList<>(node.getConnections())) {
            if (conn instanceof GridConnection gc && gc.getDirection(node) == null) {
                gc.destroy();
            }
        }
    }

    // ── Grid Node Events ──

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            updateManagerRegistration();
        }
    }

    private void updateManagerRegistration() {
        var manager = WirelessFrequencyManager.get();
        if (manager == null || frequencyId <= 0 || level == null) return;

        // validate frequency still exists; clear zombie binding
        if (!manager.isFrequencyValid(frequencyId)) {
            frequencyId = -1;
            saveChanges();
            markForUpdate();
            return;
        }

        if (!manager.registerTransmitter(frequencyId, level.dimension(), worldPosition,
                getMainNode().getNode(), isAdvanced())) {
            frequencyId = -1;
            saveChanges();
            markForUpdate();
            return;
        }
        manager.registerDevice(frequencyId, new WirelessFrequencyManager.DeviceEntry(
                level.dimension(), worldPosition, true, isAdvanced()));
        markForUpdate();
    }

    // ── Lifecycle ──

    @Override
    public void onReady() {
        super.onReady();
        updateManagerRegistration();
    }

    @Override
    public void setRemoved() {
        var manager = WirelessFrequencyManager.get();
        if (manager != null && frequencyId > 0) {
            destroyAllVirtualConnections();
            manager.unregisterTransmitter(frequencyId);
            if (level != null) {
                manager.unregisterDevice(frequencyId, level.dimension(), worldPosition);
            }
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        updateManagerRegistration();
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.WIRELESS_OVERLOADED_CONTROLLER.get().asItem();
    }

    public static void wirelessServerTick(Level level, BlockPos pos, BlockState state,
                                          WirelessOverloadedControllerBlockEntity be) {
        OverloadedControllerBlockEntity.serverTick(level, pos, state, be);
    }

    // ── Persistence ──

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("FrequencyId", frequencyId);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        frequencyId = tag.contains("FrequencyId") ? tag.getInt("FrequencyId") : -1;
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.nbt.CompoundTag output,
                               @Nullable net.minecraft.world.entity.player.Player player) {
        super.exportSettings(mode, output, player);
        FrequencyBindingHelper.exportMemorySettings(mode, output, frequencyId);
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.nbt.CompoundTag input,
                               @Nullable net.minecraft.world.entity.player.Player player) {
        super.importSettings(mode, input, player);
        // setFrequency guards against duplicates on transmitters and reverts on conflict,
        // so the paste is a no-op if the target frequency is already bound elsewhere.
        FrequencyBindingHelper.importMemorySettings(mode, input, this::setFrequency);
    }
}
