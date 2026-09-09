package com.moakiee.ae2lt.mixin.staff;

import java.util.function.UnaryOperator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "chanceCubes.rewards.DefaultRewards$12", remap = false)
abstract class PhaseChanceBombMixin {
    @WrapOperation(method = "trigger", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/core/NonNullList;replaceAll(Ljava/util/function/UnaryOperator;)V"))
    private void ae2lt$excludeLockedReplacement(NonNullList<ItemStack> items, UnaryOperator<ItemStack> replacement, Operation<Void> original) {
        original.call(items, (UnaryOperator<ItemStack>) stack -> PhaseItemProtection.isProtected(stack) ? stack : replacement.apply(stack));
    }

    @WrapOperation(method = "trigger", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/core/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;"))
    private Object ae2lt$excludeLockedArmor(NonNullList<ItemStack> items, int index, Object replacement, Operation<Object> original) {
        return PhaseItemProtection.isProtected(items.get(index)) ? items.get(index) : original.call(items, index, replacement);
    }
}
