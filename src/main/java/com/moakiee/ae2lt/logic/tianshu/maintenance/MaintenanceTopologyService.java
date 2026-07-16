package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Builds the target-first dependency order used by reserve-stock configuration. */
public final class MaintenanceTopologyService {
    private static final int MAX_DEPTH = 32;
    /** One extra key is retained as an overflow sentinel for the packet boundary. */
    private static final int MAX_KEYS = InventoryMaintenanceLimits.MAX_ENTRIES + 1;

    public static List<Entry> build(ICraftingService crafting, AEKey target) {
        if (crafting == null) return List.of();
        return build(crafting::getCraftingFor, target);
    }

    public static List<Entry> build(
            Function<AEKey, ? extends Iterable<IPatternDetails>> patternsFor,
            AEKey target) {
        if (patternsFor == null || target == null) return List.of();
        var depths = new LinkedHashMap<AEKey, Integer>();
        var craftable = new LinkedHashMap<AEKey, Boolean>();
        var queue = new ArrayDeque<AEKey>();
        depths.put(target, 0);
        queue.add(target);

        while (!queue.isEmpty() && depths.size() < MAX_KEYS) {
            var key = queue.removeFirst();
            int depth = depths.get(key);
            if (depth >= MAX_DEPTH) continue;
            var patterns = patternsFor.apply(key);
            boolean hasPattern = false;
            if (patterns != null) {
                for (var pattern : patterns) {
                    if (!produces(pattern, key)) continue;
                    hasPattern = true;
                    for (var input : pattern.getInputs()) {
                        for (var possible : input.getPossibleInputs()) {
                            var inputKey = possible.what();
                            if (inputKey == null || depths.containsKey(inputKey)) continue;
                            depths.put(inputKey, depth + 1);
                            queue.addLast(inputKey);
                            if (depths.size() >= MAX_KEYS) break;
                        }
                        if (depths.size() >= MAX_KEYS) break;
                    }
                    if (depths.size() >= MAX_KEYS) break;
                }
            }
            craftable.put(key, hasPattern);
        }

        var result = new ArrayList<Entry>(depths.size());
        for (var entry : depths.entrySet()) {
            result.add(new Entry(entry.getKey(), entry.getValue(),
                    craftable.getOrDefault(entry.getKey(), false)));
        }
        return List.copyOf(result);
    }

    private static boolean produces(IPatternDetails pattern, AEKey key) {
        if (pattern == null) return false;
        for (var output : pattern.getOutputs()) {
            if (output != null && output.amount() > 0 && key.equals(output.what())) return true;
        }
        return false;
    }

    public record Entry(AEKey key, int depth, boolean craftable) { }

    private MaintenanceTopologyService() { }
}
