package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * Provider-wide output-return sweep with a bounded idle backoff.
 *
 * <p>Targets in one round are spread over the current interval. A completely
 * idle round doubles {@code 20 -> 40 -> 80 -> 128}; dispatch activity or a
 * readable matching output immediately restores the 20-tick interval.</p>
 */
final class ProviderReturnSweep {
    static final int ACTIVE_INTERVAL = 20;
    static final int MAX_INTERVAL = 128;

    private final DueTaskQueue<ProviderTarget> due = new DueTaskQueue<>();
    private List<ProviderTarget> targets = List.of();
    private Set<ProviderTarget> targetSet = Set.of();
    private final Set<ProviderTarget> remaining = new HashSet<>();
    private int interval = ACTIVE_INTERVAL;
    private boolean roundActive;

    void synchronize(
            List<? extends ProviderTarget> currentTargets,
            long gameTick) {
        var distinctSet = new LinkedHashSet<ProviderTarget>();
        distinctSet.addAll(currentTargets);
        var distinct = List.copyOf(distinctSet);
        if (distinct.equals(targets)) {
            return;
        }

        targets = distinct;
        targetSet = Set.copyOf(distinct);
        interval = ACTIVE_INTERVAL;
        roundActive = false;
        startRound(gameTick);
    }

    @Nullable
    ProviderTarget pollDue(long gameTick) {
        return due.pollDue(gameTick);
    }

    void recordPeriodic(
            ProviderTarget target,
            long gameTick,
            OutputReturnResult result) {
        if (!targetSet.contains(target) || !remaining.remove(target)) {
            return;
        }
        if (result.keepsSweepActive()) {
            activate(gameTick);
        }
        finishRoundIfComplete(gameTick);
    }

    /**
     * Counts a demand-driven pre-dispatch scan as activity and, when possible,
     * as this round's visit for the same target.
     */
    void recordDispatch(ProviderTarget target, long gameTick) {
        boolean wasIdle = interval != ACTIVE_INTERVAL;
        interval = ACTIVE_INTERVAL;
        roundActive = true;

        if (targetSet.contains(target) && remaining.remove(target)) {
            due.remove(target);
        }
        if (remaining.isEmpty()) {
            completeRound(gameTick);
        } else if (wasIdle) {
            // Accelerate only the unfinished part of the current round. Keeping
            // its cursor/remaining set prevents repeated dispatch from starving
            // targets near the end of a sweep.
            scheduleRemaining(gameTick);
        }
    }

    long nextDueTick() {
        return due.nextDueTick();
    }

    int interval() {
        return interval;
    }

    void clear() {
        due.clear();
        targets = List.of();
        targetSet = Set.of();
        remaining.clear();
        interval = ACTIVE_INTERVAL;
        roundActive = false;
    }

    private void activate(long gameTick) {
        if (interval == ACTIVE_INTERVAL) {
            roundActive = true;
            return;
        }
        interval = ACTIVE_INTERVAL;
        roundActive = true;
        scheduleRemaining(gameTick);
    }

    private void finishRoundIfComplete(long gameTick) {
        if (remaining.isEmpty()) {
            completeRound(gameTick);
        }
    }

    private void completeRound(long gameTick) {
        interval = roundActive
                ? ACTIVE_INTERVAL
                : Math.min(MAX_INTERVAL, interval * 2);
        roundActive = false;
        startRound(gameTick + 1L);
    }

    private void startRound(long firstTick) {
        due.clear();
        remaining.clear();
        remaining.addAll(targets);
        schedule(targets, firstTick);
    }

    private void scheduleRemaining(long firstTick) {
        due.clear();
        var orderedRemaining = new ArrayList<ProviderTarget>(remaining.size());
        for (var target : targets) {
            if (remaining.contains(target)) {
                orderedRemaining.add(target);
            }
        }
        schedule(orderedRemaining, firstTick);
    }

    private void schedule(List<ProviderTarget> scheduled, long firstTick) {
        int size = scheduled.size();
        if (size == 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            // End the round at interval - 1 instead of placing a one-target
            // round at offset zero. This keeps one idle target at the actual
            // 20/40/80/128-tick cadence while still distributing 1024 targets
            // as 51-52 visits per tick over a 20-tick round.
            long offset = ((long) (i + 1) * interval - 1L) / size;
            due.schedule(scheduled.get(i), firstTick + offset);
        }
    }
}
