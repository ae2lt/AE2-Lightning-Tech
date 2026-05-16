package com.moakiee.ae2lt.blockentity.workbench;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.energy.DeviceEnergyBuffer;
import com.moakiee.ae2lt.device.module.DeviceModuleStorage;
import com.moakiee.ae2lt.device.network.DeviceNetworkBinding;

public interface DeviceWorkbenchAdapter {
    DeviceKind deviceKind();

    DeviceModuleStorage moduleStorage();

    DeviceEnergyBuffer energyBuffer();

    DeviceNetworkBinding networkBinding();

    List<StructuralSlotSpec> structuralSlots();

    Predicate<ItemStack> moduleInputValidator(ItemStack device, HolderLookup.Provider registries);

    List<ItemStack> listModuleEntries(ItemStack device, HolderLookup.Provider registries);

    boolean canInstallOne(ItemStack device, HolderLookup.Provider registries, ItemStack candidate);

    boolean installOne(ItemStack device, HolderLookup.Provider registries, ItemStack candidate);

    ItemStack uninstallOne(ItemStack device, HolderLookup.Provider registries, String typeId);

    ItemStack uninstallAll(ItemStack device, HolderLookup.Provider registries, String typeId);

    String moduleTypeId(ItemStack stack);

    int maxInstallAmount(ItemStack stack);

    int baseOverloadBudget(ItemStack device, HolderLookup.Provider registries);

    int currentIdleOverload(ItemStack device, HolderLookup.Provider registries);

    ItemStack getStructuralSlot(ItemStack device, HolderLookup.Provider registries, StructuralSlotSpec spec);

    void setStructuralSlot(ItemStack device, HolderLookup.Provider registries, StructuralSlotSpec spec, ItemStack stack);

    ItemStack removeStructuralSlot(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            int amount);

    boolean canPlaceStructural(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            ItemStack stack);

    default boolean mayPickupStructural(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            Player player,
            ItemStack carried) {
        return !device.isEmpty();
    }

    default void onDeviceInserted(ItemStack device) {
    }

    default void onModulesChanged(ItemStack device, HolderLookup.Provider registries, Dist dist) {
    }
}
