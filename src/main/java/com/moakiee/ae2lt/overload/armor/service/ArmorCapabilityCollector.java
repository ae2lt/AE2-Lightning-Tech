package com.moakiee.ae2lt.overload.armor.service;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.overload.armor.BaseOverloadArmorItem;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;

public final class ArmorCapabilityCollector {
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

    private static List<ActiveCapability> collect(Player player, boolean expandByCount) {
        var out = new ArrayList<ActiveCapability>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty() || !(armor.getItem() instanceof BaseOverloadArmorItem)) {
                continue;
            }

            var snapshot = OverloadArmorState.snapshot(player, armor, player.level().registryAccess(), true);
            if (!snapshot.hasCore() || snapshot.locked()) {
                continue;
            }

            for (ItemStack moduleStack : OverloadArmorState.loadModuleStacks(armor, player.level().registryAccess())) {
                if (!(moduleStack.getItem() instanceof OverloadDeviceModuleItem module)
                        || !(moduleStack.getItem() instanceof OverloadArmorSubmoduleItem submoduleProvider)) {
                    continue;
                }

                int iterations = expandByCount ? Math.max(1, moduleStack.getCount()) : 1;
                for (int i = 0; i < iterations; i++) {
                    ItemStack unit = moduleStack.copyWithCount(1);
                    submoduleProvider.collectSubmodules(unit, submodule -> {
                        if (submodule == null || !OverloadArmorState.isSubmoduleRuntimeActive(armor, submodule.id())) {
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

    public record ActiveCapability(ItemStack armor, String submoduleId, DeviceCapability capability) {
    }
}
