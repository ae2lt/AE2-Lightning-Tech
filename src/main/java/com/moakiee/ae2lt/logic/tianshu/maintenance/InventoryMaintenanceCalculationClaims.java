package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Network-wide, expiring claim that prevents duplicate calculations for one exact target key. */
final class InventoryMaintenanceCalculationClaims {
    private static final long LEASE_TICKS = 1_200L;
    private static final Map<IGrid, Map<AEKey, Claim>> CLAIMS = new WeakHashMap<>();

    static synchronized boolean tryClaim(IGrid grid, AEKey key, UUID owner, long now) {
        if (grid == null || key == null || owner == null) return false;
        var byKey = CLAIMS.computeIfAbsent(grid, ignored -> new HashMap<>());
        var existing = byKey.get(key);
        if (existing != null && existing.expiresAt() > now && !existing.owner().equals(owner)) {
            return false;
        }
        byKey.put(key, new Claim(owner, saturatingAdd(now, LEASE_TICKS)));
        return true;
    }

    static synchronized boolean claimedByOther(
            IGrid grid, AEKey key, UUID owner, long now) {
        var byKey = CLAIMS.get(grid);
        if (byKey == null) return false;
        var claim = byKey.get(key);
        if (claim == null) return false;
        if (claim.expiresAt() <= now) {
            byKey.remove(key);
            if (byKey.isEmpty()) CLAIMS.remove(grid);
            return false;
        }
        return !claim.owner().equals(owner);
    }

    static synchronized void release(IGrid grid, AEKey key, UUID owner) {
        var byKey = CLAIMS.get(grid);
        if (byKey == null) return;
        var claim = byKey.get(key);
        if (claim != null && claim.owner().equals(owner)) byKey.remove(key);
        if (byKey.isEmpty()) CLAIMS.remove(grid);
    }

    private static long saturatingAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record Claim(UUID owner, long expiresAt) { }
    private InventoryMaintenanceCalculationClaims() { }
}
