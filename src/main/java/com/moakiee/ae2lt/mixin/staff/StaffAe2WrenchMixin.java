package com.moakiee.ae2lt.mixin.staff;

import appeng.util.InteractionUtil;
import com.moakiee.ae2lt.item.staff.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InteractionUtil.class)
public abstract class StaffAe2WrenchMixin {
    @Inject(method = {"canWrenchDisassemble", "canWrenchRotate"}, at = @At("HEAD"), cancellable = true)
    private static void ae2lt$moduleWrench(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof MimicryStaffItem) cir.setReturnValue(StaffEnergy.canUse(stack) && StaffState.has(stack, StaffModule.WRENCH));
    }
}
