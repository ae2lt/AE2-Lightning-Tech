package com.moakiee.ae2lt.item.staff;

import java.util.List;
import java.util.stream.Stream;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.module.DeviceModuleStorage;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.CommonHooks;

/** Workbench storage view over the same bounded slots used by gameplay. */
public final class StaffModuleStorage implements DeviceModuleStorage {
    public static final StaffModuleStorage INSTANCE = new StaffModuleStorage();
    private StaffModuleStorage() {}
    @Override public DeviceKind deviceKind() { return DeviceKind.MIMICRY_STAFF; }
    @Override public List<ItemStack> listEntries(ItemStack device) {
        if (StaffPhaseService.isProjection(device)) return List.of();
        return StaffState.modules(device).stream().filter(s -> !s.isEmpty()).toList();
    }
    public static String typeId(ItemStack module) { return BuiltInRegistries.ITEM.getKey(module.getItem()).toString(); }
    @Override public int getCount(ItemStack device, String typeId) {
        return (int) listEntries(device).stream().filter(s -> typeId(s).equals(typeId)).count();
    }
    public boolean canInstall(ItemStack device, ItemStack candidate, RegistryLookup<Enchantment> lookup) {
        if (StaffPhaseService.isProjection(device)) return false;
        int slot = StaffState.slotFor(candidate);
        if (!(device.getItem() instanceof MimicryStaffItem) || slot < 0
                || !StaffState.modules(device).get(slot).isEmpty() || lookup == null) return false;
        var proposed = device.copy();
        var modules = StaffState.modules(proposed);
        modules.set(slot, candidate.copyWithCount(1));
        StaffState.setModules(proposed, modules);
        return StaffEnchantments.compatible(StaffEnchantments.proposed(proposed, lookup));
    }
    @Override public boolean canInstallOne(ItemStack device, ItemStack candidate) {
        return canInstall(device, candidate, CommonHooks.resolveLookup(Registries.ENCHANTMENT));
    }
    @Override public boolean installOne(ItemStack device, ItemStack candidate) {
        if (!canInstallOne(device, candidate)) return false;
        var modules = StaffState.modules(device);
        modules.set(StaffState.slotFor(candidate), candidate.copyWithCount(1));
        StaffState.setModules(device, modules);
        return true;
    }
    @Override public ItemStack uninstallOne(ItemStack device, String typeId) {
        if (StaffPhaseService.isProjection(device)) return ItemStack.EMPTY;
        var modules = StaffState.modules(device);
        for (int slot = 0; slot < modules.size(); slot++) {
            var module = modules.get(slot);
            if (!module.isEmpty() && typeId(module).equals(typeId)) {
                modules.set(slot, ItemStack.EMPTY);
                StaffState.setModules(device, modules);
                return module;
            }
        }
        return ItemStack.EMPTY;
    }
    @Override public ItemStack uninstallAll(ItemStack device, String typeId) { return uninstallOne(device, typeId); }
    @Override public boolean hasAnyInstalled(ItemStack device) { return !listEntries(device).isEmpty(); }
    @Override public Stream<ItemStack> installedModuleStacks(ItemStack device) { return listEntries(device).stream(); }
}
