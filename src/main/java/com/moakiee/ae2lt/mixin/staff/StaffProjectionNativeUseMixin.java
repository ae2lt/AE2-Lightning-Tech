package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.StaffPhaseService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerGameMode.class)
abstract class StaffProjectionNativeUseMixin {
    @Shadow @Final protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean ae2lt$scopedBlockLoot(BlockPos pos, Operation<Boolean> original) {
        return StaffPhaseService.withNativeCopies(player, () -> original.call(pos));
    }

    @WrapMethod(method = "useItemOn")
    private InteractionResult ae2lt$scopedBlockUse(ServerPlayer actor, Level level, ItemStack stack, InteractionHand hand,
            BlockHitResult hit, Operation<InteractionResult> original) {
        return StaffPhaseService.withNativeCopies(actor, () -> original.call(actor, level, stack, hand, hit));
    }

    @WrapOperation(method = {"destroyBlock", "useItemOn"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack ae2lt$registerNativeSnapshot(ItemStack source, Operation<ItemStack> original) {
        return StaffPhaseService.registerNativeCopy(source, original.call(source));
    }
}
