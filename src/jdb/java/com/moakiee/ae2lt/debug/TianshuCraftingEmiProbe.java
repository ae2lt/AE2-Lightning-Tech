package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Development-only browser transfer acceptance, using EMI's registered handler and real packets. */
public final class TianshuCraftingEmiProbe {
    private static volatile String report = "idle";
    private TianshuCraftingEmiProbe() {}
    public static String showCrafting() {
        Minecraft.getInstance().tell(() -> EmiApi.displayRecipes(EmiStack.of(Items.CRAFTING_TABLE)));
        return "queued EMI crafting table recipe";
    }
    public static String checkCrafting() {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            var player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var menu = (TianshuCraftingTermMenu) player.containerMenu;
            var matrix = menu.getCraftingMatrix();
            int planks = 0;
            for (int i = 0; i < matrix.size(); i++) if (matrix.getStackInSlot(i).is(Items.OAK_PLANKS)) planks += matrix.getStackInSlot(i).getCount();
            var host = (TianshuWirelessCraftingTermMenuHost) menu.getHost();
            long stored = host.getActionableNode().getGrid().getStorageService().getInventory()
                    .extract(AEItemKey.of(Items.OAK_PLANKS), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.ofPlayer(player));
            var result = menu.getSlots(appeng.menu.SlotSemantics.CRAFTING_RESULT).getFirst().getItem();
            report = (planks == 4 && stored == 60 && result.is(Items.CRAFTING_TABLE) ? "PASS" : "FAIL")
                    + ": EMI normal crafting, grid planks=" + planks + ", ME planks=" + stored + ", preview=" + result;
            System.out.println("TIANSHU_EMI_ACCEPTANCE " + report);
        });
        return "queued EMI verification";
    }
    public static String status() { return report; }
}
