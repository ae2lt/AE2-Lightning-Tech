package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Development-only setup and observations; UI actions use the real native widgets. */
public final class TianshuMagnetClientProbe {
    private static volatile String serverReport = "idle";
    private TianshuMagnetClientProbe() {}

    public static String seed() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return "server not ready";
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            if (!(player.containerMenu instanceof TianshuEnhancedWirelessCraftingMenu menu)) return;
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAGNET);
            menu.setFilter(menu.getSlots(AE2wtlibSlotSemantics.PICKUP_CONFIG).getFirst().index, new ItemStack(Items.DIAMOND));
            menu.setFilter(menu.getSlots(AE2wtlibSlotSemantics.INSERT_CONFIG).getFirst().index, new ItemStack(Items.GOLD_INGOT));
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
        });
        return "queued native filter fixtures";
    }

    public static String observeServer() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return "server not ready";
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            if (player.containerMenu instanceof TianshuEnhancedWirelessCraftingMenu menu) {
                serverReport = describe(menu);
                org.slf4j.LoggerFactory.getLogger("TianshuMagnetClientProbe").info(serverReport);
            }
        });
        return "queued native filter observation";
    }

    private static String describe(TianshuEnhancedWirelessCraftingMenu menu) {
        var host = menu.getMagnetMenu().getMagnetHost();
        return "native magnet: container=" + menu.containerId + ", page=" + menu.wirelessPage
                + ", pickup=" + host.pickupConfig.getKey(0) + ", insert=" + host.insertConfig.getKey(0)
                + ", secondPickup=" + host.pickupConfig.getKey(1)
                + ", modes=" + host.getPickupMode() + "/" + host.getInsertMode()
                + ", carried=" + menu.getCarried()
                + ", smithing=" + menu.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING).get(1).getItem()
                + ", anvil=" + menu.getAnvilInput();
    }

    public static String status() {
        var mc = Minecraft.getInstance();
        return "server=" + serverReport + "; client="
                + (mc.player != null && mc.player.containerMenu instanceof TianshuEnhancedWirelessCraftingMenu menu ? describe(menu) : "not open")
                + "; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName());
    }
}
