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
        float speed = working ? 48.0F : 8.0F;
        float contraction = 1.0F - activity * (0.07F + 0.02F * (float) Math.sin(time * 4.5F));
        float pulse = 1.0F + activity * 0.035F * (float) Math.sin(time * 6.0F);

        stack.pushPose();
        stack.scale(1.50F, 1.50F, 1.50F);

        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(time * speed * 0.42F));
        stack.mulPose(Axis.XP.rotationDegrees(time * -speed * 0.27F));
        CoreEffectMesh.sphere(
                stack,
                consumer,
                0.72F * pulse,
                palette.primaryR() * 0.18F,
                palette.primaryG() * 0.18F,
                palette.primaryB() * 0.18F,
                0.98F);
        stack.popPose();

        renderMatrixRing(stack, consumer, 1.02F * contraction, 0.105F, 0.028F,
                time * speed, 18.0F, 0.0F,
                1.00F, 0.97F, 0.99F);
        renderMatrixRing(stack, consumer, 1.22F * contraction, 0.092F, 0.024F,
                -time * speed * 0.78F, 68.0F, 28.0F,
                0.96F, 0.75F, 0.85F);
        renderMatrixRing(stack, consumer, 1.42F * contraction, 0.080F, 0.021F,
                time * speed * 0.56F, 112.0F, -24.0F,
                1.00F, 0.90F, 0.95F);
        stack.popPose();
    }

    private static void renderMatrixRing(PoseStack stack,
                                         com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                         float radius, float halfWidth, float halfThickness,
                                         float yaw, float pitch, float roll,
                                         float red, float green, float blue) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(roll));
        CoreEffectMesh.ringBand(
                stack,
                consumer,
                radius - halfWidth,
                radius + halfWidth,
                halfThickness,
                red,
                green,
                blue,
                0.94F);
        stack.popPose();
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
