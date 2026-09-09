package com.moakiee.ae2lt.mixin.staff;

import com.moakiee.ae2lt.item.staff.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class StaffMiningProgressMixin {
    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void ae2lt$staffProgress(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        var stack = player.getMainHandItem();
        if (stack.getItem() instanceof MimicryStaffItem) {
            if (!StaffEnergy.canUse(stack)) cir.setReturnValue(0F);
            else if (StaffState.has(stack, StaffModule.SPEED))
                cir.setReturnValue(StaffState.settings(stack).transformProgress(cir.getReturnValue()));
        }
    }
}
