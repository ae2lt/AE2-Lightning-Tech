package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Runtime scheduling owner for the provider's adjacent physical targets. */
final class ProviderNormalDispatch {
    private final Map<Direction, ProviderTarget> targets =
            new EnumMap<>(Direction.class);
    private int cursor;

    ProviderTarget target(
            ServerLevel level,
            BlockPos providerPos,
            Direction pushDirection) {
        var targetPos = providerPos.relative(pushDirection);
        var targetFace = pushDirection.getOpposite();
        return targets.compute(pushDirection, (direction, current) -> {
            if (current == null
                    || !current.dimension().equals(level.dimension())
                    || !current.pos().equals(targetPos)
                    || current.boundFace() != targetFace) {
                return new ProviderTarget(
                        level.dimension(), targetPos, targetFace);
            }
            return current;
        });
    }

    void restore(Direction pushDirection, ProviderTarget target) {
        targets.put(pushDirection, target);
    }

    List<Direction> dispatchOrder(List<Direction> directions) {
        if (directions.isEmpty()) {
            return List.of();
        }
        int start = Math.floorMod(cursor, directions.size());
        var ordered = new ArrayList<Direction>(directions.size());
        for (int i = 0; i < directions.size(); i++) {
            ordered.add(directions.get((start + i) % directions.size()));
        }
        cursor = (cursor + 1) % directions.size();
        return ordered;
    }

    long dispatchBatch(
            java.util.Collection<ProviderTarget> currentTargets,
            long maxCopies,
            BatchAttempt attempt) {
        var orderedTargets = List.copyOf(currentTargets);
        long remaining = maxCopies;
        int targetsRemaining = orderedTargets.size();
        for (var target : orderedTargets) {
            if (remaining <= 0L) {
                break;
            }
            long share = ceilingDivide(remaining, targetsRemaining--);
            var result = attempt.push(target, share);
            if (result.ownedCopies > 0L) {
                remaining -= Math.min(remaining, result.ownedCopies);
            }
            if (result.stop || result.globalAbort) {
                break;
            }
        }
        return remaining;
    }

    void patternsChanged() {
        for (var target : targets.values()) {
            target.clearBatchHistory();
        }
    }

    void clearRuntimeState() {
        for (var target : targets.values()) {
            target.clearRuntimeState();
        }
        patternsChanged();
    }

    void clear() {
        clearRuntimeState();
        targets.clear();
        cursor = 0;
    }

    private static long ceilingDivide(long amount, int divisor) {
        return amount <= 0L || divisor <= 0
                ? 0L
                : 1L + (amount - 1L) / divisor;
    }

    @FunctionalInterface
    interface BatchAttempt {
        BatchAttemptResult push(ProviderTarget target, long maxCopies);
    }

    record BatchAttemptResult(
            long ownedCopies, boolean globalAbort, boolean stop) {
    }
}
