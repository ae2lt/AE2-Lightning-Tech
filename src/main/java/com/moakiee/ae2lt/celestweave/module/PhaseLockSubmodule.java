package com.moakiee.ae2lt.celestweave.module;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.PhaseWingFlight;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;

/**
 * Uses the chestplate as the phase-lock controller, moves every worn Celestweave armor piece into
 * UUID-bound server storage and leaves inert projections in their vanilla equipment slots.
 */
public final class PhaseLockSubmodule extends AbstractCelestweaveArmorSubmodule {
    public static final PhaseLockSubmodule INSTANCE = new PhaseLockSubmodule();

    public static final String ARMOR_LOCK_CONFIG_KEY = "phase_armor_lock";
    public static final String FLIGHT_LOCK_CONFIG_KEY = "flight_lock";
    public static final String BLOCK_EXTERNAL_FORCES_CONFIG_KEY = "phase_block_external_forces";
    public static final String BLOCK_EXTERNAL_TELEPORTS_CONFIG_KEY = "phase_block_external_teleports";

    private PhaseLockSubmodule() {
    }

    @Override
    public String id() {
        return "phase_lock";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.phase_lock.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.phase_lock.desc";
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
    public void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            updateMovementProtection(player, armor);
            updateFlightLock(player);
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            PhaseFlightMovementGuard.clearPhaseLockProtection(player);
            PhaseFlightPlayerState.setFlightLocked(player, false);
            if (!PhaseWingFlight.canUse(player)) {
                PhaseFlightPlayerState.endControl(player);
            }
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            updateMovementProtection(player, armor);
            updateFlightLock(player);
        }
        return 0;
    }

    @Override
    public List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return List.of(
                armorLockConfig(armor),
                flightLockConfig(armor),
                blockExternalForcesConfig(armor),
                blockExternalTeleportsConfig(armor));
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (!ARMOR_LOCK_CONFIG_KEY.equals(key)
                && !FLIGHT_LOCK_CONFIG_KEY.equals(key)
                && !BLOCK_EXTERNAL_FORCES_CONFIG_KEY.equals(key)
                && !BLOCK_EXTERNAL_TELEPORTS_CONFIG_KEY.equals(key)) {
            return false;
        }
        var options = getOptions(armor);
        options.put(key, value instanceof ByteTag byteTag ? byteTag : ByteTag.valueOf(true));
        setOptions(armor, options);
        return true;
    }

    public static boolean isArmorLockEnabled(ItemStack armor) {
        return booleanOption(armor, ARMOR_LOCK_CONFIG_KEY);
    }

    public static boolean isFlightLockEnabled(ItemStack armor) {
        return booleanOption(armor, FLIGHT_LOCK_CONFIG_KEY);
    }

    /**
     * Flight lock exists only while its chest module is active and some flight source is available.
     * NeoForge's mayFly contract (game mode or CREATIVE_FLIGHT attribute) and either enabled
     * Celestweave flight module are equivalent sources; this policy never grants flight itself.
     */
    public static boolean isFlightLockEnabled(Player player) {
        return isFlightLockConfigured(player) && hasFlightSource(player);
    }

    public static boolean hasFlightSource(Player player) {
        return player != null && ((IPlayerExtension) player).mayFly();
    }

    public static boolean isFlightLockConfigured(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack chest = CelestweaveEquipmentAccess.findArmor(player, EquipmentSlot.CHEST);
        return !chest.isEmpty()
                && CelestweaveArmorState.isSubmoduleRuntimeActive(chest, INSTANCE.id())
                && isFlightLockEnabled(chest);
    }

    public static boolean blocksExternalForces(ItemStack armor) {
        return booleanOption(armor, BLOCK_EXTERNAL_FORCES_CONFIG_KEY);
    }

    public static boolean blocksExternalTeleports(ItemStack armor) {
        return booleanOption(armor, BLOCK_EXTERNAL_TELEPORTS_CONFIG_KEY);
    }

    private CelestweaveArmorSubmoduleConfig armorLockConfig(ItemStack armor) {
        return booleanConfig(
                ARMOR_LOCK_CONFIG_KEY,
                "ae2lt.celestweave.config.phase_armor_lock",
                isArmorLockEnabled(armor));
    }

    private CelestweaveArmorSubmoduleConfig flightLockConfig(ItemStack armor) {
        return booleanConfig(
                FLIGHT_LOCK_CONFIG_KEY,
                "ae2lt.celestweave.config.flight_lock",
                isFlightLockEnabled(armor));
    }

    private CelestweaveArmorSubmoduleConfig blockExternalForcesConfig(ItemStack armor) {
        return booleanConfig(
                BLOCK_EXTERNAL_FORCES_CONFIG_KEY,
                "ae2lt.celestweave.config.phase_block_external_forces",
                blocksExternalForces(armor));
    }

    private CelestweaveArmorSubmoduleConfig blockExternalTeleportsConfig(ItemStack armor) {
        return booleanConfig(
                BLOCK_EXTERNAL_TELEPORTS_CONFIG_KEY,
                "ae2lt.celestweave.config.phase_block_external_teleports",
                blocksExternalTeleports(armor));
    }

    private CelestweaveArmorSubmoduleConfig booleanConfig(String key, String translationKey, boolean value) {
        return config(
                key,
                Component.translatable(translationKey),
                ByteTag.valueOf(value),
                booleanChoices(),
                Component.translatable(translationKey + ".hint"));
    }

    private static boolean booleanOption(ItemStack armor, String key) {
        var options = INSTANCE.getOptions(armor);
        return !options.contains(key, Tag.TAG_BYTE) || options.getBoolean(key);
    }

    private static void updateMovementProtection(Player player, ItemStack armor) {
        PhaseFlightMovementGuard.updatePhaseLockProtection(
                player,
                blocksExternalForces(armor),
                blocksExternalTeleports(armor));
    }

    private static void updateFlightLock(Player player) {
        boolean wasFlightLocked = PhaseFlightPlayerState.isFlightLocked(player);
        boolean flightSourceAvailable = hasFlightSource(player);
        boolean flightLockEnabled = isFlightLockEnabled(player);
        if (flightLockEnabled) {
            PhaseFlightPlayerState.activate(player);
        }
        PhaseFlightPlayerState.setFlightLocked(player, flightLockEnabled);
        if (!flightLockEnabled && !PhaseWingFlight.canUse(player)) {
            if (!flightSourceAvailable && PhaseFlightPlayerState.isControlled(player)) {
                PhaseFlightPlayerState.synchronizeFlying(player, false);
            }
            PhaseFlightPlayerState.endControl(player);
        }
        if (wasFlightLocked != flightLockEnabled && player instanceof ServerPlayer serverPlayer) {
            CelestweaveArmorState.syncFlightSettingsToClient(serverPlayer);
        }
    }
}
