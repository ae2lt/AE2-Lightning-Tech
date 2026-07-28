package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the sparse Hyperdimensional Pigmee markings over the portal shell.
 */
final class HyperdimensionalPigmeeTextureLayer {
    static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID,
                    "block/hyperdimensional_pigmee_fumo_overlay"));

    private static final float SHELL_SCALE = 1.025F;

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
        poseStack.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        render(poseStack, buffers, packedOverlay);
        poseStack.popPose();
    }

    static void renderItem(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        render(poseStack, buffers, packedOverlay);
        poseStack.popPose();
    }

    private static void render(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                buffers.getBuffer(RenderType.entityTranslucentEmissive(
                        TextureAtlas.LOCATION_BLOCKS)),
                null,
                model,
                1.0F,
                1.0F,
                1.0F,
                LightTexture.FULL_BRIGHT,
                packedOverlay,
                ModelData.EMPTY,
                null);
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
