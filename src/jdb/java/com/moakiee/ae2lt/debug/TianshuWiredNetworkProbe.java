package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Finish the initially power-only wired fixture without replacing any existing blocks or player items. */
public final class TianshuWiredNetworkProbe {
    public static String addDrive() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.serverLevel();
            var pos = new BlockPos(13, -58, -10);
            if (!level.isEmptyBlock(pos)) { System.out.println("TIANSHU_WIRED_FIXTURE drive position occupied"); return; }
            level.setBlockAndUpdate(pos, AEBlocks.DRIVE.block().defaultBlockState());
            var cell = AEItems.ITEM_CELL_1K.stack();
            var storage = StorageCells.getCellInventory(cell, null);
            for (var item : new net.minecraft.world.item.Item[]{Items.STONE, Items.OAK_PLANKS, Items.DIAMOND})
                storage.insert(AEItemKey.of(item), 64, Actionable.MODULATE, IActionSource.ofPlayer(player));
            storage.persist();
            ((DriveBlockEntity) level.getBlockEntity(pos)).getInternalInventory().setItemDirect(0, cell);
            System.out.println("TIANSHU_WIRED_FIXTURE added drive above existing test cable");
        });
        return "queued wired test drive with three item types";
    }

    public static String seedWorkInputs() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            if (!(player.containerMenu instanceof TianshuCraftingTermMenu menu)) return;
            var smith = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING);
            if (!smith.getFirst().hasItem()) smith.get(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            if (!smith.get(1).hasItem()) smith.get(1).set(new ItemStack(Items.DIAMOND_SWORD));
            if (!smith.get(2).hasItem()) smith.get(2).set(new ItemStack(Items.NETHERITE_INGOT));
            var anvil = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL);
            if (!anvil.getFirst().hasItem()) anvil.getFirst().set(new ItemStack(Items.DIAMOND_PICKAXE));
            var stone = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_STONECUTTING).getFirst();
            if (!stone.hasItem()) stone.set(new ItemStack(Items.STONE, 8));
            var cellSlot = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).getFirst();
            if (!cellSlot.hasItem()) {
                var cell = AEItems.ITEM_CELL_1K.stack();
                ((appeng.api.storage.cells.ICellWorkbenchItem) cell.getItem()).getConfigInventory(cell)
                        .createMenuWrapper().setItemDirect(0, new ItemStack(Items.DIAMOND));
                cellSlot.set(cell);
            }
            menu.broadcastChanges();
        });
        return "queued empty work-slot fixtures, preserving existing inventory";
    }
}
