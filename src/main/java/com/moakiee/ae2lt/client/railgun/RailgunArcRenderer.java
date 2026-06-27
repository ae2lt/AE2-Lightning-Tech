package com.moakiee.ae2lt.client.railgun;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * Lightweight client-side arc renderer for chain-lightning visuals between two
 * points. Inspired by Mekanism's {@code BoltRenderer} (MIT) but minimal: each
 * arc subdivides its path into N jittered sub-segments, then draws three
 * stacked camera-facing billboard quads (outer glow, mid, hot core) with
 * additive-style blending. Arcs auto-fade and self-remove after their lifetime.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunArcRenderer {

    /** A single live arc instance. */
    public static final class Arc {
        final List<Vec3> points;
        final int totalLifetime;
        int remaining;
        final float coreR, coreG, coreB;
        final float glowR, glowG, glowB;
        final float coreWidth;
        final float glowWidth;

        Arc(List<Vec3> points, int lifetime,
            float coreR, float coreG, float coreB,
            float glowR, float glowG, float glowB,
            float coreWidth, float glowWidth) {
            this.points = points;
            this.totalLifetime = lifetime;
            this.remaining = lifetime;
            this.coreR = coreR; this.coreG = coreG; this.coreB = coreB;
            this.glowR = glowR; this.glowG = glowG; this.glowB = glowB;
            this.coreWidth = coreWidth;
            this.glowWidth = glowWidth;
        }
    }

    // Render-thread-only: packet handlers route via ctx.enqueueWork to the client thread.
    private static final List<Arc> ACTIVE = new ArrayList<>();
    private static final Random RAND = new Random();

    private RailgunArcRenderer() {}

    /**
     * Spawn an arc from {@code from} to {@code to} that lives for {@code lifetime}
     * ticks. {@code spread} controls the perpendicular jitter on each interior
     * vertex; {@code segments} is how many sub-segments to subdivide into.
     * Color is the standard plasma blue-white.
     */
    public static void spawnPlasma(Vec3 from, Vec3 to, int segments, float spread, int lifetime) {
        spawn(from, to, segments, spread, lifetime,
                1.00F, 0.80F, 0.90F,   // hot white-pink core
                1.00F, 0.30F, 0.60F,   // wider pink glow
                0.05F, 0.16F);
    }

    /** Variant for the chain-lightning "branch" look (slightly thicker, more spread). */
    public static void spawnChain(Vec3 from, Vec3 to, int lifetime) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0E-3) return;
        int segments = Math.max(6, Math.min(28, (int) Math.round(len * 1.5D)));
        float spread = (float) Math.min(0.55D, 0.10D + len * 0.02D);
        spawnPlasma(from, to, segments, spread, lifetime);
    }

    /** Short crackling arc radiating from an explosion impact (warmer color). */
    public static void spawnImpactSpark(Vec3 from, Vec3 to, int lifetime) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        int segments = Math.max(4, Math.min(14, (int) Math.round(len * 2.5D)));
        float spread = (float) Math.min(0.35D, 0.06D + len * 0.04D);
        spawn(from, to, segments, spread, lifetime,
                1.00F, 0.82F, 0.88F,   // hot pale-pink core (electric snap)
                0.90F, 0.25F, 0.55F,   // deep pink glow
                0.04F, 0.13F);
    }

    /** Short crackling arc branching from the sustained beam (cyan-white beam palette). */
    public static void spawnBeamSpark(Vec3 from, Vec3 to, int lifetime) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        int segments = Math.max(4, Math.min(14, (int) Math.round(len * 2.5D)));
        float spread = (float) Math.min(0.35D, 0.06D + len * 0.04D);
        spawn(from, to, segments, spread, lifetime,
                0.95F, 1.00F, 1.00F,   // hot cyan-white core
                0.52F, 0.88F, 1.00F,   // bright cyan glow
                0.04F, 0.13F);
    }

    public static void spawn(Vec3 from, Vec3 to, int segments, float spread, int lifetime,
                             float coreR, float coreG, float coreB,
                             float glowR, float glowG, float glowB,
                             float coreWidth, float glowWidth) {
        if (segments < 2) segments = 2;
        Vec3 axis = to.subtract(from);
        double len = axis.length();
        if (len < 1.0E-3) return;
        Vec3 dir = axis.normalize();
        // Find any pair of perpendicular vectors (used for jitter).
        Vec3 perpA = Math.abs(dir.y) > 0.95D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 sideX = dir.cross(perpA).normalize();
        Vec3 sideY = dir.cross(sideX).normalize();

        List<Vec3> pts = new ArrayList<>(segments + 1);
        pts.add(from);
        for (int i = 1; i < segments; i++) {
            double t = i / (double) segments;
            // Bell-shaped attenuation: less jitter near endpoints, max in the middle.
            double attenuation = 4.0D * t * (1.0D - t);
            double jx = (RAND.nextDouble() - 0.5D) * 2.0D * spread * attenuation;
            double jy = (RAND.nextDouble() - 0.5D) * 2.0D * spread * attenuation;
            Vec3 base = from.add(axis.scale(t));
            pts.add(base.add(sideX.scale(jx)).add(sideY.scale(jy)));
        }
        pts.add(to);

        ACTIVE.add(new Arc(pts, lifetime,
                coreR, coreG, coreB,
                glowR, glowG, glowB,
                coreWidth, glowWidth));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;

        // Decrement and prune.
        ACTIVE.removeIf(a -> --a.remaining <= 0);
        if (ACTIVE.isEmpty()) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack stack = e.getPoseStack();
        stack.pushPose();
        stack.translate(-camPos.x, -camPos.y, -camPos.z);

        // Depth-test ON so arcs are occluded by blocks/entities (AFTER_TRANSLUCENT_BLOCKS
        // may leave it disabled). depthMask off so additive billboards don't write depth.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        // Additive-style blending for that hot plasma feel.
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var matrix = stack.last().pose();
        for (Arc arc : ACTIVE) {
            float lifeT = (float) arc.remaining / (float) arc.totalLifetime;
            // Quick attack, slower decay; fade alpha with sqrt for a punchier feel.
            float alpha = (float) Math.sqrt(Math.max(0.0F, lifeT));
            drawArc(bb, matrix, arc, camPos, alpha);
        }
        var built = bb.build();
        if (built != null) {
            BufferUploader.drawWithShader(built);
        }

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        stack.popPose();
    }

    private static void drawArc(BufferBuilder bb, org.joml.Matrix4f matrix, Arc arc, Vec3 camPos, float alpha) {
        for (int i = 0; i < arc.points.size() - 1; i++) {
            Vec3 a = arc.points.get(i);
            Vec3 b = arc.points.get(i + 1);
            // Outer wide blue glow
            addSegmentBillboard(bb, matrix, a, b, camPos, arc.glowWidth,
                    arc.glowR, arc.glowG, arc.glowB, 0.45F * alpha);
            // Mid layer
            addSegmentBillboard(bb, matrix, a, b, camPos, arc.glowWidth * 0.55F,
                    (arc.glowR + arc.coreR) * 0.5F,
                    (arc.glowG + arc.coreG) * 0.5F,
                    (arc.glowB + arc.coreB) * 0.5F,
                    0.65F * alpha);
            // Hot core
            addSegmentBillboard(bb, matrix, a, b, camPos, arc.coreWidth,
                    arc.coreR, arc.coreG, arc.coreB, 0.95F * alpha);
        }
    }

    private static void addSegmentBillboard(BufferBuilder bb, org.joml.Matrix4f matrix,
                                            Vec3 a, Vec3 b, Vec3 camPos, float width,
                                            float r, float g, float bCol, float alpha) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-9) return;
        Vec3 dir = axis.normalize();
        Vec3 mid = a.add(b).scale(0.5D);
        Vec3 toCam = camPos.subtract(mid);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-9) {
            side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (side.lengthSqr() < 1.0E-9) {
                side = dir.cross(new Vec3(1.0D, 0.0D, 0.0D));
            }
        }
        side = side.normalize().scale(width);
        Vec3 a1 = a.add(side);
        Vec3 a2 = a.subtract(side);
        Vec3 b1 = b.add(side);
        Vec3 b2 = b.subtract(side);
        bb.addVertex(matrix, (float) a1.x, (float) a1.y, (float) a1.z).setColor(r, g, bCol, alpha);
        bb.addVertex(matrix, (float) a2.x, (float) a2.y, (float) a2.z).setColor(r, g, bCol, alpha);
        bb.addVertex(matrix, (float) b2.x, (float) b2.y, (float) b2.z).setColor(r, g, bCol, alpha);
        bb.addVertex(matrix, (float) b1.x, (float) b1.y, (float) b1.z).setColor(r, g, bCol, alpha);
    }
}
