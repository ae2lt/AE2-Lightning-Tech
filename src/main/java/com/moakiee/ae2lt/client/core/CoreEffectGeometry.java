package com.moakiee.ae2lt.client.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;

final class CoreEffectGeometry {
    private CoreEffectGeometry() {
    }

    static void renderTianshu(PoseStack stack, MultiBufferSource buffers,
                              CoreEffectPalette palette,
                              double stepPhase, double spinDegrees) {
        var consumer = buffers.getBuffer(CoreEffectRenderTypes.tianshu());
        stack.pushPose();
        stack.scale(1.80F, 1.80F, 1.80F);
        renderCubeCore(stack, consumer, palette, stepPhase, spinDegrees);
        stack.popPose();
    }

    static void renderMatrix(PoseStack stack, MultiBufferSource buffers,
                             CoreEffectPalette palette,
                             CoreEffectAnimationState.Sample animation) {
        var coreConsumer = buffers.getBuffer(CoreEffectRenderTypes.matrixCore());
        float activity = (float) animation.activity();
        float ambientTime = (float) animation.ambientTime();
        float corePhase = (float) animation.primaryPhase();
        float ringPhase = (float) animation.secondaryPhase();
        float contraction = 1.0F
                - activity * (0.07F + 0.02F * (float) Math.sin(ambientTime * 4.5F));
        float pulse = 1.0F + activity * 0.035F * (float) Math.sin(ambientTime * 6.0F);
        float glowPulse = 0.94F + 0.06F * (float) Math.sin(animation.glowPhase());
        float primaryR = brighten(palette.primaryR(), 0.34F);
        float primaryG = brighten(palette.primaryG(), 0.34F);
        float primaryB = brighten(palette.primaryB(), 0.34F);
        float accentR = brighten(palette.accentR(), 0.22F);
        float accentG = brighten(palette.accentG(), 0.22F);
        float accentB = brighten(palette.accentB(), 0.22F);

        stack.pushPose();
        stack.scale(1.50F, 1.50F, 1.50F);

        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(corePhase * 0.42F));
        stack.mulPose(Axis.XP.rotationDegrees(corePhase * -0.27F));
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
        float diskAlpha = lerp(0.22F, 0.34F, activity) * glowPulse;
        float innerYaw = ringPhase * 0.55F;
        renderMatrixRing(stack, glowConsumer, ringRadius, 0.055F, 0.006F,
                innerYaw, 10.0F, -6.0F,
                primaryR, primaryG, primaryB, diskAlpha);
        renderMatrixRing(stack, glowConsumer, ringRadius, 0.008F, 0.011F,
                innerYaw, 10.0F, -6.0F,
                accentR, accentG, accentB, diskAlpha * 1.65F);

        float ringAlpha = lerp(0.42F, 0.66F, activity) * glowPulse;
        float middleYaw = -ringPhase * 0.82F;
        float outerYaw = ringPhase * 0.68F;
        renderMatrixRing(stack, glowConsumer, constraintRadius, 0.018F, 0.014F,
                middleYaw, 61.0F, 24.0F,
                accentR, accentG, accentB, ringAlpha);
        renderMatrixRing(stack, glowConsumer, constraintRadius, 0.016F, 0.011F,
                outerYaw, 118.0F, -20.0F,
                primaryR, primaryG, primaryB, ringAlpha * 0.82F);

        float nodePulse = 1.0F
                + lerp(0.06F, 0.16F, activity) * (float) Math.sin(ambientTime * 5.5F);
        renderOrbitNodes(stack, glowConsumer,
                constraintRadius, middleYaw, 61.0F, 24.0F,
                ringPhase * 1.65F,
                2, 0.074F * nodePulse,
                1.00F, 0.64F, 0.16F, ringAlpha * 1.18F);
        renderOrbitNodes(stack, glowConsumer,
                constraintRadius, outerYaw, 118.0F, -20.0F,
                35.0F - ringPhase * 1.25F,
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

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static void renderCubeCore(PoseStack stack,
                                       com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                       CoreEffectPalette palette,
                                       double stepPhase, double spinDegrees) {
        int step = (int) Math.floor(stepPhase);
        float progress = (float) (stepPhase - step);
        float turn = smoothStep(clamp((progress - 0.12F) / 0.70F)) * 90.0F;
        int axis = Math.floorMod(step, 3);
        int layer = Math.floorMod(step / 3, 3) - 1;
        float direction = (step & 1) == 0 ? 1.0F : -1.0F;

        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees((float) spinDegrees));
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
