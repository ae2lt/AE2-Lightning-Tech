package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.BorrowedCapacityCalculator;
import com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper;
import com.moakiee.ae2lt.grid.OverloadedSubtreeNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;

@Mixin(GridNode.class)
public abstract class GridNodeMaxChannelsMixin implements OverloadedSubtreeNode {

    @Shadow int usedChannels;

    @Unique
    private boolean ae2lt$inOverloadedSubtree;

    @Override
    public boolean ae2lt$isInOverloadedSubtree() {
        return ae2lt$inOverloadedSubtree;
    }

    @Override
    public void ae2lt$setUsedChannels(int channels) {
        this.usedChannels = channels;
    }

    /**
     * Propagate the overloaded-subtree flag down the BFS tree.
     */
    @Inject(method = "setControllerRoute", at = @At("HEAD"))
    private void ae2lt$propagateSubtreeFlag(IPathItem route, CallbackInfo ci) {
        var parentPathItem = route.getControllerRoute();
        if (!(parentPathItem instanceof GridNode parent)) {
            ae2lt$inOverloadedSubtree = false;
            return;
        }

        var parentOwner = parent.getOwner();
        if (parentOwner instanceof ControllerBlockEntity) {
            ae2lt$inOverloadedSubtree = parentOwner instanceof OverloadedControllerBlockEntity;
        } else if (parent instanceof OverloadedSubtreeNode marker) {
            ae2lt$inOverloadedSubtree = marker.ae2lt$isInOverloadedSubtree();
        } else {
            ae2lt$inOverloadedSubtree = false;
        }
    }

    /**
     * During the DFS pass, cancel AE2's bottom-up channel accumulation and
     * return the max-flow value instead. The actual usedChannels field is
     * set by Phase 4 (force-apply) after the DFS completes.
     */
    @Inject(method = "propagateChannelsUpwards", at = @At("HEAD"), cancellable = true)
    private void ae2lt$useFlowForPropagation(boolean consumesChannel, CallbackInfoReturnable<Integer> cir) {
        var networkNodes = BorrowedCapacityCalculator.activeNetworkNodes;
        if (networkNodes == null) return;
        var self = (IGridNode) (GridNode) (Object) this;
        if (!networkNodes.contains(self)) return;

        var nodeFlow = BorrowedCapacityCalculator.activeNodeFlow;
        int flow = nodeFlow.getInt(self);
        cir.setReturnValue(flow);
    }

    @Inject(method = "getMaxChannels", at = @At("HEAD"), cancellable = true)
    private void ae2lt$overloadedChannelCapacity(CallbackInfoReturnable<Integer> cir) {
        var self = (GridNode) (Object) this;
        var owner = OverloadedChannelOwnerHelper.tryGetOwner(self);

        if (!OverloadedChannelOwnerHelper.is128ChannelOwner(owner)) {
            return;
        }

        if (self.hasFlag(GridFlags.CANNOT_CARRY)) {
            return;
        }

        var channelMode = self.getGrid().getPathingService().getChannelMode();
        if (channelMode == ChannelMode.INFINITE) {
            return;
        }

        if (owner instanceof OverloadedControllerBlockEntity || ae2lt$inOverloadedSubtree) {
            cir.setReturnValue(Integer.MAX_VALUE / 2);
        }
    }
}
