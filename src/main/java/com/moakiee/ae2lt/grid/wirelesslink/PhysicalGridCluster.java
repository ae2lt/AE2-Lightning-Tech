package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.networking.IGridNode;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Resolves one physical AE2 topology component while deliberately excluding
 * overloaded-frequency bridges.
 *
 * <p>Checking {@code IGridConnection.isInWorld()} is not sufficient: AE2 uses
 * non-world connections for legitimate cable-bus part-to-cable edges as well.
 * The frequency layer therefore explicitly tracks only the bridges it creates,
 * and every other AE2 connection remains part of the physical cluster.</p>
 */
final class PhysicalGridCluster {
    private PhysicalGridCluster() {
    }

    static Set<IGridNode> collect(IGridNode start) {
        var visited = newIdentityNodeSet();
        var queue = new ArrayDeque<IGridNode>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            var node = queue.removeFirst();
            for (var connection : node.getConnections()) {
                if (WirelessLinkOps.isWirelessBridge(connection)) {
                    continue;
                }
                IGridNode other;
                try {
                    other = connection.getOtherSide(node);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (other != null && visited.add(other)) {
                    queue.addLast(other);
                }
            }
        }
        return visited;
    }

    static Set<IGridNode> directNeighbours(Iterable<IGridNode> changedNodes) {
        var changed = newIdentityNodeSet();
        for (var node : changedNodes) {
            changed.add(node);
        }

        var neighbours = newIdentityNodeSet();
        for (var node : changed) {
            for (var connection : node.getConnections()) {
                if (WirelessLinkOps.isWirelessBridge(connection)) {
                    continue;
                }
                IGridNode other;
                try {
                    other = connection.getOtherSide(node);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (other != null && !changed.contains(other)) {
                    neighbours.add(other);
                }
            }
        }
        return neighbours;
    }

    static Set<IGridNode> newIdentityNodeSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
