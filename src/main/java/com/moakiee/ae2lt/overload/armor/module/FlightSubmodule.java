package com.moakiee.ae2lt.overload.armor.module;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.overload.armor.ArmorDynamicLoadRules;
import com.moakiee.ae2lt.overload.armor.ArmorFlightSpeedRules;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;

public final class FlightSubmodule extends AbstractOverloadArmorSubmodule {

    public static final FlightSubmodule INSTANCE = new FlightSubmodule();

    public static final String INERTIA_CONFIG_KEY = "flight_inertia";

    private static final String TAG_HAD_MAYFLY = "FlightHadMayfly";
    private static final String TAG_WAS_FLYING = "FlightWasFlying";
    private static final String TAG_PREVIOUS_SPEED = "FlightPreviousFlyingSpeed";
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
            grantFlight(player, armor);
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            revokeFlight(player, armor);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            if (!player.getAbilities().mayfly) {
                grantFlight(player, armor);
            } else {
                player.getAbilities().setFlyingSpeed(ArmorFlightSpeedRules.activeFlightSpeed(armor));
                player.onUpdateAbilities();
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

    @Override
    public List<OverloadArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return List.of(speedConfig(armor), inertiaConfig(armor));
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (FlightSpeedOption.CONFIG_KEY.equals(key)) {
            var option = FlightSpeedOption.fromTag(value);
            var options = getOptions(armor);
            options.put(FlightSpeedOption.CONFIG_KEY, option.toTag());
            setOptions(armor, options);
            return true;
        }
        if (INERTIA_CONFIG_KEY.equals(key)) {
            var options = getOptions(armor);
            options.put(INERTIA_CONFIG_KEY, value instanceof ByteTag bt ? bt : ByteTag.valueOf(true));
            setOptions(armor, options);
            return true;
        }
        return false;
    }

    public static float flightSpeed(ItemStack armor) {
        return selectedSpeed(armor).flyingSpeed();
    }

    public static FlightSpeedOption selectedSpeed(ItemStack armor) {
        return INSTANCE.getSelectedSpeed(armor);
    }

    private OverloadArmorSubmoduleConfig speedConfig(ItemStack armor) {
        return config(
                FlightSpeedOption.CONFIG_KEY,
                Component.translatable("ae2lt.overload_armor.config.speed_multiplier"),
                getSelectedSpeed(armor).toTag(),
                speedChoices(),
                Component.translatable("ae2lt.overload_armor.config.speed_multiplier.hint"));
    }

    private List<OverloadArmorSubmoduleConfigChoice> speedChoices() {
        return List.of(
                choice(FlightSpeedOption.ONE.toTag(), Component.literal(FlightSpeedOption.ONE.label())),
                choice(FlightSpeedOption.TWO.toTag(), Component.literal(FlightSpeedOption.TWO.label())),
                choice(FlightSpeedOption.FOUR.toTag(), Component.literal(FlightSpeedOption.FOUR.label())));
    }

    private OverloadArmorSubmoduleConfig inertiaConfig(ItemStack armor) {
        return config(
                INERTIA_CONFIG_KEY,
                Component.translatable("ae2lt.overload_armor.config.flight_inertia"),
                ByteTag.valueOf(isInertiaEnabled(armor)),
                booleanChoices(),
                Component.translatable("ae2lt.overload_armor.config.flight_inertia.hint"));
    }

    public static boolean isInertiaEnabled(ItemStack armor) {
        var options = INSTANCE.getOptions(armor);
        if (!options.contains(INERTIA_CONFIG_KEY, Tag.TAG_BYTE)) {
            return true;
        }
        return options.getBoolean(INERTIA_CONFIG_KEY);
    }

    private FlightSpeedOption getSelectedSpeed(ItemStack armor) {
        var options = getOptions(armor);
        return FlightSpeedOption.fromTag(options.get(FlightSpeedOption.CONFIG_KEY));
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

    private static void grantFlight(Player player, ItemStack armor) {
        var abilities = player.getAbilities();
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        if (!data.contains(TAG_HAD_MAYFLY, CompoundTag.TAG_BYTE)) {
            data.putBoolean(TAG_HAD_MAYFLY, abilities.mayfly);
            data.putBoolean(TAG_WAS_FLYING, abilities.flying);
            data.putFloat(TAG_PREVIOUS_SPEED, abilities.getFlyingSpeed());
            OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);
        }
        abilities.mayfly = true;
        abilities.setFlyingSpeed(ArmorFlightSpeedRules.activeFlightSpeed(armor));
        player.onUpdateAbilities();
    }

    private static void revokeFlight(Player player, ItemStack armor) {
        restoreStoredAbilities(player, armor);
    }

    private static void restoreStoredAbilities(Player player, ItemStack armor) {
        var data = OverloadArmorState.getSubmoduleData(armor, INSTANCE);
        boolean hadMayfly = data.contains(TAG_HAD_MAYFLY, CompoundTag.TAG_BYTE) && data.getBoolean(TAG_HAD_MAYFLY);
        boolean wasFlying = data.contains(TAG_WAS_FLYING, CompoundTag.TAG_BYTE) && data.getBoolean(TAG_WAS_FLYING);
        float previousSpeed = data.contains(TAG_PREVIOUS_SPEED, CompoundTag.TAG_FLOAT)
                ? data.getFloat(TAG_PREVIOUS_SPEED)
                : FlightSpeedOption.VANILLA_FLYING_SPEED;
        data.remove(TAG_HAD_MAYFLY);
        data.remove(TAG_WAS_FLYING);
        data.remove(TAG_PREVIOUS_SPEED);
        OverloadArmorState.setSubmoduleData(armor, INSTANCE, data);

        var abilities = player.getAbilities();
        if (player.isCreative() || player.isSpectator()) {
            abilities.setFlyingSpeed(previousSpeed > 0.0F ? previousSpeed : FlightSpeedOption.VANILLA_FLYING_SPEED);
            player.onUpdateAbilities();
            return;
        }
        boolean phaseFlightActive = OverloadArmorState.isSubmoduleRuntimeActive(armor, PhaseFlightSubmodule.INSTANCE.id());
        abilities.mayfly = hadMayfly || phaseFlightActive;
        abilities.flying = (wasFlying || phaseFlightActive) && abilities.mayfly;
        abilities.setFlyingSpeed(phaseFlightActive
                ? ArmorFlightSpeedRules.activeFlightSpeed(armor)
                : previousSpeed > 0.0F ? previousSpeed : FlightSpeedOption.VANILLA_FLYING_SPEED);
        player.onUpdateAbilities();
    }
}
