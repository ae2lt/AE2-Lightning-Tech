package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.thunderbolt.api.eject.EjectCapabilityRegistry;
import com.moakiee.thunderbolt.api.eject.EjectEndpoint;
import com.moakiee.thunderbolt.api.eject.EjectOfflinePolicy;

/**
 * @deprecated Use {@link EjectCapabilityRegistry}. Kept as a binary/source compatibility bridge for
 * third-party AE2LT integrations; Thunderbolt owns the actual registry and mixins.
 */
@Deprecated(forRemoval = false)
public final class EjectModeRegistry {
    public record EjectEntry(
            @Nullable WeakReference<? extends BlockEntity> hostRef,
            GhostOutputBlockEntity ghostBE,
            ResourceKey<Level> hostDim,
            BlockPos hostPos) {
        @Nullable
        public BlockEntity getHost() {
            return hostRef != null ? hostRef.get() : null;
        }

    }

    public record DimPos(ResourceKey<Level> dimension, BlockPos pos) {}

    private record EndpointKey(ResourceKey<Level> dimension, long pos, Direction face) {}

    private static final Map<EndpointKey, List<EjectEntry>> LEGACY_ENTRIES = new HashMap<>();

    private EjectModeRegistry() {}

    public static void setBypass(boolean value) {
        EjectCapabilityRegistry.setBypass(value);
    }

    public static boolean isBypassed() {
        return EjectCapabilityRegistry.isBypassed();
    }

    public static boolean isEmpty() {
        return LEGACY_ENTRIES.isEmpty();
    }

    /** Lifecycle is now owned by Thunderbolt; retained only for old callers. */
    public static void onServerStart(MinecraftServer server) {
        // Thunderbolt owns server lifecycle and persistence.
    }

    /** Lifecycle is now owned by Thunderbolt; retained only for old callers. */
    public static void onServerStop() {
        LEGACY_ENTRIES.clear();
    }

    public static void register(ResourceKey<Level> dim, long pos, Direction face, EjectEntry entry) {
        var endpoint = new EjectEndpoint(
                dim, BlockPos.of(pos), face, entry.hostDim(), entry.hostPos(),
                EjectOfflinePolicy.REJECT);
        EjectCapabilityRegistry.register(endpoint, (server, ignored) -> {
            var referenced = entry.getHost();
            if (referenced != null) return referenced;
            var level = server.getLevel(entry.hostDim());
            return level != null ? level.getBlockEntity(entry.hostPos()) : null;
        });
        LEGACY_ENTRIES.computeIfAbsent(new EndpointKey(dim, pos, face), ignored -> new ArrayList<>())
                .add(entry);
    }

    public static void unregister(ResourceKey<Level> dim, long pos, Direction face) {
        EjectCapabilityRegistry.unregister(dim, BlockPos.of(pos), face);
        LEGACY_ENTRIES.remove(new EndpointKey(dim, pos, face));
    }

    @Nullable
    public static EjectEntry lookupByFace(ResourceKey<Level> dim, long pos, Direction face) {
        var entries = LEGACY_ENTRIES.get(new EndpointKey(dim, pos, face));
        if (entries == null || entries.isEmpty()) return null;
        for (var entry : entries) if (entry.getHost() != null) return entry;
        return entries.get(0);
    }

    @Nullable
    public static EjectEntry lookupAny(ResourceKey<Level> dim, long pos) {
        EjectEntry fallback = null;
        for (var entry : LEGACY_ENTRIES.entrySet()) {
            if (!entry.getKey().dimension().equals(dim) || entry.getKey().pos() != pos) continue;
            for (var candidate : entry.getValue()) {
                if (candidate.getHost() != null) return candidate;
                if (fallback == null) fallback = candidate;
            }
        }
        return fallback;
    }

    public static List<DimPos> unregisterAll(BlockEntity host, boolean persistToSavedData) {
        var hostLevel = host.getLevel();
        var hostDimension = hostLevel != null ? hostLevel.dimension() : null;
        var removed = EjectCapabilityRegistry.unregisterAll(host).stream()
                .map(pos -> new DimPos(pos.dimension(), pos.pos()))
                .toList();
        LEGACY_ENTRIES.entrySet().removeIf(mapEntry -> mapEntry.getValue().removeIf(entry -> {
            var referenced = entry.getHost();
            return referenced == host || (hostDimension != null && entry.hostDim().equals(hostDimension)
                    && entry.hostPos().equals(host.getBlockPos()));
        }) || mapEntry.getValue().isEmpty());
        return removed;
    }
}
