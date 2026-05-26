package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.overload.armor.ArmorDynamicLoadRules;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public final class PhaseFlightSubmodule extends AbstractOverloadArmorSubmodule {

    public static final PhaseFlightSubmodule INSTANCE = new PhaseFlightSubmodule();

    private static final String TAG_HAD_MAYFLY = "PhaseHadMayfly";
    private static final String TAG_WAS_FLYING = "PhaseWasFlying";
    private static final String TAG_PREVIOUS_SPEED = "PhasePreviousFlyingSpeed";
    private static final String PLAYER_PHASE_TAG = "ae2lt.phase_flight.active";
    private static final float DEFAULT_FLYING_SPEED = 0.05F;

    private PhaseFlightSubmodule() {
    }

    @Override
    public String id() {
        return "phase_flight";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.phase_flight.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.phase_flight.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            grantPhaseFlight(player, armor);
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            revokePhaseFlight(player, armor);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player == null || dist != Dist.DEDICATED_SERVER || !AE2LTCommonConfig.overloadArmorPhaseFlightEnabled()) {
            if (player != null && dist == Dist.DEDICATED_SERVER) {
                revokePhaseFlight(player, armor);
            }
            return 0;
        }

        grantPhaseFlight(player, armor);
        player.noPhysics = true;
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        applyPhaseMotion(player);
        return ArmorDynamicLoadRules.phaseFlightStateLoad(
                true,
                player.isInWall(),
                AE2LTCommonConfig.overloadArmorPhaseFlightBaseLoad(),
                AE2LTCommonConfig.overloadArmorPhaseFlightInsideBlockLoad());
    }

    private static void grantPhaseFlight(Player player, ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        var abilities = player.getAbilities();
        player.getPersistentData().putBoolean(PLAYER_PHASE_TAG, true);
        if (!data.contains(TAG_HAD_MAYFLY, CompoundTag.TAG_BYTE)) {
            data.putBoolean(TAG_HAD_MAYFLY, abilities.mayfly);
            data.putBoolean(TAG_WAS_FLYING, abilities.flying);
            data.putFloat(TAG_PREVIOUS_SPEED, abilities.getFlyingSpeed());
            OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
        }
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed((float) Math.max(
                DEFAULT_FLYING_SPEED,
                DEFAULT_FLYING_SPEED * AE2LTCommonConfig.overloadArmorPhaseFlightSpeedMultiplier()));
        player.onUpdateAbilities();
    }

    private static void revokePhaseFlight(Player player, ItemStack armor) {
        player.noPhysics = false;
        player.setNoGravity(false);
        player.getPersistentData().remove(PLAYER_PHASE_TAG);
        if (player.isInWall()) {
            escapeFromBlocks(player);
        }

        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        boolean hadMayfly = data.contains(TAG_HAD_MAYFLY, CompoundTag.TAG_BYTE) && data.getBoolean(TAG_HAD_MAYFLY);
        boolean wasFlying = data.contains(TAG_WAS_FLYING, CompoundTag.TAG_BYTE) && data.getBoolean(TAG_WAS_FLYING);
        float previousSpeed = data.contains(TAG_PREVIOUS_SPEED, CompoundTag.TAG_FLOAT)
                ? data.getFloat(TAG_PREVIOUS_SPEED)
                : DEFAULT_FLYING_SPEED;
        data.remove(TAG_HAD_MAYFLY);
        data.remove(TAG_WAS_FLYING);
        data.remove(TAG_PREVIOUS_SPEED);
        OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);

        var abilities = player.getAbilities();
        if (player.isCreative() || player.isSpectator()) {
            abilities.setFlyingSpeed(previousSpeed > 0.0F ? previousSpeed : DEFAULT_FLYING_SPEED);
            player.onUpdateAbilities();
            return;
        }
        boolean otherFlightActive = OverloadArmorState.isSubmoduleRuntimeActive(armor, FlightSubmodule.INSTANCE.id());
        abilities.mayfly = hadMayfly || otherFlightActive;
        abilities.flying = (wasFlying || otherFlightActive) && abilities.mayfly;
        abilities.setFlyingSpeed(previousSpeed > 0.0F ? previousSpeed : DEFAULT_FLYING_SPEED);
        player.onUpdateAbilities();
    }

    private static void applyPhaseMotion(Player player) {
        double speed = AE2LTCommonConfig.overloadArmorPhaseFlightSpeedMultiplier();
        if (speed <= 0.0D) {
            return;
        }
        Vec3 forward = new Vec3(player.getLookAngle().x, 0.0D, player.getLookAngle().z);
        if (forward.lengthSqr() < 1.0E-6D) {
            forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 motion = forward.scale(player.zza).add(right.scale(player.xxa)).add(0.0D, player.yya, 0.0D);
        if (motion.lengthSqr() <= 1.0E-6D) {
            return;
        }
        player.setDeltaMovement(motion.normalize().scale(speed));
        player.hurtMarked = true;
    }

    private static void escapeFromBlocks(Player player) {
        var level = player.level();
        BlockPos origin = player.blockPosition();
        for (int radius = 0; radius <= 3; radius++) {
            for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                    origin.offset(radius, radius, radius))) {
                Vec3 target = Vec3.atBottomCenterOf(candidate);
                if (level.noCollision(player, player.getBoundingBox().move(target.subtract(player.position())))) {
                    player.teleportTo(target.x, target.y, target.z);
                    return;
                }
            }
        }
    }

    public static boolean hasTransientPhaseState(Player player) {
        return player.getPersistentData().getBoolean(PLAYER_PHASE_TAG);
    }

    public static void clearTransientPhaseState(Player player) {
        player.noPhysics = false;
        player.setNoGravity(false);
        player.getPersistentData().remove(PLAYER_PHASE_TAG);
    }
}
