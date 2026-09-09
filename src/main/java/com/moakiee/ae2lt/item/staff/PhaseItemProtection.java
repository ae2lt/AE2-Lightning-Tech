package com.moakiee.ae2lt.item.staff;

import com.moakiee.ae2lt.item.PhaseLockProjectionItem;
import com.moakiee.ae2lt.registry.ModDataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** Transfer admission checks. Never copies, restores a snapshot, or changes normal stack reads. */
public final class PhaseItemProtection {
    private PhaseItemProtection() {}

    public static boolean isProtected(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof PhaseLockProjectionItem || isLockedStaff(stack));
    }

    public static boolean isLockedStaff(ItemStack stack) {
        return StaffPhaseService.isProjection(stack) || stack.getItem() instanceof MimicryStaffItem
                && stack.getOrDefault(ModDataComponents.MIMICRY_PHASE_LOCK.get(), false);
    }

    public static boolean containsProtected(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isProtected(inventory.getItem(i))) return true;
        }
        return false;
    }

    public static ItemStack transferable(ItemStack stack) {
        return isProtected(stack) ? ItemStack.EMPTY : stack;
    }

    public static boolean blocksClick(AbstractContainerMenu menu, int slot, int button, ClickType type, Player player) {
        if (isProtected(menu.getCarried())) return true;
        if (slot >= 0 && slot < menu.slots.size() && isProtected(menu.getSlot(slot).getItem())) return true;
        return type == ClickType.SWAP && (button >= 0 && button < 9 || button == 40)
                && isProtected(player.getInventory().getItem(button));
    }

    /** Move retained real stacks to the replacement player; the old inventory relinquishes them. */
    public static void transferRetainedStaff(Inventory from, Inventory to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack retained = from.getItem(i);
            if (!isLockedStaff(retained) || StaffPhaseService.isProjection(retained)) continue;
            ItemStack destination = to.getItem(i);
            if (destination.isEmpty() || ItemStack.matches(destination, retained)) {
                from.setItem(i, ItemStack.EMPTY);
                to.setItem(i, retained);
            } else {
                int free = to.getFreeSlot();
                if (free >= 0) {
                    from.setItem(i, ItemStack.EMPTY);
                    to.setItem(free, retained);
                }
            }
        }
    }
}
