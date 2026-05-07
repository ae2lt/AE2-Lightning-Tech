package com.moakiee.ae2lt.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.blockentity.networking.ControllerBlockEntity;

/**
 * Centralized owner checks for the overloaded-channel subsystem.
 * Any grid-node owner implementing {@link OverloadedGridNodeOwner} is
 * automatically granted elevated channel capacity by the AE2LT mixins.
 */
public final class OverloadedChannelOwnerHelper {
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final Set<String> LOGGED_OWNER_LOOKUP_FAILURES =
            Collections.synchronizedSet(new java.util.HashSet<>());

    private OverloadedChannelOwnerHelper() {
    }

    public static boolean is128ChannelOwner(@Nullable Object owner) {
        return owner instanceof OverloadedGridNodeOwner;
    }

    public static boolean is128ChannelConnection(@Nullable Object ownerA, @Nullable Object ownerB) {
        return is128ChannelOwner(ownerA) && is128ChannelOwner(ownerB);
    }

    public static int channelsPerController() {
        return AE2LTCommonConfig.overloadedControllerChannelsPerController();
    }

    public static int supplyPerController(int cableCapacityFactor) {
        long supply = (long) channelsPerController() * Math.max(1, cableCapacityFactor);
        return (int) Math.min(Integer.MAX_VALUE / 2, supply);
    }

    public static @Nullable Object tryGetOwner(IGridNode node) {
        try {
            return node.getOwner();
        } catch (RuntimeException exception) {
            String key = node.getClass().getName() + ":" + exception.getClass().getName();
            if (LOGGED_OWNER_LOOKUP_FAILURES.add(key)) {
                LOG.warn("AE2LT failed to read grid node owner from {}.", node.getClass().getName(), exception);
            }
            return null;
        }
    }


    /**
     * Returns ALL controller nodes in the grid, including subclasses.
     * AE2's {@code getMachineNodes(Class)} uses exact class matching
     * ({@code owner.getClass()}), so subclasses of {@code ControllerBlockEntity}
     * must be queried by their concrete class. This method scans all registered
     * machine classes and collects those assignable to {@code ControllerBlockEntity}.
     */
    public static List<IGridNode> getAllControllerNodes(IGrid grid) {
        List<IGridNode> all = new ArrayList<>();
        for (var clazz : grid.getMachineClasses()) {
            if (ControllerBlockEntity.class.isAssignableFrom(clazz)) {
                for (var node : grid.getMachineNodes(clazz)) {
                    all.add(node);
                }
            }
        }
        return all;
    }

    /**
     * @return total channel capacity for an overloaded-controller network,
     *         or 0 if no overloaded controllers are present / channel mode is INFINITE.
     */
    public static int calculateOverloadedNetworkCapacity(IGrid grid) {
        int overloadedCount = 0;
        for (var node : getAllControllerNodes(grid)) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) {
                overloadedCount++;
            }
        }
        if (overloadedCount == 0) {
            return 0;
        }

        var channelMode = grid.getPathingService().getChannelMode();
        if (channelMode == ChannelMode.INFINITE) {
            return 0;
        }

        long capacity = (long) channelsPerController() * overloadedCount * channelMode.getCableCapacityFactor();
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

}
