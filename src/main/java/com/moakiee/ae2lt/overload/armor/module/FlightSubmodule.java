package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.overload.armor.ArmorDynamicLoadRules;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public final class FlightSubmodule extends AbstractOverloadArmorSubmodule {

    public static final FlightSubmodule INSTANCE = new FlightSubmodule();

    private static final int ELYTRA_BOOST_INTERVAL_TICKS = 10;

    private FlightSubmodule() {}

    @Override
    public String id() {
        return "flight";
    }

    @Override
    public String nameKey() {
        return "ae2lt.overload_armor.feature.flight.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.overload_armor.feature.flight.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getIdleOverloaded(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }

    @Override
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            grantFlight(player);
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            revokeFlight(player);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            if (!player.getAbilities().mayfly) {
                grantFlight(player);
            }
            if (player.isFallFlying() && player.isSprinting()) {
                tickElytraBoost(player, armor);
                return AE2LTCommonConfig.overloadArmorFlightMovingLoad();
            }
            return ArmorDynamicLoadRules.flightStateLoad(
                    player.getAbilities().flying,
                    isMoving(player),
                    AE2LTCommonConfig.overloadArmorFlightHoverLoad(),
                    AE2LTCommonConfig.overloadArmorFlightMovingLoad());
        }
        return 0;
    }

    private static boolean isMoving(Player player) {
        Vec3 motion = player.getDeltaMovement();
        return motion.horizontalDistanceSqr() > 1.0E-4D || Math.abs(motion.y) > 1.0E-3D;
    }

    private static void tickElytraBoost(Player player, ItemStack armor) {
        Vec3 look = player.getLookAngle();
        Vec3 motion = player.getDeltaMovement();
        Vec3 boosted = motion.add(look.scale(0.03D));
        double maxSpeedSqr = 9.0D;
        if (boosted.lengthSqr() > maxSpeedSqr) {
            boosted = boosted.normalize().scale(3.0D);
        }
        player.setDeltaMovement(boosted);
        player.hurtMarked = true;
        if (player.tickCount % ELYTRA_BOOST_INTERVAL_TICKS == 0) {
            OverloadArmorState.addPulseLoad(
                    armor,
                    INSTANCE.id(),
                    AE2LTCommonConfig.overloadArmorElytraBoostPulseLoad());
        }
    }

    private static void grantFlight(Player player) {
        var abilities = player.getAbilities();
        abilities.mayfly = true;
        player.onUpdateAbilities();
    }

    private static void revokeFlight(Player player) {
        var abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();
    }
}
