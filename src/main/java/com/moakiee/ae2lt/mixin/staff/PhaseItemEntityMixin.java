package com.moakiee.ae2lt.mixin.staff;

import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemEntity.class)
abstract class PhaseItemEntityMixin {
    // All stack-bearing constructors, NBT loading and later setters pass here. Replace the argument,
    // never mutate the source reference: a thief may have passed the actual player inventory stack.
    @ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack ae2lt$discardProtectedPayload(ItemStack stack) {
        return PhaseItemProtection.transferable(stack);
    }
}
