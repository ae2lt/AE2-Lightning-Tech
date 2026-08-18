package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;

/**
 * Global static registry mapping interception positions ({@code M.relative(F)})
 * to eject-mode handler entries.
 * <p>
 * Three-level Map: {@code dim -> pos(long) -> EnumMap<Direction, EjectEntry>}
 * <p>
 * The inner map uses fastutil {@link Long2ObjectOpenHashMap} to avoid boxing
 * the {@code long} position key on every lookup.
 */
public final class EjectModeRegistry {

    public record EjectEntry(
            @Nullable WeakReference<? extends BlockEntity> hostRef,
            GhostOutputBlockEntity ghostBE,
            ResourceKey<Level> hostDim,
            BlockPos hostPos
    ) {
        @Nullable
        public BlockEntity getHost() {
            return hostRef != null ? hostRef.get() : null;
        }
    }

    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<EnumMap<Direction, List<EjectEntry>>>>
            registrations = new IdentityHashMap<>();

    @Nullable
    private static EjectModeSavedData savedData;

    private static final ThreadLocal<Integer> bypassDepth = ThreadLocal.withInitial(() -> 0);

    public static void setBypass(boolean value) {
        if (value) {
            bypassDepth.set(bypassDepth.get() + 1);
            return;
        }

        int current = bypassDepth.get();
        if (current <= 1) {
            bypassDepth.remove();
        } else {
            bypassDepth.set(current - 1);
        }
    }

    public static boolean isBypassed() {
        return bypassDepth.get() > 0;
    }

    private EjectModeRegistry() {}

    // ---- Server lifecycle --------------------------------------------------

    public static void onServerStart(MinecraftServer server) {
        savedData = EjectModeSavedData.get(server);
        registrations.clear();
        bypassDepth.remove();

        for (var pe : savedData.getAll()) {
            var ghostBE = new GhostOutputBlockEntity(pe.interceptPos());
            var targetLevel = server.getLevel(pe.interceptDim());
            if (targetLevel != null) {
                ghostBE.setLevel(targetLevel);
            }

            var entry = new EjectEntry(
                    null,
                    ghostBE,
                    pe.hostDim(),
                    pe.hostPos()
            );
            addToMap(pe.interceptDim(), pe.interceptPos().asLong(), pe.interceptFace(), entry);
        }
    }

    public static void onServerStop() {
        savedData = null;
        registrations.clear();
        bypassDepth.remove();
    }

    // ---- Registration ------------------------------------------------------

    public static void register(ResourceKey<Level> dim, long posLong, Direction face, EjectEntry entry) {
        addToMap(dim, posLong, face, entry);

        if (savedData != null) {
            savedData.add(new EjectModeSavedData.PersistentReg(
                    dim,
                    BlockPos.of(posLong),
                    face,
                    entry.hostDim(),
                    entry.hostPos()
            ));
        }
    }

    public static void unregister(ResourceKey<Level> dim, long posLong, Direction face) {
        var dimMap = registrations.get(dim);
        if (dimMap == null) return;
        var faceMap = dimMap.get(posLong);
        if (faceMap == null) return;
        faceMap.remove(face);
        if (faceMap.isEmpty()) {
            dimMap.remove(posLong);
            if (dimMap.isEmpty()) {
                registrations.remove(dim);
            }
        }

        if (savedData != null) {
            savedData.removeByIntercept(dim, BlockPos.of(posLong), face);
        }
    }

    // ---- Lookup ------------------------------------------------------------

    @Nullable
    public static EjectEntry lookupByFace(ResourceKey<Level> dim, long posLong, Direction face) {
        var dimMap = registrations.get(dim);
        if (dimMap == null) return null;
        var faceMap = dimMap.get(posLong);
        if (faceMap == null) return null;
        var list = faceMap.get(face);
        if (list == null) return null;
        EjectEntry fallback = null;
        for (var entry : list) {
            if (entry.getHost() != null) return entry;
            if (fallback == null) fallback = entry;
        }
        return fallback;
    }

    @Nullable
    public static EjectEntry lookupAny(ResourceKey<Level> dim, long posLong) {
        var dimMap = registrations.get(dim);
        if (dimMap == null) return null;
        var faceMap = dimMap.get(posLong);
        if (faceMap == null) return null;
        EjectEntry fallback = null;
        for (var list : faceMap.values()) {
            for (var entry : list) {
                if (entry.getHost() != null) return entry;
                if (fallback == null) fallback = entry;
            }
        }
        return fallback;
    }

    public record DimPos(ResourceKey<Level> dimension, BlockPos pos) {}

    public static List<DimPos> unregisterAll(
            BlockEntity host,
            boolean persistToSavedData) {

        var hostLevel = host.getLevel();
        ResourceKey<Level> hostDim = hostLevel != null
                ? hostLevel.dimension() : null;
        BlockPos hostPos = host.getBlockPos();

        var removed = new ArrayList<DimPos>();
        for (var dimIt = registrations.entrySet().iterator(); dimIt.hasNext(); ) {
            var dimEntry = dimIt.next();
            var dim = dimEntry.getKey();
            var dimMap = dimEntry.getValue();
            var posIt = dimMap.long2ObjectEntrySet().iterator();
            while (posIt.hasNext()) {
                var posEntry = posIt.next();
                var faceMap = posEntry.getValue();
                boolean any = false;
                for (var faceIt = faceMap.entrySet().iterator(); faceIt.hasNext(); ) {
                    var faceEntry = faceIt.next();
                    var list = faceEntry.getValue();
                    boolean changed = list.removeIf(e -> matchesHost(e, host, hostDim, hostPos));
                    if (changed) any = true;
                    if (list.isEmpty()) faceIt.remove();
                }
                if (any) {
                    removed.add(new DimPos(dim, BlockPos.of(posEntry.getLongKey())));
                }
                if (faceMap.isEmpty()) posIt.remove();
            }
            if (dimMap.isEmpty()) dimIt.remove();
        }

        if (persistToSavedData && savedData != null && hostDim != null) {
            savedData.removeByHost(hostDim, hostPos);
        }

        return removed;
    }

    // ---- Internals ---------------------------------------------------------

    private static void addToMap(ResourceKey<Level> dim, long posLong, Direction face, EjectEntry entry) {
        registrations
                .computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(posLong, k -> new EnumMap<>(Direction.class))
                .computeIfAbsent(face, k -> new ArrayList<>())
                .add(entry);
    }

    private static boolean matchesHost(
            EjectEntry e,
            BlockEntity host,
            @Nullable ResourceKey<Level> hostDim,
            BlockPos hostPos) {
        var ref = e.getHost();
        if (ref == host) return true;
        if (ref == null && hostDim != null) {
            return e.hostDim().equals(hostDim)
                    && e.hostPos().equals(hostPos);
        }
        return false;
    }
}
