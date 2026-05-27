package com.moakiee.ae2lt.blockentity.workbench;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import appeng.menu.SlotSemantic;

import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.DeviceSlotType;
import com.moakiee.ae2lt.device.energy.DeviceEnergyBuffer;
import com.moakiee.ae2lt.device.module.ArmorModuleStorage;
import com.moakiee.ae2lt.device.module.DeviceModuleStorage;
import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.device.network.DeviceNetworkBinding;
import com.moakiee.ae2lt.overload.armor.ArmorDeviceEnergyBuffer;
import com.moakiee.ae2lt.overload.armor.ArmorEnergyModuleItem;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.OverloadArmorState;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.registry.ModItems;

public final class ArmorWorkbenchAdapter implements DeviceWorkbenchAdapter {
    public static final ArmorWorkbenchAdapter HELMET = new ArmorWorkbenchAdapter(ArmorPart.HEAD);
    public static final ArmorWorkbenchAdapter CHESTPLATE = new ArmorWorkbenchAdapter(ArmorPart.CHEST);
    public static final ArmorWorkbenchAdapter LEGGINGS = new ArmorWorkbenchAdapter(ArmorPart.LEGS);
    public static final ArmorWorkbenchAdapter BOOTS = new ArmorWorkbenchAdapter(ArmorPart.FEET);

    private static final List<StructuralSlotSpec> STRUCTURAL_SLOTS = List.of(
            slot(0, DeviceSlotType.CORE, com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.OVERLOAD_DEVICE_WORKBENCH_CORE));

    private final ArmorPart part;
    private final ArmorModuleStorage moduleStorage;

    private ArmorWorkbenchAdapter(ArmorPart part) {
        this.part = part;
        this.moduleStorage = new ArmorModuleStorage(part);
    }

    @Override
    public DeviceKind deviceKind() {
        return part.deviceKind();
    }

    @Override
    public DeviceModuleStorage moduleStorage() {
        return moduleStorage;
    }

    @Override
    public DeviceEnergyBuffer energyBuffer() {
        return ArmorDeviceEnergyBuffer.INSTANCE;
    }

    @Override
    public DeviceNetworkBinding networkBinding() {
        return ArmorNetworkBinding.INSTANCE;
    }

    @Override
    public List<StructuralSlotSpec> structuralSlots() {
        return STRUCTURAL_SLOTS;
    }

    @Override
    public Predicate<ItemStack> moduleInputValidator(ItemStack device, HolderLookup.Provider registries) {
        return stack -> (stack.getItem() instanceof OverloadArmorSubmoduleItem
                || stack.getItem() instanceof ArmorEnergyModuleItem)
                && OverloadArmorState.canInstallModule(device, registries, stack);
    }

    @Override
    public List<ItemStack> listModuleEntries(ItemStack device, HolderLookup.Provider registries) {
        return OverloadArmorState.loadModuleStacks(device, registries);
    }

    @Override
    public boolean canInstallOne(ItemStack device, HolderLookup.Provider registries, ItemStack candidate) {
        return OverloadArmorState.canInstallModule(device, registries, candidate);
    }

    @Override
    public boolean installOne(ItemStack device, HolderLookup.Provider registries, ItemStack candidate) {
        return OverloadArmorState.installOneModule(device, registries, candidate);
    }

    @Override
    public ItemStack uninstallOne(ItemStack device, HolderLookup.Provider registries, String typeId) {
        return OverloadArmorState.uninstallOneModule(device, registries, typeId);
    }

    @Override
    public ItemStack uninstallAll(ItemStack device, HolderLookup.Provider registries, String typeId) {
        return OverloadArmorState.uninstallAllOfType(device, registries, typeId);
    }

    @Override
    public String moduleTypeId(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
            return "";
        }
        var ref = new String[]{""};
        provider.collectSubmodules(stack, submodule -> {
            if (submodule != null && !submodule.id().isBlank() && ref[0].isEmpty()) {
                ref[0] = submodule.id();
            }
        });
        return ref[0];
    }

    @Override
    public int maxInstallAmount(ItemStack stack) {
        return OverloadArmorState.getSubmoduleMaxInstallAmountForStack(stack);
    }

    @Override
    public int baseOverloadBudget(ItemStack device, HolderLookup.Provider registries) {
        return OverloadArmorState.getBaseOverload(device, registries);
    }

    @Override
    public int currentIdleOverload(ItemStack device, HolderLookup.Provider registries) {
        return OverloadArmorState.computeTotalIdleOverload(device, registries);
    }

    @Override
    public ItemStack getStructuralSlot(ItemStack device, HolderLookup.Provider registries, StructuralSlotSpec spec) {
        return OverloadArmorState.getSlot(device, registries, toArmorSlot(spec));
    }

    @Override
    public void setStructuralSlot(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            ItemStack stack) {
        if (stack != null
                && !stack.isEmpty()
                && spec.slotType() == DeviceSlotType.CORE
                && !OverloadArmorState.canInstallCore(device, registries, stack)) {
            return;
        }
        OverloadArmorState.ensureArmorId(device);
        OverloadArmorState.setSlot(device, registries, toArmorSlot(spec), stack.copy());
    }

    @Override
    public ItemStack removeStructuralSlot(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            int amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        var existing = getStructuralSlot(device, registries, spec);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (amount >= existing.getCount()) {
            setStructuralSlot(device, registries, spec, ItemStack.EMPTY);
            return existing;
        }
        var remaining = existing.copy();
        var removed = remaining.split(amount);
        setStructuralSlot(device, registries, spec, remaining);
        return removed;
    }

    @Override
    public boolean canPlaceStructural(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            ItemStack stack) {
        if (device.isEmpty() || stack.isEmpty()) {
            return false;
        }
        return switch (spec.slotType()) {
            case CORE -> stack.is(ModItems.ULTIMATE_OVERLOAD_CORE.get())
                    && OverloadArmorState.canInstallCore(device, registries, stack);
            default -> false;
        };
    }

    @Override
    public boolean mayPickupStructural(
            ItemStack device,
            HolderLookup.Provider registries,
            StructuralSlotSpec spec,
            Player player,
            ItemStack carried) {
        if (device.isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public void onDeviceInserted(ItemStack device) {
        OverloadArmorState.ensureArmorId(device);
    }

    @Override
    public void onModulesChanged(ItemStack device, HolderLookup.Provider registries, Dist dist) {
        OverloadArmorState.ensureArmorId(device);
        OverloadArmorState.reconcileInstalledSubmodules(null, device, registries, dist);
    }

    private static StructuralSlotSpec slot(int index, DeviceSlotType type, SlotSemantic semantic) {
        return new StructuralSlotSpec(index, type, semantic);
    }

    private static int toArmorSlot(StructuralSlotSpec spec) {
        return switch (spec.slotType()) {
            case CORE -> OverloadArmorState.SLOT_CORE;
            default -> -1;
        };
    }
}
