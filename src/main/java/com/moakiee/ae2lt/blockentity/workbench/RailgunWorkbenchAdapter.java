package com.moakiee.ae2lt.blockentity.workbench;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.menu.SlotSemantic;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.energy.DeviceEnergyBuffer;
import com.moakiee.ae2lt.device.energy.NetworkBoundEnergyBuffer;
import com.moakiee.ae2lt.device.module.DeviceModuleStorage;
import com.moakiee.ae2lt.device.network.DeviceNetworkBinding;
import com.moakiee.ae2lt.device.network.RailgunNetworkBinding;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;
import com.moakiee.ae2lt.item.railgun.RailgunModuleItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleStorage;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;

public final class RailgunWorkbenchAdapter implements DeviceWorkbenchAdapter {
    public static final RailgunWorkbenchAdapter INSTANCE = new RailgunWorkbenchAdapter();

    private static final List<StructuralSlotSpec> STRUCTURAL_SLOTS = List.of(
            slot(0, DeviceSlotType.CORE, com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.OVERLOAD_DEVICE_WORKBENCH_CORE));

    private RailgunWorkbenchAdapter() {}

    @Override
    public DeviceKind deviceKind() {
        return DeviceKind.RAILGUN;
    }

    @Override
    public DeviceModuleStorage moduleStorage() {
        return RailgunModuleStorage.INSTANCE;
    }

    @Override
    public DeviceEnergyBuffer energyBuffer() {
        return NetworkBoundEnergyBuffer.INSTANCE;
    }

    @Override
    public DeviceNetworkBinding networkBinding() {
        return RailgunNetworkBinding.INSTANCE;
    }

    @Override
    public List<StructuralSlotSpec> structuralSlots() {
        return STRUCTURAL_SLOTS;
    }

    @Override
    public Predicate<ItemStack> moduleInputValidator(ItemStack device, HolderLookup.Provider registries) {
        return stack -> stack.getItem() instanceof RailgunModuleItem
                && RailgunModuleStorage.INSTANCE.canInstallOne(device, stack);
    }

    @Override
    public List<ItemStack> listModuleEntries(ItemStack device, HolderLookup.Provider registries) {
        return RailgunModuleStorage.INSTANCE.listEntries(device);
    }

    @Override
    public boolean canInstallOne(ItemStack device, HolderLookup.Provider registries, ItemStack candidate) {
        return RailgunModuleStorage.INSTANCE.canInstallOne(device, candidate);
    }

    @Override
    public boolean installOne(ItemStack device, HolderLookup.Provider registries, ItemStack candidate) {
        return RailgunModuleStorage.INSTANCE.installOne(device, candidate);
    }

    @Override
    public ItemStack uninstallOne(ItemStack device, HolderLookup.Provider registries, String typeId) {
        return RailgunModuleStorage.INSTANCE.uninstallOne(device, typeId);
    }

    @Override
    public ItemStack uninstallAll(ItemStack device, HolderLookup.Provider registries, String typeId) {
        return RailgunModuleStorage.INSTANCE.uninstallAll(device, typeId);
    }

    @Override
    public String moduleTypeId(ItemStack stack) {
        return RailgunModuleEntries.typeId(stack);
    }

    @Override
    public int maxInstallAmount(ItemStack stack) {
        return stack.getItem() instanceof RailgunModuleItem module
                ? module.getMaxInstallAmount()
                : 0;
    }

    @Override
    public int baseOverloadBudget(ItemStack device, HolderLookup.Provider registries) {
        return RailgunModuleStorage.INSTANCE.baseOverloadBudget(device);
    }

    @Override
    public int currentIdleOverload(ItemStack device, HolderLookup.Provider registries) {
        return RailgunModuleStorage.INSTANCE.currentIdleOverload(device);
    }

    @Override
    public ItemStack getStructuralSlot(ItemStack device, HolderLookup.Provider registries, StructuralSlotSpec spec) {
        return RailgunModuleStorage.entryData(device).first(RailgunModuleType.CORE);
    }

    @Override
    public void setStructuralSlot(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            ItemStack stack) {
        RailgunModuleStorage.INSTANCE.uninstallAll(device, RailgunModuleType.CORE.getSerializedName());
        if (!stack.isEmpty()) {
            RailgunModuleStorage.INSTANCE.installOne(device, stack.copyWithCount(1));
        }
    }

    @Override
    public ItemStack removeStructuralSlot(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            int amount) {
        return RailgunModuleStorage.INSTANCE.uninstallOne(device, RailgunModuleType.CORE.getSerializedName());
    }

    @Override
    public boolean canPlaceStructural(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            ItemStack stack) {
        return !device.isEmpty()
                && stack.getItem() instanceof RailgunModuleItem module
                && module.moduleType() == RailgunModuleType.CORE
                && RailgunModuleStorage.INSTANCE.canInstallOne(device, stack);
    }

    @Override
    public boolean mayPickupStructural(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            Player player,
            ItemStack carried) {
        return !device.isEmpty();
    }

    private static StructuralSlotSpec slot(int index, DeviceSlotType type, SlotSemantic semantic) {
        return new StructuralSlotSpec(index, type, semantic);
    }
}
