package com.moakiee.ae2lt.client.railgun;

import java.util.ArrayList;

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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * Client-side ground-aligned shockwave + camera-facing flash sphere renderer.
 * Both are short-lived "expanding ring + bright core" effects used at the
 * impact point of charged shots. Cheap: a flat quad ring on the ground for
 * the wave + a camera-billboarded quad for the flash core. Additive-blended.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunShockwaveRenderer {

    private static final int RING_SEGMENTS = 64;
    private static final int OCCLUSION_REFRESH_TICKS = 1;
    private static final float SHOCKWAVE_RADIUS_JITTER = 0.095F;

    /** A single live shockwave + flash burst. */
    public static final class Burst {
        final Vec3 center;
        final float maxRadius;
        final int totalLifetime;
        int remaining;
        final float r, g, b;
        final boolean[] occludedSegments = new boolean[RING_SEGMENTS];
        long lastOcclusionTick = Long.MIN_VALUE;

        Burst(Vec3 center, float maxRadius, int lifetime, float r, float g, float b) {
            this.center = center;
            this.maxRadius = maxRadius;
            this.totalLifetime = lifetime;
            this.remaining = lifetime;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    // Plain ArrayList: render-thread-only (packet handlers enqueueWork to client thread).
    private static final java.util.List<Burst> ACTIVE = new ArrayList<>();

    private RailgunShockwaveRenderer() {}

    /** Spawn a shockwave at {@code center} that expands to {@code maxRadius} over {@code lifetime} ticks. */
    public static void spawn(Vec3 center, float maxRadius, int lifetime) {
        ACTIVE.add(new Burst(center, maxRadius, lifetime,
                1.00F, 0.50F, 0.75F));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;
        ACTIVE.removeIf(b -> --b.remaining <= 0);
        if (ACTIVE.isEmpty()) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        long gameTick = mc.level.getGameTime();
        PoseStack stack = e.getPoseStack();
        stack.pushPose();
        stack.translate(-camPos.x, -camPos.y, -camPos.z);

        // Depth-test ON so the ring is occluded by blocks (AFTER_TRANSLUCENT_BLOCKS
        // may leave it disabled). depthMask off so the additive ring doesn't pollute
        // depth for later passes.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var matrix = stack.last().pose();
        for (Burst burst : ACTIVE) {
            float t = 1.0F - (float) burst.remaining / (float) burst.totalLifetime;
            // Ease-out expansion: fast initial growth, slower at the end.
            float radius = burst.maxRadius * (1.0F - (1.0F - t) * (1.0F - t));
            float ringFade = 1.0F - t;
            float ringWidth = burst.maxRadius * 0.18F * (1.0F - t * 0.5F);
            if (gameTick - burst.lastOcclusionTick >= OCCLUSION_REFRESH_TICKS) {
                refreshOcclusion(mc.level, burst, radius, ringWidth, t, gameTick);
            }
            addRing(bb, matrix, burst, radius, ringWidth, t,
                    burst.r, burst.g, burst.b, 0.85F * ringFade);
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

    private static void refreshOcclusion(Level level, Burst burst, float radius, float thickness,
                                         float progress, long gameTick) {
        burst.lastOcclusionTick = gameTick;
        if (level == null || radius <= 0.0F || thickness <= 0.0F) {
            java.util.Arrays.fill(burst.occludedSegments, false);
            return;
        }
        Vec3 center = burst.center;
        double cy = center.y + 0.05D;
        Vec3 castFrom = new Vec3(center.x, cy, center.z);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = (i / (double) RING_SEGMENTS) * Math.PI * 2.0D;
            double a1 = ((i + 1) / (double) RING_SEGMENTS) * Math.PI * 2.0D;
            float radius0 = irregularRadius(center, radius, i, progress);
            float radius1 = irregularRadius(center, radius, i + 1, progress);
            float outer0 = radius0 + thickness * 0.5F;
            float outer1 = radius1 + thickness * 0.5F;
            double midA = (a0 + a1) * 0.5D;
            double testR = Math.max(outer0, outer1) + 0.05D;
            double mx = center.x + Math.cos(midA) * testR;
            double mz = center.z + Math.sin(midA) * testR;
            HitResult hr = level.clip(new ClipContext(castFrom,
                    new Vec3(mx, cy, mz),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            burst.occludedSegments[i] = hr.getType() != HitResult.Type.MISS;
        }
    }

    private static void addRing(BufferBuilder bb, org.joml.Matrix4f matrix, Burst burst,
                                float radius, float thickness, float progress,
                                float r, float g, float b, float alpha) {
        if (radius <= 0.0F || thickness <= 0.0F) return;
        Vec3 center = burst.center;
        double cy = center.y + 0.05D;
        float y = (float) cy;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            if (burst.occludedSegments[i]) continue;
            double a0 = (i / (double) RING_SEGMENTS) * Math.PI * 2.0D;
            double a1 = ((i + 1) / (double) RING_SEGMENTS) * Math.PI * 2.0D;
            float radius0 = irregularRadius(center, radius, i, progress);
            float radius1 = irregularRadius(center, radius, i + 1, progress);
            float inner0 = Math.max(0.0F, radius0 - thickness * 0.5F);
            float inner1 = Math.max(0.0F, radius1 - thickness * 0.5F);
            float outer0 = radius0 + thickness * 0.5F;
            float outer1 = radius1 + thickness * 0.5F;
            double cos0 = Math.cos(a0), sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1), sin1 = Math.sin(a1);
            // Ground-aligned (XZ plane) ring quad strip, with a lightly broken edge.
            float ix0 = (float) (center.x + cos0 * inner0);
            float iz0 = (float) (center.z + sin0 * inner0);
            float ix1 = (float) (center.x + cos1 * inner1);
            float iz1 = (float) (center.z + sin1 * inner1);
            float ox0 = (float) (center.x + cos0 * outer0);
            float oz0 = (float) (center.z + sin0 * outer0);
            float ox1 = (float) (center.x + cos1 * outer1);
            float oz1 = (float) (center.z + sin1 * outer1);
            // Soft outer edge: feather alpha to zero at the very rim.
            bb.addVertex(matrix, ix0, y, iz0).setColor(r, g, b, alpha);
            bb.addVertex(matrix, ix1, y, iz1).setColor(r, g, b, alpha);
            bb.addVertex(matrix, ox1, y, oz1).setColor(r, g, b, alpha * 0.15F);
            bb.addVertex(matrix, ox0, y, oz0).setColor(r, g, b, alpha * 0.15F);
        }
    }

    private static float irregularRadius(Vec3 center, float radius, int segment, float progress) {
        if (radius <= 0.0F) return radius;
        double phase = center.x * 0.17D + center.y * 0.11D + center.z * 0.13D;
        double slow = Math.sin(segment * 0.59D + phase + progress * 2.4D);
        double fast = Math.sin(segment * 1.37D + phase * 0.43D - progress * 3.1D);
        float offset = (float) (slow * 0.68D + fast * 0.32D);
        return radius * (1.0F + offset * SHOCKWAVE_RADIUS_JITTER);
    }

}
