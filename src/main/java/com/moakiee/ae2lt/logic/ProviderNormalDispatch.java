package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Runtime scheduling owner for the provider's adjacent physical targets. */
final class ProviderNormalDispatch {
    private static final int INITIAL_COOLDOWN = 5;
    private static final int MAX_COOLDOWN = 40;

    private final Map<Direction, ProviderTarget> targets =
            new EnumMap<>(Direction.class);
    private final Map<ProviderTarget, Map<ProviderPatternKey, Penalty>> penalties =
            new HashMap<>();
    private final DueTaskQueue<TargetPatternKey<ProviderTarget>> expirations =
            new DueTaskQueue<>();
    private final DispatchFairnessScheduler<ProviderTarget, ProviderPatternKey> fairness =
            new DispatchFairnessScheduler<>();
    private final Map<ProviderTarget, Long> returnNextPoll = new HashMap<>();
    private final Map<ProviderTarget, Integer> returnBackoff = new HashMap<>();
    private int cursor;
    private long topologyVersion;
    private Set<ProviderTarget> activeTargets = Set.of();

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
                topologyVersion++;
                return new ProviderTarget(
                        level.dimension(), targetPos, targetFace);
            }
            return current;
        });
    }

    void restore(Direction pushDirection, ProviderTarget target) {
        var previous = targets.put(pushDirection, target);
        if (previous != target) {
            topologyVersion++;
        }
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

    DispatchFairnessScheduler<ProviderTarget, ProviderPatternKey>.Pass beginPass(
            ProviderPatternKey pattern,
            java.util.Collection<ProviderTarget> currentTargets,
            long gameTick) {
        return fairness.beginPass(
                pattern, currentTargets, topologyVersion, gameTick);
    }

    long dispatchBatch(
            ProviderPatternKey pattern,
            java.util.Collection<ProviderTarget> currentTargets,
            long maxCopies,
            long gameTick,
            BatchAttempt attempt) {
        var currentActive = Set.copyOf(currentTargets);
        if (!currentActive.equals(activeTargets)) {
            activeTargets = currentActive;
            topologyVersion++;
        }
        long remaining = maxCopies;
        try (var pass = beginPass(pattern, currentTargets, gameTick)) {
            int attemptBudget = pass.activeTargetsAtStart();
            for (int attempts = 0;
                 attempts < attemptBudget && remaining > 0L;
                 attempts++) {
                var target = pass.poll();
                if (target == null) {
                    break;
                }

                long retryAfter = retryAfter(target, pattern, gameTick);
                if (retryAfter > gameTick) {
                    pass.cooldown(target, retryAfter);
                    continue;
                }

                long share = Math.min(remaining, pass.allowance(target));
                if (share <= 0L) {
                    continue;
                }
                var result = attempt.push(target, share);
                if (result.ownedCopies <= 0L) {
                    if (result.globalAbort) {
                        break;
                    }
                    long due = recordRejection(target, pattern, gameTick);
                    pass.cooldown(target, due);
                    continue;
                }

                pass.success(target, result.ownedCopies);
                recordSuccess(target, pattern);
                remaining -= result.ownedCopies;
                if (result.stop || result.globalAbort) {
                    break;
                }
            }
        }
        return remaining;
    }

    long retryAfter(
            ProviderTarget target,
            ProviderPatternKey pattern,
            long gameTick) {
        purgeExpired(gameTick);
        var byPattern = penalties.get(target);
        if (byPattern == null) {
            return Long.MIN_VALUE;
        }
        var penalty = byPattern.get(pattern);
        return penalty == null ? Long.MIN_VALUE : penalty.retryAfter;
    }

    long recordRejection(
            ProviderTarget target,
            ProviderPatternKey pattern,
            long gameTick) {
        purgeExpired(gameTick);
        var byPattern = penalties.computeIfAbsent(
                target, ignored -> new HashMap<>());
        var previous = byPattern.get(pattern);
        int cooldown = previous == null
                ? INITIAL_COOLDOWN
                : Math.min(MAX_COOLDOWN, previous.cooldown * 2);
        long retryAfter = gameTick + cooldown;
        byPattern.put(pattern, new Penalty(retryAfter, cooldown));
        expirations.schedule(
                new TargetPatternKey<>(target, pattern), retryAfter);
        return retryAfter;
    }

    void recordSuccess(ProviderTarget target, ProviderPatternKey pattern) {
        var byPattern = penalties.get(target);
        if (byPattern == null) {
            return;
        }
        byPattern.remove(pattern);
        expirations.remove(new TargetPatternKey<>(target, pattern));
        if (byPattern.isEmpty()) {
            penalties.remove(target);
        }
    }

    void patternsChanged() {
        penalties.clear();
        expirations.clear();
        fairness.clear();
    }

    boolean returnDue(ProviderTarget target, long gameTick) {
        return gameTick >= returnNextPoll.getOrDefault(target, 0L);
    }

    void recordReturn(
            ProviderTarget target,
            long gameTick,
            boolean foundItems,
            int minimum,
            int maximum) {
        int interval = foundItems
                ? minimum
                : Math.min(
                        returnBackoff.getOrDefault(target, minimum) * 2,
                        maximum);
        returnBackoff.put(target, interval);
        returnNextPoll.put(target, gameTick + interval);
    }

    void resetReturns(
            Iterable<ProviderTarget> currentTargets,
            long gameTick,
            int minimum) {
        for (var target : currentTargets) {
            returnBackoff.put(target, minimum);
            returnNextPoll.put(target, gameTick + minimum);
        }
    }

    long nextReturnPoll(Iterable<ProviderTarget> currentTargets) {
        long next = Long.MAX_VALUE;
        for (var target : currentTargets) {
            next = Math.min(
                    next, returnNextPoll.getOrDefault(target, 0L));
        }
        return next;
    }

    void clearReturnSchedule() {
        returnNextPoll.clear();
        returnBackoff.clear();
    }

    void clearRuntimeState() {
        for (var target : targets.values()) {
            target.clearRuntimeState();
        }
        patternsChanged();
        clearReturnSchedule();
    }

    void clear() {
        clearRuntimeState();
        targets.clear();
        activeTargets = Set.of();
        cursor = 0;
        topologyVersion++;
    }

    private void purgeExpired(long gameTick) {
        TargetPatternKey<ProviderTarget> expired;
        while ((expired = expirations.pollDue(gameTick)) != null) {
            var byPattern = penalties.get(expired.target());
            if (byPattern == null) {
                continue;
            }
            var penalty = byPattern.get(expired.pattern());
            if (penalty == null || penalty.retryAfter > gameTick) {
                continue;
            }
            byPattern.remove(expired.pattern());
            if (byPattern.isEmpty()) {
                penalties.remove(expired.target());
            }
        }
    }

    private record Penalty(long retryAfter, int cooldown) {
    }

    @FunctionalInterface
    interface BatchAttempt {
        BatchAttemptResult push(ProviderTarget target, long maxCopies);
    }

    record BatchAttemptResult(
            long ownedCopies, boolean globalAbort, boolean stop) {
    }
}
