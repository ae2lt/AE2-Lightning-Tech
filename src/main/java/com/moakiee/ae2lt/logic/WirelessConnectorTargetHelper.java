package com.moakiee.ae2lt.logic;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class WirelessConnectorTargetHelper {
    private WirelessConnectorTargetHelper() {
    }

    public static Set<BlockPos> collectTargets(Level level, BlockPos origin, boolean contiguous) {
        return collectTargets(level, origin, contiguous, Integer.MAX_VALUE);
    }

    public static Set<BlockPos> collectTargets(Level level, BlockPos origin, boolean contiguous, int maxTargets) {
        if (maxTargets <= 0) {
            return Set.of();
        }

        if (!contiguous) {
            return level.getBlockEntity(origin) != null ? Set.of(origin.immutable()) : Set.of();
        }

        if (!level.isLoaded(origin)) {
            return Set.of();
        }

        var originState = level.getBlockState(origin);
        var originBe = level.getBlockEntity(origin);
        if (originBe == null) {
            return Set.of();
        }

        var visited = new LinkedHashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(origin.immutable());

        while (!queue.isEmpty() && visited.size() < maxTargets) {
            var current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (visited.size() >= maxTargets) {
                break;
            }

            for (var direction : Direction.values()) {
                var next = current.relative(direction);
                if (visited.contains(next) || !level.isLoaded(next)) {
                    continue;
                }

                var nextBe = level.getBlockEntity(next);
                if (nextBe == null || nextBe.getClass() != originBe.getClass()) {
                    continue;
                }

                if (!level.getBlockState(next).is(originState.getBlock())) {
                    continue;
                }

                queue.addLast(next.immutable());
            }
        }

        return visited;
    }
}
