package com.moakiee.ae2lt.celestweave.module;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/**
 * Uses the chestplate as the phase-lock controller, moves every worn Celestweave armor piece into
 * UUID-bound server storage and leaves inert projections in their vanilla equipment slots.
 */
public final class PhaseLockSubmodule extends AbstractCelestweaveArmorSubmodule {
    public static final PhaseLockSubmodule INSTANCE = new PhaseLockSubmodule();

    public static final String ARMOR_LOCK_CONFIG_KEY = "phase_armor_lock";
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
        }
    }

    @Override
    public void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            PhaseFlightMovementGuard.clearPhaseLockProtection(player);
        }
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (player != null && dist == Dist.DEDICATED_SERVER) {
            updateMovementProtection(player, armor);
        }
        return 0;
    }

    @Override
    public List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor) {
        return List.of(
                armorLockConfig(armor),
                blockExternalForcesConfig(armor),
                blockExternalTeleportsConfig(armor));
    }

    @Override
    public boolean setConfig(ItemStack armor, String key, @Nullable Tag value) {
        if (!ARMOR_LOCK_CONFIG_KEY.equals(key)
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
}
