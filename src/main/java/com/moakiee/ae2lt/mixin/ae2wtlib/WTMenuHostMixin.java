package com.moakiee.ae2lt.mixin.ae2wtlib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGridNode;

import com.moakiee.ae2lt.integration.ae2wtlib.WirelessTerminalFrequencyLink;

import de.mari_023.ae2wtlib.terminal.WTMenuHost;

/**
 * When an ae2wtlib wireless terminal has a bound overloaded frequency card
 * installed, redirect the terminal's actionable node and range validation to
 * the frequency's powered transmitter network. This lets the terminal access
 * the bound ME network remotely / cross-dimensionally, similar to a quantum
 * bridge card.
 */
@Mixin(value = WTMenuHost.class, remap = false)
public abstract class WTMenuHostMixin {
    /** AE2WTLib uses false here to select its remote-link power drain. */
    @Shadow
    private boolean rangeCheck;

    @Inject(method = "getActionableNode", at = @At("HEAD"), cancellable = true)
    private void ae2lt$redirectToFrequencyNode(CallbackInfoReturnable<IGridNode> cir) {
        var route = ae2lt$resolveFrequencyRoute();
        if (route.usesFrequencyRoute()) {
            cir.setReturnValue(route.node());
        }
    }

    @Inject(method = "rangeCheck", at = @At("HEAD"), cancellable = true)
    private void ae2lt$validateFrequencyRange(CallbackInfoReturnable<Boolean> cir) {
        var route = ae2lt$resolveFrequencyRoute();
        if (route.usesFrequencyRoute()) {
            rangeCheck = false;
            cir.setReturnValue(route.isNetworkPowered());
        }
    }

    @Unique
    private WirelessTerminalFrequencyLink.Resolution ae2lt$resolveFrequencyRoute() {
        WTMenuHost self = (WTMenuHost) (Object) this;
        return WirelessTerminalFrequencyLink.resolveRoute(self.getPlayer(), self.getUpgrades());
    }
}
