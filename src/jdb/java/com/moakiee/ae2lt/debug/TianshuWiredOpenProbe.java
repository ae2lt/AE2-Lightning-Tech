package com.moakiee.ae2lt.debug;

import appeng.api.parts.PartHelper;
import appeng.core.definitions.AEItems;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Open only after the placed cable/part update has reached the actual client. */
public final class TianshuWiredOpenProbe {
    public static String open(int x, int y, int z) {
        var mc = Minecraft.getInstance();
        var pos = new BlockPos(x, y, z);
        if (mc.level == null || PartHelper.getPart(mc.level, pos, Direction.NORTH) == null)
            return "client part not synchronized yet";
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var part = PartHelper.getPart(player.serverLevel(), pos, Direction.NORTH);
            if (!(part instanceof com.moakiee.ae2lt.part.TianshuCraftingTerminalPart terminal)) return;
            MenuOpener.open(TianshuCraftingTermMenu.TYPE, player, MenuLocators.forPart(terminal));
            if (player.containerMenu instanceof TianshuCraftingTermMenu menu) {
                menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).getFirst().set(AEItems.ITEM_CELL_1K.stack());
                menu.broadcastChanges();
            }
        });
        return "queued wired menu after verifying client part exists";
    }
}
