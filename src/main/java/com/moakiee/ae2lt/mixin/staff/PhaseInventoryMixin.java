package com.moakiee.ae2lt.mixin.staff;

import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import com.moakiee.ae2lt.item.staff.StaffPhaseService;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
abstract class PhaseInventoryMixin {
    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void ae2lt$validateIncomingProjection(int slot, ItemStack incoming, CallbackInfo ci) {
        var inventory = (Inventory) (Object) this;
        if (StaffPhaseService.isProjection(incoming) && !StaffPhaseService.validAt(inventory.player, slot, incoming)) {
            inventory.setItem(slot, ItemStack.EMPTY);
            ci.cancel();
        }
    }

    @Inject(method = "getItem", at = @At("RETURN"), cancellable = true)
    private void ae2lt$hideInvalidProjection(int slot, CallbackInfoReturnable<ItemStack> cir) {
        var inventory = (Inventory) (Object) this;
        if (!StaffPhaseService.validAt(inventory.player, slot, cir.getReturnValue())) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "getSelected", at = @At("RETURN"), cancellable = true)
    private void ae2lt$hideInvalidSelectedProjection(CallbackInfoReturnable<ItemStack> cir) {
        var inventory = (Inventory) (Object) this;
        if (!StaffPhaseService.validAt(inventory.player, inventory.selected, cir.getReturnValue())) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void ae2lt$admitRemoval(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (PhaseItemProtection.isProtected(((Inventory) (Object) this).getItem(slot))) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true)
    private void ae2lt$admitWholeRemoval(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (PhaseItemProtection.isProtected(((Inventory) (Object) this).getItem(slot))) cir.setReturnValue(ItemStack.EMPTY);
    }

    // The caller clears the list after Player.drop, irrespective of its result. Skip both operations.
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private Object ae2lt$skipLockedDrop(List<ItemStack> list, int index, Operation<Object> original) {
        return PhaseItemProtection.transferable((ItemStack) original.call(list, index));
    }
}
