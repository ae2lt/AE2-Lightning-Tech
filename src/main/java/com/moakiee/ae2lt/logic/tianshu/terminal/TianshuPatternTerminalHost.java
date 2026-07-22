package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.helpers.IPatternTerminalMenuHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public interface TianshuPatternTerminalHost extends IPatternTerminalMenuHost, IActionHost {
    TianshuEncodingMode getTianshuEncodingMode();
    void setTianshuEncodingMode(TianshuEncodingMode mode);

    /**
     * Runs an AE2 menu synchronization while {@link #getInventory()} resolves to that menu's
     * captured storage view. AE2 invalidates a menu when the host returns a different inventory
     * instance, so hosts that create per-menu wrappers must keep the identity stable here.
     */
    default void runWithMenuInventory(MEStorage menuInventory, Runnable action) {
        action.run();
    }

    default List<TianshuSupercomputerPortBlockEntity> getAvailableTianshu() {
        var node = getActionableNode();
        var grid = node != null ? node.getGrid() : null;
        if (grid == null) return List.of();
        return grid.getActiveMachines(TianshuSupercomputerPortBlockEntity.class).stream()
                .filter(TianshuSupercomputerPortBlockEntity::isLinkActive)
                .sorted(Comparator
                        .comparing((TianshuSupercomputerPortBlockEntity port) ->
                                port.getLevel().dimension().location().toString())
                        .thenComparing(port -> port.getTianshuId().toString())
                        .thenComparingLong(port -> port.getControllerPos().asLong())
                        .thenComparingLong(port -> port.getBlockPos().asLong()))
                .toList();
    }

    /** Captures one machine once; menus must retain this identity for their whole lifetime. */
    @Nullable
    default TianshuTerminalTarget selectTianshuTarget() {
        var available = getAvailableTianshu();
        return available.isEmpty() ? null : TianshuTerminalTarget.from(available.getFirst());
    }

    /** Resolves a captured identity without falling back to another machine. */
    @Nullable
    default TianshuSupercomputerPortBlockEntity resolveTianshuTarget(
            @Nullable TianshuTerminalTarget target) {
        if (target == null) return null;
        for (var port : getAvailableTianshu()) {
            if (target.matches(port)) return port;
        }
        return null;
    }
}
