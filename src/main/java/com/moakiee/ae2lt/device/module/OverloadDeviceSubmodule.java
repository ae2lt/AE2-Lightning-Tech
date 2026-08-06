package com.moakiee.ae2lt.device.module;

import net.minecraft.world.item.ItemStack;

/**
 * Marker for the optional lifecycle subinterface used by armor submodules.
 *
 * <p>Concretely realized by {@code CelestweaveArmorSubmodule}; placed here so device-layer
 * code can reference the lifecycle without depending on the armor package. Most
 * Phase-0 callers only care about whether a module returns a non-null instance.
 */
public interface OverloadDeviceSubmodule {

    String id();

    default boolean isInstalled(ItemStack device) {
        return true;
    }

    default boolean isActive(ItemStack device) {
        return false;
    }
}
