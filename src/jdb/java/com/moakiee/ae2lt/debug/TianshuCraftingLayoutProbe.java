package com.moakiee.ae2lt.debug;

import appeng.api.parts.PartHelper;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

/** Actual placed wired terminal used only in the disposable client layout test world. */
public final class TianshuCraftingLayoutProbe {
    private static String report = "idle";
    public static String clickPage(int index) {
        var mc = Minecraft.getInstance();
        mc.tell(() -> {
            var label = net.minecraft.network.chat.Component.translatable(
                    com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.values()[index].translationKey()).getString();
            for (var child : mc.screen.children()) {
                if (child instanceof appeng.client.gui.widgets.TabButton button
                        && button.getClass().getSimpleName().equals("WorkPageTabButton")) {
                    if (button.getMessage().getString().equals(label) || button.getMessage().getString().startsWith(label + "\n")) {
                        mc.screen.mouseClicked(button.getX() + 8, button.getY() + 8, 0);
                        mc.screen.mouseReleased(button.getX() + 8, button.getY() + 8, 0);
                        return;
                    }
                }
            }
            report = "page button not present";
        });
        return "queued actual page-button click";
    }
    public static String clickTool(String tool) {
        var label = switch (tool) {
            case "settings" -> de.mari_023.ae2wtlib.api.TextConstants.TERMINAL_SETTINGS;
            case "magnet" -> de.mari_023.ae2wtlib.api.TextConstants.MAGNET_FILTER;
            case "trash" -> de.mari_023.ae2wtlib.api.TextConstants.TRASH;
            default -> throw new IllegalArgumentException(tool);
        };
        return clickLabel(label.getString());
    }
    public static String clickLabel(String label) {
        var mc = Minecraft.getInstance();
        mc.tell(() -> {
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget button
                        && button.visible && (button.getMessage().getString().equals(label)
                        || button instanceof appeng.client.gui.widgets.ITooltip tooltip
                        && !tooltip.getTooltipMessage().isEmpty()
                        && tooltip.getTooltipMessage().getFirst().getString().equals(label))) {
                    mc.screen.mouseClicked(button.getX() + 3, button.getY() + 3, 0);
                    mc.screen.mouseReleased(button.getX() + 3, button.getY() + 3, 0);
                    return;
                }
            }
            report = "button not present: " + label;
        });
        return "queued actual labeled-button click";
    }
    public static String openWired() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            var pos = player.blockPosition().offset(3, 1, 0);
            var level = player.serverLevel();
            level.setBlockAndUpdate(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            PartHelper.setPart(level, pos, null, player, AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
            var part = PartHelper.setPart(level, pos, Direction.NORTH, player, ModItems.TIANSHU_CRAFTING_TERMINAL.get());
            if (part == null) { report = "wired part placement failed"; return; }
            PartHelper.getPartHost(level, pos).markForUpdate();
            report = "placed wired terminal; wait for client synchronization before TianshuWiredOpenProbe.open: " + pos + "; menu=" + player.containerMenu.getClass().getName();
            System.out.println("TIANSHU_LAYOUT_ACCEPTANCE " + report);
        });
        return "queued placed wired terminal";
    }
    public static String status() { return report; }
}
