package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.*;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity", remap = false)
public abstract class StaffCuttingBoardMixin {
    @Inject(method = "processStoredItemUsingTool", at = @At("HEAD"), cancellable = true)
    private void ae2lt$requirePower(ItemStack tool, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (tool.getItem() instanceof MimicryStaffItem && !StaffEnergy.canUse(tool)) cir.setReturnValue(false);
    }

    @WrapOperation(method = "lambda$processStoredItemUsingTool$2", at = @At(value = "INVOKE",
            target = "Lvectorwing/farmersdelight/common/utility/ItemUtils;spawnItemEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;DDDDDD)V"))
    private void ae2lt$staffCuttingOutput(Level level, ItemStack output, double x, double y, double z,
            double dx, double dy, double dz, Operation<Void> original,
            ItemStack tool, Player player, RecipeHolder<?> recipe) {
        if (!(level instanceof ServerLevel server) || player == null || !StaffDrops.active(tool)) {
            original.call(level, output, x, y, z, dx, dy, dz);
            return;
        }
        for (var remainder : StaffDrops.process(server, player, tool, output)) {
            original.call(level, remainder, x, y, z, dx, dy, dz);
        }
    }
}
