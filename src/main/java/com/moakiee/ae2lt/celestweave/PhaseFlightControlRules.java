package com.moakiee.ae2lt.celestweave;

import net.minecraft.world.entity.player.Player;

/** Shared rules for separating an explicit flight toggle from vanilla landing behavior. */
public final class PhaseFlightControlRules {
    private PhaseFlightControlRules() {
    }

    public static boolean rejectFlightToggle(
            boolean phaseModeEnabled,
            boolean insideWall,
            boolean requestedFlying) {
        return phaseModeEnabled && insideWall && !requestedFlying;
    }

    public static boolean suppressLandingExit(boolean phaseModeEnabled) {
        return phaseModeEnabled;
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
