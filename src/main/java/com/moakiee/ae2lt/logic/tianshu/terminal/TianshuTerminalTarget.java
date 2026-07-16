package com.moakiee.ae2lt.logic.tianshu.terminal;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Server-side identity captured when a terminal menu is opened. */
public record TianshuTerminalTarget(
        UUID machineId, ResourceKey<Level> dimension, BlockPos controllerPos) {
    public TianshuTerminalTarget {
        if (machineId == null || dimension == null || controllerPos == null) {
            throw new IllegalArgumentException("A terminal target requires a complete machine identity");
        }
        controllerPos = controllerPos.immutable();
    }

    public static TianshuTerminalTarget from(TianshuSupercomputerPortBlockEntity port) {
        if (port == null || port.getLevel() == null || port.getControllerPos() == null) {
            throw new IllegalArgumentException("Cannot capture an unbound Tianshu port");
        }
        return new TianshuTerminalTarget(
                port.getTianshuId(), port.getLevel().dimension(), port.getControllerPos());
    }

    public boolean matches(TianshuSupercomputerPortBlockEntity port) {
        return port != null
                && port.getLevel() != null
                && machineId.equals(port.getTianshuId())
                && dimension.equals(port.getLevel().dimension())
                && controllerPos.equals(port.getControllerPos());
    }
}
