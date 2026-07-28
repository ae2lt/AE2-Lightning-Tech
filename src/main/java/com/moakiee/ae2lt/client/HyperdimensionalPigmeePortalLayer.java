package com.moakiee.ae2lt.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Replays the Pigmee model's geometry into vanilla's End Portal render type.
 *
 * <p>The portal shader only accepts vertex positions, so normal baked-model
 * rendering cannot feed it directly. Extracting the positions also lets the
 * effect follow the actual Pigmee silhouette instead of surrounding it with a
 * box.</p>
 */
final class HyperdimensionalPigmeePortalLayer {
    private static final long MODEL_SEED = 42L;
    private static final float SHELL_SCALE = 1.0125F;

    private HyperdimensionalPigmeePortalLayer() {
    }

    static void renderBlock(BakedModel model, BlockState state, ModelData modelData,
            PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.endPortal());
        RandomSource random = RandomSource.create();

        beginShell(poseStack);
        for (RenderType sourceType : model.getRenderTypes(state, random, modelData)) {
            for (Direction direction : Direction.values()) {
                random.setSeed(MODEL_SEED);
                renderQuads(
                        poseStack.last(),
                        consumer,
                        model.getQuads(state, direction, random, modelData, sourceType));
            }
            random.setSeed(MODEL_SEED);
            renderQuads(
                    poseStack.last(),
                    consumer,
                    model.getQuads(state, null, random, modelData, sourceType));
        }
        poseStack.popPose();
    }

    static void renderItem(BakedModel model, PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.endPortal());
        RandomSource random = RandomSource.create();

        beginShell(poseStack);
        for (Direction direction : Direction.values()) {
            random.setSeed(MODEL_SEED);
            renderQuads(poseStack.last(), consumer, model.getQuads(null, direction, random));
        }
        random.setSeed(MODEL_SEED);
        renderQuads(poseStack.last(), consumer, model.getQuads(null, null, random));
        poseStack.popPose();
    }

    private static void beginShell(PoseStack poseStack) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    private static void renderQuads(
            PoseStack.Pose pose, VertexConsumer consumer, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                consumer.addVertex(
                        pose.pose(),
                        Float.intBitsToFloat(vertices[offset]),
                        Float.intBitsToFloat(vertices[offset + 1]),
                        Float.intBitsToFloat(vertices[offset + 2]));
            }
        }
    }
}
