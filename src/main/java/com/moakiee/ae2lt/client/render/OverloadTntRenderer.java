package com.moakiee.ae2lt.client.render;

import com.moakiee.ae2lt.entity.OverloadTntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class OverloadTntRenderer extends EntityRenderer<OverloadTntEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public OverloadTntRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(OverloadTntEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5F, 0.0F);

        int fuse = entity.getFuse();
        if ((float) fuse - partialTick + 1.0F < 10.0F) {
            float scale = 1.0F - ((float) fuse - partialTick + 1.0F) / 10.0F;
            scale = Mth.clamp(scale, 0.0F, 1.0F);
            scale *= scale;
            scale *= scale;
            float inflatedScale = 1.0F + scale * 0.3F;
            poseStack.scale(inflatedScale, inflatedScale, inflatedScale);
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        TntMinecartRenderer.renderWhiteSolidBlock(
                blockRenderer,
                entity.getRenderBlockState(),
                poseStack,
                bufferSource,
                packedLight,
                fuse / 5 % 2 == 0);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(OverloadTntEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
