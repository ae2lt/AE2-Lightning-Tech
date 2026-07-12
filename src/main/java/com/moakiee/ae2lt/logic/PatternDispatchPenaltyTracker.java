package com.moakiee.ae2lt.logic;

import java.util.HashMap;
import java.util.Map;

/** Short-lived target rejection state scoped to a specific processing pattern. */
final class PatternDispatchPenaltyTracker<T, P> {
    private static final int INITIAL_COOLDOWN = 5;
    private static final int MAX_COOLDOWN = 40;

    private final Map<T, Map<P, Penalty>> penalties = new HashMap<>();

    boolean shouldSkip(T target, P pattern, long gameTick) {
        var byPattern = penalties.get(target);
        if (byPattern == null) return false;
        var penalty = byPattern.get(pattern);
        return penalty != null && gameTick < penalty.retryAfter;
    }

    void recordRejection(T target, P pattern, long gameTick) {
        var byPattern = penalties.computeIfAbsent(target, ignored -> new HashMap<>());
        var penalty = byPattern.get(pattern);
        int cooldown = penalty == null
                ? INITIAL_COOLDOWN
                : Math.min(MAX_COOLDOWN, penalty.cooldown * 2);
        byPattern.put(pattern, new Penalty(gameTick + cooldown, cooldown));
    }

    void recordSuccess(T target, P pattern) {
        var byPattern = penalties.get(target);
        if (byPattern == null) return;
        byPattern.remove(pattern);
        if (byPattern.isEmpty()) penalties.remove(target);
    }

    void removeTarget(T target) {
        penalties.remove(target);
    }

    void clear() {
        penalties.clear();
    }

    private record Penalty(long retryAfter, int cooldown) {
    }
}
