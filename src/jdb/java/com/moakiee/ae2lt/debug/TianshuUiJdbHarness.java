package com.moakiee.ae2lt.debug;

import appeng.client.gui.style.StyleManager;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessMenuHost;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessPatternEncodingTermScreen;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessTerminalItem;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/** JDB-only launcher for opening the real Tianshu terminal screen without building a test network by hand. */
public final class TianshuUiJdbHarness {
    private static volatile String status = "idle";

    public static String openTestWorld() {
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            status = "opening world";
            minecraft.createWorldOpenFlows().openWorld(
                    "新的世界AE2LT Tianshu Automated Test",
                    () -> status = "world open cancelled");
        });
        return status = "world open queued";
    }

    public static String showTerminal() {
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            var player = minecraft.player;
            if (player == null) {
                status = "no client player";
                return;
            }
            if (ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL == null) {
                status = "ae2wtlib terminal unavailable";
                return;
            }
            var item = (TianshuWirelessTerminalItem)
                    ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get();
            var stack = new ItemStack(item);
            ItemMenuHostLocator locator = new ItemMenuHostLocator() {
                @Override public BlockHitResult hitResult() { return null; }
                @Override public ItemStack locateItem(net.minecraft.world.entity.player.Player ignored) { return stack; }
            };
            var host = new TianshuWirelessMenuHost(item, player, locator,
                    (ignoredPlayer, ignoredMenu) -> { });
            var menu = new TianshuWirelessPatternEncodingTermMenu(
                    player.containerMenu.containerId + 1, player.getInventory(), host);
            player.containerMenu = menu;
            var style = StyleManager.loadStyleDoc(
                    "/screens/terminals/tianshu_pattern_encoding_terminal.json");
            minecraft.setScreen(new TianshuWirelessPatternEncodingTermScreen(
                    menu, player.getInventory(), stack.getHoverName(), style));
            status = "terminal screen opened";
        });
        return status = "terminal open queued";
    }

    public static String status() { return status; }

    private TianshuUiJdbHarness() { }
}
