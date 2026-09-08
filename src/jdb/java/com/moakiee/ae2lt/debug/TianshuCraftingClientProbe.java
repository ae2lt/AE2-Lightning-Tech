package com.moakiee.ae2lt.debug;

import appeng.api.ids.AEComponents;
import appeng.core.definitions.AEItems;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Development-only JDB entry points for native F2 screenshot acceptance in a disposable test world. */
public final class TianshuCraftingClientProbe {
    private static volatile String report = "idle";
    private TianshuCraftingClientProbe() {}

    public static String openWorld() {
        var mc = Minecraft.getInstance();
        mc.tell(() -> {
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.getWindow().setWindowed(1280, 900);
            mc.resizeDisplay();
            mc.createWorldOpenFlows().openWorld("TianshuCraftingQA", () -> report = "world open failed");
            report = "opening QA world";
        });
        return "queued world";
    }

    public static String openTerminal() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return "server not ready";
        server.execute(() -> {
            if (server.getPlayerList().getPlayers().isEmpty()) { report = "player not ready"; return; }
            var player = server.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            player.getInventory().clearContent();
            player.experienceLevel = 30;
            var item = ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get();
            var terminal = new ItemStack(item);
            terminal.set(AEComponents.STORED_ENERGY, 1000000.0);
            if (net.neoforged.fml.ModList.get().isLoaded("ae2wtlib"))
                item.getUpgrades(terminal).addItems(new ItemStack(de.mari_023.ae2wtlib.AE2wtlibItems.MAGNET_CARD));
            player.getInventory().setItem(0, terminal);
            player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 16));
            player.getInventory().setItem(10, new ItemStack(Items.STONE, 32));
            player.getInventory().setItem(11, new ItemStack(Items.DIAMOND_HELMET));
            player.inventoryMenu.broadcastChanges();
            item.open(player, MenuLocators.forInventorySlot(0), false);
            if (!(player.containerMenu instanceof TianshuCraftingTermMenu menu)) { report = "terminal failed to open"; return; }
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING).get(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING).get(1).set(new ItemStack(Items.DIAMOND_SWORD));
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING).get(2).set(new ItemStack(Items.NETHERITE_INGOT));
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).get(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_STONECUTTING).get(0).set(new ItemStack(Items.STONE, 8));
            var cell = AEItems.ITEM_CELL_1K.stack();
            var cellItem = (appeng.api.storage.cells.ICellWorkbenchItem) cell.getItem();
            cellItem.getConfigInventory(cell).createMenuWrapper().setItemDirect(0, new ItemStack(Items.DIAMOND));
            cellItem.getConfigInventory(cell).createMenuWrapper().setItemDirect(54, new ItemStack(Items.GOLD_INGOT));
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).getFirst().set(cell);
            menu.broadcastChanges();
            report = "terminal open with real fixture inputs";
        });
        return "queued terminal";
    }

    public static String f2() {
        var mc = Minecraft.getInstance();
        mc.tell(() -> mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F2, 0, GLFW.GLFW_PRESS, 0));
        return "queued native F2";
    }

    public static String click(double x, double y, int button) {
        var mc = Minecraft.getInstance();
        mc.tell(() -> {
            if (mc.screen != null) {
                mc.screen.mouseClicked(x, y, button);
                mc.screen.mouseReleased(x, y, button);
            }
        });
        return "queued click";
    }

    public static String type(String text) {
        var mc = Minecraft.getInstance();
        mc.tell(() -> { if (mc.screen != null) for (char c : text.toCharArray()) mc.screen.charTyped(c, 0); });
        return "queued text";
    }

    public static String key(int key, int modifiers) {
        var mc = Minecraft.getInstance();
        mc.tell(() -> mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), key, 0, GLFW.GLFW_PRESS, modifiers));
        return "queued key";
    }

    public static String status() {
        var mc = Minecraft.getInstance();
        return report + "; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName())
                + "; menu=" + (mc.player == null ? "no player" : mc.player.containerMenu.getClass().getName())
                + "; size=" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight();
    }
}
