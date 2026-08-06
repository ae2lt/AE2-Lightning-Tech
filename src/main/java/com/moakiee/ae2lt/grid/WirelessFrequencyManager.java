package com.moakiee.ae2lt.grid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

import appeng.api.networking.IGridNode;

/**
 * Global registry of wireless frequencies and their transmitters.
 * Replaces the old UUID-based WirelessTransmitterManager.
 * Persisted as overworld SavedData.
 */
public final class WirelessFrequencyManager extends SavedData {

    private static final String DATA_NAME = "ae2lt_wireless_frequencies";

    // ── Transmitter Entry ──

    public record TransmitterEntry(
            ResourceKey<Level> dimension,
            BlockPos pos,
            @Nullable IGridNode cachedNode,
            boolean advanced
    ) {}

    // ── Device Entry (controllers + receivers union, for the Connections tab) ──

    public record DeviceEntry(
            ResourceKey<Level> dimension,
            BlockPos pos,
            boolean isController,
            boolean advanced,
            String deviceName
    ) {
        public DeviceEntry(ResourceKey<Level> dimension, BlockPos pos,
                           boolean isController, boolean advanced) {
            this(dimension, pos, isController, advanced, defaultDeviceName(isController, advanced));
        }

        private static String defaultDeviceName(boolean isController, boolean advanced) {
            if (isController) {
                return advanced
                        ? "block.ae2lt.advanced_wireless_overloaded_controller"
                        : "block.ae2lt.wireless_overloaded_controller";
            }
            return "block.ae2lt.wireless_receiver";
        }
    }

    // ── Listener ──

    @FunctionalInterface
    public interface TransmitterListener {
        void onTransmitterChanged(int freqId, boolean available);
    }

    @FunctionalInterface
    public interface DeviceListener {
        void onDevicesChanged(int freqId);
    }

    // ── Transmitter Node Provider ──

    public interface WirelessTransmitterNodeProvider {
        @Nullable IGridNode getWirelessGridNode();
        int getTransmitterFrequencyId();
    }

    // ── State ──

    private final Int2ObjectOpenHashMap<WirelessFrequency> frequencies = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<TransmitterEntry> transmitters = new Int2ObjectOpenHashMap<>();
    private final FrequencyDeviceIndex<DeviceEntry> devices = new FrequencyDeviceIndex<>();
    private final Map<Integer, List<TransmitterListener>> listeners = new HashMap<>();
    private final List<DeviceListener> deviceListeners = new ArrayList<>();
    private final Set<Integer> pendingDeviceNotifications = new HashSet<>();

    private int uniqueId = 0;

    @Nullable
    private static WirelessFrequencyManager instance;

    private WirelessFrequencyManager() {}

