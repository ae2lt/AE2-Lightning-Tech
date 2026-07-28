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
        var coreConsumer = buffers.getBuffer(CoreEffectRenderTypes.matrixCore());
        float activity = working ? 1.0F : 0.0F;
        float coreSpeed = working ? 48.0F : 8.0F;
        float ringSpeed = working ? 84.0F : 22.0F;
        float contraction = 1.0F - activity * (0.07F + 0.02F * (float) Math.sin(time * 4.5F));
        float pulse = 1.0F + activity * 0.035F * (float) Math.sin(time * 6.0F);
        float glowPulse = 0.94F + 0.06F * (float) Math.sin(time * (working ? 4.2F : 1.2F));
        float primaryR = brighten(palette.primaryR(), 0.34F);
        float primaryG = brighten(palette.primaryG(), 0.34F);
        float primaryB = brighten(palette.primaryB(), 0.34F);
        float accentR = brighten(palette.accentR(), 0.22F);
        float accentG = brighten(palette.accentG(), 0.22F);
        float accentB = brighten(palette.accentB(), 0.22F);

        stack.pushPose();
        stack.scale(1.50F, 1.50F, 1.50F);

        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(time * coreSpeed * 0.42F));
        stack.mulPose(Axis.XP.rotationDegrees(time * -coreSpeed * 0.27F));
        CoreEffectMesh.sphere(
                stack,
                coreConsumer,
                0.72F * pulse,
                palette.primaryR() * 0.18F,
                palette.primaryG() * 0.18F,
                palette.primaryB() * 0.18F,
                0.98F);
        stack.popPose();

        var glowConsumer = buffers.getBuffer(CoreEffectRenderTypes.matrixGlow());
        float ringRadius = 1.22F * contraction;
        float constraintRadius = 1.12F * contraction;
        float diskAlpha = (working ? 0.34F : 0.22F) * glowPulse;
        float innerYaw = time * ringSpeed * 0.55F;
        renderMatrixRing(stack, glowConsumer, ringRadius, 0.055F, 0.006F,
                innerYaw, 10.0F, -6.0F,
                primaryR, primaryG, primaryB, diskAlpha);
        renderMatrixRing(stack, glowConsumer, ringRadius, 0.008F, 0.011F,
                innerYaw, 10.0F, -6.0F,
                accentR, accentG, accentB, diskAlpha * 1.65F);

        float ringAlpha = (working ? 0.66F : 0.42F) * glowPulse;
        float middleYaw = -time * ringSpeed * 0.82F;
        float outerYaw = time * ringSpeed * 0.68F;
        renderMatrixRing(stack, glowConsumer, constraintRadius, 0.018F, 0.014F,
                middleYaw, 61.0F, 24.0F,
                accentR, accentG, accentB, ringAlpha);
        renderMatrixRing(stack, glowConsumer, constraintRadius, 0.016F, 0.011F,
                outerYaw, 118.0F, -20.0F,
                primaryR, primaryG, primaryB, ringAlpha * 0.82F);

        float nodePulse = 1.0F + (working ? 0.16F : 0.06F) * (float) Math.sin(time * 5.5F);
        renderOrbitNodes(stack, glowConsumer,
                constraintRadius, middleYaw, 61.0F, 24.0F,
                time * ringSpeed * 1.65F,
                2, 0.074F * nodePulse,
                1.00F, 0.64F, 0.16F, ringAlpha * 1.18F);
        renderOrbitNodes(stack, glowConsumer,
                constraintRadius, outerYaw, 118.0F, -20.0F,
                35.0F - time * ringSpeed * 1.25F,
                3, 0.064F * nodePulse,
                1.00F, 0.64F, 0.16F, ringAlpha * 1.02F);
        stack.popPose();
    }

    private static void renderMatrixRing(PoseStack stack,
                                         com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                         float radius, float halfWidth, float halfThickness,
                                         float yaw, float pitch, float roll,
                                         float red, float green, float blue, float alpha) {
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
                alpha);
        stack.popPose();
    }

    private static void renderOrbitNodes(PoseStack stack,
                                         com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                         float radius, float yaw, float pitch, float roll,
                                         float orbitDegrees,
                                         int count, float nodeRadius,
                                         float red, float green, float blue, float alpha) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(roll));
        for (int index = 0; index < count; index++) {
            stack.pushPose();
            stack.mulPose(Axis.YP.rotationDegrees(
                    orbitDegrees + index * 360.0F / count));
            stack.translate(radius, 0.0F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(45.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(45.0F));
            CoreEffectMesh.octahedron(
                    stack,
                    consumer,
                    nodeRadius,
                    red,
                    green,
                    blue,
                    alpha);
            CoreEffectMesh.octahedron(
                    stack,
                    consumer,
                    nodeRadius * 0.52F,
                    1.00F,
                    0.94F,
                    0.72F,
                    Math.min(1.0F, alpha * 1.18F));
            stack.popPose();
        }
        stack.popPose();
    }

    private static float brighten(float color, float amount) {
        return color + (1.0F - color) * amount;
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
