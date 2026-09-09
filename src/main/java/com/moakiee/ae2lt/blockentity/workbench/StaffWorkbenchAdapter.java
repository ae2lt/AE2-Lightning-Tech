package com.moakiee.ae2lt.blockentity.workbench;

import java.util.List;
import java.util.function.Predicate;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.energy.DeviceEnergyBuffer;
import com.moakiee.ae2lt.device.module.DeviceModuleStorage;
import com.moakiee.ae2lt.device.network.*;
import com.moakiee.ae2lt.item.staff.*;
import com.moakiee.ae2lt.registry.ModDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Uses the existing device workbench for installation and faithful module removal. */
public final class StaffWorkbenchAdapter implements DeviceWorkbenchAdapter {
    public static final StaffWorkbenchAdapter INSTANCE = new StaffWorkbenchAdapter();
    private StaffWorkbenchAdapter() {}

    private static final DeviceEnergyBuffer ENERGY = new DeviceEnergyBuffer() {
        @Override public long stored(ItemStack stack) { return StaffEnergy.stored(stack); }
        @Override public long capacity(ItemStack stack) { return StaffEnergy.CAPACITY; }
        @Override public boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
            if (amount < 0 || amount > StaffEnergy.stored(stack)) return false;
            stack.set(ModDataComponents.MIMICRY_ENERGY.get(), StaffEnergy.stored(stack) - (int) amount);
            return true;
        }
        @Override public void refill(ItemStack stack, ServerPlayer player) {}
        @Override public int receiveFe(ItemStack stack, int amount, boolean simulate) {
            return StaffEnergy.capability(stack).receiveEnergy(amount, simulate);
        }
        @Override public IEnergyStorage asEnergyStorage(ItemStack stack) { return StaffEnergy.capability(stack); }
    };
    @Override public DeviceKind deviceKind() { return DeviceKind.MIMICRY_STAFF; }
    @Override public DeviceModuleStorage moduleStorage() { return StaffModuleStorage.INSTANCE; }
    @Override public DeviceEnergyBuffer energyBuffer() { return ENERGY; }
    @Override public DeviceNetworkBinding networkBinding() { return RailgunNetworkBinding.INSTANCE; }
    @Override public List<StructuralSlotSpec> structuralSlots() { return List.of(); }
    @Override public Predicate<ItemStack> moduleInputValidator(ItemStack device, HolderLookup.Provider registries) {
        return candidate -> canInstallOne(device, registries, candidate);
    }
    @Override public List<ItemStack> listModuleEntries(ItemStack device, HolderLookup.Provider registries) {
        return moduleStorage().listEntries(device);
    }
    @Override public boolean canInstallOne(ItemStack device, HolderLookup.Provider registries, ItemStack candidate) {
        return StaffModuleStorage.INSTANCE.canInstall(device, candidate, registries.lookupOrThrow(Registries.ENCHANTMENT));
    }
    @Override public boolean installOne(ItemStack device, HolderLookup.Provider registries, ItemStack candidate) {
        return canInstallOne(device, registries, candidate) && moduleStorage().installOne(device, candidate);
    }
    @Override public ItemStack uninstallOne(ItemStack device, HolderLookup.Provider registries, String typeId) {
        return moduleStorage().uninstallOne(device, typeId);
    }
    @Override public ItemStack uninstallAll(ItemStack device, HolderLookup.Provider registries, String typeId) {
        return moduleStorage().uninstallAll(device, typeId);
    }
    @Override public String moduleTypeId(ItemStack stack) { return StaffModuleStorage.typeId(stack); }
    @Override public int maxInstallAmount(ItemStack stack) { return StaffState.slotFor(stack) >= 0 ? 1 : 0; }
    @Override public ItemStack getStructuralSlot(ItemStack device, HolderLookup.Provider registries, StructuralSlotSpec spec) { return ItemStack.EMPTY; }
    @Override public void setStructuralSlot(ItemStack device, HolderLookup.Provider registries, StructuralSlotSpec spec, ItemStack stack) {}
    @Override public ItemStack removeStructuralSlot(ItemStack device, HolderLookup.Provider registries, StructuralSlotSpec spec, int amount) { return ItemStack.EMPTY; }
    @Override public boolean canPlaceStructural(ItemStack device, HolderLookup.Provider registries, StructuralSlotSpec spec, ItemStack stack) { return false; }
}
