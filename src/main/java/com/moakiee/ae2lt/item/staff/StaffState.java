package com.moakiee.ae2lt.item.staff;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.item.railgun.RailgunModuleItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;

public final class StaffState {
    public static final int SLOTS = 12;
    private StaffState() {}

    public static StaffSettings settings(ItemStack stack) {
        if (StaffPhaseService.isProjection(stack) && StaffPhaseService.clientView()) return StaffPhaseService.view(stack).settings();
        stack = StaffPhaseService.resolve(stack);
        return stack.getOrDefault(ModDataComponents.MIMICRY_SETTINGS.get(), StaffSettings.DEFAULT);
    }

    public static List<ItemStack> modules(ItemStack stack) {
        stack = StaffPhaseService.resolve(stack);
        var contents = stack.getOrDefault(ModDataComponents.MIMICRY_MODULES.get(), ItemContainerContents.EMPTY);
        var result = new ArrayList<ItemStack>(SLOTS);
        for (int i = 0; i < SLOTS; i++) result.add(contents.getSlots() > i ? contents.getStackInSlot(i) : ItemStack.EMPTY);
        return result;
    }

    public static ItemStack module(ItemStack stack, StaffModule type) {
        stack = StaffPhaseService.resolve(stack);
        var contents = stack.getOrDefault(ModDataComponents.MIMICRY_MODULES.get(), ItemContainerContents.EMPTY);
        if (contents.getSlots() <= type.slot()) return ItemStack.EMPTY;
        var installed = contents.getStackInSlot(type.slot());
        return installed.getItem() instanceof StaffModuleItem item && item.module() == type ? installed : ItemStack.EMPTY;
    }

    public static boolean has(ItemStack stack, StaffModule module) {
        if (StaffPhaseService.isProjection(stack) && StaffPhaseService.clientView()) return StaffPhaseService.view(stack).has(module);
        return !module(stack, module).isEmpty();
    }

    public static int slotFor(ItemStack module) {
        if (module.getItem() instanceof StaffModuleItem item) return item.module().slot();
        if (module.getItem() instanceof RailgunModuleItem item) return switch (item.moduleType()) {
            case CORE -> 10;
            case OVERLOAD_EXECUTION, MULTIDIMENSIONAL_EXECUTION -> 11;
            default -> -1;
        };
        return -1;
    }

    public static boolean has(ItemStack stack, RailgunModuleType type) {
        if (StaffPhaseService.isProjection(stack) && StaffPhaseService.clientView()) return StaffPhaseService.view(stack).has(type);
        stack = StaffPhaseService.resolve(stack);
        int slot = switch (type) { case CORE -> 10; case OVERLOAD_EXECUTION, MULTIDIMENSIONAL_EXECUTION -> 11; default -> -1; };
        var contents = stack.getOrDefault(ModDataComponents.MIMICRY_MODULES.get(), ItemContainerContents.EMPTY);
        if (slot < 0 || contents.getSlots() <= slot) return false;
        return contents.getStackInSlot(slot).getItem() instanceof RailgunModuleItem item && item.moduleType() == type;
    }

    public static void setModules(ItemStack stack, List<ItemStack> modules) {
        if (StaffPhaseService.isProjection(stack)) throw new IllegalArgumentException("Unlock the staff before modifying modules");
        if (modules.size() != SLOTS) throw new IllegalArgumentException("Expected twelve staff slots");
        for (int i = 0; i < SLOTS; i++) {
            var entry = modules.get(i);
            if (!entry.isEmpty() && (slotFor(entry) != i || entry.getCount() != 1)) throw new IllegalArgumentException("Invalid staff module");
        }
        stack.set(ModDataComponents.MIMICRY_MODULES.get(), ItemContainerContents.fromItems(modules));
    }
}
