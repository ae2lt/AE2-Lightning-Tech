package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "reliquary.entity.LyssaHook", remap = false)
abstract class PhaseLyssaMixin {
    @WrapOperation(method = "stealFromLivingEntity", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack ae2lt$excludeBeforeDamageAndSpawn(LivingEntity target, EquipmentSlot slot, Operation<ItemStack> original) {
        return PhaseItemProtection.transferable(original.call(target, slot));
    }
}