    private WirelessFrequencyManager(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag);
    }

    // ── Lifecycle ──

    public static void onServerStart(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        instance = overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WirelessFrequencyManager::new,
                        WirelessFrequencyManager::new),
                DATA_NAME);
        // Broadcasts are flushed from the server tick, outside chunk post-load callbacks.
        instance.addDeviceListener(freqId ->
                com.moakiee.ae2lt.network.SyncFrequencyDetailPacket.broadcastConnectionsTo(server, freqId));
    }

    public static void onServerStop() {
        if (instance != null) {
            instance.listeners.clear();
            instance.deviceListeners.clear();
            instance.pendingDeviceNotifications.clear();
        }
        instance = null;
    }

    @Nullable
    public static WirelessFrequencyManager get() {
        return instance;
    }

    public static void flushPendingDeviceNotifications() {
        if (instance != null) {
            instance.flushDeviceListeners();
        }
    }

    // ── Frequency CRUD ──

    @Nullable
    public WirelessFrequency createFrequency(ServerPlayer creator, String name, int color,
                                              FrequencySecurityLevel security, String password) {
        do {
            uniqueId++;
            if (uniqueId < 0) uniqueId = 1;
        } while (frequencies.containsKey(uniqueId));

        var freq = new WirelessFrequency(uniqueId, name, color, creator, security, password);
        frequencies.put(freq.getId(), freq);
        setDirty();
        return freq;
    }

    public boolean deleteFrequency(int id, MinecraftServer server) {
        WirelessFrequency removed = frequencies.remove(id);
        if (removed != null) {
            // clear controller binding if transmitter is loaded
            TransmitterEntry txEntry = transmitters.get(id);
            if (txEntry != null && server != null) {
                ServerLevel txLevel = server.getLevel(txEntry.dimension());
                var be = txLevel == null ? null : getLoadedBlockEntity(txLevel, txEntry.pos());
                if (be instanceof WirelessTransmitterNodeProvider provider
                        && provider.getTransmitterFrequencyId() == id) {
                    if (be instanceof com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity ctrl) {
                        ctrl.clearFrequency();
                    }
                }
            }
            transmitters.remove(id);
            devices.clearFrequency(id);
            fireListeners(id, false);
            queueDeviceListeners(id);
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * Returns true if the given frequency ID is still valid (exists in the registry).
     */
    public boolean isFrequencyValid(int freqId) {
        return freqId > 0 && frequencies.containsKey(freqId);
    }

    /**
     * Mark the SavedData as dirty so changes persist. Called by external mutation paths
     * (edit frequency, change membership, etc.) that bypass create/delete.
     */
    public void markModified() {
        setDirty();
    }

    @Nullable
    public WirelessFrequency getFrequency(int id) {
        return frequencies.get(id);
    }

    public Collection<WirelessFrequency> getAllFrequencies() {
        return frequencies.values();
    }

    // ── Transmitter Registration ──

    public boolean canRegisterTransmitter(int freqId, ResourceKey<Level> dimension, BlockPos pos) {
        var existing = transmitters.get(freqId);
        return existing == null
                || existing.dimension().equals(dimension) && existing.pos().equals(pos);
    }

    public boolean registerTransmitter(int freqId, ResourceKey<Level> dimension, BlockPos pos,
                                        @Nullable IGridNode node, boolean advanced) {
        if (!canRegisterTransmitter(freqId, dimension, pos)) {
            return false;
        }

        transmitters.put(freqId, new TransmitterEntry(dimension, pos, node, advanced));
        setDirty();
        fireListeners(freqId, true);
        return true;
    }

    public void unregisterTransmitter(int freqId) {
        if (transmitters.remove(freqId) != null) {
            setDirty();
            fireListeners(freqId, false);
        }
    }

    public void updateNode(int freqId, @Nullable IGridNode node) {
        var entry = transmitters.get(freqId);
        if (entry != null) {
            transmitters.put(freqId, new TransmitterEntry(entry.dimension(), entry.pos(), node, entry.advanced()));
        }
    }

    @Nullable
    public TransmitterEntry findTransmitter(int freqId) {
        return transmitters.get(freqId);
    }

    /**
     * Resolve a transmitter to a live GridNode.
     * Re-evaluates the node from the block entity when the chunk is loaded.
     */
    @Nullable
    public IGridNode resolveNode(int freqId, MinecraftServer server) {
        var entry = transmitters.get(freqId);
        if (entry == null) return null;

        ServerLevel targetLevel = server.getLevel(entry.dimension());
        if (targetLevel == null) {
            return entry.cachedNode();
        }

        // Chunk not loaded: fall back to cached node. Loaded but BE missing/wrong type: treat as invalid.
        var chunk = targetLevel.getChunkSource()
                .getChunkNow(entry.pos().getX() >> 4, entry.pos().getZ() >> 4);
        if (chunk == null) {
            return entry.cachedNode();
        }
        var be = chunk.getBlockEntity(entry.pos());
        if (be instanceof WirelessTransmitterNodeProvider provider) {
            IGridNode node = provider.getWirelessGridNode();
            updateNode(freqId, node);
            return node;
        }
        return null;
    }

    /**
     * Whether the frequency's transmitter is an advanced wireless controller.
     * Reads the persisted advanced flag, so it stays accurate even when the
     * transmitter chunk is unloaded.
     */
    public boolean isAdvancedTransmitter(int freqId) {
        var entry = transmitters.get(freqId);
        return entry != null && entry.advanced();
    }

    /**
     * Frequency-card variant of {@link #resolveNode}: only returns a node when
     * the transmitter is an advanced controller. Frequency-card connectivity
     * (terminal remote access, hand-held device links) is reserved for advanced
     * controllers, so a normal-controller transmitter makes the card inert —
     * this returns {@code null} as if the frequency had no transmitter.
     */
    @Nullable
    public IGridNode resolveAdvancedNode(int freqId, MinecraftServer server) {
        return isAdvancedTransmitter(freqId) ? resolveNode(freqId, server) : null;
    }

    // ── Listeners ──

    public void addListener(int freqId, TransmitterListener listener) {
        listeners.computeIfAbsent(freqId, k -> new ArrayList<>()).add(listener);
    }

    public void removeListener(int freqId, TransmitterListener listener) {
        var list = listeners.get(freqId);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(freqId);
            }
        }
    }

    private void fireListeners(int freqId, boolean available) {
        var list = listeners.get(freqId);
        if (list == null || list.isEmpty()) return;
        for (var listener : List.copyOf(list)) {
            listener.onTransmitterChanged(freqId, available);
        }
    }

    // ── Device Registry (controllers + receivers) ──

    public void registerDevice(int freqId, DeviceEntry entry) {
        if (freqId <= 0) return;
        if (devices.put(freqId, entry.dimension().location().toString(), entry.pos().asLong(), entry)) {
            setDirty();
            queueDeviceListeners(freqId);
        }
    }

    public void unregisterDevice(int freqId, ResourceKey<Level> dim, BlockPos pos) {
        if (freqId <= 0) return;
        if (devices.remove(freqId, dim.location().toString(), pos.asLong())) {
            setDirty();
            queueDeviceListeners(freqId);
        }
    }

    public List<DeviceEntry> getDevices(int freqId) {
        return devices.get(freqId);
    }

    public void addDeviceListener(DeviceListener l) {
        deviceListeners.add(l);
    }

    private void queueDeviceListeners(int freqId) {
        pendingDeviceNotifications.add(freqId);
    }

    private void flushDeviceListeners() {
        if (pendingDeviceNotifications.isEmpty() || deviceListeners.isEmpty()) return;
        var dirtyFrequencies = List.copyOf(pendingDeviceNotifications);
        pendingDeviceNotifications.clear();
        for (int freqId : dirtyFrequencies) {
            fireDeviceListeners(freqId);
        }
    }

    private void fireDeviceListeners(int freqId) {
        if (deviceListeners.isEmpty()) return;
        for (var l : List.copyOf(deviceListeners)) {
            l.onDevicesChanged(freqId);
        }
    }

    @Nullable
    private static BlockEntity getLoadedBlockEntity(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    // ── Persistence ──

    private void read(CompoundTag root) {
        uniqueId = root.getInt("uniqueId");

        ListTag freqList = root.getList("frequencies", Tag.TAG_COMPOUND);
        for (int i = 0; i < freqList.size(); i++) {
            WirelessFrequency freq = new WirelessFrequency();
            freq.readFromTag(freqList.getCompound(i), WirelessFrequency.NBT_SAVE_ALL);
            if (freq.getId() > 0) {
                frequencies.put(freq.getId(), freq);
            }
        }

        ListTag txList = root.getList("transmitters", Tag.TAG_COMPOUND);
        for (int i = 0; i < txList.size(); i++) {
            CompoundTag entry = txList.getCompound(i);
            int freqId = entry.getInt("freqId");
            var dimKey = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getString("dim")));
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            boolean adv = entry.getBoolean("advanced");
            transmitters.put(freqId, new TransmitterEntry(dimKey, pos, null, adv));
        }

        ListTag devList = root.getList("devices", Tag.TAG_COMPOUND);
        for (int i = 0; i < devList.size(); i++) {
            CompoundTag entry = devList.getCompound(i);
            int freqId = entry.getInt("freqId");
            var dimKey = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(entry.getString("dim")));
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            boolean ctrl = entry.getBoolean("controller");
            boolean adv = entry.getBoolean("advanced");
            String deviceName = entry.contains("name")
                    ? entry.getString("name")
                    : DeviceEntry.defaultDeviceName(ctrl, adv);
            devices.put(freqId, dimKey.location().toString(), pos.asLong(),
                    new DeviceEntry(dimKey, pos, ctrl, adv, deviceName));
        }
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("uniqueId", uniqueId);

        ListTag freqList = new ListTag();
        for (var freq : frequencies.values()) {
            CompoundTag tag = new CompoundTag();
            freq.writeToTag(tag, WirelessFrequency.NBT_SAVE_ALL);
            freqList.add(tag);
        }
        root.put("frequencies", freqList);

        ListTag txList = new ListTag();
        for (var e : transmitters.int2ObjectEntrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("freqId", e.getIntKey());
            tag.putString("dim", e.getValue().dimension().location().toString());
            tag.putLong("pos", e.getValue().pos().asLong());
            tag.putBoolean("advanced", e.getValue().advanced());
            txList.add(tag);
        }
        root.put("transmitters", txList);

        ListTag devList = new ListTag();
        devices.forEach((freqId, d) -> {
            CompoundTag tag = new CompoundTag();
            tag.putInt("freqId", freqId);
            tag.putString("dim", d.dimension().location().toString());
            tag.putLong("pos", d.pos().asLong());
            tag.putBoolean("controller", d.isController());
            tag.putBoolean("advanced", d.advanced());
            tag.putString("name", d.deviceName());
            devList.add(tag);
        });
        root.put("devices", devList);

        return root;
    }
}
