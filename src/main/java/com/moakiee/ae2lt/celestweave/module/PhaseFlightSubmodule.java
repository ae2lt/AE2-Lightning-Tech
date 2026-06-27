package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorFlightSpeedRules;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.service.ArmorLightningService;
import com.moakiee.ae2lt.celestweave.service.ArmorResourceFeedback;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class PhaseFlightSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final PhaseFlightSubmodule INSTANCE = new PhaseFlightSubmodule();

    public static final String INERTIA_CONFIG_KEY = "flight_inertia";

    private static final String TAG_HAD_MAYFLY = "PhaseHadMayfly";
    private static final String TAG_WAS_FLYING = "PhaseWasFlying";
    private static final String TAG_PREVIOUS_SPEED = "PhasePreviousFlyingSpeed";
    private static final String TAG_HAD_GAME_MODE_FLIGHT = "PhaseHadGameModeFlight";
    private static final String PLAYER_PHASE_TAG = "ae2lt.phase_flight.active";
    private static final String PLAYER_ESCAPE_TICKS_TAG = "ae2lt.phase_flight.escape_ticks";
    private static final float DEFAULT_FLYING_SPEED = 0.05F;
    private static final float SPEED_EPSILON = 1.0E-6F;
    private static final int ESCAPE_PHASE_TICKS = 40;

    private PhaseFlightSubmodule() {
    }

    @Override
    public String id() {
        return "phase_flight";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.phase_flight.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.phase_flight.desc";
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
    public String installGroupId() {
        return FlightSubmodule.INSTALL_GROUP;
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

        maintainPhaseFlight(player, armor);
        applyTransientPhaseState(player);
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

    public static double phaseSpeed(ItemStack armor) {
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

    private static void grantPhaseFlight(Player player, ItemStack armor) {
        var data = CelestweaveArmorState.getSubmoduleData(armor, INSTANCE);
        var abilities = player.getAbilities();
        clearEscapePhaseIfPresent(player);
        markPhaseStateIfNeeded(player);
        if (!data.contains(TAG_HAD_MAYFLY, CompoundTag.TAG_BYTE)) {
            data.putBoolean(TAG_HAD_MAYFLY, abilities.mayfly);
            data.putBoolean(TAG_WAS_FLYING, abilities.flying);
            data.putFloat(TAG_PREVIOUS_SPEED, abilities.getFlyingSpeed());
            data.putBoolean(TAG_HAD_GAME_MODE_FLIGHT, player.isCreative() || player.isSpectator());
            CelestweaveArmorState.setSubmoduleData(armor, INSTANCE, data);
        }
        updateAbilitiesIfChanged(player, true, true, ArmorFlightSpeedRules.activeFlightSpeed(armor));
    }

    private static void maintainPhaseFlight(Player player, ItemStack armor) {
        clearEscapePhaseIfPresent(player);
        markPhaseStateIfNeeded(player);
        updateAbilitiesIfChanged(player, true, true, ArmorFlightSpeedRules.activeFlightSpeed(armor));
    }

    private static void revokePhaseFlight(Player player, ItemStack armor) {
        if (player.isInWall() && !escapeFromBlocks(player)) {
            beginEscapePhase(player, armor);
            restoreStoredAbilities(player, armor);
            return;
        }
        player.noPhysics = false;
        player.setNoGravity(false);
        clearEscapePhase(player);
        player.getPersistentData().remove(PLAYER_PHASE_TAG);

        restoreStoredAbilities(player, armor);
    }

    private static void restoreStoredAbilities(Player player, ItemStack armor) {
        var data = CelestweaveArmorState.getSubmoduleData(armor, INSTANCE);
        boolean hadMayfly = data.contains(TAG_HAD_MAYFLY, CompoundTag.TAG_BYTE) && data.getBoolean(TAG_HAD_MAYFLY);
        boolean wasFlying = data.contains(TAG_WAS_FLYING, CompoundTag.TAG_BYTE) && data.getBoolean(TAG_WAS_FLYING);
        float previousSpeed = data.contains(TAG_PREVIOUS_SPEED, CompoundTag.TAG_FLOAT)
                ? data.getFloat(TAG_PREVIOUS_SPEED)
                : DEFAULT_FLYING_SPEED;
        boolean hadGameModeFlight = capturedGameModeFlight(data, hadMayfly);
        data.remove(TAG_HAD_MAYFLY);
        data.remove(TAG_WAS_FLYING);
        data.remove(TAG_PREVIOUS_SPEED);
        data.remove(TAG_HAD_GAME_MODE_FLIGHT);
        CelestweaveArmorState.setSubmoduleData(armor, INSTANCE, data);

        var abilities = player.getAbilities();
        if (player.isCreative() || player.isSpectator()) {
            updateAbilitiesIfChanged(
                    player,
                    abilities.mayfly,
                    abilities.flying,
                    previousSpeed > 0.0F ? previousSpeed : DEFAULT_FLYING_SPEED);
            return;
        }
        boolean otherFlightActive = CelestweaveArmorState.isSubmoduleRuntimeActive(armor, FlightSubmodule.INSTANCE.id());
        var target = FlightAbilityRestoreRules.targetForNonGameModePlayer(
                hadMayfly,
                wasFlying,
                hadGameModeFlight,
                otherFlightActive);
        updateAbilitiesIfChanged(
                player,
                target.mayfly(),
                target.flying(),
                otherFlightActive
                        ? ArmorFlightSpeedRules.activeFlightSpeed(armor)
                        : previousSpeed > 0.0F ? previousSpeed : DEFAULT_FLYING_SPEED);
    }

    private static boolean capturedGameModeFlight(CompoundTag data, boolean hadMayfly) {
        if (data.contains(TAG_HAD_GAME_MODE_FLIGHT, CompoundTag.TAG_BYTE)) {
            return data.getBoolean(TAG_HAD_GAME_MODE_FLIGHT);
        }
        return hadMayfly;
    }

    private static boolean escapeFromBlocks(Player player) {
        var level = player.level();
        BlockPos origin = player.blockPosition();
        var preferred = java.util.List.of(
                origin,
                origin.above(),
                origin.below(),
                origin.north(),
                origin.south(),
                origin.east(),
                origin.west(),
                origin.above().north(),
                origin.above().south(),
                origin.above().east(),
                origin.above().west());
        for (BlockPos candidate : preferred) {
            if (tryTeleportToCollisionFree(player, candidate)) {
                return true;
            }
        }
        for (int radius = 0; radius <= 3; radius++) {
            for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                    origin.offset(radius, radius, radius))) {
                if (preferred.contains(candidate)) {
                    continue;
                }
                if (tryTeleportToCollisionFree(player, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tryTeleportToCollisionFree(Player player, BlockPos candidate) {
        var level = player.level();
        Vec3 target = Vec3.atBottomCenterOf(candidate);
        if (!level.noCollision(player, player.getBoundingBox().move(target.subtract(player.position())))) {
            return false;
        }
        player.teleportTo(target.x, target.y, target.z);
        return true;
    }

    public static boolean hasTransientPhaseState(Player player) {
        return player.getPersistentData().getBoolean(PLAYER_PHASE_TAG);
    }

    public static void applyTransientPhaseState(Player player) {
        player.noPhysics = true;
        player.setNoGravity(true);
        player.setOnGround(false);
        player.fallDistance = 0.0F;
        player.getPersistentData().putBoolean(PLAYER_PHASE_TAG, true);
    }

    public static void applyClientPhaseFlightState(Player player) {
        var abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
    }

    public static void clearTransientPhaseState(Player player) {
        player.noPhysics = false;
        player.setNoGravity(false);
        player.getPersistentData().remove(PLAYER_PHASE_TAG);
        clearEscapePhase(player);
    }

    public static boolean tickEscapePhase(Player player, @Nullable ItemStack armor) {
        int ticks = player.getPersistentData().getInt(PLAYER_ESCAPE_TICKS_TAG);
        if (ticks <= 0) {
            return false;
        }
        if (!player.isInWall()) {
            clearTransientPhaseState(player);
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer
                && !ArmorLightningService.consume(
                        serverPlayer,
                        armor,
                        LightningKey.EXTREME_HIGH_VOLTAGE,
                        ArmorOverloadRules.PHASE_FLIGHT_ESCAPE_COST_EHV_PER_TICK)) {
            ArmorResourceFeedback.noExtremeHighVoltage(serverPlayer);
            clearTransientPhaseState(player);
            return false;
        }
        applyTransientPhaseState(player);
        player.getPersistentData().putInt(PLAYER_ESCAPE_TICKS_TAG, ticks - 1);
        if (ticks <= 1) {
            clearTransientPhaseState(player);
            return false;
        }
        return true;
    }

    private static void beginEscapePhase(Player player, ItemStack armor) {
        applyTransientPhaseState(player);
        player.getPersistentData().putInt(PLAYER_ESCAPE_TICKS_TAG, ESCAPE_PHASE_TICKS);
    }

    private static void clearEscapePhase(Player player) {
        player.getPersistentData().remove(PLAYER_ESCAPE_TICKS_TAG);
    }

    private static void clearEscapePhaseIfPresent(Player player) {
        if (player.getPersistentData().contains(PLAYER_ESCAPE_TICKS_TAG)) {
            clearEscapePhase(player);
        }
    }

    private static void markPhaseStateIfNeeded(Player player) {
        if (!player.getPersistentData().getBoolean(PLAYER_PHASE_TAG)) {
            player.getPersistentData().putBoolean(PLAYER_PHASE_TAG, true);
        }
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
