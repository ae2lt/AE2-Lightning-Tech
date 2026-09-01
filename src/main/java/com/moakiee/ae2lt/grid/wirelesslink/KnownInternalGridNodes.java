package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;

/**
 * Identifies grid nodes that an optional integration creates behind a single
 * world block. These nodes are part of the integration's channel accounting,
 * but they are not valid anchors for an overloaded-frequency entrance.
 */
final class KnownInternalGridNodes {
    static final String AE2CS_ENDER_BROADCASTER =
            "io.github.lounode.ae2cs.common.block.entity.EnderBroadcasterBlockEntity";

    private KnownInternalGridNodes() {
    }

    static boolean isSupplementalEntranceExcluded(IGridNode node) {
        if (node == null) {
            return false;
        }

        boolean requiresChannel;
        Object owner;
        try {
            requiresChannel = node.hasFlag(GridFlags.REQUIRE_CHANNEL);
            if (!requiresChannel) {
                return false;
            }
            owner = node.getOwner();
        } catch (RuntimeException ignored) {
            return false;
        }
        return owner != null && isSupplementalEntranceExcluded(owner.getClass().getName(), requiresChannel);
    }

    static boolean isSupplementalEntranceExcluded(String ownerClassName, boolean requiresChannel) {
        return requiresChannel && AE2CS_ENDER_BROADCASTER.equals(ownerClassName);
    }
}
