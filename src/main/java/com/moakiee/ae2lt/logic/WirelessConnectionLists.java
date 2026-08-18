package com.moakiee.ae2lt.logic;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** @deprecated Compatibility facade; collection algorithms now live in Thunderbolt. */
@Deprecated(forRemoval = false)
public final class WirelessConnectionLists {
    public record PruneResult(int removed, int nextCursor) {}

    @FunctionalInterface
    public interface TagReader<T extends WirelessConnectionRef> {
        T read(CompoundTag tag);
    }

    private WirelessConnectionLists() {}

    public static boolean isLocalDimension(Level level, ResourceKey<Level> dimension) {
        return com.moakiee.thunderbolt.api.wireless.WirelessConnectionLists
                .isLocalDimension(level, dimension);
    }

    public static <T extends WirelessConnectionRef> int indexOf(
            List<T> source, ResourceKey<Level> dimension, BlockPos pos) {
        return com.moakiee.thunderbolt.api.wireless.WirelessConnectionLists
                .indexOf(source, dimension, pos);
    }

    public static <T extends WirelessConnectionRef> boolean addOrReplace(
            List<T> source, T connection, int maxConnections) {
        return com.moakiee.thunderbolt.api.wireless.WirelessConnectionLists
                .addOrReplace(source, connection, maxConnections);
    }

    public static <T extends WirelessConnectionRef> ListTag writeTagList(List<T> connections) {
        return com.moakiee.thunderbolt.api.wireless.WirelessConnectionLists
                .writeTagList(connections);
    }

    public static <T extends WirelessConnectionRef> void readTagList(
            CompoundTag data,
            String tagName,
            List<T> target,
            int maxConnections,
            TagReader<T> reader) {
        com.moakiee.thunderbolt.api.wireless.WirelessConnectionLists.readTagList(
                data, tagName, target, maxConnections, reader::read);
    }

    public static <T extends WirelessConnectionRef> PruneResult pruneInvalid(
            List<T> connections,
            int cursor,
            int maxChecks,
            ServerLevel hostLevel,
            BlockPos hostPos) {
        return pruneInvalid(connections, cursor, maxChecks, hostLevel, hostPos, null);
    }

    public static <T extends WirelessConnectionRef> PruneResult pruneInvalid(
            List<T> connections,
            int cursor,
            int maxChecks,
            ServerLevel hostLevel,
            BlockPos hostPos,
            Predicate<T> removalGuard) {
        var result = com.moakiee.thunderbolt.api.wireless.WirelessConnectionLists.prune(
                connections,
                cursor,
                maxChecks,
                connection -> WirelessConnectionValidator.validate(hostLevel, hostPos, connection)
                        == WirelessConnectionValidator.Status.REMOVE,
                removalGuard);
        return new PruneResult(result.removed(), result.nextCursor());
    }
}
