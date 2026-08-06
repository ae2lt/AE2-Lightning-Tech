package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Selects an additional runtime entrance for a frequency-linked physical
 * cluster. Logical frequency ownership remains cluster-scoped; these entrances
 * are transient channel paths and are never persisted as separate links.
 */
final class WirelessClusterEntrancePlanner {
    private WirelessClusterEntrancePlanner() {
    }

    /**
     * Starts from every device that still lacks a channel and walks only the
     * physical cluster. A carrying node is preferred because one new entrance
     * can then serve its local subtree. If the remaining device cannot carry
     * channels and has no unused relay nearby, linking that device directly is
     * the final fallback.
     */
    static @Nullable IGridNode findSupplementalEntrance(
            Set<IGridNode> cluster,
            Set<IGridNode> existingEntrances) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        var queue = new ArrayDeque<IGridNode>();
        IGridNode directFallback = null;

        for (var node : cluster) {
            if (!node.hasFlag(GridFlags.REQUIRE_CHANNEL) || node.meetsChannelRequirements()) {
                continue;
            }
            if (visited.add(node)) {
                queue.addLast(node);
            }
            if (directFallback == null && !existingEntrances.contains(node)) {
                directFallback = node;
            }
        }

        while (!queue.isEmpty()) {
            var node = queue.removeFirst();
            if (!existingEntrances.contains(node)
                    && !node.hasFlag(GridFlags.CANNOT_CARRY)
                    && !node.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                return node;
            }

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
                if (other != null && cluster.contains(other) && visited.add(other)) {
                    queue.addLast(other);
                }
            }
        }

        return directFallback;
    }
}
