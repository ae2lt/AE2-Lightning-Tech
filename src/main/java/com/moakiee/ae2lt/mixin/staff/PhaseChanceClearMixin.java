package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.PhaseItemProtection;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "chanceCubes.rewards.defaultRewards.ClearInventoryReward", remap = false)
abstract class PhaseChanceClearMixin {
    @WrapOperation(method = "trigger", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private void ae2lt$excludeLockedClear(Inventory inventory, int slot, ItemStack value, Operation<Void> original) {
        if (!PhaseItemProtection.isProtected(inventory.getItem(slot))) original.call(inventory, slot, value);
    }
}
