package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Immutable server-validated snapshot used by the maintenance editor sub-screens. */
public record MaintenanceEditorData(
        AEKey target,
        @Nullable UUID ruleId,
        long lowerThreshold,
        long upperThreshold,
        long amountPerJob,
        boolean enabled,
        InventoryMaintenanceStatus status,
        long currentStock,
        boolean craftable,
        boolean recoveryPage,
        List<TopologyEntry> topology,
        List<VariantEntry> variants) {
    public MaintenanceEditorData {
        topology = List.copyOf(topology);
        variants = List.copyOf(variants);
    }

    public record TopologyEntry(
            AEKey key, int depth, boolean craftable, long storedAmount,
            long globalReserve, ReservedStockMatchMode globalMode,
            long ruleReserve, ReservedStockMatchMode ruleMode) { }

    public record VariantEntry(AEKey key, long storedAmount, boolean craftable) { }
}
