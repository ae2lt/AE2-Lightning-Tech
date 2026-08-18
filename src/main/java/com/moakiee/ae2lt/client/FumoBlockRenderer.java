package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.List;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;

public class FumoBlockRenderer implements BlockEntityRenderer<FumoBlockEntity> {

    private static final RandomSource RAND = RandomSource.create();

    public FumoBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FumoBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        boolean spinning = blockEntity.isSpinning();

        BlockState renderState = spinning && state.hasProperty(FumoBlock.FACING)
                ? state.setValue(FumoBlock.FACING, Direction.NORTH)
                : state;

        poseStack.pushPose();
        if (spinning) {
            poseStack.translate(0.5D, 0.0D, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(blockEntity.getRenderYRot(partialTick)));
            poseStack.translate(-0.5D, 0.0D, -0.5D);
        }

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(renderState);
        ModelData modelData = ModelData.EMPTY;
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();

        if (level != null) {
            ModelBlockRenderer modelRenderer = dispatcher.getModelRenderer();
            for (RenderType renderType : model.getRenderTypes(renderState, RAND, modelData)) {
                modelRenderer.tesselateBlock(
                        level,
                        model,
                        renderState,
                        pos,
                        poseStack,
                        buffer.getBuffer(renderType),
                        false,
                        RAND,
                        42L,
                        packedOverlay,
                        modelData,
                        renderType);
            }
        } else {
            int color = Minecraft.getInstance().getBlockColors().getColor(renderState, null, null, 0);
            float r = (color >> 16 & 0xFF) / 255.0F;
            float g = (color >> 8 & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            PoseStack.Pose pose = poseStack.last();

            for (RenderType renderType : model.getRenderTypes(renderState, RAND, modelData)) {
                VertexConsumer consumer = buffer.getBuffer(renderType);
                for (Direction dir : Direction.values()) {
                    RAND.setSeed(42L);
                    renderQuads(pose, consumer, model.getQuads(renderState, dir, RAND, modelData, renderType),
                            r, g, b, packedLight, packedOverlay);
                }
                RAND.setSeed(42L);
                renderQuads(pose, consumer, model.getQuads(renderState, null, RAND, modelData, renderType),
                        r, g, b, packedLight, packedOverlay);
            }
        }

        poseStack.popPose();
    }

    private static void renderQuads(PoseStack.Pose pose, VertexConsumer consumer, List<BakedQuad> quads,
                                    float r, float g, float b, int packedLight, int packedOverlay) {
        for (BakedQuad quad : quads) {
            float shade = getShade(quad);
            float qr;
            float qg;
            float qb;
            if (quad.isTinted()) {
                qr = Mth.clamp(r, 0.0F, 1.0F) * shade;
                qg = Mth.clamp(g, 0.0F, 1.0F) * shade;
                qb = Mth.clamp(b, 0.0F, 1.0F) * shade;
            } else {
                qr = shade;
                qg = shade;
                qb = shade;
            }
            consumer.putBulkData(pose, quad, qr, qg, qb, packedLight, packedOverlay);
        }
    }

    private static float getShade(BakedQuad quad) {
        if (!quad.isShade()) {
            return 1.0F;
        }
        Direction dir = quad.getDirection();
        if (dir == null) {
            return 1.0F;
        }
        return switch (dir) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case EAST, WEST -> 0.6F;
        };
    }
}

