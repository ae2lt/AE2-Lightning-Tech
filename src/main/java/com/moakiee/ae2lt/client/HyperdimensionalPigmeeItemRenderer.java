package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the normal item first and then adds its End Portal silhouette pass.
 */
final class HyperdimensionalPigmeeItemRenderer extends BlockEntityWithoutLevelRenderer {
    HyperdimensionalPigmeeItemRenderer(
            BlockEntityRenderDispatcher dispatcher, EntityModelSet entityModels) {
        super(dispatcher, entityModels);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel wrappedModel =
                minecraft.getItemRenderer().getModel(stack, null, null, 0);
        if (!(wrappedModel instanceof HyperdimensionalPigmeeBakedModel hyperModel)) {
            return;
        }

        BakedModel model = hyperModel.baseModel();
        VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(
                buffers,
                ItemBlockRenderTypes.getRenderType(stack, true),
                true,
                stack.hasFoil());
        RandomSource random = RandomSource.create();
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            renderQuads(
                    poseStack.last(),
                    consumer,
                    model.getQuads(null, direction, random),
                    packedLight,
                    packedOverlay);
        }
        random.setSeed(42L);
        renderQuads(
                poseStack.last(),
                consumer,
                model.getQuads(null, null, random),
                packedLight,
                packedOverlay);

        HyperdimensionalPigmeePortalLayer.renderItem(model, poseStack, buffers);
        HyperdimensionalPigmeeTextureLayer.renderItem(
                poseStack, buffers, packedOverlay);
    }

    private static void renderQuads(PoseStack.Pose pose, VertexConsumer consumer,
            Iterable<net.minecraft.client.renderer.block.model.BakedQuad> quads,
            int packedLight, int packedOverlay) {
        for (var quad : quads) {
            consumer.putBulkData(
                    pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
        }
    }
}
