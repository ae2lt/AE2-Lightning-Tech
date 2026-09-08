package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.client.TianshuCraftingTermScreen;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native screen clicks and read-only observations in the disposable acceptance world. */
public final class TianshuCraftingInteractionProbe {
    private static volatile String report = "idle";
    public static String hover(double x, double y) {
        var mc = Minecraft.getInstance();
        mc.tell(() -> org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),
                x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight()));
        return "queued native cursor move";
    }
    public static String openWireless() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            var terminal = player.getInventory().getItem(0);
            ((de.mari_023.ae2wtlib.api.terminal.ItemWT) terminal.getItem()).open(player,
                    appeng.menu.locator.MenuLocators.forInventorySlot(0), false);
        });
        return "queued wireless open, preserving inventory";
    }
    public static String statusAndCapture() {
        var mc = Minecraft.getInstance();
        mc.tell(() -> {
            report = TianshuCraftingClientProbe.status();
            System.out.println("TIANSHU_SCREEN_ACCEPTANCE " + report);
            TianshuCraftingClientProbe.f2();
        });
        return "queued screen snapshot";
    }
    public static String clickResult(int button) {
        var mc = Minecraft.getInstance();
        mc.tell(() -> {
            if (!(mc.screen instanceof TianshuCraftingTermScreen<?> screen)) return;
            for (var slot : screen.getMenu().slots) {
                if (screen.getMenu().isWorkResult(slot) && slot.isActive() && slot.x >= 0) {
                    double x = screen.getGuiLeft() + slot.x + 8;
                    double y = screen.getGuiTop() + slot.y + 8;
                    screen.mouseClicked(x, y, button);
                    screen.mouseReleased(x, y, button);
                    report = "clicked result at " + x + "," + y + " / button=" + button;
                    return;
                }
            }
        });
        return "queued actual result-slot mouse click";
    }
    public static String clickCell() {
        var mc = Minecraft.getInstance();
        mc.tell(() -> {
            if (!(mc.screen instanceof TianshuCraftingTermScreen<?> screen)) return;
            var slot = screen.getMenu().getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).getFirst();
            if (!slot.isActive() || slot.x < 0) return;
            double x = screen.getGuiLeft() + slot.x + 8;
            double y = screen.getGuiTop() + slot.y + 8;
            screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0);
        });
        return "queued actual cell-slot click";
    }
    public static String storeCarriedInPlayer(int inventorySlot) {
        var mc = Minecraft.getInstance();
        mc.tell(() -> {
            if (!(mc.screen instanceof TianshuCraftingTermScreen<?> screen)) return;
            for (var slot : screen.getMenu().slots) {
                if (slot.container == mc.player.getInventory() && slot.getContainerSlot() == inventorySlot) {
                    double x = screen.getGuiLeft() + slot.x + 8;
                    double y = screen.getGuiTop() + slot.y + 8;
                    screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0);
                    return;
                }
            }
        });
        return "queued actual player-slot click";
    }
    public static String snapshot() {
        var mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null) return "no server";
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            if (!(player.containerMenu instanceof TianshuCraftingTermMenu menu)) { report = "no terminal"; return; }
            var host = (com.moakiee.ae2lt.logic.tianshu.terminal.TianshuCraftingTerminalHost) menu.getHost();
            var stone = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_STONECUTTING);
            report = "page=" + menu.workPage + "; carried=" + menu.getCarried()
                    + "; ME stone=" + host.getInventory().extract(AEItemKey.of(Items.STONE), Long.MAX_VALUE,
                            Actionable.SIMULATE, IActionSource.ofPlayer(player))
                    + "; player slabs=" + player.getInventory().items.stream().filter(s -> s.is(Items.STONE_SLAB)).mapToInt(ItemStack::getCount).sum()
                    + "; input=" + stone.getFirst().getItem() + "; result=" + stone.getLast().getItem()
                    + "; cell=" + menu.getCell() + "; copy=" + menu.cellCopyMode
                    + "; first mark=" + menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG).getFirst().getItem();
            System.out.println("TIANSHU_NATIVE_CLIENT " + report);
        });
        return "queued authoritative snapshot";
    }
    public static String layout() {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TianshuCraftingTermScreen<?> screen)) return "no terminal screen";
        var out = new StringBuilder(screen.getClass().getSimpleName());
        for (var slot : screen.getMenu().slots) {
            if (screen.getMenu().isWorkResult(slot) && slot.isActive() && slot.x >= 0) out.append("; result center=")
                    .append(screen.getGuiLeft() + slot.x + 8).append(',').append(screen.getGuiTop() + slot.y + 8);
        }
        return out + "; GUI=" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight();
    }
    public static String status() { return report; }
}
