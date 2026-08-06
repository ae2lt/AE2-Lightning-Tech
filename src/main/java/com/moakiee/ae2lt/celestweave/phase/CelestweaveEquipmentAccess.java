package com.moakiee.ae2lt.celestweave.phase;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;

/** Single source of truth for armor that is logically equipped. */
public final class CelestweaveEquipmentAccess {
    private CelestweaveEquipmentAccess() {
    }

    public static ItemStack findArmor(Player player, EquipmentSlot slot) {
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && player instanceof ServerPlayer serverPlayer) {
            ItemStack privateArmor = PhaseLockService.getPrivateArmor(serverPlayer, slot);
            if (isArmor(privateArmor)) {
                return privateArmor;
            }
        }
        ItemStack equipped = player.getItemBySlot(slot);
        return isArmor(equipped) ? equipped : ItemStack.EMPTY;
    }

    private static boolean isArmor(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BaseCelestweaveArmorItem;
    }
}
