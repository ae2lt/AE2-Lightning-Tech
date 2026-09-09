package com.moakiee.ae2lt.mixin.staff;

import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
abstract class PhaseMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void ae2lt$admitTransfer(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (PhaseItemProtection.blocksClick((AbstractContainerMenu) (Object) this, slot, button, type, player)) ci.cancel();
    }

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void ae2lt$admitQuickMove(ItemStack stack, int start, int end, boolean reverse, CallbackInfoReturnable<Boolean> cir) {
        if (PhaseItemProtection.isProtected(stack)) cir.setReturnValue(false);
    }
}
