package com.moakiee.ae2lt.logic.railgun;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Shared target admission rules for every server-side railgun damage path. */
public final class RailgunTargetRules {

    private RailgunTargetRules() {}

    /**
     * Returns whether {@code target} may be affected by this shot.
     *
     * <p>Living entities retain the railgun's broad targeting semantics and do not need
     * to opt into vanilla projectile picking. Non-living entities must explicitly report
     * that projectiles may hit them. Player-only state stays in the player branch, and
     * fake players are never admitted to a damage or death pipeline.
     */
    public static boolean canAffect(
            @Nullable Entity shooter,
            @Nullable Entity target,
            boolean allowPlayerTargets) {
        if (target == null || target == shooter) return false;

        if (target instanceof Player targetPlayer) {
            return allowPlayerTargets
                    && !targetPlayer.isFakePlayer()
                    && !targetPlayer.isSpectator()
                    && !targetPlayer.isCreative()
                    && targetPlayer.isAlive()
                    && !targetPlayer.isDeadOrDying();
        }

        if (target instanceof LivingEntity living) {
            return living.isAlive() && !living.isDeadOrDying();
        }

        return target.canBeHitByProjectile();
    }
}
