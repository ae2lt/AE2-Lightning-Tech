package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.stacks.AEKey;
import java.util.Objects;
import java.util.UUID;

public record InventoryMaintenanceRule(
        UUID id,
        AEKey key,
        long lowerThreshold,
        long upperThreshold,
        long amountPerJob,
        boolean enabled,
        boolean replenishing,
        UUID activeCraftingId) {

    public InventoryMaintenanceRule {
        id = Objects.requireNonNull(id, "id");
        key = Objects.requireNonNull(key, "key");
        if (lowerThreshold < 0 || upperThreshold <= lowerThreshold) {
            throw new IllegalArgumentException("maintenance thresholds require 0 <= lower < upper");
        }
        if (amountPerJob <= 0) throw new IllegalArgumentException("amount per job must be positive");
    }

    public InventoryMaintenanceRule withRuntime(boolean newReplenishing, UUID craftingId) {
        return new InventoryMaintenanceRule(id, key, lowerThreshold, upperThreshold, amountPerJob,
                enabled, newReplenishing, craftingId);
    }

    public long nextRequestAmount(long currentStock) {
        return Math.max(0L, Math.min(amountPerJob, upperThreshold - Math.max(0L, currentStock)));
    }
}
