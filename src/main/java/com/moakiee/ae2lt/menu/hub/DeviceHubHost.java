package com.moakiee.ae2lt.menu.hub;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

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
        player.openMenu(new DeviceHubHost(defaultTab), buf -> buf.writeVarInt(defaultTab));
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
}
