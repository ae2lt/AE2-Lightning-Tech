package com.moakiee.ae2lt.mixin;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps AE2CS receiver recalculation from creating a duplicate logical link after its old
 * connection has already been destroyed by another synchronous recalculation path.
 */
@Pseudo
@Mixin(
        targets = "io.github.lounode.ae2cs.api.linker.broadcast.BroadcastFrequencyBand",
        remap = false)
public abstract class BroadcastFrequencyBandMixin {
    @WrapOperation(
            method = "applyReceiver(Lnet/minecraft/core/GlobalPos;Lappeng/api/networking/IGridNode;Lio/github/lounode/ae2cs/api/CustomChannelProviderHost;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/GridHelper;createConnection(Lappeng/api/networking/IGridNode;Lappeng/api/networking/IGridNode;)Lappeng/api/networking/IGridConnection;"),
            remap = false)
    private IGridConnection ae2lt$reuseExistingReceiverConnection(
            IGridNode controllerNode,
            IGridNode receiverNode,
            Operation<IGridConnection> original) {
        var existing = WirelessLinkOps.findConnection(controllerNode, receiverNode);
        if (existing != null && !existing.isInWorld()) {
            return existing;
        }
        return original.call(controllerNode, receiverNode);
    }
}
