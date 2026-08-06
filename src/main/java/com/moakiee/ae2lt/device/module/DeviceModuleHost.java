package com.moakiee.ae2lt.device.module;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;

/**
 * Container view of the modules installed on a device instance.
 *
 * <p>{@link #installedModuleStacks()} returns the non-empty stacks (one per occupied slot)
 * so capability resolution can read each module's per-stack data without re-scanning slots.
 */
public interface DeviceModuleHost {

    DeviceKind deviceKind();

    List<ModuleSlotSpec> slotSpecs();

    ItemStack getModule(int slot);

    boolean trySetModule(int slot, ItemStack stack);

    Stream<ItemStack> installedModuleStacks();
}
