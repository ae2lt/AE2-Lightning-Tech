package com.moakiee.ae2lt.client.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;

final class CoreEffectGeometry {
    private CoreEffectGeometry() {
    }

    static void renderTianshu(PoseStack stack, MultiBufferSource buffers,
                              CoreEffectPalette palette, float time, boolean working) {
        var consumer = buffers.getBuffer(CoreEffectRenderTypes.tianshu());
        stack.pushPose();
        stack.scale(1.80F, 1.80F, 1.80F);
        renderCubeCore(
                stack,
                consumer,
                palette,
                time,
                working ? 0.72F : 5.5F,
                working ? 30.0F : 3.0F);
        stack.popPose();
    }

    static void renderMatrix(PoseStack stack, MultiBufferSource buffers,
                             CoreEffectPalette palette, float time, boolean working) {
        var consumer = buffers.getBuffer(CoreEffectRenderTypes.matrix());
        float activity = working ? 1.0F : 0.0F;
        float contraction = 1.0F - activity * (0.14F + 0.045F * (float) Math.sin(time * 5.0F));

        stack.pushPose();
        stack.scale(1.50F, 1.50F, 1.50F);
        stack.mulPose(Axis.YP.rotationDegrees(time * (1.6F + activity * 13.4F)));
        stack.mulPose(Axis.XP.rotationDegrees(12.0F));
        stack.mulPose(Axis.ZP.rotationDegrees(-7.0F));

        stack.pushPose();
        stack.mulPose(Axis.XP.rotationDegrees(time * (-2.0F - activity * 18.0F)));
        stack.mulPose(Axis.YP.rotationDegrees(time * (3.0F + activity * 23.0F)));
        CoreEffectMesh.icosahedron(
                stack,
                consumer,
                0.58F + activity * 0.035F * (float) Math.sin(time * 4.0F),
                palette.primaryR() * 0.18F,
                palette.primaryG() * 0.18F,
                palette.primaryB() * 0.18F,
                0.98F);
        stack.popPose();

        for (int plate = 0; plate < 7; plate++) {
            stack.pushPose();
            int plane = plate % 3;
            if (plane == 1) {
                stack.mulPose(Axis.XP.rotationDegrees(68.0F));
            } else if (plane == 2) {
                stack.mulPose(Axis.ZP.rotationDegrees(74.0F));
            }
            float direction = (plate & 1) == 0 ? 1.0F : -1.0F;
            stack.mulPose(Axis.YP.rotationDegrees(
                    plate * 137.5F + time * direction * (2.0F + activity * 24.0F)));
            stack.scale(contraction, contraction, contraction);
            boolean accent = plate % 3 == 1;
            CoreEffectMesh.annularSegment(
                    stack,
                    consumer,
                    0.78F + (plate % 2) * 0.06F,
                    1.18F + (plate % 2) * 0.08F,
                    0.055F,
                    43.0F + (plate % 3) * 7.0F,
                    8,
                    accent ? palette.accentR() : palette.primaryR(),
                    accent ? palette.accentG() : palette.primaryG(),
                    accent ? palette.accentB() : palette.primaryB(),
                    0.88F);
            stack.popPose();
        }

        renderMatterShards(stack, consumer, palette, time, working);
        stack.popPose();
    }

    private static void renderMatterShards(PoseStack stack,
                                           com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                           CoreEffectPalette palette,
                                           float time, boolean working) {
        float speed = working ? 1.25F : 0.10F;
        for (int shard = 0; shard < 12; shard++) {
            float cycle = time * speed + shard / 12.0F;
            float progress = cycle - (float) Math.floor(cycle);
            float radius = 1.72F - progress * 1.05F;
            float angle = progress * 720.0F + shard * 137.5F;
            float height = (float) Math.sin(Math.toRadians(angle * 0.7F)) * radius * 0.34F;

            stack.pushPose();
            stack.mulPose(Axis.YP.rotationDegrees(angle));
            stack.translate(radius, height, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(angle * 0.37F));
            stack.mulPose(Axis.ZP.rotationDegrees(angle * -0.23F));
            stack.scale(1.0F, 0.34F, 0.48F);
            boolean accent = shard % 4 == 0;
            CoreEffectMesh.cube(
                    stack,
                    consumer,
                    0.105F * (0.72F + progress * 0.28F),
                    accent ? palette.accentR() : palette.primaryR(),
                    accent ? palette.accentG() : palette.primaryG(),
                    accent ? palette.accentB() : palette.primaryB(),
                    0.82F);
            stack.popPose();
        }
    }

    private static void renderCubeCore(PoseStack stack,
                                       com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                       CoreEffectPalette palette,
                                       float time, float stepDuration, float spinSpeed) {
        int step = (int) Math.floor(time / stepDuration);
        float progress = time / stepDuration - step;
        float turn = smoothStep(clamp((progress - 0.12F) / 0.70F)) * 90.0F;
        int axis = Math.floorMod(step, 3);
        int layer = Math.floorMod(step / 3, 3) - 1;
        float direction = (step & 1) == 0 ? 1.0F : -1.0F;

        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(time * spinSpeed));
        stack.mulPose(Axis.XP.rotationDegrees(24.0F));
        stack.mulPose(Axis.ZP.rotationDegrees(-8.0F));

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    renderCubelet(stack, consumer, palette, x, y, z,
                            axis, layer, turn * direction);
                }
            }
        }
        stack.popPose();
    }

    private static void renderCubelet(PoseStack stack,
                                      com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                      CoreEffectPalette palette,
                                      int x, int y, int z,
                                      int axis, int layer, float turn) {
        stack.pushPose();
        int coordinate = axis == 0 ? x : axis == 1 ? y : z;
        if (coordinate == layer) {
            if (axis == 0) {
                stack.mulPose(Axis.XP.rotationDegrees(turn));
            } else if (axis == 1) {
                stack.mulPose(Axis.YP.rotationDegrees(turn));
            } else {
                stack.mulPose(Axis.ZP.rotationDegrees(turn));
            }
        }

        float spacing = 0.56F;
        stack.translate(x * spacing, y * spacing, z * spacing);
        boolean corner = x != 0 && y != 0 && z != 0;
        CoreEffectMesh.cube(
                stack,
                consumer,
                0.245F,
                corner ? palette.accentR() : palette.primaryR(),
                corner ? palette.accentG() : palette.primaryG(),
                corner ? palette.accentB() : palette.primaryB(),
                corner ? 0.92F : 0.86F);
        stack.popPose();
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

}
