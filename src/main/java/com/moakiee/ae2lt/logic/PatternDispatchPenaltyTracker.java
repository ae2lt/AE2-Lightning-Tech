package com.moakiee.ae2lt.logic;

import java.util.HashMap;
import java.util.Map;

/** Short-lived target rejection state scoped to a specific processing pattern. */
final class PatternDispatchPenaltyTracker<T, P> {
    private static final int INITIAL_COOLDOWN = 5;
    private static final int MAX_COOLDOWN = 40;
    // FAST speed mode: retry rejected (target, pattern) pairs much sooner
    private static final int FAST_INITIAL_COOLDOWN = 2;
    private static final int FAST_MAX_COOLDOWN = 10;

    private final Map<T, Map<P, Penalty>> penalties = new HashMap<>();
    private final DueTaskQueue<TargetPattern<T, P>> expirations = new DueTaskQueue<>();

    boolean shouldSkip(T target, P pattern, long gameTick) {
        return retryAfter(target, pattern, gameTick) > gameTick;
    }

    long retryAfter(T target, P pattern, long gameTick) {
        purgeExpired(gameTick);
        var byPattern = penalties.get(target);
        if (byPattern == null) return Long.MIN_VALUE;
        var penalty = byPattern.get(pattern);
        return penalty == null ? Long.MIN_VALUE : penalty.retryAfter;
    }

    long recordRejection(T target, P pattern, long gameTick, boolean fast) {
        purgeExpired(gameTick);
        int initial = fast ? FAST_INITIAL_COOLDOWN : INITIAL_COOLDOWN;
        int max = fast ? FAST_MAX_COOLDOWN : MAX_COOLDOWN;
        var byPattern = penalties.computeIfAbsent(target, ignored -> new HashMap<>());
        var penalty = byPattern.get(pattern);
        int cooldown = penalty == null
                ? initial
                : Math.min(max, penalty.cooldown * 2);
        long retryAfter = gameTick + cooldown;
        byPattern.put(pattern, new Penalty(retryAfter, cooldown));
        expirations.schedule(new TargetPattern<>(target, pattern), retryAfter);
        return retryAfter;
    }

    void recordSuccess(T target, P pattern) {
        var byPattern = penalties.get(target);
        if (byPattern == null) return;
        byPattern.remove(pattern);
        expirations.remove(new TargetPattern<>(target, pattern));
        if (byPattern.isEmpty()) penalties.remove(target);
    }

    void removeTarget(T target) {
        var byPattern = penalties.remove(target);
        if (byPattern == null) return;
        for (var pattern : byPattern.keySet()) {
            expirations.remove(new TargetPattern<>(target, pattern));
        }
    }

    void clear() {
        penalties.clear();
        expirations.clear();
    }

    int trackedPenaltyCount() {
        int count = 0;
        for (var byPattern : penalties.values()) {
            count += byPattern.size();
        }
        return count;
    }

    private void purgeExpired(long gameTick) {
        TargetPattern<T, P> expired;
        while ((expired = expirations.pollDue(gameTick)) != null) {
            var byPattern = penalties.get(expired.target());
            if (byPattern == null) continue;
            var penalty = byPattern.get(expired.pattern());
            if (penalty == null || penalty.retryAfter() > gameTick) continue;
            byPattern.remove(expired.pattern());
            if (byPattern.isEmpty()) {
                penalties.remove(expired.target());
            }
        }
    }

    private record TargetPattern<T, P>(T target, P pattern) {
    }

    private record Penalty(long retryAfter, int cooldown) {
    }
}
