package com.moakiee.ae2lt.mixin.ae2wtlib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ILinkStatus;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;

import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

/**
 * When an ae2wtlib wireless terminal has a bound overloaded frequency card
 * installed, redirect the terminal's actionable node (and link status) to the
 * frequency's transmitter network. This lets the terminal access the bound
 * ME network remotely / cross-dimensionally, similar to a quantum bridge card.
 *
 * <p>Both overrides run server-side only. AE2's {@code MEStorageMenu} syncs the
 * server-side link status to the client via {@code SetLinkStatusPacket}, so the
 * client display follows automatically without a client-side override.</p>
 */
@Mixin(WTMenuHost.class)
public abstract class WTMenuHostMixin {

    @Inject(method = "getActionableNode", at = @At("HEAD"), cancellable = true)
    private void ae2lt$redirectToFrequencyNode(CallbackInfoReturnable<IGridNode> cir) {
        IGridNode node = ae2lt$resolveFrequencyNode();
        if (node != null) {
            cir.setReturnValue(node);
        }
    }

    @Inject(method = "getLinkStatus", at = @At("HEAD"), cancellable = true)
    private void ae2lt$frequencyLinkStatus(CallbackInfoReturnable<ILinkStatus> cir) {
        WTMenuHost self = (WTMenuHost) (Object) this;
        if (self.getPlayer().level().isClientSide()) {
            return;
        }
        if (ae2lt$boundFrequencyId() <= 0) {
            return;
        }
        IGridNode node = ae2lt$resolveFrequencyNode();
        if (node == null) {
            return;
        }
        IGrid grid = node.getGrid();
        if (grid != null && grid.getEnergyService().isNetworkPowered()) {
            cir.setReturnValue(ILinkStatus.ofConnected());
        }
    }

    /**
     * Resolves the live transmitter grid node for the bound frequency, or
     * {@code null} if there is no bound frequency card, the manager is missing
     * (client side), or the transmitter chunk is unavailable.
     */
    @Unique
    private IGridNode ae2lt$resolveFrequencyNode() {
        WTMenuHost self = (WTMenuHost) (Object) this;
        if (!(self.getPlayer().level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        int freqId = ae2lt$boundFrequencyId();
        if (freqId <= 0) {
            return null;
        }
        var manager = WirelessFrequencyManager.get();
        if (manager == null) {
            return null;
        }
        // Frequency-card terminal access is reserved for advanced transmitters;
        // a normal-controller transmitter resolves to null, leaving the terminal
        // without remote access (the card is effectively inert).
        return manager.resolveAdvancedNode(freqId, serverLevel.getServer());
    }

    @Unique
    private int ae2lt$boundFrequencyId() {
        WTMenuHost self = (WTMenuHost) (Object) this;
        var upgrades = self.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            var card = upgrades.getStackInSlot(i);
            if (card.getItem() instanceof OverloadedFrequencyCardItem) {
                var data = OverloadedFrequencyCardItem.getData(card);
                if (data.isBound()) {
                    return data.frequencyId();
                }
            }
        }
        return -1;
    }
}
