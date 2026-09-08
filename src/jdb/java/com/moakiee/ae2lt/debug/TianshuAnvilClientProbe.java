package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Development-only fixtures for native anvil cost rendering and F2 acceptance. */
public final class TianshuAnvilClientProbe {
    private static volatile String report = "idle";
    private TianshuAnvilClientProbe() {}

    public static String anvilCostCase(int cost, int levels, boolean creative, boolean valid) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return "server not ready";
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            if (!(player.containerMenu instanceof TianshuCraftingTermMenu menu)) { report = "terminal not open"; return; }
            player.setGameMode(creative ? net.minecraft.world.level.GameType.CREATIVE : net.minecraft.world.level.GameType.SURVIVAL);
            player.setExperienceLevels(levels);
            menu.setWorkPage(com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.ANVIL);
            var slots = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL);
            slots.get(0).set(ItemStack.EMPTY);
            slots.get(1).set(ItemStack.EMPTY);
            menu.setAnvilName("");
            var pick = new ItemStack(Items.DIAMOND_PICKAXE);
            pick.setDamageValue(100);
            pick.set(net.minecraft.core.component.DataComponents.REPAIR_COST, valid ? cost - 1 : cost);
            slots.get(0).set(pick);
            slots.get(1).set(valid ? new ItemStack(Items.DIAMOND) : ItemStack.EMPTY);
            menu.broadcastChanges();
            report = "anvil fixture: cost=" + menu.anvilCost + ", levels=" + player.experienceLevel
                    + ", creative=" + creative + ", result=" + slots.get(2).hasItem()
                    + ", mayPickup=" + slots.get(2).mayPickup(player);
            org.slf4j.LoggerFactory.getLogger("TianshuCraftingClientProbe").info(report);
        });
        return "queued anvil fixture";
    }

    public static String anvilStatus() {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof TianshuCraftingTermMenu menu)) return "terminal not open";
        return report + "; client cost=" + menu.anvilCost + ", engine=" + menu.getAnvil().getCost()
                + ", levels=" + player.experienceLevel + ", creative=" + player.getAbilities().instabuild
                + ", result=" + menu.getAnvil().getSlot(2).hasItem()
                + ", mayPickup=" + menu.getAnvil().getSlot(2).mayPickup(player);
    }

}
