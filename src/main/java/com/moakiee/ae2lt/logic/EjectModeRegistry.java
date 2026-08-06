package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.thunderbolt.api.eject.EjectCapabilityRegistry;

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

        private EjectCapabilityRegistry.Entry toThunderboltEntry() {
            return new EjectCapabilityRegistry.Entry(hostRef, ghostBE, hostDim, hostPos);
        }
    }

    public record DimPos(ResourceKey<Level> dimension, BlockPos pos) {}

    private EjectModeRegistry() {}

    public static void setBypass(boolean value) {
        EjectCapabilityRegistry.setBypass(value);
    }

    public static boolean isBypassed() {
        return EjectCapabilityRegistry.isBypassed();
    }

    public static boolean isEmpty() {
        return EjectCapabilityRegistry.isEmpty();
    }

    /** Lifecycle is now owned by Thunderbolt; retained only for old callers. */
    public static void onServerStart(MinecraftServer server) {
        EjectCapabilityRegistry.onServerStart(server);
    }

    /** Lifecycle is now owned by Thunderbolt; retained only for old callers. */
    public static void onServerStop() {
        EjectCapabilityRegistry.onServerStop();
    }

    public static void register(ResourceKey<Level> dim, long pos, Direction face, EjectEntry entry) {
        EjectCapabilityRegistry.register(dim, pos, face, entry.toThunderboltEntry());
    }

    public static void unregister(ResourceKey<Level> dim, long pos, Direction face) {
        EjectCapabilityRegistry.unregister(dim, pos, face);
    }

    @Nullable
    public static EjectEntry lookupByFace(ResourceKey<Level> dim, long pos, Direction face) {
        return fromThunderbolt(EjectCapabilityRegistry.lookupByFace(dim, pos, face));
    }

    @Nullable
    public static EjectEntry lookupAny(ResourceKey<Level> dim, long pos) {
        return fromThunderbolt(EjectCapabilityRegistry.lookupAny(dim, pos));
    }

    public static List<DimPos> unregisterAll(BlockEntity host, boolean persistToSavedData) {
        return EjectCapabilityRegistry.unregisterAll(host, persistToSavedData).stream()
                .map(pos -> new DimPos(pos.dimension(), pos.pos()))
                .toList();
    }

    @Nullable
    private static EjectEntry fromThunderbolt(@Nullable EjectCapabilityRegistry.Entry entry) {
        if (entry == null) return null;
        GhostOutputBlockEntity ghost;
        if (entry.ghostBlockEntity() instanceof GhostOutputBlockEntity legacyGhost) {
            ghost = legacyGhost;
        } else {
            ghost = new GhostOutputBlockEntity(entry.ghostBlockEntity().getBlockPos());
            if (entry.ghostBlockEntity().getLevel() != null) {
                ghost.setLevel(entry.ghostBlockEntity().getLevel());
            }
        }
        return new EjectEntry(
                entry.hostRef(), ghost, entry.hostDimension(), entry.hostPos());
    }
}
