package com.moakiee.ae2lt.client.railgun;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;

/**
 * Client-side visual utilities for railgun beams. Each frame, callers compose:
 *   origin    = {@link #computeBarrelOrigin}
 *   direction = {@link #computeBarrelDirection}
 *   endpoint  = {@link #computeBarrelEndpoint}
 *
 * <p>Third-person beam direction uses {@code yHeadRot} (not {@code yRot}). The
 * model head is driven by {@code yHeadRot}, which only syncs to {@code yRot}
 * once per tick in {@code Player#aiStep}; using {@code yRot} here would race
 * ahead of the rendered barrel by up to one tick (~50 ms) during fast camera
 * motion. Server-supplied {@code (to - from)} is also unsuitable for direction
 * since it's frozen at packet-send time.
 *
 * <p>First-person barrel tracks the hand rendering's subtle rotation offset
 * (from {@code ItemInHandRenderer.renderHandsWithItems}), which applies a
 * small lag derived from {@code xBob}/{@code yBob} smoothing. Without this
 * the beam snaps to the camera while the held gun model visibly lags behind.
 */
public final class RailgunVisuals {

    // First-person muzzle offsets (view-space). Origin sits at the gun's
    // centerline between the four rails, slightly forward of the receiver —
    // makes the beam appear to emerge from inside the rail cage.
    private static final double FP_SIDE_OFFSET = 0.56D;
    private static final double FP_FORWARD_OFFSET = 1.40D;
    private static final double FP_VERTICAL_OFFSET = -0.40D;

    // Third-person muzzle geometry, derived from the model:
    //   arm pivot (HumanoidModel): X = ±5/16, Y = body pivot + 2/16 down
    //   muzzle = front opening of the rail bore. Only top (y=12..13) and
    //   bottom (y=8..9) rails reach z=-10; we anchor the beam at the bottom
    //   rail's upper edge (y=9) which reads visually as "out of the barrel".
    private static final double TP_SHOULDER_HEIGHT_FACTOR = 0.764D; // 1.376 / 1.8
    private static final double TP_SHOULDER_SIDE = 0.3125D;          // 5/16
    private static final double TP_BARREL_OUTWARD = 0.0625D;         // 1/16
    private static final double TP_BARREL_ALONG_ARM = 1.331D;
    private static final double TP_BARREL_PERP_UP = 0.180D;
    // Arm tilt vs. horizontal: arm.xRot baseline (-1.48) is 5.2° below -π/2.
    private static final float TP_ARM_PITCH_OFFSET_DEG =
            (float) Math.toDegrees(RailgunClientExtensions.MAIN_ARM_X_ROT_BASE + Math.PI / 2.0D);

    private RailgunVisuals() {}

    /** World-space muzzle position for the given player at the current frame. */
    public static Vec3 computeBarrelOrigin(Player player, float partialTick) {
        if (isLocalFirstPerson(player)) {
            return computeFirstPersonBarrelOrigin(player, partialTick);
        }
        return computeThirdPersonBarrelOrigin(player, partialTick);
    }

