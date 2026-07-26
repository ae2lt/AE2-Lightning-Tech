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

    boolean shouldSkip(T target, P pattern, long gameTick) {
        var byPattern = penalties.get(target);
        if (byPattern == null) return false;
        var penalty = byPattern.get(pattern);
        return penalty != null && gameTick < penalty.retryAfter;
    }

    void recordRejection(T target, P pattern, long gameTick, boolean fast) {
        int initial = fast ? FAST_INITIAL_COOLDOWN : INITIAL_COOLDOWN;
        int max = fast ? FAST_MAX_COOLDOWN : MAX_COOLDOWN;
        var byPattern = penalties.computeIfAbsent(target, ignored -> new HashMap<>());
        var penalty = byPattern.get(pattern);
        int cooldown = penalty == null
                ? initial
                : Math.min(max, penalty.cooldown * 2);
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
