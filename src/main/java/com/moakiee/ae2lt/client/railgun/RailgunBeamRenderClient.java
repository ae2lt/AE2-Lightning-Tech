package com.moakiee.ae2lt.client.railgun;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.network.railgun.RailgunBeamUpdatePacket;

/**
 * Renders persistent left-click beams.
 *
 * <p>Visual model: each active shooter has a {@link BeamState} carrying the
 * server-supplied eye position, the impact endpoint, and bookkeeping ticks.
 * Each frame, the renderer:
 * <ul>
 *   <li>Resolves the player's current gun-barrel position (so the beam visibly
 *       emanates from the weapon, not the screen-center).</li>
 *   <li>Subdivides the beam into several short prism segments, each with
 *       independent vertex-color alpha that animates over time. This produces a
 *       slow "energy flow" that travels along the beam.</li>
 *   <li>Stacks three rectangular-prism layers per segment (outer halo + mid +
 *       core), each rotating around the beam axis at a different rate so the
 *       beam reads as a glowing cyan-white plasma stream with real volume from
 *       any viewing angle.</li>
 *   <li>Adds a camera-facing flash quad at the impact tip that tracks the
 *       breath pulse for a "burning hot endpoint" feel.</li>
 *   <li>Periodically (every {@value #ARC_INTERVAL_TICKS} ticks per beam)
 *       branches a short electric arc off a random point along the beam via
 *       {@link RailgunArcRenderer} for crackle.</li>
 * </ul>
 *
 * <p>Stale beam states (no packet for {@value #STALE_TICKS} ticks) self-expire
 * to recover from missed stop signals.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunBeamRenderClient {

    private static final Map<UUID, BeamState> ACTIVE = new ConcurrentHashMap<>();
    private static final long STALE_TICKS = 6L;
    private static final float OUTER_RADIUS = 0.20F;
    private static final float MID_RADIUS = 0.11F;
    private static final float CORE_RADIUS = 0.038F;
    /** Number of sub-segments along the beam length for the energy-flow effect. */
    private static final int BEAM_SEGMENTS = 12;
    /** How fast the breath pulse oscillates (rad / tick). Lower = slower breath. */
    private static final double PULSE_RATE = 0.30D;
    /** How fast the energy-flow stripes scroll along the beam (rad / tick). */
    private static final double FLOW_RATE = 0.22D;
    /** Self-rotation speeds (rad / tick) of each prism layer around the beam axis. */
    private static final double SPIN_OUTER = 0.18D;
    private static final double SPIN_MID = -0.32D;
    private static final double SPIN_CORE = 0.55D;
    /** Tick spacing between auto-spawned crackle arcs per beam. */
    private static final long ARC_INTERVAL_TICKS = 3L;
    private static volatile boolean localRequestedFiring = false;
    private static volatile boolean localFiring = false;

    /** Per-shooter beam state (mutable; updated in place by packet handler). */
    public static final class BeamState {
        public final UUID shooterId;
        public Vec3 from;
        public Vec3 to;
        public long lastUpdateTick;
        public long lastArcTick;

        BeamState(UUID shooterId, Vec3 f, Vec3 t, long tick) {
            this.shooterId = shooterId;
            this.from = f;
            this.to = t;
            this.lastUpdateTick = tick;
            this.lastArcTick = tick;
        }
    }

    private record BeamGeometry(Vec3 origin, Vec3 endpoint) {}

    private static BeamGeometry resolveBeamGeometry(BeamState s, Minecraft mc, float partialTick) {
        Player shooter = mc.level == null ? null : mc.level.getPlayerByUUID(s.shooterId);
        if (shooter == null) {
            return new BeamGeometry(s.from, s.to);
        }
        Vec3 origin = RailgunVisuals.computeBarrelOrigin(shooter, partialTick);
        Vec3 endpoint = RailgunVisuals.computeBarrelEndpoint(shooter, origin, s.from, s.to, partialTick);
        return new BeamGeometry(origin, endpoint);
    }

    private RailgunBeamRenderClient() {}

    public static void reset() {
        ACTIVE.clear();
        localRequestedFiring = false;
        localFiring = false;
    }

    public static void setLocalRequestedFiring(boolean firing) {
        localRequestedFiring = firing;
        if (!firing) {
            setLocalFiring(false);
        }
    }

    private static void setLocalFiring(boolean firing) {
        localFiring = firing;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!firing) {
            ACTIVE.remove(mc.player.getUUID());
        }
    }

    /** 本地玩家当前是否在持续左键开火（供音效控制器读取）。 */
    public static boolean isLocalFiring() {
        return localFiring;
    }

    public static void applyUpdate(RailgunBeamUpdatePacket p) {
        Minecraft mc = Minecraft.getInstance();
        long tick = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        if (!p.active()) {
            ACTIVE.remove(p.shooterId());
            if (mc.player != null && p.shooterId().equals(mc.player.getUUID())) {
                localRequestedFiring = false;
                localFiring = false;
            }
            return;
        }
        boolean isLocal = mc.player != null && p.shooterId().equals(mc.player.getUUID());
        if (isLocal) {
            if (!localRequestedFiring) return;
            localFiring = true;
        }
        ACTIVE.compute(p.shooterId(), (k, prev) -> {
            if (prev == null) {
                return new BeamState(p.shooterId(), p.from(), p.to(), tick);
            }
            prev.from = p.from();
            prev.to = p.to();
            prev.lastUpdateTick = tick;
            return prev;
        });
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        // Interpolation factor for the current render frame; without this every
        // {@code getEyePosition()}/{@code getYRot()} call snaps in 50 ms steps and the
        // beam visibly stutters during movement.
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        refreshLocalBeam(mc, now, partialTick);
        ACTIVE.entrySet().removeIf(en -> now - en.getValue().lastUpdateTick > STALE_TICKS);
        if (ACTIVE.isEmpty()) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack stack = e.getPoseStack();
        stack.pushPose();
        stack.translate(-camPos.x, -camPos.y, -camPos.z);

        // Depth-test ON so the beam is occluded by blocks/entities (the endpoint is
        // already clipped server- and client-side; without depth-test the prism would
        // still paint over every block, producing an X-ray beam). AFTER_TRANSLUCENT_BLOCKS
        // may leave depth-test disabled, so we enable it explicitly.
        // depthMask off so the additive prism doesn't pollute depth for later passes.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        // Additive blending for the bright plasma feel.
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Slow-breath pulse that affects all layers' alpha. Range ~0.85..1.05.
        // Use partial-tick-interpolated time for smooth animation between ticks.
        double smoothTime = now + partialTick;
        float pulse = 0.95F + 0.10F * (float) Math.sin(smoothTime * PULSE_RATE);

        var bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var matrix = stack.last().pose();
        for (BeamState s : ACTIVE.values()) {
            // Per-frame: rebuild origin AND endpoint so the beam stays parallel to the
            // rendered barrel during fast camera motion (see RailgunVisuals).
            BeamGeometry g = resolveBeamGeometry(s, mc, partialTick);
            addBeam(bb, matrix, g.origin, g.endpoint, pulse, smoothTime);
            addEndpointGlow(bb, matrix, g.endpoint, camPos, pulse);
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

        // Crackle arcs (use the shared bolt renderer; runs in its own pass).
        spawnCrackleArcs(mc, now, partialTick);
    }

    private static void refreshLocalBeam(Minecraft mc, long now, float partialTick) {
        if (!localFiring || mc.player == null || mc.level == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof ElectromagneticRailgunItem) || mc.screen != null) {
            setLocalFiring(false);
            return;
        }

        // Server update packets own the beam trace. Per-frame rendering still
        // resolves the current barrel origin/direction in resolveBeamGeometry().
    }

    /**
     * Build the prism segments along the beam. Hot path: uses primitive doubles
     * to avoid the per-frame Vec3 allocations the original implementation produced.
     */
    private static void addBeam(BufferBuilder bb, org.joml.Matrix4f matrix, Vec3 origin, Vec3 endpoint,
                                float pulse, double smoothTime) {
        double ax = endpoint.x - origin.x;
        double ay = endpoint.y - origin.y;
        double az = endpoint.z - origin.z;
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1.0E-3D) return;
        double inv = 1.0D / len;
        double dx = ax * inv, dy = ay * inv, dz = az * inv;

        // Build a fixed world-space orthonormal basis in the plane perpendicular to
        // dir. Decoupling from the camera is what turns this from a flat billboard
        // into real volume — the prism keeps its shape from any viewing angle.
        double hx, hy, hz;
        if (Math.abs(dy) < 0.95D) { hx = 0.0D; hy = 1.0D; hz = 0.0D; }
        else                       { hx = 1.0D; hy = 0.0D; hz = 0.0D; }
        // e1 = dir × helper
        double e1x = dy * hz - dz * hy;
        double e1y = dz * hx - dx * hz;
        double e1z = dx * hy - dy * hx;
        double e1l = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= e1l; e1y /= e1l; e1z /= e1l;
        // e2 = dir × e1 (already unit length, dir & e1 are perpendicular & unit)
        double e2x = dy * e1z - dz * e1y;
        double e2y = dz * e1x - dx * e1z;
        double e2z = dx * e1y - dy * e1x;

        // Each layer spins around dir at its own rate for a "twisting plasma" feel.
        double cO = Math.cos(smoothTime * SPIN_OUTER), sO = Math.sin(smoothTime * SPIN_OUTER);
        double cM = Math.cos(smoothTime * SPIN_MID),   sM = Math.sin(smoothTime * SPIN_MID);
        double cC = Math.cos(smoothTime * SPIN_CORE),  sC = Math.sin(smoothTime * SPIN_CORE);
        // Rotated bases: u = e1*cos + e2*sin, v = -e1*sin + e2*cos
        double oUx = e1x * cO + e2x * sO, oUy = e1y * cO + e2y * sO, oUz = e1z * cO + e2z * sO;
        double oVx = -e1x * sO + e2x * cO, oVy = -e1y * sO + e2y * cO, oVz = -e1z * sO + e2z * cO;
        double mUx = e1x * cM + e2x * sM, mUy = e1y * cM + e2y * sM, mUz = e1z * cM + e2z * sM;
        double mVx = -e1x * sM + e2x * cM, mVy = -e1y * sM + e2y * cM, mVz = -e1z * sM + e2z * cM;
        double cUx = e1x * cC + e2x * sC, cUy = e1y * cC + e2y * sC, cUz = e1z * cC + e2z * sC;
        double cVx = -e1x * sC + e2x * cC, cVy = -e1y * sC + e2y * cC, cVz = -e1z * sC + e2z * cC;

        double ox = origin.x, oy = origin.y, oz = origin.z;

        // Sub-segment the beam so vertex-color interpolation can paint a traveling
        // energy wave along its length. Each segment also slightly tapers from the
        // muzzle (thicker) to the impact tip (thinner) for a sense of depth.
        for (int i = 0; i < BEAM_SEGMENTS; i++) {
            float t0 = i / (float) BEAM_SEGMENTS;
            float t1 = (i + 1) / (float) BEAM_SEGMENTS;
            double afx = ox + ax * t0, afy = oy + ay * t0, afz = oz + az * t0;
            double atx = ox + ax * t1, aty = oy + ay * t1, atz = oz + az * t1;
            // Subtle taper: 1.0 at the muzzle, 0.78 at the tip.
            float taper0 = 1.0F - 0.22F * t0;
            float taper1 = 1.0F - 0.22F * t1;
            // Energy flow: bright "stripes" travel from muzzle toward impact (smooth between ticks).
            float flow0 = 0.55F + 0.45F * (float) Math.sin(t0 * 9.0F - smoothTime * FLOW_RATE);
            float flow1 = 0.55F + 0.45F * (float) Math.sin(t1 * 9.0F - smoothTime * FLOW_RATE);
            float a0 = pulse * flow0;
            float a1 = pulse * flow1;

            // Outer wide blue halo — soft and large.
            addPrismSegment(bb, matrix, afx, afy, afz, atx, aty, atz,
                    oUx, oUy, oUz, oVx, oVy, oVz,
                    OUTER_RADIUS * taper0, OUTER_RADIUS * taper1,
                    0.22F, 0.58F, 1.00F, 0.18F * a0, 0.18F * a1);
            // Mid bright cyan — most of the beam's color comes from here.
            addPrismSegment(bb, matrix, afx, afy, afz, atx, aty, atz,
                    mUx, mUy, mUz, mVx, mVy, mVz,
                    MID_RADIUS * taper0, MID_RADIUS * taper1,
                    0.52F, 0.88F, 1.00F, 0.34F * a0, 0.34F * a1);
            // Hot near-white core — narrow and bright, with extra pulse intensity.
            float coreA0 = Math.min(1.0F, 1.10F * a0);
            float coreA1 = Math.min(1.0F, 1.10F * a1);
            addPrismSegment(bb, matrix, afx, afy, afz, atx, aty, atz,
                    cUx, cUy, cUz, cVx, cVy, cVz,
                    CORE_RADIUS * taper0, CORE_RADIUS * taper1,
                    0.95F, 1.00F, 1.00F, 0.70F * coreA0, 0.70F * coreA1);
        }
    }

    /**
     * Draw a hot camera-facing flash quad at the beam's impact point. Sells the
     * "burning into the surface" feel; tracks the breath pulse.
     */
    private static void addEndpointGlow(BufferBuilder bb, org.joml.Matrix4f matrix, Vec3 center,
                                        Vec3 cameraPos, float pulse) {
        double tcx = cameraPos.x - center.x;
        double tcy = cameraPos.y - center.y;
        double tcz = cameraPos.z - center.z;
        double tcLenSqr = tcx * tcx + tcy * tcy + tcz * tcz;
        if (tcLenSqr < 1.0E-6D) return;
        double tcInv = 1.0D / Math.sqrt(tcLenSqr);
        double fx = tcx * tcInv, fy = tcy * tcInv, fz = tcz * tcInv;

        // right = fwd × up; fall back if degenerate.
        double rx = fy * 0.0D - fz * 1.0D;       // = -fz
        double ry = fz * 0.0D - fx * 0.0D;       // = 0
        double rz = fx * 1.0D - fy * 0.0D;       // = fx
        double rLenSqr = rx * rx + ry * ry + rz * rz;
        if (rLenSqr < 1.0E-9D) {
            // Fallback: fwd × (1,0,0) = (0, fz, -fy)
            rx = 0.0D; ry = fz; rz = -fy;
            rLenSqr = ry * ry + rz * rz;
        }
        float radius = 0.55F * pulse;
        double rNormScale = radius / Math.sqrt(rLenSqr);
        rx *= rNormScale; ry *= rNormScale; rz *= rNormScale;
        // up = right × fwd, then renormalize and scale to radius
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;
        double uLen = Math.sqrt(ux * ux + uy * uy + uz * uz);
        double uScale = radius / uLen;
        ux *= uScale; uy *= uScale; uz *= uScale;

        double cx = center.x, cy = center.y, cz = center.z;
        // Outer faded quad
        emitGlowQuad(bb, matrix,
                cx + rx + ux, cy + ry + uy, cz + rz + uz,
                cx - rx + ux, cy - ry + uy, cz - rz + uz,
                cx - rx - ux, cy - ry - uy, cz - rz - uz,
                cx + rx - ux, cy + ry - uy, cz + rz - uz,
                0.65F, 0.90F, 1.00F, 0.05F * pulse);
        // Inner bright core (45% radius)
        double irx = rx * 0.45D, iry = ry * 0.45D, irz = rz * 0.45D;
        double iux = ux * 0.45D, iuy = uy * 0.45D, iuz = uz * 0.45D;
        emitGlowQuad(bb, matrix,
                cx + irx + iux, cy + iry + iuy, cz + irz + iuz,
                cx - irx + iux, cy - iry + iuy, cz - irz + iuz,
                cx - irx - iux, cy - iry - iuy, cz - irz - iuz,
                cx + irx - iux, cy + iry - iuy, cz + irz - iuz,
                1.00F, 1.00F, 1.00F, 0.90F * pulse);
    }

    private static void emitGlowQuad(BufferBuilder bb, org.joml.Matrix4f matrix,
                                     double p0x, double p0y, double p0z,
                                     double p1x, double p1y, double p1z,
                                     double p2x, double p2y, double p2z,
                                     double p3x, double p3y, double p3z,
                                     float r, float g, float b, float a) {
        bb.addVertex(matrix, (float) p0x, (float) p0y, (float) p0z).setColor(r, g, b, a);
        bb.addVertex(matrix, (float) p1x, (float) p1y, (float) p1z).setColor(r, g, b, a);
        bb.addVertex(matrix, (float) p2x, (float) p2y, (float) p2z).setColor(r, g, b, a);
        bb.addVertex(matrix, (float) p3x, (float) p3y, (float) p3z).setColor(r, g, b, a);
    }

    /**
     * Emit the four side faces of a rectangular prism segment whose cross-section
     * is aligned to (uX,uY,uZ) and (vX,vY,vZ) — a pre-rotated orthonormal pair in
     * the plane perpendicular to the beam axis. Radii may differ at each end for
     * taper. End caps are hidden by the endpoint glow and the muzzle.
     */
    private static void addPrismSegment(BufferBuilder bb, org.joml.Matrix4f matrix,
                                        double fx, double fy, double fz,
                                        double tx, double ty, double tz,
                                        double uX, double uY, double uZ,
                                        double vX, double vY, double vZ,
                                        float radiusFrom, float radiusTo,
                                        float r, float g, float b,
                                        float aFrom, float aTo) {
        double uFx = uX * radiusFrom, uFy = uY * radiusFrom, uFz = uZ * radiusFrom;
        double vFx = vX * radiusFrom, vFy = vY * radiusFrom, vFz = vZ * radiusFrom;
        double uTx = uX * radiusTo,   uTy = uY * radiusTo,   uTz = uZ * radiusTo;
        double vTx = vX * radiusTo,   vTy = vY * radiusTo,   vTz = vZ * radiusTo;
        // Four corners at each end (CCW from the muzzle looking down the beam).
        double f0x = fx + uFx + vFx, f0y = fy + uFy + vFy, f0z = fz + uFz + vFz;
        double f1x = fx - uFx + vFx, f1y = fy - uFy + vFy, f1z = fz - uFz + vFz;
        double f2x = fx - uFx - vFx, f2y = fy - uFy - vFy, f2z = fz - uFz - vFz;
        double f3x = fx + uFx - vFx, f3y = fy + uFy - vFy, f3z = fz + uFz - vFz;
        double t0x = tx + uTx + vTx, t0y = ty + uTy + vTy, t0z = tz + uTz + vTz;
        double t1x = tx - uTx + vTx, t1y = ty - uTy + vTy, t1z = tz - uTz + vTz;
        double t2x = tx - uTx - vTx, t2y = ty - uTy - vTy, t2z = tz - uTz - vTz;
        double t3x = tx + uTx - vTx, t3y = ty + uTy - vTy, t3z = tz + uTz - vTz;
        emitFace(bb, matrix, f0x, f0y, f0z, f1x, f1y, f1z, t1x, t1y, t1z, t0x, t0y, t0z, r, g, b, aFrom, aTo);
        emitFace(bb, matrix, f1x, f1y, f1z, f2x, f2y, f2z, t2x, t2y, t2z, t1x, t1y, t1z, r, g, b, aFrom, aTo);
        emitFace(bb, matrix, f2x, f2y, f2z, f3x, f3y, f3z, t3x, t3y, t3z, t2x, t2y, t2z, r, g, b, aFrom, aTo);
        emitFace(bb, matrix, f3x, f3y, f3z, f0x, f0y, f0z, t0x, t0y, t0z, t3x, t3y, t3z, r, g, b, aFrom, aTo);
    }

    private static void emitFace(BufferBuilder bb, org.joml.Matrix4f matrix,
                                 double p0x, double p0y, double p0z,
                                 double p1x, double p1y, double p1z,
                                 double p2x, double p2y, double p2z,
                                 double p3x, double p3y, double p3z,
                                 float r, float g, float b,
                                 float aFrom, float aTo) {
        bb.addVertex(matrix, (float) p0x, (float) p0y, (float) p0z).setColor(r, g, b, aFrom);
        bb.addVertex(matrix, (float) p1x, (float) p1y, (float) p1z).setColor(r, g, b, aFrom);
        bb.addVertex(matrix, (float) p2x, (float) p2y, (float) p2z).setColor(r, g, b, aTo);
        bb.addVertex(matrix, (float) p3x, (float) p3y, (float) p3z).setColor(r, g, b, aTo);
    }

    /**
     * Periodically spawn small crackle arcs branching off the beam to sell the
     * "high voltage current" feel. Throttled per-beam by {@link BeamState#lastArcTick}.
     */
    private static void spawnCrackleArcs(Minecraft mc, long now, float partialTick) {
        if (mc.level == null) return;
        for (BeamState s : ACTIVE.values()) {
            if (now - s.lastArcTick < ARC_INTERVAL_TICKS) continue;
            s.lastArcTick = now;
            BeamGeometry g = resolveBeamGeometry(s, mc, partialTick);
            Vec3 axis = g.endpoint.subtract(g.origin);
            double len = axis.length();
            if (len < 1.0D) continue;
            // 1-2 small arcs per pulse
            int n = 1 + mc.level.random.nextInt(2);
            for (int i = 0; i < n; i++) {
                double t = 0.10D + mc.level.random.nextDouble() * 0.85D;
                Vec3 fromArc = g.origin.add(axis.scale(t));
                Vec3 randDir = new Vec3(
                        mc.level.random.nextDouble() - 0.5D,
                        mc.level.random.nextDouble() - 0.5D,
                        mc.level.random.nextDouble() - 0.5D);
                if (randDir.lengthSqr() < 1.0E-6D) continue;
                randDir = randDir.normalize().scale(0.4D + mc.level.random.nextDouble() * 0.7D);
                Vec3 toArc = fromArc.add(randDir);
                RailgunArcRenderer.spawnBeamSpark(fromArc, toArc, 14 + mc.level.random.nextInt(8));
            }
        }
    }
}
