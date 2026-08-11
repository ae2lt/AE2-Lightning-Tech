package com.moakiee.ae2lt.client;

import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the sparse Hyperdimensional Pigmee markings over the portal shell.
 */
final class HyperdimensionalPigmeeTextureLayer {
    // Plain ResourceLocation so ModelManager.getModel resolves the baked model
    // directly; variant-qualified lookups miss and yield the missing model.
    static final ResourceLocation MODEL = new ResourceLocation(
            AE2LightningTech.MODID,
            "block/hyperdimensional_pigmee_fumo_overlay");

    private static final long MODEL_SEED = 42L;
    private static final float SURFACE_OFFSET = 0.004F;

    private HyperdimensionalPigmeeTextureLayer() {
    }

    static void renderBlock(
            BlockState state,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationFor(state)));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        render(poseStack, buffers, packedOverlay);
        poseStack.popPose();
    }

    static void renderItem(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedOverlay) {
        poseStack.pushPose();
        render(poseStack, buffers, packedOverlay);
        poseStack.popPose();
    }

    private static void render(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        VertexConsumer consumer = buffers.getBuffer(
                RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        RandomSource random = RandomSource.create();
        for (Direction direction : Direction.values()) {
            random.setSeed(MODEL_SEED);
            renderQuads(
                    poseStack,
                    consumer,
                    model.getQuads(null, direction, random),
                    direction,
                    packedOverlay);
        }
        random.setSeed(MODEL_SEED);
        for (BakedQuad quad : model.getQuads(null, null, random)) {
            renderQuads(
                    poseStack,
                    consumer,
                    List.of(quad),
                    quad.getDirection(),
                    packedOverlay);
        }
    }

    private static void renderQuads(
            PoseStack poseStack,
            VertexConsumer consumer,
            List<BakedQuad> quads,
            Direction direction,
            int packedOverlay) {
        if (quads.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(
                direction.getStepX() * SURFACE_OFFSET,
                direction.getStepY() * SURFACE_OFFSET,
                direction.getStepZ() * SURFACE_OFFSET);
        for (BakedQuad quad : quads) {
            consumer.putBulkData(
                    poseStack.last(),
                    quad,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    LightTexture.FULL_BRIGHT,
                    packedOverlay,
                    true);
        }
        poseStack.popPose();
    }

    private static float rotationFor(BlockState state) {
        if (!state.hasProperty(com.moakiee.ae2lt.block.FumoBlock.FACING)) {
            return 0.0F;
        }
        Direction facing = state.getValue(com.moakiee.ae2lt.block.FumoBlock.FACING);
        return switch (facing) {
            // Blockstate Y rotations are baked with a negative Y-axis quaternion.
            // PoseStack rotations must therefore use the inverse-signed angle.
            case SOUTH -> -180.0F;
            case WEST -> -270.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }
}
