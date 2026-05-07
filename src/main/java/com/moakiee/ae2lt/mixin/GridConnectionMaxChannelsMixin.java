package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.grid.BorrowedCapacityCalculator;
import com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.pathing.ChannelMode;
import appeng.me.GridConnection;

@Mixin(GridConnection.class)
public abstract class GridConnectionMaxChannelsMixin {

    @Shadow int usedChannels;

    @Inject(method = "getMaxChannels", at = @At("HEAD"), cancellable = true)
    private void ae2lt$use128ChannelsForOwnConnections(CallbackInfoReturnable<Integer> cir) {
        var self = (GridConnection) (Object) this;

        var ownerA = OverloadedChannelOwnerHelper.tryGetOwner(self.a());
        var ownerB = OverloadedChannelOwnerHelper.tryGetOwner(self.b());

        if (!OverloadedChannelOwnerHelper.is128ChannelConnection(ownerA, ownerB)) {
            return;
        }

        var channelMode = self.b().getGrid().getPathingService().getChannelMode();
        if (channelMode == ChannelMode.INFINITE) {
            return;
        }

        cir.setReturnValue(Integer.MAX_VALUE / 2);
    }

    /**
     * During the DFS pass, cancel AE2's routing-tree-based channel propagation
     * and return the exact per-connection flow from Dinic's max-flow result.
     * The actual usedChannels field is set by Phase 4 (force-apply) after the
     * DFS completes. Only intercepts connections belonging to the max-flow network.
     */
    @Inject(method = "propagateChannelsUpwards()I", at = @At("HEAD"), cancellable = true)
    private void ae2lt$useFlowForConnectionPropagation(CallbackInfoReturnable<Integer> cir) {
        var connFlow = BorrowedCapacityCalculator.activeConnectionFlow;
        if (connFlow == null) return;

        var networkNodes = BorrowedCapacityCalculator.activeNetworkNodes;
        if (networkNodes == null) return;

        var self = (GridConnection) (Object) this;
        if (!networkNodes.contains(self.a()) && !networkNodes.contains(self.b())) return;

        int flow = connFlow.getInt(self);
        cir.setReturnValue(flow);
    }
}
