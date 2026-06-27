package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import com.moakiee.ae2lt.client.railgun.RailgunClientExtensions;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;

@Mixin(HumanoidModel.class)
public class RailgunHumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void ae2lt$poseRailgun(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        InteractionHand hand = null;
        if (entity.getMainHandItem().getItem() instanceof ElectromagneticRailgunItem) {
            hand = InteractionHand.MAIN_HAND;
        } else if (entity.getOffhandItem().getItem() instanceof ElectromagneticRailgunItem) {
            hand = InteractionHand.OFF_HAND;
        }
        if (hand == null) {
            return;
        }

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? entity.getMainArm()
                : entity.getMainArm().getOpposite();
        RailgunClientExtensions.poseRailgunArms((HumanoidModel<?>) (Object) this, entity, arm);
    }
}
