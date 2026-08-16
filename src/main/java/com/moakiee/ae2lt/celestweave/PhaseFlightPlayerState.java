package com.moakiee.ae2lt.celestweave;

import net.minecraft.world.entity.player.Player;

/**
 * Player-owned phase-flight intent. Vanilla's public ability bits are only a projection of this
 * state and may be overwritten by external flight-control systems when the lock is disabled.
 */
public final class PhaseFlightPlayerState {
    private PhaseFlightPlayerState() {
    }

    /** Starts controlling flight without changing the player's current vanilla flying intent. */
    public static void activate(Player player) {
        if (player instanceof Access access && !access.ae2lt$isPhaseFlightControlled()) {
            access.ae2lt$setPhaseFlying(access.ae2lt$getVanillaFlying());
            access.ae2lt$setPhaseFlightLocked(true);
            access.ae2lt$setPhaseFlightControlled(true);
        }
    }

    public static boolean isControlled(Player player) {
        return player instanceof Access access && access.ae2lt$isPhaseFlightControlled();
    }

    public static boolean isFlying(Player player) {
        return readEffectiveFlying(player, getVanillaFlying(player));
    }

    /** Resolves one actual field read through the lock without mutating either state. */
    public static boolean readEffectiveFlying(Player player, boolean vanillaFlying) {
        if (!(player instanceof Access access)) {
            return vanillaFlying;
        }
        return PhaseFlightControlRules.effectiveFlying(
                access.ae2lt$isPhaseFlightControlled(),
                access.ae2lt$isPhaseFlightLocked(),
                access.ae2lt$isPhaseFlying(),
                vanillaFlying);
    }

    public static boolean isJumpHeld(Player player) {
        return player instanceof Access access
                && access.ae2lt$isPhaseFlightControlled()
                && access.ae2lt$isPhaseJumpHeld();
    }

    /** The only state transition for an explicit hover-flight input. */
    public static void applyFlightInput(Player player, boolean flying) {
        writeFlying(player, flying);
    }

    /** Applies the server-authoritative initial/resync value without representing local input. */
    public static void synchronizeFlying(Player player, boolean flying) {
        writeFlying(player, flying);
    }

    private static void writeFlying(Player player, boolean flying) {
        if (player instanceof Access access && access.ae2lt$isPhaseFlightControlled()) {
            access.ae2lt$setPhaseFlying(flying);
            access.ae2lt$setVanillaFlying(flying);
        }
    }

    public static void setJumpHeld(Player player, boolean jumpHeld) {
        if (player instanceof Access access && access.ae2lt$isPhaseFlightControlled()) {
            access.ae2lt$setPhaseJumpHeld(jumpHeld);
        }
    }

    public static boolean isFlightLocked(Player player) {
        return player instanceof Access access
                && access.ae2lt$isPhaseFlightControlled()
                && access.ae2lt$isPhaseFlightLocked();
    }

    public static boolean ownsMayfly(Player player) {
        return player instanceof Access access && access.ae2lt$ownsMayfly();
    }

    public static void setMayflyOwned(Player player, boolean owned) {
        if (player instanceof Access access) {
            access.ae2lt$setMayflyOwned(owned);
        }
    }

    public static void setFlightLocked(Player player, boolean locked) {
        if (!(player instanceof Access access)
                || !access.ae2lt$isPhaseFlightControlled()
                || access.ae2lt$isPhaseFlightLocked() == locked) {
            return;
        }
        if (locked) {
            access.ae2lt$setPhaseFlying(access.ae2lt$getVanillaFlying());
        } else {
            access.ae2lt$setVanillaFlying(access.ae2lt$isPhaseFlying());
        }
        access.ae2lt$setPhaseFlightLocked(locked);
    }

    /** Reads the public field without triggering the locked projection in Player#getAbilities. */
    public static boolean getVanillaFlying(Player player) {
        return player instanceof Access access
                ? access.ae2lt$getVanillaFlying()
                : player != null && player.getAbilities().flying;
    }

    /** Keeps only Celestweave-owned vanilla flight available while controls are active. */
    public static void maintainVanillaAbilities(Player player) {
        if (!isControlled(player) || !ownsMayfly(player)) {
            return;
        }
        player.getAbilities().mayfly = true;
    }

    public static void endControl(Player player) {
        if (player instanceof Access access) {
            access.ae2lt$setPhaseJumpHeld(false);
            access.ae2lt$setPhaseFlying(false);
            access.ae2lt$setPhaseFlightLocked(true);
            access.ae2lt$setMayflyOwned(false);
            access.ae2lt$setPhaseFlightControlled(false);
        }
    }

    public interface Access {
        boolean ae2lt$isPhaseFlightControlled();

        void ae2lt$setPhaseFlightControlled(boolean controlled);

        boolean ae2lt$isPhaseFlying();

        void ae2lt$setPhaseFlying(boolean flying);

        boolean ae2lt$isPhaseJumpHeld();

        void ae2lt$setPhaseJumpHeld(boolean jumpHeld);

        boolean ae2lt$isPhaseFlightLocked();

        void ae2lt$setPhaseFlightLocked(boolean locked);

        boolean ae2lt$ownsMayfly();

        void ae2lt$setMayflyOwned(boolean owned);

        boolean ae2lt$getVanillaFlying();

        void ae2lt$setVanillaFlying(boolean flying);
    }
}
