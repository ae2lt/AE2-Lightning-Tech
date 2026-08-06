package com.moakiee.ae2lt.celestweave;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;

/**
 * Player-owned phase-flight intent. Vanilla's public ability bits are only a projection of this
 * state and may be overwritten by bosses or other flight-denial systems.
 */
public final class PhaseFlightPlayerState {
    private static final Map<Abilities, WeakReference<Player>> ABILITY_OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PhaseFlightPlayerState() {
    }

    /** Activating the phase module directly enables its private flight state. */
    public static void activate(Player player) {
        if (player instanceof Access access && !access.ae2lt$isPhaseFlightControlled()) {
            access.ae2lt$setPhaseFlying(true);
            access.ae2lt$setPhaseFlightControlled(true);
        }
        registerAbilityOwner(player);
    }

    public static boolean isControlled(Player player) {
        return player instanceof Access access && access.ae2lt$isPhaseFlightControlled();
    }

    public static boolean isFlying(Player player) {
        return player instanceof Access access
                && access.ae2lt$isPhaseFlightControlled()
                && access.ae2lt$isPhaseFlying();
    }

    public static void setFlying(Player player, boolean flying) {
        if (player instanceof Access access && access.ae2lt$isPhaseFlightControlled()) {
            access.ae2lt$setPhaseFlying(flying);
        }
    }

    /** Mirrors private intent into the vanilla fields inspected by movement and anti-cheat code. */
    public static void syncVanillaAbilities(Player player) {
        if (!(player instanceof Access access) || !access.ae2lt$isPhaseFlightControlled()) {
            return;
        }
        var abilities = player.getAbilities();
        ABILITY_OWNERS.put(abilities, new WeakReference<>(player));
        abilities.mayfly = true;
        abilities.flying = access.ae2lt$isPhaseFlying();
    }

    /** Used by optional compatibility hooks around direct public-field writes. */
    public static boolean rejectsExternalFlyingDisable(Abilities abilities) {
        if (abilities == null) {
            return false;
        }
        WeakReference<Player> reference = ABILITY_OWNERS.get(abilities);
        Player player = reference == null ? null : reference.get();
        if (player == null) {
            ABILITY_OWNERS.remove(abilities);
            return false;
        }
        return isFlying(player);
    }

    public static void endControl(Player player) {
        if (player instanceof Access access) {
            ABILITY_OWNERS.remove(player.getAbilities());
            access.ae2lt$setPhaseFlying(false);
            access.ae2lt$setPhaseFlightControlled(false);
        }
    }

    private static void registerAbilityOwner(Player player) {
        if (player != null) {
            ABILITY_OWNERS.put(player.getAbilities(), new WeakReference<>(player));
        }
    }

    public interface Access {
        boolean ae2lt$isPhaseFlightControlled();

        void ae2lt$setPhaseFlightControlled(boolean controlled);

        boolean ae2lt$isPhaseFlying();

        void ae2lt$setPhaseFlying(boolean flying);
    }
}
