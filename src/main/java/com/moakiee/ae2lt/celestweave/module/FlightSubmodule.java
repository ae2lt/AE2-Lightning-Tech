package com.moakiee.ae2lt.celestweave.module;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.celestweave.ArmorFlightSpeedRules;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

public final class FlightSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final FlightSubmodule INSTANCE = new FlightSubmodule();

    public static final String INSTALL_GROUP = "flight";
    public static final String INERTIA_CONFIG_KEY = "flight_inertia";

    private static final String TAG_HAD_MAYFLY = "FlightHadMayfly";
    private static final String TAG_WAS_FLYING = "FlightWasFlying";
    private static final String TAG_PREVIOUS_SPEED = "FlightPreviousFlyingSpeed";
    private static final String TAG_HAD_GAME_MODE_FLIGHT = "FlightHadGameModeFlight";
    private static final float SPEED_EPSILON = 1.0E-6F;
    private FlightSubmodule() {}

    @Override
    public String id() {
        return "flight";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.flight.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.flight.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

    @Override
    public String installGroupId() {
        return INSTALL_GROUP;
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
                updateAbilitiesIfChanged(
                        player,
                        true,
                        player.getAbilities().flying,
                        ArmorFlightSpeedRules.activeFlightSpeed(armor));
            }
            if (player.isFallFlying() && player.isSprinting()) {
                tickElytraBoost(player, armor);
            }
        }
        return 0;
    }

    @Override
    public List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor) {
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

    private CelestweaveArmorSubmoduleConfig speedConfig(ItemStack armor) {
        return config(
                FlightSpeedOption.CONFIG_KEY,
                Component.translatable("ae2lt.celestweave.config.speed_multiplier"),
                getSelectedSpeed(armor).toTag(),
                speedChoices(),
                Component.translatable("ae2lt.celestweave.config.speed_multiplier.hint"));
    }

    private List<CelestweaveArmorSubmoduleConfigChoice> speedChoices() {
        return List.of(
                choice(FlightSpeedOption.ONE.toTag(), Component.literal(FlightSpeedOption.ONE.label())),
                choice(FlightSpeedOption.TWO.toTag(), Component.literal(FlightSpeedOption.TWO.label())),
                choice(FlightSpeedOption.FOUR.toTag(), Component.literal(FlightSpeedOption.FOUR.label())));
    }

    private CelestweaveArmorSubmoduleConfig inertiaConfig(ItemStack armor) {
        return config(
                INERTIA_CONFIG_KEY,
                Component.translatable("ae2lt.celestweave.config.flight_inertia"),
                ByteTag.valueOf(isInertiaEnabled(armor)),
                booleanChoices(),
                Component.translatable("ae2lt.celestweave.config.flight_inertia.hint"));
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
    }

    private static void grantFlight(Player player, ItemStack armor) {
        var abilities = player.getAbilities();
        var data = CelestweaveArmorState.getSubmoduleData(armor, INSTANCE);
        if (!data.contains(TAG_HAD_MAYFLY, CompoundTag.TAG_BYTE)) {
            data.putBoolean(TAG_HAD_MAYFLY, abilities.mayfly);
            data.putBoolean(TAG_WAS_FLYING, abilities.flying);
            data.putFloat(TAG_PREVIOUS_SPEED, abilities.getFlyingSpeed());
            data.putBoolean(TAG_HAD_GAME_MODE_FLIGHT, player.isCreative() || player.isSpectator());
            CelestweaveArmorState.setSubmoduleData(armor, INSTANCE, data);
        }
        updateAbilitiesIfChanged(
                player,
                true,
                abilities.flying,
                ArmorFlightSpeedRules.activeFlightSpeed(armor));
    }

    private static void revokeFlight(Player player, ItemStack armor) {
        restoreStoredAbilities(player, armor);
    }

    private static void restoreStoredAbilities(Player player, ItemStack armor) {
        var data = CelestweaveArmorState.getSubmoduleData(armor, INSTANCE);
        boolean hadMayfly = data.contains(TAG_HAD_MAYFLY, CompoundTag.TAG_BYTE) && data.getBoolean(TAG_HAD_MAYFLY);
        boolean wasFlying = data.contains(TAG_WAS_FLYING, CompoundTag.TAG_BYTE) && data.getBoolean(TAG_WAS_FLYING);
        float previousSpeed = data.contains(TAG_PREVIOUS_SPEED, CompoundTag.TAG_FLOAT)
                ? data.getFloat(TAG_PREVIOUS_SPEED)
                : FlightSpeedOption.VANILLA_FLYING_SPEED;
        boolean hadGameModeFlight = capturedGameModeFlight(data, hadMayfly);
        data.remove(TAG_HAD_MAYFLY);
        data.remove(TAG_WAS_FLYING);
        data.remove(TAG_PREVIOUS_SPEED);
        data.remove(TAG_HAD_GAME_MODE_FLIGHT);
        CelestweaveArmorState.setSubmoduleData(armor, INSTANCE, data);

        var abilities = player.getAbilities();
        if (player.isCreative() || player.isSpectator()) {
            abilities.setFlyingSpeed(previousSpeed > 0.0F ? previousSpeed : FlightSpeedOption.VANILLA_FLYING_SPEED);
            player.onUpdateAbilities();
            return;
        }
        boolean phaseFlightActive = CelestweaveArmorState.isSubmoduleRuntimeActive(armor, PhaseFlightSubmodule.INSTANCE.id());
        var target = FlightAbilityRestoreRules.targetForNonGameModePlayer(
                hadMayfly,
                wasFlying,
                hadGameModeFlight,
                phaseFlightActive);
        updateAbilitiesIfChanged(
                player,
                target.mayfly(),
                target.flying(),
                phaseFlightActive
                        ? ArmorFlightSpeedRules.activeFlightSpeed(armor)
                        : previousSpeed > 0.0F ? previousSpeed : FlightSpeedOption.VANILLA_FLYING_SPEED);
    }

    private static boolean capturedGameModeFlight(CompoundTag data, boolean hadMayfly) {
        if (data.contains(TAG_HAD_GAME_MODE_FLIGHT, CompoundTag.TAG_BYTE)) {
            return data.getBoolean(TAG_HAD_GAME_MODE_FLIGHT);
        }
        return hadMayfly;
    }

    private static boolean updateAbilitiesIfChanged(
            Player player,
            boolean mayfly,
            boolean flying,
            float desiredSpeed) {
        var abilities = player.getAbilities();
        boolean changed = false;
        if (abilities.mayfly != mayfly) {
            abilities.mayfly = mayfly;
            changed = true;
        }
        if (abilities.flying != flying) {
            abilities.flying = flying;
            changed = true;
        }
        if (Math.abs(abilities.getFlyingSpeed() - desiredSpeed) > SPEED_EPSILON) {
            abilities.setFlyingSpeed(desiredSpeed);
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
        return changed;
    }
}
