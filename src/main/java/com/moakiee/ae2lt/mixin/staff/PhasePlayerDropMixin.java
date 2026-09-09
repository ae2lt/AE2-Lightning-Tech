package com.moakiee.ae2lt.mixin.staff;

import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PhasePlayerDropMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void ae2lt$rejectDrop(ItemStack stack, boolean around, boolean trace, CallbackInfoReturnable<ItemEntity> cir) {
        if (PhaseItemProtection.isProtected(stack)) cir.setReturnValue(null);
    }
}
