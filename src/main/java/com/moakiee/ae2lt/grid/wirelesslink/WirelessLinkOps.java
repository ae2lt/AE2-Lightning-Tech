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

    private static @Nullable IGridConnection findConnection(IGridNode node, IGridNode other) {
        for (var connection : node.getConnections()) {
            if (connection.getOtherSide(node) == other && hasLiveConnection(connection, other)) {
                return connection;
            }
        }
        return null;
    }

    public static synchronized void destroy(@Nullable IGridConnection connection, @Nullable IGridNode node) {
        if (connection == null || !WIRELESS_BRIDGES.remove(connection)) {
            return;
        }
        if (hasLiveConnection(connection, node) && !connection.isInWorld()) {
            connection.destroy();
        }
    }

    public static synchronized boolean isWirelessBridge(@Nullable IGridConnection connection) {
        return connection != null && WIRELESS_BRIDGES.contains(connection);
    }

    static synchronized void trackWirelessBridge(IGridConnection connection) {
        WIRELESS_BRIDGES.add(connection);
    }

    public static synchronized void clearWirelessBridgeTracking() {
        WIRELESS_BRIDGES.clear();
    }

    public static synchronized IGridConnection createVirtualConnection(IGridNode targetNode, IGridNode transmitterNode) {
        var existing = findConnection(targetNode, transmitterNode);
        if (existing != null) {
            return reuseVirtualConnection(existing);
        }

        try {
            var connection = GridConnection.create(targetNode, transmitterNode, null);
            trackWirelessBridge(connection);
            return connection;
        } catch (IllegalStateException duplicateCandidate) {
            existing = findConnection(targetNode, transmitterNode);
            if (isDuplicateConnectionFailure(duplicateCandidate)
                    && existing != null
                    && !existing.isInWorld()) {
                return reuseVirtualConnection(existing);
            }
            throw duplicateCandidate;
        }
    }

    private static IGridConnection reuseVirtualConnection(IGridConnection connection) {
        if (connection.isInWorld()) {
            throw new IllegalStateException("A physical connection already exists between the target and transmitter.");
        }
        if (!isWirelessBridge(connection)) {
            throw new IllegalStateException(
                    "A virtual connection owned by another system already exists between the target and transmitter.");
        }
        return connection;
    }

    private static boolean isDuplicateConnectionFailure(IllegalStateException exception) {
        var message = exception.getMessage();
        return message != null && message.contains("already exists");
    }
}
