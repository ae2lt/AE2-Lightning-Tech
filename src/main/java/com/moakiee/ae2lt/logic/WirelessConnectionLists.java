package com.moakiee.ae2lt.logic;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class WirelessConnectionLists {
    public record PruneResult(int removed, int nextCursor) {
    }

    @FunctionalInterface
    public interface TagReader<T extends WirelessConnectionRef> {
        T read(CompoundTag tag);
    }

    private WirelessConnectionLists() {
    }

    public static boolean isLocalDimension(@Nullable Level level, ResourceKey<Level> dimension) {
        return level == null || level.dimension().equals(dimension);
    }

    public static <T extends WirelessConnectionRef> int indexOf(
            List<T> source,
            ResourceKey<Level> dimension,
            BlockPos pos) {
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).sameTarget(dimension, pos)) {
                return i;
            }
        }
        return -1;
    }

    public static <T extends WirelessConnectionRef> boolean addOrReplace(
            List<T> source,
            T connection,
            int maxConnections) {
        int index = indexOf(source, connection.dimension(), connection.pos());
        if (index >= 0) {
            source.set(index, connection);
            return true;
        }
        if (source.size() >= maxConnections) {
            return false;
        }
        source.add(connection);
        return true;
    }

    public static <T extends WirelessConnectionRef> ListTag writeTagList(List<T> connections) {
        var list = new ListTag();
        for (var connection : connections) {
            list.add(connection.toTag());
        }
        return list;
    }

    public static <T extends WirelessConnectionRef> void readTagList(
            CompoundTag data,
            String tagName,
            List<T> target,
            int maxConnections,
            TagReader<T> reader) {
        target.clear();
        if (!data.contains(tagName, Tag.TAG_LIST)) {
            return;
        }
        var list = data.getList(tagName, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && target.size() < maxConnections; i++) {
            target.add(reader.read(list.getCompound(i)));
        }
    }

    public static <T extends WirelessConnectionRef> PruneResult pruneInvalid(
            List<T> connections,
            int cursor,
            int maxChecks,
            ServerLevel hostLevel,
            BlockPos hostPos) {
        return pruneInvalidInternal(connections, cursor, maxChecks, hostLevel, hostPos, null);
    }

    public static <T extends WirelessConnectionRef> PruneResult pruneInvalid(
            List<T> connections,
            int cursor,
            int maxChecks,
            ServerLevel hostLevel,
            BlockPos hostPos,
            Predicate<T> removalGuard) {
        return pruneInvalidInternal(connections, cursor, maxChecks, hostLevel, hostPos, removalGuard);
    }

    private static <T extends WirelessConnectionRef> PruneResult pruneInvalidInternal(
            List<T> connections,
            int cursor,
            int maxChecks,
            ServerLevel hostLevel,
            BlockPos hostPos,
            @Nullable Predicate<T> removalGuard) {
        if (connections.isEmpty()) {
            return new PruneResult(0, 0);
        }
        if (maxChecks <= 0) {
            return new PruneResult(0, Math.min(Math.max(cursor, 0), connections.size() - 1));
        }

        int checksRemaining = Math.min(maxChecks, connections.size());
        int removed = 0;
        int index = Math.min(Math.max(cursor, 0), connections.size() - 1);

        while (checksRemaining-- > 0 && !connections.isEmpty()) {
            if (index >= connections.size()) {
                index = 0;
            }

            var connection = connections.get(index);
            if (WirelessConnectionValidator.validate(hostLevel, hostPos, connection)
                    == WirelessConnectionValidator.Status.REMOVE
                    && (removalGuard == null || removalGuard.test(connection))) {
                connections.remove(index);
                removed++;
            } else {
                index++;
            }
        }

        return new PruneResult(removed, connections.isEmpty() ? 0 : index % connections.size());
    }
}
