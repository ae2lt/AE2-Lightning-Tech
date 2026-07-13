package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.helpers.IPatternTerminalMenuHost;
import appeng.api.networking.security.IActionHost;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import java.util.Comparator;
import org.jetbrains.annotations.Nullable;

public interface TianshuPatternTerminalHost extends IPatternTerminalMenuHost, IActionHost {
    TianshuEncodingMode getTianshuEncodingMode();
    void setTianshuEncodingMode(TianshuEncodingMode mode);

    @Nullable
    default TianshuSupercomputerPortBlockEntity getSelectedTianshu() {
        var node = getActionableNode();
        var grid = node != null ? node.getGrid() : null;
        if (grid == null) return null;
        return grid.getActiveMachines(TianshuSupercomputerPortBlockEntity.class).stream()
                .filter(TianshuSupercomputerPortBlockEntity::isFormed)
                .sorted(Comparator.comparingLong(port -> port.getBlockPos().asLong()))
                .findFirst().orElse(null);
    }
}
