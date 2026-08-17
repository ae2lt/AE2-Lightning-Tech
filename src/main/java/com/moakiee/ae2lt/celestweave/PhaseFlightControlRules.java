package com.moakiee.ae2lt.celestweave;

import net.minecraft.world.entity.player.Player;

/** Shared input rules for phase-flight state transitions. */
public final class PhaseFlightControlRules {
    private PhaseFlightControlRules() {
    }

    public static boolean rejectFlightToggle(
            boolean phaseModeEnabled,
            boolean insideWall,
            boolean requestedFlying) {
        return phaseModeEnabled && insideWall && !requestedFlying;
    }

    public static boolean isCrouchChord(
            boolean flightControlActive,
            boolean jumpHeld,
            boolean shiftHeld) {
        return flightControlActive && jumpHeld && shiftHeld;
    }

    public static boolean preserveFlightOnLanding(
            boolean flightLocked,
            boolean phaseModeEnabled,
            boolean phaseFlying) {
        return flightLocked && !phaseModeEnabled && phaseFlying;
    }

    public static boolean effectiveFlying(
            boolean controlled,
            boolean flightLocked,
            boolean phaseFlying,
            boolean vanillaFlying) {
        return controlled && flightLocked ? phaseFlying : vanillaFlying;
    }

    public static boolean exposeGroundCrouch(
            boolean flightLocked,
            boolean phaseModeEnabled,
            boolean phaseFlying,
            boolean onGround,
            boolean shiftHeld) {
        return flightLocked && !phaseModeEnabled && phaseFlying && onGround && shiftHeld;
    }

    /**
     * Uses the full player bounding box instead of {@link Player#isInWall()}, whose suffocation
     * probe can report clear while another part of a phase-flying player still overlaps a block.
     */
    public static boolean intersectsWorldCollision(Player player) {
        return player != null
                && !player.level().noCollision(player, player.getBoundingBox());
    }
}
