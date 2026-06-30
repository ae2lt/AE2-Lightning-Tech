package com.moakiee.ae2lt.grid;

import java.util.function.IntConsumer;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.util.SettingsFrom;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import com.qianchang.ae2lt_core.ae2.channel.OverloadedChannelOwnerHelper;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.wirelesslink.MultiblockLinkReadiness;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkOps;
import com.moakiee.ae2lt.logic.MemoryCardConfigSupport;

/**
 * Shared receiver-side frequency binding. It mirrors the original
 * WirelessReceiver virtual-connection lifecycle, but can be attached to any
 * AE-networked block entity whose main node should join a wireless controller.
 */
public final class FrequencyBindingHelper
        implements WirelessFrequencyManager.TransmitterListener,
                   com.moakiee.ae2lt.api.frequency.FrequencyBindingAccess {
    public static final String TAG_FREQUENCY_ID = "FrequencyId";
    public static final String TAG_MEMORY_FREQUENCY = "Frequency";

    private static final Logger LOG = LoggerFactory.getLogger("ae2lt-wireless");

    /** Initial retry delay when a transmitter event is missed. */
    private static final int INITIAL_RETRY_COOLDOWN_TICKS = 20;
    /** Upper bound for retry backoff; keeps unloaded chunks from causing steady update churn. */
    private static final int MAX_RETRY_COOLDOWN_TICKS = 20 * 10;

    private final com.moakiee.ae2lt.api.frequency.FrequencyBindingHost host;

    private int frequencyId = -1;
    @Nullable
    private IGridConnection virtualConnection;
    private boolean needsConnectionUpdate;
    private int retryCooldownTicks;
    private int nextRetryCooldownTicks = INITIAL_RETRY_COOLDOWN_TICKS;
    private int subscribedFrequencyId = -1;

    public FrequencyBindingHelper(com.moakiee.ae2lt.api.frequency.FrequencyBindingHost host) {
        this.host = host;
    }

    public int getFrequencyId() {
        return frequencyId;
    }

    public void setFrequency(int newFreqId) {
        if (newFreqId == this.frequencyId) return;

        detach();
        this.frequencyId = newFreqId;
        attach();
        host.saveFrequencyBindingChanges();
        host.markFrequencyBindingForUpdate();
    }

    public void clearFrequency() {
        detach();
        this.frequencyId = -1;
        host.saveFrequencyBindingChanges();
        host.markFrequencyBindingForUpdate();
    }

    @Override
    public void onTransmitterChanged(int freqId, boolean available) {
        if (freqId != frequencyId) return;

        if (!available) {
            var manager = WirelessFrequencyManager.get();
            if (manager != null && !manager.isFrequencyValid(freqId)) {
                // The frequency was deleted, so fully unbind this device.
                detach();
                this.frequencyId = -1;
                host.saveFrequencyBindingChanges();
                host.markFrequencyBindingForUpdate();
                return;
            }
        }

        requestConnectionUpdate();
    }

    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            requestConnectionUpdate();
        }
    }

    public void serverTick() {
        if (retryCooldownTicks > 0) {
            retryCooldownTicks--;
            return;
        }
        if (!needsConnectionUpdate) return;

        var be = host.getFrequencyBindingBlockEntity();
        if (frequencyId <= 0 || be.getLevel() == null || be.getLevel().isClientSide()) {
            needsConnectionUpdate = false;
            return;
        }

        if (be.getMainNode().getNode() == null) {
            scheduleRetry();
            return;
        }

        needsConnectionUpdate = false;

        boolean wasConnected = hasLiveVirtualConnection();
        if (virtualConnection != null) {
            revalidateConnection();
        }

        if (virtualConnection == null) {
            tryEstablishConnection();
        }

        boolean connected = hasLiveVirtualConnection();
        if (connected) {
            resetRetryBackoff();
        }
        if (connected != wasConnected) {
            host.markFrequencyBindingForUpdate();
        }
    }

    public void onReady() {
        if (frequencyId > 0) {
            var manager = WirelessFrequencyManager.get();
            if (manager != null && !manager.isFrequencyValid(frequencyId)) {
                detach();
                frequencyId = -1;
                host.saveFrequencyBindingChanges();
                return;
            }
        }
        attach();
    }

    public void setRemoved() {
        detach();
    }

    public void clearRemoved() {
        attach();
    }

    public void save(CompoundTag tag) {
        tag.putInt(TAG_FREQUENCY_ID, frequencyId);
    }

    public void load(CompoundTag tag) {
        frequencyId = tag.contains(TAG_FREQUENCY_ID) ? tag.getInt(TAG_FREQUENCY_ID) : -1;
    }

    public static void writeMemoryFrequency(CompoundTag tag, int frequencyId) {
        if (frequencyId > 0) {
            tag.putInt(TAG_MEMORY_FREQUENCY, frequencyId);
        }
    }

    public static boolean importMemoryFrequency(CompoundTag tag, IntConsumer setter) {
        if (!tag.contains(TAG_MEMORY_FREQUENCY)) {
            return false;
        }
        setter.accept(tag.getInt(TAG_MEMORY_FREQUENCY));
        return true;
    }

    public static void exportMemorySettings(SettingsFrom mode, DataComponentMap.Builder builder, int frequencyId) {
        MemoryCardConfigSupport.exportMemoryCardSettings(mode, builder, tag -> writeMemoryFrequency(tag, frequencyId));
    }

    public static void importMemorySettings(SettingsFrom mode, DataComponentMap input, IntConsumer setter) {
        MemoryCardConfigSupport.importMemoryCardSettings(mode, input, tag -> importMemoryFrequency(tag, setter));
    }

    public int getGridUsedChannels() {
        var grid = host.getFrequencyBindingBlockEntity().getMainNode().getGrid();
        if (grid == null) return 0;
        return OverloadedChannelOwnerHelper.countUsedChannels(grid);
    }

    public int getGridMaxChannels() {
        var grid = host.getFrequencyBindingBlockEntity().getMainNode().getGrid();
        if (grid == null) return 0;

        var channelMode = grid.getPathingService().getChannelMode();
        if (channelMode == appeng.api.networking.pathing.ChannelMode.INFINITE) {
            return -1;
        }

        int overloadedCount = 0;
        int vanillaCount = 0;
        for (var node : OverloadedChannelOwnerHelper.getAllControllerNodes(grid)) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) {
                overloadedCount++;
            } else {
                vanillaCount++;
            }
        }

        int factor = Math.max(1, channelMode.getCableCapacityFactor());
        long cap = (long) overloadedCount * OverloadedChannelOwnerHelper.channelsPerController() * factor
                + (long) vanillaCount * 32L * factor;
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    public boolean isConnected() {
        return hasLiveVirtualConnection();
    }

    private boolean hasLiveVirtualConnection() {
        if (virtualConnection == null) return false;

        IGridNode myNode = host.getFrequencyBindingBlockEntity().getMainNode().getNode();
        if (myNode == null) return false;
        for (var conn : myNode.getConnections()) {
            if (conn == virtualConnection && !conn.isInWorld()) return true;
        }

        return false;
    }

    /**
     * Releases side effects for the current frequencyId without changing the
     * stored id: listener, virtual connection, and device registry entry.
     */
    private void detach() {
        clearConnectionUpdate();
        unsubscribeListener();
        destroyVirtualConnection();
        unregisterDeviceIfBound();
    }

    /**
     * Rebuilds side effects for the current frequencyId and asks the next
     * server tick to establish the virtual connection.
     */
    private void attach() {
        subscribeListener();
        registerDevice();
        if (frequencyId > 0) {
            requestConnectionUpdate();
        }
    }

    private void requestConnectionUpdate() {
        needsConnectionUpdate = true;
        retryCooldownTicks = 0;
        resetRetryBackoff();
    }

    private void clearConnectionUpdate() {
        needsConnectionUpdate = false;
        retryCooldownTicks = 0;
        resetRetryBackoff();
    }

    private void resetRetryBackoff() {
        nextRetryCooldownTicks = INITIAL_RETRY_COOLDOWN_TICKS;
    }

    /** Called after a failed connection attempt; backs off to avoid retry/update churn. */
    private void scheduleRetry() {
        needsConnectionUpdate = true;
        retryCooldownTicks = nextRetryCooldownTicks;
        nextRetryCooldownTicks = Math.min(nextRetryCooldownTicks * 2, MAX_RETRY_COOLDOWN_TICKS);
    }

    private void unregisterDeviceIfBound() {
        if (frequencyId <= 0) return;
        var be = host.getFrequencyBindingBlockEntity();
        if (be.getLevel() == null) return;
        var manager = WirelessFrequencyManager.get();
        if (manager != null) {
            manager.unregisterDevice(frequencyId, be.getLevel().dimension(), be.getBlockPos());
        }
    }

    private void subscribeListener() {
        if (frequencyId <= 0) return;
        if (subscribedFrequencyId == frequencyId) return;
        if (subscribedFrequencyId > 0) {
            unsubscribeListener();
        }

        var manager = WirelessFrequencyManager.get();
        if (manager != null) {
            manager.addListener(frequencyId, this);
            subscribedFrequencyId = frequencyId;
        }
    }

    private void unsubscribeListener() {
        if (subscribedFrequencyId <= 0) return;
        var manager = WirelessFrequencyManager.get();
        if (manager != null) {
            manager.removeListener(subscribedFrequencyId, this);
        }
        subscribedFrequencyId = -1;
    }

    private void registerDevice() {
        if (frequencyId <= 0) return;

        var be = host.getFrequencyBindingBlockEntity();
        var manager = WirelessFrequencyManager.get();
        if (manager != null && be.getLevel() != null) {
            manager.registerDevice(frequencyId, new WirelessFrequencyManager.DeviceEntry(
                    be.getLevel().dimension(),
                    be.getBlockPos(),
                    false,
                    false,
                    host.getFrequencyBindingDeviceName()));
        }
    }

    private void tryEstablishConnection() {
        var be = host.getFrequencyBindingBlockEntity();
        if (frequencyId <= 0 || be.getLevel() == null || be.getLevel().isClientSide()) return;
        if (virtualConnection != null) return;

        IGridNode myNode = be.getMainNode().getNode();
        if (myNode == null) {
            scheduleRetry();
            return;
        }
        if (!MultiblockLinkReadiness.canKeepVirtualConnection(myNode)) {
            MultiblockLinkReadiness.refreshAfterVirtualConnectionRemoved(myNode);
            scheduleRetry();
            return;
        }

        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;  // The server registry is not ready; retrying would not help.

        var entry = manager.findTransmitter(frequencyId);
        if (entry == null) {
            // The transmitter may be in an unloaded chunk or not through onReady yet.
            scheduleRetry();
            return;
        }

        if (!entry.advanced() && !be.getLevel().dimension().equals(entry.dimension())) {
            return;  // Dimension mismatch is a configuration issue, not a transient state.
        }

        var server = ((ServerLevel) be.getLevel()).getServer();
        IGridNode remoteNode = manager.resolveNode(frequencyId, server);
        if (remoteNode == null) {
            scheduleRetry();
            return;
        }

        if (alreadyHasFrequencyChannel(myNode, remoteNode)) {
            return;
        }

        if (wouldMergeControllerNetworks(myNode.getGrid(), remoteNode.getGrid())) {
            LOG.warn("Virtual connection blocked to avoid controller-network merge: device@{} -> freq={}",
                    be.getBlockPos(), frequencyId);
            return;
        }

        try {
            virtualConnection = WirelessLinkOps.createVirtualConnection(myNode, remoteNode);
            LOG.debug("Virtual connection established: device@{} -> freq={}", be.getBlockPos(), frequencyId);
        } catch (IllegalStateException e) {
            LOG.warn("Virtual connection FAILED: device@{} -> freq={}: {}",
                    be.getBlockPos(), frequencyId, e.getMessage());
            scheduleRetry();
        }
    }

    private void destroyVirtualConnection() {
        if (virtualConnection == null) return;

        IGridNode myNode = host.getFrequencyBindingBlockEntity().getMainNode().getNode();
        if (myNode != null) {
            for (var conn : myNode.getConnections()) {
                if (conn == virtualConnection) {
                    if (!conn.isInWorld()) {
                        virtualConnection.destroy();
                    }
                    break;
                }
            }
            MultiblockLinkReadiness.refreshAfterVirtualConnectionRemoved(myNode);
        }
        virtualConnection = null;
    }

    private void revalidateConnection() {
        var be = host.getFrequencyBindingBlockEntity();
        if (frequencyId <= 0 || be.getLevel() == null || be.getLevel().isClientSide()) return;
        if (virtualConnection == null) return;

        IGridNode myNode = be.getMainNode().getNode();
        if (myNode == null) return;
        if (!MultiblockLinkReadiness.canKeepVirtualConnection(myNode)) {
            destroyVirtualConnection();
            scheduleRetry();
            return;
        }

        boolean connectionAlive = false;
        IGridNode connectedTarget = null;
        for (var conn : myNode.getConnections()) {
            if (conn == virtualConnection && !conn.isInWorld()) {
                connectionAlive = true;
                connectedTarget = conn.getOtherSide(myNode);
                break;
            }
        }

        if (!connectionAlive) {
            virtualConnection = null;
            return;
        }

        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;

        var server = ((ServerLevel) be.getLevel()).getServer();
        IGridNode currentTarget = manager.resolveNode(frequencyId, server);
        if (currentTarget == null || connectedTarget != currentTarget) {
            destroyVirtualConnection();
        }
    }

    private static boolean isAlreadyInFrequencyGrid(IGridNode targetNode, IGridNode transmitterNode) {
        IGrid targetGrid = targetNode.getGrid();
        IGrid transmitterGrid = transmitterNode.getGrid();
        return targetGrid != null && transmitterGrid != null && targetGrid == transmitterGrid;
    }

    private static boolean alreadyHasFrequencyChannel(IGridNode targetNode, IGridNode transmitterNode) {
        return isAlreadyInFrequencyGrid(targetNode, transmitterNode)
                && targetNode.meetsChannelRequirements();
    }

    private static boolean wouldMergeControllerNetworks(@Nullable IGrid targetGrid, @Nullable IGrid frequencyGrid) {
        if (targetGrid == null || targetGrid == frequencyGrid) {
            return false;
        }
        return !OverloadedChannelOwnerHelper.getAllControllerNodes(targetGrid).isEmpty();
    }
}
