package com.moakiee.ae2lt.mixin.ae2wtlib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;

import appeng.api.networking.IGridNode;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;

import de.mari_023.ae2wtlib.terminal.WTMenuHost;

/**
 * When an ae2wtlib wireless terminal has a bound overloaded frequency card
 * installed, redirect the terminal's actionable node to the frequency's
 * transmitter network. This lets the terminal access the bound ME network
 * remotely / cross-dimensionally, similar to a quantum bridge card.
 *
 * <p>The override runs server-side only. Forge AE2 (15.x) derives the link
 * status from the actionable node's grid, so no link-status override is needed
 * here (unlike the NeoForge port, which hooks AE2 16's {@code ILinkStatus}).</p>
 */
@Mixin(value = WTMenuHost.class, remap = false)
public abstract class WTMenuHostMixin {

    @Inject(method = "getActionableNode", at = @At("HEAD"), cancellable = true)
    private void ae2lt$redirectToFrequencyNode(CallbackInfoReturnable<IGridNode> cir) {
        IGridNode node = ae2lt$resolveFrequencyNode();
        if (node != null) {
            cir.setReturnValue(node);
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
