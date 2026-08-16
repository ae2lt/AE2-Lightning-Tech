package com.moakiee.ae2lt.celestweave;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.celestweave.module.FlightSpeedOption;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

/** Shared Elytra authorization and thrust for real and phase-locked Celestweave chest items. */
public final class PhaseWingFlight {
    private static final double FIREWORK_FORWARD_ACCELERATION = 0.1D;
    private static final double FIREWORK_TARGET_SPEED = 1.5D;
    private static final double FIREWORK_STEERING = 0.5D;

    private PhaseWingFlight() {
    }

    public static boolean canUse(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return CelestweaveArmorState.isAnyClientFlightControlActive();
        }
        for (var active : ArmorCapabilityCollector.collectPerInstalledStack(player)) {
            if (active.capability() instanceof DeviceCapability.ElytraFlight) {
                return true;
            }
        }
        return false;
    }

    public static boolean canElytraFly(LivingEntity entity) {
        return entity instanceof Player player
                && canUse(player)
                && !PhaseFlightPlayerState.isFlying(player);
    }

    public static boolean elytraFlightTick(LivingEntity entity) {
        return canElytraFly(entity);
    }

    public static boolean isFlightActive(Player player) {
        return player != null
                && (PhaseFlightPlayerState.isFlying(player)
                        || player.isFallFlying() && canUse(player));
    }

    public static void tickThrust(Player player) {
        if (player == null
                || !player.isFallFlying()
                || !PhaseFlightPlayerState.isJumpHeld(player)
                || !canUse(player)) {
            return;
        }
        double speedMultiplier = thrustMultiplier(player.getAbilities().getFlyingSpeed());
        Vec3 target = fireworkThrust(
                player.getDeltaMovement(),
                player.getLookAngle(),
                speedMultiplier);
        PhaseFlightMovementGuard.runAsSelfMovement(player, () -> player.setDeltaMovement(target));
        player.hurtMarked = true;
    }

    /** Matches the velocity update used by a firework rocket attached to a gliding player. */
    static double thrustMultiplier(float flyingSpeed) {
        return Math.max(1.0D, flyingSpeed / FlightSpeedOption.VANILLA_FLYING_SPEED);
    }

    static Vec3 fireworkThrust(Vec3 motion, Vec3 look, double speedMultiplier) {
        double forwardAcceleration = FIREWORK_FORWARD_ACCELERATION * speedMultiplier;
        double targetSpeed = FIREWORK_TARGET_SPEED * speedMultiplier;
        return motion.add(
                look.x * forwardAcceleration
                        + (look.x * targetSpeed - motion.x) * FIREWORK_STEERING,
                look.y * forwardAcceleration
                        + (look.y * targetSpeed - motion.y) * FIREWORK_STEERING,
                look.z * forwardAcceleration
                        + (look.z * targetSpeed - motion.z) * FIREWORK_STEERING);
    }
}
