package com.moakiee.ae2lt.logic.terminal;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Connects one physical multiblock pattern warehouse to its port node.
 *
 * <p>The child node is deliberately not an in-world node: the warehouse is part of the
 * multiblock, not another cable endpoint. It only makes the warehouse owner independently
 * discoverable through {@code IGrid#getActiveMachines}, while the port remains the sole
 * channel-consuming network link.
 */
public final class InternalPatternContainerLink {
    private static final IGridNodeListener<BlockEntity> NODE_LISTENER =
            (owner, node) -> {
                // The internal node has no persistent state. Its connection is reconstructed
                // from the validated multiblock binding.
            };

    private final BlockEntity owner;
    private final ItemLike visualRepresentation;
    private IManagedGridNode managedNode;
    private IGridNode linkedPortNode;
    private IGridConnection connection;

    public InternalPatternContainerLink(BlockEntity owner, ItemLike visualRepresentation) {
        this.owner = owner;
        this.visualRepresentation = visualRepresentation;
    }

    public void bind(IManagedGridNode portNode) {
        var level = owner.getLevel();
        var targetNode = portNode != null && portNode.isReady() ? portNode.getNode() : null;
        if (level == null || level.isClientSide || targetNode == null) {
            return;
        }
        if (connection != null && linkedPortNode == targetNode
                && managedNode != null && managedNode.isReady()) {
            return;
        }

        disconnect();
        managedNode = GridHelper.createManagedNode(owner, NODE_LISTENER)
                .setInWorldNode(false)
                .setVisualRepresentation(visualRepresentation)
                .setIdlePowerUsage(0.0D);
        managedNode.create(level, owner.getBlockPos());
        connection = GridHelper.createConnection(managedNode.getNode(), targetNode);
        linkedPortNode = targetNode;
    }

    public void disconnect() {
        if (connection != null) {
            connection.destroy();
            connection = null;
        }
        if (managedNode != null) {
            managedNode.destroy();
            managedNode = null;
        }
        linkedPortNode = null;
    }

    public IGrid getGrid() {
        return managedNode != null ? managedNode.getGrid() : null;
    }

    public boolean isActive() {
        return connection != null && managedNode != null && managedNode.isActive();
    }
}
