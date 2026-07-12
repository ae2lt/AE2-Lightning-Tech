package com.moakiee.ae2lt.logic.tianshu.maintenance;

public record InventoryMaintenanceDecision(boolean replenishing, long requestAmount) {
    public static InventoryMaintenanceDecision evaluate(
            InventoryMaintenanceRule rule, long currentStock, boolean taskActive) {
        if (rule == null || !rule.enabled()) return new InventoryMaintenanceDecision(false, 0L);
        long stock = Math.max(0L, currentStock);
        boolean replenishing = rule.replenishing();
        if (stock >= rule.upperThreshold()) replenishing = false;
        else if (!replenishing && stock < rule.lowerThreshold()) replenishing = true;
        long amount = replenishing && !taskActive ? rule.nextRequestAmount(stock) : 0L;
        return new InventoryMaintenanceDecision(replenishing, amount);
    }
}
