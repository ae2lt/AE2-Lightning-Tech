package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessIngredientSource;
import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiSupply;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyPacket;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import mezz.jei.common.transfer.BasicRecipeTransferHandlerServer;
import mezz.jei.common.transfer.TransferOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real storage, native wireless hosts and JEI server transfer; excluded from the mod jar. */
@GameTestHolder("ae2lt_jei_supply")
@PrefixGameTestTemplate(false)
public final class JeiWirelessSupplyGameTests {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static WirelessJeiSupplyPacket packet(ServerPlayer player, int id, boolean take, ItemStack... stacks) {
        return new WirelessJeiSupplyPacket(player.containerMenu.containerId, id, take, List.of(stacks));
    }
    private static int count(ServerPlayer p) {
        return p.getInventory().items.stream().filter(s -> s.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void supplyAndNativeTransfer(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "JeiSupplyQA"));
        player.getInventory().clearContent();
        var base = helper.absolutePos(new BlockPos(2, 2, 2));
        var wap = base.north();
        var table = base.south();
        level.setBlockAndUpdate(base, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(base.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(wap, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        var cell = AEItems.ITEM_CELL_1K.stack();
        var cellStorage = StorageCells.getCellInventory(cell, null);
        cellStorage.insert(AEItemKey.of(Items.OAK_PLANKS), 256, Actionable.MODULATE, IActionSource.ofPlayer(player));
        cellStorage.persist();
        ((DriveBlockEntity) level.getBlockEntity(base.east())).getInternalInventory().setItemDirect(0, cell);
        player.setPos(table.getX() + .5, table.getY() + 1, table.getZ() + .5);
        var terminal = new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
        terminal.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), wap));
        terminal.set(AEComponents.STORED_ENERGY, 1000000.0);
        // An unlinked earlier terminal must not hide the connected one.
        player.getInventory().setItem(0, new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get()));
        player.getInventory().setItem(1, terminal);
        var menu = new CraftingMenu(17, player.getInventory(), ContainerLevelAccess.create(level, table));
        player.containerMenu = menu;
        helper.runAfterDelay(50, () -> {
            var locators = TianshuWirelessIngredientSource.locate(player);
            check(locators.size() == 2, "find both carried terminals");
            var host = TianshuWirelessIngredientSource.open(player, locators.get(1));
            check(host != null, "powered real ME network must connect");
            var storage = host.getInventory();
            var source = IActionSource.ofPlayer(player);
            var key = AEItemKey.of(Items.OAK_PLANKS);
            var materials = new ItemStack(Items.OAK_PLANKS, 4);
            var offer = WirelessJeiSupply.handle(player, packet(player, 1, false, materials));
            check(offer.status() == 1 && offer.items().getFirst().getCount() == 4, "offer from second connected terminal");
            check(storage.extract(key, 999, Actionable.SIMULATE, source) == 256 && count(player) == 0,
                    "preview must not extract");
            double energy = terminal.getOrDefault(AEComponents.STORED_ENERGY, 0.0);
            var take = packet(player, 1, true, materials);
            check(WirelessJeiSupply.handle(player, take).status() == 2, "take succeeds");
            check(count(player) == 4 && storage.extract(key, 999, Actionable.SIMULATE, source) == 252, "take conserves items");
            check(terminal.getOrDefault(AEComponents.STORED_ENERGY, 0.0) < energy, "terminal energy must not be restored by inventory commit");
            check(WirelessJeiSupply.handle(player, take).status() == 0 && count(player) == 4, "replay must not extract twice");
            var from = menu.slots.stream().filter(s -> s.getItem().is(Items.OAK_PLANKS)).findFirst().orElseThrow();
            BasicRecipeTransferHandlerServer.setItems(player, List.of(new TransferOperation(from.index, 1),
                    new TransferOperation(from.index, 2), new TransferOperation(from.index, 4), new TransferOperation(from.index, 5)),
                    menu.slots.subList(1, 10), menu.slots.subList(10, 46), false, true);
            check(count(player) == 0 && menu.getSlot(0).getItem().is(Items.CRAFTING_TABLE), "native JEI transfer creates actual crafting preview");
            for (int slot : new int[]{1, 2, 4, 5}) check(menu.getSlot(slot).getItem().getCount() == 1, "one recipe per click");

            WirelessJeiSupply.handle(player, packet(player, 2, false, materials));
            check(WirelessJeiSupply.handle(player, packet(player, 2, true, new ItemStack(Items.OAK_PLANKS, 5))).status() == 0,
                    "reject counts exceeding offer");
            WirelessJeiSupply.handle(player, packet(player, 3, false, materials));
            var forged = materials.copy();
            forged.set(DataComponents.CUSTOM_NAME, Component.literal("not in storage"));
            check(WirelessJeiSupply.handle(player, packet(player, 3, true, forged)).status() == 0, "reject forged components");
            WirelessJeiSupply.handle(player, packet(player, 4, false, materials));
            for (int i = 2; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            check(WirelessJeiSupply.handle(player, packet(player, 4, true, materials)).status() == 0, "full inventory rejects whole operation");
            for (int i = 2; i < 36; i++) player.getInventory().setItem(i, ItemStack.EMPTY);
            WirelessJeiSupply.handle(player, packet(player, 5, false, materials));
            terminal.set(AEComponents.STORED_ENERGY, 0.0);
            check(WirelessJeiSupply.handle(player, packet(player, 5, true, materials)).status() == 0, "power rechecked at take");
            terminal.set(AEComponents.STORED_ENERGY, energy);
            WirelessJeiSupply.handle(player, packet(player, 6, false, materials));
            terminal.remove(AEComponents.WIRELESS_LINK_TARGET);
            check(WirelessJeiSupply.handle(player, packet(player, 6, true, materials)).status() == 0, "link rechecked at take");
            terminal.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), wap));
            WirelessJeiSupply.handle(player, packet(player, 7, false, materials));
            player.containerMenu = new CraftingMenu(17, player.getInventory(), ContainerLevelAccess.create(level, table));
            check(WirelessJeiSupply.handle(player, packet(player, 7, true, materials)).status() == 0, "menu identity rechecked despite reused id");
            player.containerMenu = menu;
            var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow().getEquippedCurios();
            check(curios.getSlots() > 0, "Curios fixture slots available");
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setItem(1, ItemStack.EMPTY);
            curios.setStackInSlot(0, terminal);
            check(TianshuWirelessIngredientSource.locate(player).size() == 1, "Curios terminal without backpack terminal");
            check(WirelessJeiSupply.handle(player, packet(player, 9, false, materials)).status() == 1
                    && WirelessJeiSupply.handle(player, packet(player, 9, true, materials)).status() == 2, "Curios refill succeeds");
            curios.setStackInSlot(0, ItemStack.EMPTY);
            player.getInventory().clearContent();
            player.getInventory().setItem(1, terminal);
            var pattern = new ItemStack(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
            pattern.set(AEComponents.STORED_ENERGY, energy);
            pattern.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), wap));
            player.getInventory().setItem(1, pattern);
            check(WirelessJeiSupply.handle(player, packet(player, 10, false, materials)).status() == 1
                    && WirelessJeiSupply.handle(player, packet(player, 10, true, materials)).status() == 2, "wireless pattern terminal refill");
            player.getInventory().clearContent();
            player.getInventory().setItem(1, terminal);
            if (net.neoforged.fml.ModList.get().isLoaded("ae2wtlib")) Universal.checkSupply(player, terminal, materials);
            WirelessJeiSupply.handle(player, packet(player, 8, false, materials));
            storage.extract(key, 9999, Actionable.MODULATE, source);
            check(WirelessJeiSupply.handle(player, packet(player, 8, true, materials)).status() == 0 && count(player) == 0,
                    "stock race never creates items");
            var before = WirelessJeiInventoryPlan.copy(player.getInventory().items);
            var simulation = WirelessJeiInventoryPlan.insert(player.getInventory().items, List.of(materials));
            check(simulation != null && count(player) == 0 && java.util.stream.IntStream.range(0, 36)
                    .allMatch(i -> ItemStack.matches(before.get(i), player.getInventory().getItem(i))), "simulation stays detached");
            System.out.println("JEI_WIRELESS_SUPPLY_PASS real ME extraction, native JEI crafting, energy, replay, components, space, power, link, menu, stock, Curios, both terminals, simulation");
            player.containerMenu = player.inventoryMenu;
            helper.succeed();
        });
    }

    private static final class Universal {
        private static void checkSupply(ServerPlayer player, ItemStack terminal, ItemStack materials) {
            var universal = new ItemStack(de.mari_023.ae2wtlib.AE2wtlibItems.UNIVERSAL_TERMINAL);
            var definition = de.mari_023.ae2wtlib.api.registration.WTDefinition.of(
                    com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME);
            universal.set(definition.componentType(), com.mojang.datafixers.util.Unit.INSTANCE);
            universal.set(AEComponents.STORED_ENERGY, terminal.get(AEComponents.STORED_ENERGY));
            universal.set(AEComponents.WIRELESS_LINK_TARGET, terminal.get(AEComponents.WIRELESS_LINK_TARGET));
            player.getInventory().setItem(1, universal);
            check(WirelessJeiSupply.handle(player, packet(player, 11, false, materials)).status() == 1
                    && WirelessJeiSupply.handle(player, packet(player, 11, true, materials)).status() == 2, "WUT Tianshu module refill");
            player.getInventory().clearContent();
            player.getInventory().setItem(1, terminal);
            System.out.println("JEI_WIRELESS_SUPPLY_PASS universal wireless terminal module");
        }
    }
}
