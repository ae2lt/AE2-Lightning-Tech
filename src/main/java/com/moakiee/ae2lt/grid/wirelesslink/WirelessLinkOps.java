package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.GridConnection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public final class WirelessLinkOps {
    /**
     * Connections created by the overloaded-frequency system are logical bridges,
     * not edges of the target's physical ME cluster. Identity semantics are
     * intentional: AE2 connections do not expose a stable persistent id.
     */
    private static final Set<IGridConnection> WIRELESS_BRIDGES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private WirelessLinkOps() {
    }

    public static boolean hasLiveConnection(@Nullable IGridConnection connection, @Nullable IGridNode node) {
        if (connection == null || node == null) {
            return false;
        }
        for (var candidate : node.getConnections()) {
            if (candidate == connection) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConnectedTo(@Nullable IGridConnection connection, @Nullable IGridNode node, @Nullable IGridNode other) {
        return hasLiveConnection(connection, node)
                && !connection.isInWorld()
                && other != null
                && connection.getOtherSide(node) == other;
    }

    public static void destroy(@Nullable IGridConnection connection, @Nullable IGridNode node) {
        WIRELESS_BRIDGES.remove(connection);
        if (hasLiveConnection(connection, node) && !connection.isInWorld()) {
            connection.destroy();
        }
    }

    public static boolean isWirelessBridge(@Nullable IGridConnection connection) {
        return connection != null && WIRELESS_BRIDGES.contains(connection);
    }

    static void trackWirelessBridge(IGridConnection connection) {
        WIRELESS_BRIDGES.add(connection);
    }

    public static void clearWirelessBridgeTracking() {
        WIRELESS_BRIDGES.clear();
    }

    public static IGridConnection createVirtualConnection(IGridNode targetNode, IGridNode transmitterNode) {
        for (var connection : targetNode.getConnections()) {
            if (connection.getOtherSide(targetNode) == transmitterNode) {
                if (!connection.isInWorld()) {
                    trackWirelessBridge(connection);
                    return connection;
                }
                throw new IllegalStateException("A physical connection already exists between the target and transmitter.");
            }
        }
        var connection = GridConnection.create(targetNode, transmitterNode, null);
        trackWirelessBridge(connection);
        return connection;
    }
}
