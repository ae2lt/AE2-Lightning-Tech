package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;

public class LightningAssemblyChamberRenderer
        implements BlockEntityRenderer<LightningAssemblyChamberBlockEntity> {

    public LightningAssemblyChamberRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LightningAssemblyChamberBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack stack = blockEntity.getClientRecipeResult();
        if (stack.isEmpty()) {
            return;
        }

        var itemRenderer = Minecraft.getInstance().getItemRenderer();
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);

        if (!(stack.getItem() instanceof BlockItem)) {
            poseStack.translate(0, -0.3F, 0);
        } else {
            poseStack.translate(0, -0.2F, 0);
        }

        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                blockEntity.getLevel(),
                0);
        poseStack.popPose();
    }
}
