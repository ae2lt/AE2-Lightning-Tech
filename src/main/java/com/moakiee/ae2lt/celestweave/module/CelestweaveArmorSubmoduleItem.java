package com.moakiee.ae2lt.celestweave.module;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorPart;

/**
 * Implemented by items that can sit in an armor module slot and contribute one or more
 * {@link CelestweaveArmorSubmodule} instances to the armor's runtime.
 *
 * <p>Also implements {@link OverloadDeviceModuleItem} so the unified device layer can bind
 * armor module items into capability-driven services. Capability declaration defaults to empty;
 * Phase 5 submodules (reflect / dash / flight / ...) override {@link #capabilities} to expose
 * their per-stack contribution.
 */
public interface CelestweaveArmorSubmoduleItem extends OverloadDeviceModuleItem {

    ArmorPart armorPart();

    @Override
    default Set<DeviceKind> acceptableDevices() {
        return Set.of(armorPart().deviceKind());
    }

    @Override
    default DeviceSlotType acceptableSlot() {
        return armorPart().moduleSlot();
    }

    @Override
    default List<DeviceCapability> capabilities(ItemStack stack) {
        return List.of();
    }

    void collectSubmodules(ItemStack stack, Consumer<CelestweaveArmorSubmodule> output);
}
