package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.thunderbolt.core.planner.Sat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative implementation of the manual "refill seeds" actions. */
public final class TianshuSeedRefillService {
    public static RefillResult refillPattern(
            TianshuSupercomputerPortBlockEntity target, UUID patternId) {
        if (target == null || patternId == null) return RefillResult.UNAVAILABLE;
        var repository = target.getClosedLoopPatternRepository();
        if (repository == null) return RefillResult.UNAVAILABLE;
        var payload = repository.get(patternId);
        if (payload == null) return RefillResult.UNAVAILABLE;
        return refill(target, requirements(payload));
    }

    public static RefillResult refillAll(TianshuSupercomputerPortBlockEntity target) {
        if (target == null) return RefillResult.UNAVAILABLE;
        var repository = target.getClosedLoopPatternRepository();
        if (repository == null) return RefillResult.UNAVAILABLE;
        var required = new LinkedHashMap<AEKey, Long>();
        for (var payload : repository.patterns()) {
            if (!payload.enabled()) continue;
            for (var entry : requirements(payload).entrySet()) {
                // The storage is shared and seeds are allocated to jobs only when they start.
                // A seed already present for one pattern can also start another pattern later.
                required.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }
        return refill(target, required);
    }

    public static Map<AEKey, Long> requirements(ClosedLoopPatternPayload payload) {
        var result = new LinkedHashMap<AEKey, Long>();
        if (payload != null) {
            for (var seed : payload.seeds()) {
                long perTask = Sat.mul(seed.amount(), payload.executionSeedMultiplier());
                result.merge(seed.what(), Sat.mul(perTask, payload.storedTaskMultiplier()), Sat::add);
            }
        }
        return Map.copyOf(result);
    }

    private static RefillResult refill(
            TianshuSupercomputerPortBlockEntity target, Map<AEKey, Long> required) {
        var grid = target.getGrid();
        if (!target.isFormed() || grid == null
                || !target.getFunctionProfile().supportsClosedLoopSeeds()) {
            return RefillResult.UNAVAILABLE;
        }
        var moved = new LinkedHashMap<AEKey, Long>();
        var missing = new LinkedHashMap<AEKey, Long>();
        var network = grid.getStorageService().getInventory();
        for (var entry : required.entrySet()) {
            long need = Math.max(0L, entry.getValue() - target.reusableSeedAmount(entry.getKey()));
            if (need <= 0) continue;
            long canStore = target.insertReusableSeed(entry.getKey(), need, Actionable.SIMULATE);
            long extracted = canStore > 0
                    ? network.extract(entry.getKey(), canStore, Actionable.MODULATE, target.getActionSource())
                    : 0L;
            long inserted = extracted > 0
                    ? target.insertReusableSeed(entry.getKey(), extracted, Actionable.MODULATE)
                    : 0L;
            if (inserted < extracted) {
                network.insert(entry.getKey(), extracted - inserted, Actionable.MODULATE,
                        target.getActionSource());
            }
            if (inserted > 0) moved.put(entry.getKey(), inserted);
            long left = need - inserted;
            if (left > 0) missing.put(entry.getKey(), left);
        }
        return new RefillResult(true, Map.copyOf(moved), Map.copyOf(missing));
    }

    public record RefillResult(
            boolean available,
            Map<AEKey, Long> moved,
            Map<AEKey, Long> missing) {
        private static final RefillResult UNAVAILABLE =
                new RefillResult(false, Map.of(), Map.of());

        public boolean complete() {
            return available && missing.isEmpty();
        }
    }

    private TianshuSeedRefillService() { }
}
