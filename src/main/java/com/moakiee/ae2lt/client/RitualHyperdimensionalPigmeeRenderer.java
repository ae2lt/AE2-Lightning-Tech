package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.entity.RitualHyperdimensionalPigmeeEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;

public final class RitualHyperdimensionalPigmeeRenderer extends ItemEntityRenderer {
    public RitualHyperdimensionalPigmeeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ItemEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        if (entity instanceof RitualHyperdimensionalPigmeeEntity ritualEntity) {
            float scale = ritualEntity.getCeremonyScale(partialTick);
            poseStack.scale(scale, scale, scale);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
