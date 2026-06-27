package com.moakiee.ae2lt.menu.hub;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.item.railgun.RailgunModuleStorage;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.item.railgun.RailgunStructuralCore;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.logic.railgun.RailgunBinding;
import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmoduleOptionUi;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmoduleItem;
import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Immutable snapshot of a device's current status, built server-side and synced to the client.
 */
public record DeviceStatusModel(
        String displayName,
        boolean hasCore, boolean powered,
        List<ModuleInfo> modules,
        int selectedModuleIndex,
        List<ModuleConfigInfo> moduleConfigs,
        boolean terrainDestruction, boolean pvp, boolean soundEnabled
) {
    public record ModuleInfo(String nameKey, int count, boolean enabled) {
    }

    public record ModuleConfigInfo(String key, String label, String value, boolean editable) {
    }

    public static final DeviceStatusModel EMPTY = new DeviceStatusModel(
            "", false, false, List.of(), -1, List.of(), false, false, false);

    /** Build status snapshot from an armor stack worn by the player. */
    public static DeviceStatusModel fromArmorStack(ItemStack armor, ServerPlayer player) {
        return fromArmorStack(armor, player, 0);
    }

    /** Build status snapshot from an armor stack worn by the player. */
    public static DeviceStatusModel fromArmorStack(ItemStack armor, ServerPlayer player, int selectedModuleIndex) {
        if (armor == null || armor.isEmpty() || !(armor.getItem() instanceof BaseCelestweaveArmorItem armorItem)) {
            return EMPTY;
        }
        String name = armor.getHoverName().getString();

        var resolve = ArmorNetworkBinding.INSTANCE.resolve(armor, player);
        boolean gridReachable = resolve.success();
        boolean appFlux = AppFluxBridge.isAvailable();

        long stored = ArmorEnergyBuffer.read(armor, player.registryAccess());

        var snapshot = CelestweaveArmorState.snapshot(player, armor, player.registryAccess(), true);
        boolean powered = DeviceHubDisplayRules.powerAvailable(stored, gridReachable, appFlux);

        List<ModuleInfo> modules = new ArrayList<>();
        for (var stack : CelestweaveArmorState.loadModuleStacks(armor, player.registryAccess())) {
            if (!(stack.getItem() instanceof CelestweaveArmorSubmoduleItem provider)) {
                continue;
            }
            int count = Math.max(1, stack.getCount());
            provider.collectSubmodules(stack, sub -> {
                boolean enabled = CelestweaveArmorState.isSubmoduleEnabled(armor, sub);
                modules.add(new ModuleInfo(sub.nameKey(), count, enabled));
            });
        }
        int clampedModuleIndex = modules.isEmpty()
                ? -1
                : Math.clamp(selectedModuleIndex, 0, modules.size() - 1);
        List<ModuleConfigInfo> moduleConfigs = moduleConfigs(armor, player, clampedModuleIndex);

        return new DeviceStatusModel(
                name, snapshot.hasCore(), powered, modules, clampedModuleIndex, moduleConfigs, false, false, false);
    }

    /** Build status snapshot from a railgun stack held by the player. */
    public static DeviceStatusModel fromRailgunStack(ItemStack railgun, ServerPlayer player) {
        if (railgun == null || railgun.isEmpty()) {
            return EMPTY;
        }
        String name = railgun.getHoverName().getString();

        var resolve = RailgunBinding.resolve(railgun, player);
        boolean gridReachable = resolve.success();
        boolean appFlux = AppFluxBridge.isAvailable();

        long stored = RailgunEnergyBuffer.read(railgun);

        boolean powered = DeviceHubDisplayRules.powerAvailable(stored, gridReachable, appFlux);

        var entries = RailgunModuleStorage.entryData(railgun);
        boolean hasStructuralCore = RailgunStructuralCore.hasCore(railgun);
        List<ModuleInfo> modules = new ArrayList<>();
        if (entries.hasCore()) {
            modules.add(new ModuleInfo("ae2lt.device_hub.module.railgun.core", 1, true));
        }
        if (entries.computeCount() > 0) {
            modules.add(new ModuleInfo(
                    "ae2lt.device_hub.module.railgun.compute",
                    entries.computeCount(),
                    true));
        }
        if (entries.accelerationCount() > 0) {
            modules.add(new ModuleInfo(
                    "ae2lt.device_hub.module.railgun.acceleration",
                    entries.accelerationCount(),
                    true));
        }
        if (entries.hasOverloadExecution()) {
            modules.add(new ModuleInfo(
                    "ae2lt.device_hub.module.railgun.overload_execution",
                    1,
                    true));
        }

        RailgunSettings settings = railgun.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), RailgunSettings.DEFAULT);
        boolean terrainAllowed = AE2LTCommonConfig.railgunTerrainDestructionEnabled();

        return new DeviceStatusModel(
                name, hasStructuralCore, powered, modules, -1, List.of(),
                terrainAllowed && settings.terrainDestruction(), settings.pvp(), settings.soundEnabled());
    }

    private static List<ModuleConfigInfo> moduleConfigs(ItemStack armor, ServerPlayer player, int selectedModuleIndex) {
        var submodules = CelestweaveArmorState.collectSubmodules(armor, player.registryAccess());
        if (selectedModuleIndex < 0 || selectedModuleIndex >= submodules.size()) {
            return List.of();
        }
        return submodules.get(selectedModuleIndex).getConfigUI(armor).stream()
                .map(DeviceStatusModel::moduleConfigInfo)
                .toList();
    }

    private static ModuleConfigInfo moduleConfigInfo(CelestweaveArmorSubmoduleOptionUi option) {
        return new ModuleConfigInfo(
                option.key(),
                option.label().getString(),
                option.value().getString(),
                option.editable());
    }
}
