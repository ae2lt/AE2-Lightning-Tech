package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.*;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Extends just the known loot predicate entries, preserving all their other conditions. */
@Mixin(ItemPredicate.class)
public abstract class StaffItemPredicateMixin {
    @WrapOperation(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/core/HolderSet;)Z"))
    private boolean ae2lt$moduleToolPredicate(ItemStack stack, HolderSet<Item> items, Operation<Boolean> original) {
        if (!(stack.getItem() instanceof MimicryStaffItem)) return original.call(stack, items);
        if (items.unwrapKey().map(key -> key.location().toString().equals("farmersdelight:tools/knives")).orElse(false)) {
            return StaffEnergy.canUse(stack) && StaffState.has(stack, StaffModule.KNIFE);
        }
        if (items.unwrapKey().isEmpty() && items.size() == 1 && items.contains(Items.SHEARS.builtInRegistryHolder())) {
            return StaffEnergy.canUse(stack) && StaffState.has(stack, StaffModule.SHEARS);
        }
        return original.call(stack, items);
    }
}
