package com.moakiee.ae2lt.menu.hub;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;

/**
 * MenuProvider that opens the unified DeviceHub UI.
 */
public class DeviceHubHost implements MenuProvider {
    private final int defaultTab;

    public DeviceHubHost(int defaultTab) {
        this.defaultTab = defaultTab;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.ae2lt.device_hub");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DeviceHubMenu(containerId, playerInventory, defaultTab);
    }

    /** Open the hub for a player with the given default tab. */
    public static void open(ServerPlayer player, int defaultTab) {
        int resolvedTab = resolveDefaultTab(player, defaultTab);
        if (resolvedTab < 0) {
            return;
        }
        player.openMenu(new DeviceHubHost(resolvedTab), buf -> buf.writeVarInt(resolvedTab));
    }

    /** Map an equipment slot to its corresponding hub tab index. */
    public static int tabForArmorSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> 1;
        };
    }

    private static int resolveDefaultTab(Player player, int defaultTab) {
        if (hasDeviceForTab(player, defaultTab)) {
            return defaultTab;
        }
        for (int tab = 0; tab < DeviceHubMenu.TAB_COUNT; tab++) {
            if (hasDeviceForTab(player, tab)) {
                return tab;
            }
        }
        return -1;
    }

    private static boolean hasDeviceForTab(Player player, int tab) {
        return switch (tab) {
            case DeviceHubMenu.TAB_HELMET -> hasArmor(player, EquipmentSlot.HEAD);
            case DeviceHubMenu.TAB_CHESTPLATE -> hasArmor(player, EquipmentSlot.CHEST);
            case DeviceHubMenu.TAB_LEGGINGS -> hasArmor(player, EquipmentSlot.LEGS);
            case DeviceHubMenu.TAB_BOOTS -> hasArmor(player, EquipmentSlot.FEET);
            case DeviceHubMenu.TAB_RAILGUN -> hasRailgun(player);
            default -> false;
        };
    }

    private static boolean hasArmor(Player player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        return stack.getItem() instanceof BaseCelestweaveArmorItem;
    }

    private static boolean hasRailgun(Player player) {
        return player.getMainHandItem().getItem() instanceof ElectromagneticRailgunItem
                || player.getOffhandItem().getItem() instanceof ElectromagneticRailgunItem;
    }
}
