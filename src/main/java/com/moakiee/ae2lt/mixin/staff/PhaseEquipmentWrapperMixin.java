package com.moakiee.ae2lt.mixin.staff;

import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.EntityEquipmentInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityEquipmentInvWrapper.class, remap = false)
abstract class PhaseEquipmentWrapperMixin {
    @Shadow public abstract ItemStack getStackInSlot(int slot);

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void ae2lt$rejectBothExtractionModes(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (PhaseItemProtection.isProtected(getStackInSlot(slot))) cir.setReturnValue(ItemStack.EMPTY);
    }
}
