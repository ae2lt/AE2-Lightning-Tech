package com.moakiee.ae2lt.celestweave;

import net.minecraft.world.entity.player.Player;

/** Precision hovering uses ordinary ground movement attributes, without accumulated momentum. */
public final class FlightSneakMovement {
    // A fixed ordinary-ground reference for hovering, independent of blocks below the player.
    // This converts acceleration to steady movement; it is not a sneaking-speed multiplier.
    private static final double GROUND_DRAG = 0.6D * 0.91D;

    private FlightSneakMovement() {
    }

    public static boolean isActive(Player player) {
        return isActive(player, PhaseFlightPlayerState.isJumpHeld(player), player.isShiftKeyDown());
    }

    public static boolean isActive(Player player, boolean jumpHeld, boolean shiftHeld) {
        return isActive(
                PhaseFlightPlayerState.isControlled(player),
                PhaseFlightPlayerState.isFlying(player),
                player.isFallFlying(),
                player.isPassenger(),
                jumpHeld,
                shiftHeld,
                PhaseFlightControlRules.exposeGroundCrouch(
                        PhaseFlightPlayerState.isFlightLocked(player),
                        PhaseFlightMovementGuard.isPhaseModeEnabled(player),
                        PhaseFlightPlayerState.isFlying(player),
                        player.onGround(),
                        shiftHeld));
    }

    static boolean isActive(boolean controlled, boolean flying, boolean gliding, boolean passenger,
            boolean jumpHeld, boolean shiftHeld, boolean groundCrouch) {
        return controlled && flying && !gliding && !passenger
                && (jumpHeld && shiftHeld || groundCrouch);
    }

    public static float movementSpeed(float walkingSpeed) {
        // getSpeed and movement input retain attribute, enchantment and input-event modifiers;
        // 1.20.1 applies the base sneak factor and Swift Sneak in Input.tick, followed by
        // Forge's movement-input event. Do not multiply that input factor again here.
        return (float) (walkingSpeed / (1.0D - GROUND_DRAG));
    }
}
