package com.moakiee.ae2lt.me;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

/**
 * Lifecycle-safe accessors for borrowed AE2 grid nodes.
 *
 * <p>{@link IGridNode#getGrid()} deliberately throws when a node has not been initialized or has
 * already been destroyed. Callers that do not own the node, such as menus and cross-chunk wireless
 * registries, must therefore not treat {@code getGrid()} as a nullable readiness check.
 */
public final class GridNodeAccess {
    private GridNodeAccess() {
    }

    /**
     * Returns the node's grid while it is still structurally available, or {@code null} once the
     * borrowed node has left its lifecycle.
     */
    @Nullable
    public static IGrid getGridIfPresent(@Nullable IGridNode node) {
        if (node == null) {
            return null;
        }
        try {
            return node.getGrid();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    /**
     * Returns the grid only when the node is active. AE2's node implementation reports destroyed
     * nodes as inactive, so this follows the same guard used by AE2 terminal menus.
     */
    @Nullable
    public static IGrid getActiveGrid(@Nullable IGridNode node) {
        return node != null && node.isActive() ? getGridIfPresent(node) : null;
    }
}
