package com.moakiee.ae2lt.celestweave.module;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.device.module.OverloadDeviceSubmodule;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

public interface CelestweaveArmorSubmodule extends OverloadDeviceSubmodule {
    @Override
    String id();

    String nameKey();

    String descriptionKey();

    boolean defaultEnabled();

    default Component name() {
        return Component.translatable(nameKey());
    }

    default Component description() {
        return Component.translatable(descriptionKey());
    }

    default Component buttonLabel(boolean enabled) {
        return Component.translatable(
                enabled
                        ? "ae2lt.celestweave.feature.toggle.on"
                        : "ae2lt.celestweave.feature.toggle.off",
                name());
    }

    /**
     * Optional: fired exactly once when the providing item becomes physically present in the
     * armor's configuration slots. Most modules should leave this untouched and rely on
     * {@link #onActivated}; implement this only for rare bootstrap work that must happen even
     * before the armor is equipped (e.g., seeding persistent data that survives remove-and-reinsert).
     *
     * <p>The framework persists the installed ID set, so this event only fires on edge-triggered
     * changes. Called on both logical sides; inspect {@code dist} to guard side-specific work.
     */
    default void onInstalled(@Nullable Player player, Dist dist, ItemStack armor) {
    }

    /**
     * Optional: fired exactly once when the providing item is removed. Immediately preceded by an
     * automatic {@link #onDeactivated} invocation if the module was active, so the effect of
     * uninstall is always equivalent to (or stricter than) deactivation. Most modules should
     * therefore leave this untouched and put teardown in {@link #onDeactivated}.
     */
    default void onUninstalled(@Nullable Player player, Dist dist, ItemStack armor) {
    }

    /**
     * Fired when the submodule transitions into the "active" state, which the framework computes
     * from: {@code installed && enabled && equipped && core installed}.
     */
    default void onActivated(@Nullable Player player, Dist dist, ItemStack armor) {
    }

    /**
     * Fired when the submodule transitions out of the active state.
     */
    default void onDeactivated(@Nullable Player player, Dist dist, ItemStack armor) {
    }

    /**
     * Optional per-type install cap. When {@code > 0}, the workbench refuses to install a new
     * instance of this submodule once the installed count reaches this value. {@code 0} (default)
     * means unlimited within the available module slots.
     *
     * <p>This lets modules express semantics that the idle budget alone can't: e.g. "only one
     * "at most three dash modules", etc., without forcing the module to consume
     * an artificially large slot footprint.
     */
    default int getMaxInstallAmount() {
        return 0;
    }

    /**
     * Logical install group used for mutually-exclusive module tiers. Different items can
     * expose different submodule ids while still sharing a single install group.
     */
    default String installGroupId() {
        return id();
    }

    /**
     * Called once per armor tick while {@link #onActivated} has been fired but {@link #onDeactivated}
     * has not.
     */
    default int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        return 0;
    }

    /**
     * Called by the framework when the submodule's persisted data is about to be handed to runtime
     * code. Implementations should validate / normalize / fill defaults here.
     */
    default CompoundTag loadData(ItemStack armor, CompoundTag data) {
        return data.copy();
    }

    /**
     * Called by the framework right before persisting updated module data. Implementations can
     * strip ephemeral fields or reshape data here.
     */
    default CompoundTag saveData(ItemStack armor, CompoundTag data) {
        return data.copy();
    }

    default CompoundTag getData(ItemStack armor) {
        return CelestweaveArmorState.getSubmoduleData(armor, this);
    }

    default void setData(ItemStack armor, CompoundTag data) {
        CelestweaveArmorState.setSubmoduleData(armor, this, data);
    }

    /**
     * True while the submodule's providing item is physically installed in the armor. Modules
     * wanting to react to workbench install / uninstall should do so in
     * {@link #onInstalled}/{@link #onUninstalled}; this accessor is for querying state.
     */
    default boolean isInstalled(ItemStack armor, HolderLookup.Provider registries) {
        return CelestweaveArmorState.isSubmoduleInstalled(armor, registries, id());
    }

    @Deprecated(forRemoval = false)
    default boolean isInstalled(ItemStack armor) {
        return CelestweaveArmorState.isSubmoduleInstalled(armor, id());
    }

    /**
     * True while the framework considers this submodule active (see {@link #onActivated}).
     */
    default boolean isActive(ItemStack armor) {
        return CelestweaveArmorState.isSubmoduleRuntimeActive(armor, id());
    }

    List<CelestweaveArmorSubmoduleConfig> getConfigs(ItemStack armor);

    boolean setConfig(ItemStack armor, String key, @Nullable Tag value);

    List<CelestweaveArmorSubmoduleOptionUi> getConfigUI(ItemStack armor);

    @Deprecated(forRemoval = false)
    default List<CelestweaveArmorSubmoduleOptionUi> getOptionUI(ItemStack armor) {
        return getConfigUI(armor);
    }
}
