package com.moakiee.ae2lt.client.railgun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;

/**
 * Client-only railgun pose hooks. The item still uses vanilla JSON item rendering,
 * but these transforms replace bow-style charging with a forward-facing rifle grip.
 */
public final class RailgunClientExtensions implements IClientItemExtensions {
    public static final RailgunClientExtensions INSTANCE = new RailgunClientExtensions();

    private RailgunClientExtensions() {
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return stack.getItem() instanceof ElectromagneticRailgunItem
                ? HumanoidModel.ArmPose.CROSSBOW_HOLD
                : null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                           ItemStack itemInHand, float partialTick,
                                           float equipProcess, float swingProcess) {
        if (!(itemInHand.getItem() instanceof ElectromagneticRailgunItem)) {
            return false;
        }

        int side = arm == HumanoidArm.RIGHT ? 1 : -1;

        // Keep the gun locked in a shouldered position. Using swing/equip/charge
        // here makes held left-fire and right-charge visibly jitter.
        poseStack.translate(side * 0.48F, -0.58F, -0.98F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-4.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -2.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -3.0F));
        return true;
    }

    public static void poseRailgunArms(HumanoidModel<?> model, LivingEntity entity, HumanoidArm activeArm) {
        boolean right = activeArm == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart support = right ? model.leftArm : model.rightArm;
        float mirror = right ? 1.0F : -1.0F;
        main.xRot = -1.18F + model.head.xRot * 0.55F;
        main.yRot = model.head.yRot - mirror * 0.10F;
        main.zRot = mirror * 0.04F;

        support.xRot = -1.08F + model.head.xRot * 0.45F;
        support.yRot = model.head.yRot + mirror * 0.42F;
        support.zRot = -mirror * 0.18F;
    }
}
