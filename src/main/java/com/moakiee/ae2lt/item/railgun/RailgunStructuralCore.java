package com.moakiee.ae2lt.item.railgun;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;

public final class RailgunStructuralCore {
    private RailgunStructuralCore() {
    }

    public static ItemStack getCore(ItemStack railgun) {
        if (railgun == null || railgun.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return railgun
                .getOrDefault(ModDataComponents.RAILGUN_STRUCTURAL_CORE.get(), ItemStack.EMPTY)
                .copyWithCount(1);
    }

    public static void setCore(ItemStack railgun, ItemStack core) {
        if (railgun == null || railgun.isEmpty()) {
            return;
        }
        if (core == null || core.isEmpty()) {
            railgun.remove(ModDataComponents.RAILGUN_STRUCTURAL_CORE.get());
            return;
        }
        if (!isValidCore(core)) {
            return;
        }
        railgun.set(ModDataComponents.RAILGUN_STRUCTURAL_CORE.get(), core.copyWithCount(1));
    }

    public static ItemStack removeCore(ItemStack railgun, int amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        var existing = getCore(railgun);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        setCore(railgun, ItemStack.EMPTY);
        return existing;
    }

    public static boolean hasCore(ItemStack railgun) {
        return isValidCore(getCore(railgun));
    }

    public static boolean canInstallCore(ItemStack railgun, ItemStack candidateCore) {
        if (candidateCore == null || candidateCore.isEmpty()) {
            return true;
        }
        return isValidCore(candidateCore);
    }

    private static boolean isValidCore(ItemStack core) {
        return core != null && !core.isEmpty() && core.is(ModItems.ULTIMATE_OVERLOAD_CORE.get());
    }
}
