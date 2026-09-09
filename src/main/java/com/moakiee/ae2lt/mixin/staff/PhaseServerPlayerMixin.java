package com.moakiee.ae2lt.mixin.staff;

import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import com.moakiee.ae2lt.item.staff.StaffPhaseService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class PhaseServerPlayerMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void ae2lt$admitQBeforeCallbacks(boolean whole, CallbackInfoReturnable<Boolean> cir) {
        if (PhaseItemProtection.isProtected(((ServerPlayer) (Object) this).getInventory().getSelected())) cir.setReturnValue(false);
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void ae2lt$moveRetainedStaff(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        PhaseItemProtection.transferRetainedStaff(oldPlayer.getInventory(), ((ServerPlayer) (Object) this).getInventory());
        StaffPhaseService.tick((ServerPlayer) (Object) this);
    }

    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void ae2lt$invalidateBeforeRespawn(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        StaffPhaseService.prepareRespawn(oldPlayer, (ServerPlayer) (Object) this);
    }
}
