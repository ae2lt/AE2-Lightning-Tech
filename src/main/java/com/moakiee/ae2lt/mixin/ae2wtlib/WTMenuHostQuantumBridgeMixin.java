package com.moakiee.ae2lt.mixin.ae2wtlib;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ILinkStatus;
import appeng.me.cluster.implementations.QuantumCluster;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Discard dismantled/unloaded quantum bridges before WTLib dereferences its cached connection. */
@Mixin(WTMenuHost.class)
public abstract class WTMenuHostQuantumBridgeMixin {
    @Shadow
    private IActionHost quantumBridge;

    @Inject(method = "getActionableNode", at = @At("HEAD"), require = 1)
    private void ae2lt$refreshStaleQuantumNode(CallbackInfoReturnable<IGridNode> cir) {
        ae2lt$refreshStaleQuantumConnection();
    }

    @Inject(method = "getLinkStatus", at = @At("HEAD"), require = 1)
    private void ae2lt$refreshStaleQuantumStatus(CallbackInfoReturnable<ILinkStatus> cir) {
        ae2lt$refreshStaleQuantumConnection();
    }

    @Inject(method = "updateConnectedAccessPoint", at = @At("HEAD"), require = 1)
    private void ae2lt$discardStaleQuantumBridge(CallbackInfo ci) {
        // isQuantumLinked() only searches for another bridge when this cache is null.
        ae2lt$discardStaleQuantumBridge();
    }

    @Unique
    private boolean ae2lt$discardStaleQuantumBridge() {
        if (quantumBridge instanceof QuantumCluster cluster
                && (cluster.isDestroyed() || cluster.getCenter() == null)) {
            quantumBridge = null;
            return true;
        }
        return false;
    }

    @Unique
    private void ae2lt$refreshStaleQuantumConnection() {
        if (!ae2lt$discardStaleQuantumBridge()) {
            return;
        }
        // Tianshu menus query their grid before the parent menu's regular connection tick.
        // Refresh both caches together; the native code also keeps local access-point fallback.
        WTMenuHost self = (WTMenuHost) (Object) this;
        self.updateConnectedAccessPoint();
        self.updateLinkStatus();
    }
}
