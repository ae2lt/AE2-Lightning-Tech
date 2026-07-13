package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Network-wide lifecycle claim that prevents duplicate calculations for one exact target key.
 *
 * <p>The owner releases the claim when its calculation completes or is cancelled, just like a
 * crafting requester owns its links until their lifecycle ends. A time-based lease is deliberately
 * avoided: large crafting graphs may legitimately take longer than an arbitrary timeout.
 */
final class InventoryMaintenanceCalculationClaims {
    private static final Map<IGrid, Map<AEKey, UUID>> CLAIMS = new WeakHashMap<>();

    static synchronized boolean tryClaim(IGrid grid, AEKey key, UUID owner) {
        if (grid == null || key == null || owner == null) return false;
        var byKey = CLAIMS.computeIfAbsent(grid, ignored -> new HashMap<>());
        var existing = byKey.get(key);
        if (existing != null && !existing.equals(owner)) {
            return false;
        }
        byKey.put(key, owner);
        return true;
    }

    static synchronized boolean claimedByOther(IGrid grid, AEKey key, UUID owner) {
        var byKey = CLAIMS.get(grid);
        if (byKey == null) return false;
        var claim = byKey.get(key);
        if (claim == null) return false;
        return !claim.equals(owner);
    }

    static synchronized void release(IGrid grid, AEKey key, UUID owner) {
        var byKey = CLAIMS.get(grid);
        if (byKey == null) return;
        var claim = byKey.get(key);
        if (claim != null && claim.equals(owner)) byKey.remove(key);
        if (byKey.isEmpty()) CLAIMS.remove(grid);
    }
    private InventoryMaintenanceCalculationClaims() { }
}
