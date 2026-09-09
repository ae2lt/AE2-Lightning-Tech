package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moakiee.ae2lt.item.staff.MimicryStaffItem;
import com.moakiee.ae2lt.item.staff.StaffEnergy;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** The native enchant applies bonemeal before charging: reserve eligibility before that effect. */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.ApothEnchEvents", remap = false)
public abstract class StaffNaturesBlessingMixin {
    @WrapOperation(method = "rightClick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/BoneMealItem;applyBonemeal(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean ae2lt$requireFullCost(ItemStack stack, Level level, BlockPos pos, Player player,
            Operation<Boolean> original, @Local Pair<LevelBasedValue, Integer> bonemealCost) {
        if (stack.getItem() instanceof MimicryStaffItem
                && !StaffEnergy.canPay(stack, Math.max(0, (int) bonemealCost.getFirst().calculate(bonemealCost.getSecond())))) {
            return false;
        }
        return original.call(stack, level, pos, player);
    }
}
