package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

final class SpinningFumoBakedModel extends BakedModelWrapper<BakedModel> {

    SpinningFumoBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack,
                                     boolean leftHand) {
        originalModel.applyTransform(displayContext, poseStack, leftHand);
        if (displayContext == ItemDisplayContext.HEAD) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
                float angle = (minecraft.level.getGameTime() % 60L + partialTick)
                        * FumoBlockEntity.SPIN_DEGREES_PER_TICK;
                poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            }
        }
        return this;
    }
}
