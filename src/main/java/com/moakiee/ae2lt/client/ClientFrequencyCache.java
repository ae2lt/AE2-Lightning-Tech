package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.FrequencyMember;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side cache of all wireless frequencies. Populated by sync packets.
 */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class ClientFrequencyCache {

    public record CachedFrequency(int id, String name, int color,
                                   java.util.UUID ownerUUID, FrequencySecurityLevel security) {}

    public record CachedMember(java.util.UUID uuid, String name, FrequencyAccessLevel access) {}

    public record CachedConnection(String dimension, net.minecraft.core.BlockPos pos,
                                    boolean controller, boolean advanced, boolean loaded,
                                    String deviceName) {}

    private static final Map<Integer, CachedFrequency> cache = new HashMap<>();
    private static final Map<Integer, List<CachedMember>> members = new HashMap<>();
    private static final Map<Integer, List<CachedConnection>> connections = new HashMap<>();
    private static int revision = 0;

    private ClientFrequencyCache() {}

    /** Monotonic counter bumped on every mutation; screens poll it to rebuild widgets. */
    public static int revision() {
        return revision;
    }

    public static void updateFromSync(List<SyncFrequencyListPacket.FrequencyEntry> entries) {
        cache.clear();
        for (var e : entries) {
            cache.put(e.id(), new CachedFrequency(e.id(), e.name(), e.color(), e.ownerUUID(), e.security()));
        }
        // drop cached members / connections for frequencies that no longer exist
        members.keySet().retainAll(cache.keySet());
        connections.keySet().retainAll(cache.keySet());
        revision++;
    }

    public static void upsertFrequency(int id, String name, int color,
                                        java.util.UUID ownerUUID, FrequencySecurityLevel security) {
        cache.put(id, new CachedFrequency(id, name, color, ownerUUID, security));
        revision++;
    }

    public static void removeFrequency(int id) {
        boolean changed = cache.remove(id) != null;
        changed |= members.remove(id) != null;
        changed |= connections.remove(id) != null;
        if (changed) revision++;
    }

    public static void updateMembers(int frequencyId, CompoundTag tag) {
        ListTag list = tag.getList("members", Tag.TAG_COMPOUND);
        List<CachedMember> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            FrequencyMember m = new FrequencyMember(list.getCompound(i));
            result.add(new CachedMember(m.getPlayerUUID(), m.getCachedName(), m.getAccessLevel()));
        }
        members.put(frequencyId, result);
        revision++;
    }

    public static void updateConnections(int frequencyId, CompoundTag tag) {
        ListTag list = tag.getList("connections", Tag.TAG_COMPOUND);
        List<CachedConnection> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            result.add(new CachedConnection(
                    e.getString("dim"),
                    net.minecraft.core.BlockPos.of(e.getLong("pos")),
                    e.getBoolean("controller"),
                    e.getBoolean("advanced"),
                    e.getBoolean("loaded"),
                    e.contains("name") ? e.getString("name") : "block.ae2lt.wireless_receiver"));
        }
        connections.put(frequencyId, result);
        revision++;
    }

    public static List<CachedConnection> getConnections(int frequencyId) {
        return connections.getOrDefault(frequencyId, List.of());
    }

    @Nullable
    public static CachedFrequency getFrequency(int id) {
        return cache.get(id);
    }

    public static Collection<CachedFrequency> getAllFrequencies() {
        return cache.values();
    }

    public static List<CachedFrequency> getAllFrequenciesSorted() {
        var list = new ArrayList<>(cache.values());
        list.sort((a, b) -> Integer.compare(a.id(), b.id()));
        return list;
    }

    public static List<CachedMember> getMembers(int frequencyId) {
        return members.getOrDefault(frequencyId, List.of());
    }

    public static void clear() {
        cache.clear();
        members.clear();
        connections.clear();
        revision++;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}

