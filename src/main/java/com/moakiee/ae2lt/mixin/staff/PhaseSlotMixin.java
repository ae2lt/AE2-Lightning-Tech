package com.moakiee.ae2lt.mixin.staff;

import java.util.Optional;
import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
abstract class PhaseSlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void ae2lt$admitPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (PhaseItemProtection.isProtected(((Slot) (Object) this).getItem())) cir.setReturnValue(false);
    }

    @Inject(method = "tryRemove", at = @At("HEAD"), cancellable = true)
    private void ae2lt$admitSafeTake(int count, int limit, Player player, CallbackInfoReturnable<Optional<ItemStack>> cir) {
        if (PhaseItemProtection.isProtected(((Slot) (Object) this).getItem())) cir.setReturnValue(Optional.empty());
    }
}
