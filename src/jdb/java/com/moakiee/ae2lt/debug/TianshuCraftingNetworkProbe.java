package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Development-only real-network acceptance in TianshuCraftingQA. */
public final class TianshuCraftingNetworkProbe {
    private static volatile String report = "idle";
    private static BlockPos accessPoint;
    private TianshuCraftingNetworkProbe() {}

    public static String build() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.serverLevel();
            var base = player.blockPosition().offset(4, 0, 0);
            accessPoint = base.north();
            level.setBlockAndUpdate(base, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            level.setBlockAndUpdate(base.east(), AEBlocks.DRIVE.block().defaultBlockState());
            level.setBlockAndUpdate(accessPoint, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
            var cell = AEItems.ITEM_CELL_1K.stack();
            var storage = StorageCells.getCellInventory(cell, null);
            storage.insert(AEItemKey.of(Items.STONE), 128, Actionable.MODULATE, IActionSource.ofPlayer(player));
            storage.insert(AEItemKey.of(Items.OAK_PLANKS), 64, Actionable.MODULATE, IActionSource.ofPlayer(player));
            storage.insert(AEItemKey.of(Items.DIAMOND), 16, Actionable.MODULATE, IActionSource.ofPlayer(player));
            storage.persist();
            ((DriveBlockEntity) level.getBlockEntity(base.east())).getInternalInventory().setItemDirect(0, cell);
            report = "network built: " + accessPoint;
        });
        return "queued network";
    }

    public static String linkAndOpen() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            var terminal = player.getInventory().getItem(0);
            terminal.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(player.level().dimension(), accessPoint));
            player.inventoryMenu.broadcastChanges();
            ((ItemWT) terminal.getItem()).open(player, MenuLocators.forInventorySlot(0), false);
            report = "linked and opened: " + player.containerMenu.getClass().getSimpleName();
        });
        return "queued link";
    }

    public static String checkTransfer() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var menu = (TianshuCraftingTermMenu) player.containerMenu;
            var host = (com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost) menu.getHost();
            var node = host.getActionableNode();
            if (node == null) { report = "FAIL: wireless node unavailable, " + host.getLinkStatus(); return; }
            var storage = node.getGrid().getStorageService().getInventory();
            long before = storage.extract(AEItemKey.of(Items.STONE), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.ofPlayer(player));
            int playerBefore = player.getInventory().items.stream().filter(s -> s.is(Items.STONE)).mapToInt(ItemStack::getCount).sum();
            menu.fillWorkRecipe("minecraft:stone_slab_from_stone_stonecutting");
            var slots = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_STONECUTTING);
            long after = storage.extract(AEItemKey.of(Items.STONE), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.ofPlayer(player));
            int playerAfter = player.getInventory().items.stream().filter(s -> s.is(Items.STONE)).mapToInt(ItemStack::getCount).sum();
            boolean pass = before == 128 && after == 127 && playerBefore == playerAfter
                    && slots.getFirst().getItem().is(Items.STONE) && slots.getFirst().getItem().getCount() == 1
                    && slots.getLast().getItem().is(Items.STONE_SLAB) && slots.getLast().getItem().getCount() == 2;
            report = (pass ? "PASS" : "FAIL") + ": real ME transfer stone " + before + " -> " + after
                    + "; player stone " + playerBefore + " -> " + playerAfter
                    + "; input=" + slots.getFirst().getItem() + "; result=" + slots.getLast().getItem();
            System.out.println("TIANSHU_NETWORK_ACCEPTANCE " + report);
        });
        return "queued transfer check";
    }

    public static String status() { return report; }

    public static String inspect() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            if (!(player.containerMenu instanceof TianshuCraftingTermMenu menu)) {
                report = "not a Tianshu crafting menu: " + player.containerMenu.getClass().getSimpleName(); return;
            }
            var host = (com.moakiee.ae2lt.logic.tianshu.terminal.TianshuCraftingTerminalHost) menu.getHost();
            var node = menu.getGridNode();
            var out = new StringBuilder(menu.getClass().getSimpleName()).append("; host=").append(host.getClass().getSimpleName())
                    .append("; view=").append(menu.getConfigManager().getSetting(appeng.api.config.Settings.VIEW_MODE))
                    .append("; maintainable=").append(menu.isMaintainableView())
                    .append("; node=").append(node != null);
            if (node != null) {
                out.append("; powered=").append(node.isPowered()).append("; active=").append(node.isActive());
                for (var type : node.getGrid().getMachineClasses()) out.append("; machine=").append(type.getSimpleName());
                for (var drive : node.getGrid().getMachines(DriveBlockEntity.class)) {
                    out.append("; drive=").append(drive.getBlockPos());
                    for (int i = 0; i < drive.getInternalInventory().size(); i++)
                        if (!drive.getInternalInventory().getStackInSlot(i).isEmpty())
                            out.append(" cell[").append(i).append("]=").append(drive.getInternalInventory().getStackInSlot(i));
                }
            }
            for (var item : new net.minecraft.world.item.Item[]{Items.STONE, Items.OAK_PLANKS, Items.DIAMOND})
                out.append("; ").append(item).append('=').append(host.getInventory().extract(AEItemKey.of(item),
                        Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.ofPlayer(player)));
            report = out.toString();
            System.out.println("TIANSHU_NETWORK_INSPECT " + report);
        });
        return "queued read-only live network inspection";
    }
}
