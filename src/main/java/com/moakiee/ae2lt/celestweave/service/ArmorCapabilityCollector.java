package com.moakiee.ae2lt.celestweave.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmoduleItem;

public final class ArmorCapabilityCollector {
    private static final ConcurrentHashMap<UUID, CachedCapabilities> CACHE = new ConcurrentHashMap<>();
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET);

    private ArmorCapabilityCollector() {
    }

    public static List<ActiveCapability> collectPerInstalledStack(Player player) {
        return collect(player, false);
    }

    public static List<ActiveCapability> collectPerInstalledUnit(Player player) {
        return collect(player, true);
    }

    public static void clearCache(Player player) {
        CACHE.remove(player.getUUID());
    }

    private static List<ActiveCapability> collect(Player player, boolean expandByCount) {
        return CACHE.computeIfAbsent(player.getUUID(), ignored -> new CachedCapabilities())
                .get(player, expandByCount);
    }

    private static List<ActiveCapability> collectUncached(Player player, boolean expandByCount) {
        var out = new ArrayList<ActiveCapability>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty() || !(armor.getItem() instanceof BaseCelestweaveArmorItem)) {
                continue;
            }

            var snapshot = CelestweaveArmorState.snapshot(player, armor, player.level().registryAccess(), true);
            if (!snapshot.hasCore()) {
                continue;
            }

            for (ItemStack moduleStack : CelestweaveArmorState.loadModuleStacks(armor, player.level().registryAccess())) {
                if (!(moduleStack.getItem() instanceof OverloadDeviceModuleItem module)
                        || !(moduleStack.getItem() instanceof CelestweaveArmorSubmoduleItem submoduleProvider)) {
                    continue;
                }

                int iterations = expandByCount ? Math.max(1, moduleStack.getCount()) : 1;
                for (int i = 0; i < iterations; i++) {
                    ItemStack unit = moduleStack.copyWithCount(1);
                    submoduleProvider.collectSubmodules(unit, submodule -> {
                        if (submodule == null || !isSubmoduleActiveForSide(player, armor, submodule.id())) {
                            return;
                        }
                        for (var capability : module.capabilities(unit)) {
                            out.add(new ActiveCapability(armor, submodule.id(), capability));
                        }
                    });
                }
            }
        }
        return List.copyOf(out);
    }

    private static boolean isSubmoduleActiveForSide(Player player, ItemStack armor, String submoduleId) {
        if (player.level().isClientSide()) {
            var armorId = CelestweaveArmorState.getArmorId(armor);
            return armorId != null && CelestweaveArmorState.isClientSubmoduleActive(armorId, submoduleId);
        }
        return CelestweaveArmorState.isSubmoduleRuntimeActive(armor, submoduleId);
    }

    public record ActiveCapability(ItemStack armor, String submoduleId, DeviceCapability capability) {
    }

    private static final class CachedCapabilities {
        private long gameTime = Long.MIN_VALUE;
        private boolean clientSide;
        private ResourceKey<Level> dimension;
        private List<ActiveCapability> perInstalledStack;
        private List<ActiveCapability> perInstalledUnit;

        synchronized List<ActiveCapability> get(Player player, boolean expandByCount) {
            long currentGameTime = player.level().getGameTime();
            boolean currentClientSide = player.level().isClientSide();
            ResourceKey<Level> currentDimension = player.level().dimension();
            if (gameTime != currentGameTime
                    || clientSide != currentClientSide
                    || !currentDimension.equals(dimension)) {
                gameTime = currentGameTime;
                clientSide = currentClientSide;
                dimension = currentDimension;
                perInstalledStack = null;
                perInstalledUnit = null;
            }

            if (expandByCount) {
                if (perInstalledUnit == null) {
                    perInstalledUnit = collectUncached(player, true);
                }
                return perInstalledUnit;
            }
            if (perInstalledStack == null) {
                perInstalledStack = collectUncached(player, false);
            }
            return perInstalledStack;
        }
    }
}