    /**
     * Normalized barrel-aligned direction. Local first-person uses the camera
     * look vector adjusted by the hand-rendering offset so the beam visually
     * tracks the held gun model. Third-person / remote use
     * {@code yHeadRot + xRot} to match the rendered model head.
     */
    public static Vec3 computeBarrelDirection(Player player, float partialTick) {
        if (isLocalFirstPerson(player)) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 look = fromJoml(camera.getLookVector());
            if (look.lengthSqr() < 1.0E-9D) {
                return new Vec3(1.0D, 0.0D, 0.0D);
            }
            look = look.normalize();
            Vec3 right = fromJoml(camera.getLeftVector()).scale(-1.0D).normalize();
            Vec3 up = right.cross(look).normalize();
            // Apply the same subtle rotation offset that ItemInHandRenderer
            // adds in renderHandsWithItems — (viewRot - bobSmoothed) * 0.1.
            float pitchOff = fpHandPitchOffset(player, partialTick);
            float yawOff = fpHandYawOffset(player, partialTick);
            if (Math.abs(pitchOff) > 0.001F || Math.abs(yawOff) > 0.001F) {
                look = rotateDegrees(look, right, pitchOff);
                look = rotateDegrees(look, up, yawOff);
            }
            return look.normalize();
        }
        float yaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        return Vec3.directionFromRotation(pitch, yaw);
    }

    /**
     * Compose the beam endpoint: muzzle + barrelDir * length, where length is
     * the server-reported travel distance. For remote players, an additional
     * client raycast clamps the endpoint to nearby geometry so the beam never
     * appears to dangle in midair during a fast head whip.
     */
    public static Vec3 computeBarrelEndpoint(Player player, Vec3 origin,
                                             Vec3 shotFrom, Vec3 shotTo, float partialTick) {
        Vec3 dir = computeBarrelDirection(player, partialTick);
        double length = shotTo.subtract(shotFrom).length();
        if (length < 1.0E-3D) {
            return origin;
        }
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player && mc.level != null) {
            Vec3 maxEnd = origin.add(dir.scale(RailgunDefaults.BEAM_RANGE));
            HitResult hit = mc.level.clip(new ClipContext(
                    origin, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                double clipLen = hit.getLocation().subtract(origin).length();
                length = Math.min(length, clipLen);
            }
        }
        return origin.add(dir.scale(length));
    }

    /** Current frame's partial tick from Minecraft's delta tracker. */
    public static float currentPartialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    // ── First-person hand-rendering offset ────────────────────────────────

    /**
     * {@code ItemInHandRenderer.renderHandsWithItems} applies a subtle rotation
     * derived from the gap between the current view rotation and the smoothed
     * {@code xBob}/{@code yBob} values (which exponentially approach the real
     * rotation at factor 0.5 per tick). The hand therefore lags behind the
     * camera during fast rotation. We replicate this offset so the beam
     * visually matches the held gun model.
     */
    private static float fpHandPitchOffset(Player player, float partialTick) {
        if (!(player instanceof LocalPlayer lp)) return 0F;
        float xBobLerp = Mth.lerp(partialTick, lp.xBobO, lp.xBob);
        return (lp.getViewXRot(partialTick) - xBobLerp) * 0.1F;
    }

    private static float fpHandYawOffset(Player player, float partialTick) {
        if (!(player instanceof LocalPlayer lp)) return 0F;
        float yBobLerp = Mth.lerp(partialTick, lp.yBobO, lp.yBob);
        return (lp.getViewYRot(partialTick) - yBobLerp) * 0.1F;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static boolean isLocalFirstPerson(Player player) {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    private static Vec3 computeFirstPersonBarrelOrigin(Player player, float partialTick) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        Vec3 look = fromJoml(camera.getLookVector()).normalize();
        Vec3 right = fromJoml(camera.getLeftVector()).scale(-1.0D).normalize();
        Vec3 up = right.cross(look).normalize();
        double sideMul = holdingArm(player) == HumanoidArm.LEFT ? -1.0D : 1.0D;
        Vec3 offset = look.scale(FP_FORWARD_OFFSET)
                .add(right.scale(FP_SIDE_OFFSET * sideMul))
                .add(up.scale(FP_VERTICAL_OFFSET));
        // Apply the same hand-rendering rotation offset as direction.
        float pitchOff = fpHandPitchOffset(player, partialTick);
        float yawOff = fpHandYawOffset(player, partialTick);
        if (Math.abs(pitchOff) > 0.001F || Math.abs(yawOff) > 0.001F) {
            offset = rotateDegrees(offset, right, pitchOff);
            offset = rotateDegrees(offset, up, yawOff);
        }
        return eye.add(offset);
    }

    /**
     * Third-person muzzle: shoulder anchored on {@code yBodyRot}, then offset
     * by the model-derived (along-arm, perpendicular-up, outward) triple so
     * the visual origin lines up with the rendered barrel mouth.
     */
    private static Vec3 computeThirdPersonBarrelOrigin(Player player, float partialTick) {
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float bodyYawRad = bodyYaw * (float) (Math.PI / 180.0D);
        Vec3 bodyRight = new Vec3(-Math.cos(bodyYawRad), 0.0D, -Math.sin(bodyYawRad));
        double sideMul = holdingArm(player) == HumanoidArm.LEFT ? -1.0D : 1.0D;
        // The arm baseline is 5.2° below horizontal: the muzzle is on the arm,
        // not the line-of-sight, and rides above the hand.
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float viewPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float armPitch = viewPitch + TP_ARM_PITCH_OFFSET_DEG;
        Vec3 armDir = Vec3.directionFromRotation(armPitch, headYaw);
        Vec3 armPerpUp = Vec3.directionFromRotation(armPitch - 90F, headYaw);
        return player.getPosition(partialTick)
                .add(0.0D, player.getBbHeight() * TP_SHOULDER_HEIGHT_FACTOR, 0.0D)
                .add(bodyRight.scale((TP_SHOULDER_SIDE + TP_BARREL_OUTWARD) * sideMul))
                .add(armDir.scale(TP_BARREL_ALONG_ARM))
                .add(armPerpUp.scale(TP_BARREL_PERP_UP));
    }

    /** Rodrigues rotation of v around unit axis by angle in degrees. */
    private static Vec3 rotateDegrees(Vec3 v, Vec3 axis, float deg) {
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        // v * cos + (axis × v) * sin + axis * (axis · v) * (1 - cos)
        double dot = axis.dot(v);
        Vec3 cross = axis.cross(v);
        return v.scale(cos).add(cross.scale(sin)).add(axis.scale(dot * (1.0D - cos)));
    }

    private static Vec3 fromJoml(Vector3f v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static HumanoidArm holdingArm(Player player) {
        if (player.getMainHandItem().getItem() instanceof ElectromagneticRailgunItem) {
            return player.getMainArm();
        }
        if (player.getOffhandItem().getItem() instanceof ElectromagneticRailgunItem) {
            return player.getMainArm().getOpposite();
        }
        return player.getUsedItemHand() == InteractionHand.OFF_HAND
                ? player.getMainArm().getOpposite()
                : player.getMainArm();
    }
}
