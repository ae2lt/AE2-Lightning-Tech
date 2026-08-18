package com.moakiee.ae2lt.device.module;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.DeviceKind;

public interface DeviceModuleStorage {
    DeviceKind deviceKind();

    List<ItemStack> listEntries(ItemStack device);

    int getCount(ItemStack device, String typeId);

    boolean canInstallOne(ItemStack device, ItemStack candidate);

    boolean installOne(ItemStack device, ItemStack candidate);

    ItemStack uninstallOne(ItemStack device, String typeId);

    ItemStack uninstallAll(ItemStack device, String typeId);

    boolean hasAnyInstalled(ItemStack device);

    Stream<ItemStack> installedModuleStacks(ItemStack device);
}
