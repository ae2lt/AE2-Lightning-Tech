package com.moakiee.ae2lt.debug;

import appeng.api.config.*;
import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.helpers.InventoryAction;
import appeng.core.definitions.AEItems;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.menu.ISubMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuCraftingTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Differential tests against the installed AE2 menu, plus native non-crafting callbacks. Dev source set only. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class TianshuWorkCraftingGameTests {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void passed(String message) { System.out.println("TIANSHU_CRAFTING_ALIGNMENT_PASS " + message); }
    private static ServerPlayer player(ServerLevel level, String name) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        player.experienceLevel = 100;
        return player;
    }
    private static long count(Player player, Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToLong(ItemStack::getCount).sum();
    }
    private static TianshuCraftingTermMenu menu(ServerPlayer player, Host host) {
        var menu = new TianshuCraftingTermMenu(9, player.getInventory(), host);
        player.containerMenu = menu;
        menu.broadcastChanges();
        return menu;
    }
    private static Slot stone(TianshuCraftingTermMenu menu, int index) {
        return menu.getSlots(Ae2ltSlotSemantics.TIANSHU_STONECUTTING).get(index);
    }
    private static void prepareStone(TianshuCraftingTermMenu menu, int count) {
        menu.setWorkPage(TianshuWorkPage.STONECUTTING);
        stone(menu, 0).set(new ItemStack(Items.STONE, count));
        for (int i = 0; i < menu.getStonecutter().getNumRecipes(); i++) {
            if (menu.getStonecutter().getRecipes().get(i).id().toString().equals("minecraft:stone_slab_from_stone_stonecutting")) {
                menu.selectStoneRecipe(i);
                return;
            }
        }
        throw new AssertionError("native slab recipe missing");
    }
    private static void act(TianshuCraftingTermMenu menu, InventoryAction action) {
        menu.doAction((ServerPlayer) menu.getPlayer(), action, stone(menu, 1).index, 0);
    }

    @GameTest(template = "workstation_test", timeoutTicks = 100)
    public static void ae2ClickParity(GameTestHelper helper) {
        for (var action : List.of(InventoryAction.CRAFT_ITEM, InventoryAction.CRAFT_STACK,
                InventoryAction.CRAFT_SHIFT, InventoryAction.CRAFT_ALL)) {
            compare(helper, action, 2048, 0, false, true, true);
        }
        compare(helper, InventoryAction.CRAFT_STACK, 2048, 61, false, true, true);
        compare(helper, InventoryAction.CRAFT_STACK, 2048, -1, false, true, true);
        compare(helper, InventoryAction.CRAFT_SHIFT, 2048, 0, true, true, true);
        compare(helper, InventoryAction.CRAFT_ALL, 3, 0, false, true, true);
        compare(helper, InventoryAction.CRAFT_STACK, 2048, 0, false, false, true);
        compare(helper, InventoryAction.CRAFT_STACK, 2048, 0, false, true, false);
        compare(helper, InventoryAction.CRAFT_STACK, 2048, 0, false, true, true, true);
        passed("all four actions match actual AE2 output, inventory order, ME consumption and retained input; cursor/full/shortage/power/link boundaries");
        helper.succeed();
    }

    private static void compare(GameTestHelper helper, InventoryAction action, int available, int carried,
            boolean full, boolean powered, boolean connected) {
        compare(helper, action, available, carried, full, powered, connected, false);
    }

    private static void compare(GameTestHelper helper, InventoryAction action, int available, int carried,
            boolean full, boolean powered, boolean connected, boolean filtered) {
        var level = helper.getLevel();
        var a = player(level, "NativeParity");
        var b = player(level, "TianshuParity");
        var nativeHost = new Host(level, helper.absolutePos(BlockPos.ZERO));
        var workHost = new Host(level, helper.absolutePos(new BlockPos(1, 0, 0)));
        nativeHost.powered = workHost.powered = powered;
        nativeHost.connected = workHost.connected = connected;
        if (filtered) {
            var view = AEItems.VIEW_CELL.stack();
            ((ICellWorkbenchItem) view.getItem()).getConfigInventory(view).createMenuWrapper().setItemDirect(0, new ItemStack(Items.DIAMOND));
            nativeHost.view.setItemDirect(0, view.copy()); workHost.view.setItemDirect(0, view.copy());
        }
        nativeHost.storage.put(Items.STONE, available);
        nativeHost.storage.put(Items.PAPER, available);
        workHost.storage.put(Items.STONE, available);
        nativeHost.grid.setItemDirect(0, new ItemStack(Items.STONE));
        nativeHost.grid.setItemDirect(1, new ItemStack(Items.PAPER));
        var nativeMenu = new CraftingTermMenu(8, a.getInventory(), nativeHost);
        a.containerMenu = nativeMenu;
        nativeMenu.broadcastChanges();
        var workMenu = menu(b, workHost);
        try {
            prepareStone(workMenu, 1);
            var nativeResult = nativeMenu.getSlots(SlotSemantics.CRAFTING_RESULT).getFirst();
            check(nativeMenu.getCurrentRecipe().id().toString().equals("ae2lt:test_workstation_clicks"), "expected real AE2 control recipe");
            check(ItemStack.matches(nativeResult.getItem(), stone(workMenu, 1).getItem()), "matching preview before comparison");
            if (carried != 0) {
                var stack = new ItemStack(carried < 0 ? Items.DIRT : Items.STONE_SLAB, Math.abs(carried));
                nativeMenu.setCarried(stack.copy()); workMenu.setCarried(stack.copy());
            }
            if (full) for (int i = 0; i < 36; i++) {
                a.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                b.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            nativeMenu.doAction(a, action, nativeResult.index, 0);
            act(workMenu, action);
            String label = action + " / available=" + available + " / cursor=" + carried
                    + " / full=" + full + " / powered=" + powered + " / connected=" + connected + " / filtered=" + filtered;
            check(ItemStack.matches(nativeMenu.getCarried(), workMenu.getCarried()), "carried parity: " + label);
            for (int i = 0; i < 36; i++) check(ItemStack.matches(a.getInventory().getItem(i), b.getInventory().getItem(i)),
                    "destination slot " + i + " parity: " + label);
            check(nativeHost.storage.amount(Items.STONE) == workHost.storage.amount(Items.STONE), "ME consumption parity: " + label
                    + " / native=" + nativeHost.storage.amount(Items.STONE) + " / work=" + workHost.storage.amount(Items.STONE));
            check(ItemStack.matches(nativeHost.grid.getStackInSlot(0), stone(workMenu, 0).getItem()), "retained input parity: " + label);
            check(ItemStack.matches(nativeResult.getItem(), stone(workMenu, 1).getItem()), "next preview parity: " + label);
        } finally {
            nativeMenu.removed(a); workMenu.removed(b);
            a.containerMenu = a.inventoryMenu; b.containerMenu = b.inventoryMenu;
            level.removeBlockEntity(nativeHost.getBlockPos()); level.removeBlockEntity(workHost.getBlockPos());
        }
    }

    @GameTest(template = "workstation_test", timeoutTicks = 100)
    public static void workInputAndResultBoundaries(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(level, "TianshuBoundaries");
        var host = new Host(level, helper.absolutePos(BlockPos.ZERO));
        var menu = menu(player, host);
        try {
            host.storage.put(Items.STONE, 100);
            prepareStone(menu, 1);
            // Use the actual vanilla packet handler entry too, not only InventoryAction.
            menu.clicked(stone(menu, 1).index, 1, ClickType.PICKUP, player);
            check(menu.getCarried().getCount() == 64 && host.storage.amount(Items.STONE) == 68
                    && stone(menu, 0).getItem().getCount() == 1, "right-click crafts a full stack and replenishes its template");
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(stone(menu, 1).index, 0, ClickType.QUICK_MOVE, player);
            check(count(player, Items.STONE_SLAB) == 64 && host.storage.amount(Items.STONE) == 36, "raw Shift packet executes only one bounded stack");
            menu.quickMoveStack(player, stone(menu, 0).index);
            check(!stone(menu, 0).hasItem() && host.storage.amount(Items.STONE) == 37 && count(player, Items.STONE) == 0,
                    "Shift input stores in ME, not in player inventory");
            prepareStone(menu, 4);
            menu.doAction(player, InventoryAction.MOVE_REGION, stone(menu, 0).index, 0);
            check(!stone(menu, 0).hasItem() && host.storage.amount(Items.STONE) == 41 && count(player, Items.STONE_SLAB) == 64,
                    "space on input clears its input region without producing the preview");
            prepareStone(menu, 4);
            host.storage.insertLimit = 2;
            menu.clearWorkInputs(false);
            check(stone(menu, 0).getItem().getCount() == 2 && host.storage.amount(Items.STONE) == 43, "partial ME insertion retains exact remainder");
            host.storage.insertLimit = 0;
            menu.quickMoveStack(player, stone(menu, 0).index);
            check(stone(menu, 0).getItem().getCount() == 2, "full ME leaves work input in place");
            player.getInventory().clearContent();
            menu.clearWorkInputs(true);
            check(!stone(menu, 0).hasItem() && player.getInventory().getItem(8).getCount() == 2, "clear-to-player uses native hotbar ordering");
            prepareStone(menu, 1);
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            player.getInventory().setItem(8, new ItemStack(Items.STONE_SLAB, 63));
            act(menu, InventoryAction.CRAFT_SHIFT);
            check(stone(menu, 0).getItem().getCount() == 1 && player.getInventory().getItem(8).getCount() == 63,
                    "one free place cannot consume a two-item batch");
            menu.clearWorkInputs(true);
            check(stone(menu, 0).getItem().getCount() == 1, "full player inventory cannot erase cleared inputs");
            var smith = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING);
            smith.getFirst().set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            menu.setWorkPage(TianshuWorkPage.SMITHING);
            menu.doAction(player, InventoryAction.CRAFT_ALL, stone(menu, 1).index, 0);
            menu.doAction(player, InventoryAction.MOVE_REGION, stone(menu, 0).index, 0);
            check(stone(menu, 0).getItem().getCount() == 1 && smith.getFirst().hasItem(), "stale hidden work page packets rejected");
            menu.setWorkPage(TianshuWorkPage.STONECUTTING);
            var blocked = new ItemStack(Items.DIAMOND);
            stone(menu, 0).set(blocked);
            player.getInventory().setItem(8, new ItemStack(Items.STONE, 64));
            menu.fillWorkRecipe("minecraft:stone_slab_from_stone_stonecutting");
            check(stone(menu, 0).getItem().is(Items.DIAMOND) && player.getInventory().getItem(8).getCount() == 64,
                    "recipe fill preserves blocked input when both destinations are full");
            stone(menu, 0).set(ItemStack.EMPTY);
            menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).get(8).index);
            check(!stone(menu, 0).hasItem() && player.getInventory().getItem(8).getCount() == 64,
                    "failed Shift-to-ME does not silently populate a work input");
            passed("real click and Shift packet handling, input-to-ME, region clear, partial/full destinations, stale pages and blocked recipe transfer");
        } finally {
            menu.removed(player); player.containerMenu = player.inventoryMenu; level.removeBlockEntity(host.getBlockPos());
        }
        helper.succeed();
    }

    @GameTest(template = "workstation_test", timeoutTicks = 100)
    public static void smithingAndAnvilBatchCallbacks(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(level, "TianshuNativeBatch");
        var host = new Host(level, helper.absolutePos(BlockPos.ZERO));
        var menu = menu(player, host);
        int[] repairs = {0};
        boolean[] changeResult = {false};
        Consumer<AnvilUpdateEvent> update = event -> {
            if (event.getPlayer() == player && event.getLeft().is(Items.IRON_INGOT) && event.getRight().is(Items.GOLD_INGOT)) {
                event.setOutput(new ItemStack(changeResult[0] && repairs[0] > 0 ? Items.EMERALD : Items.DIAMOND));
                event.setCost(7); event.setMaterialCost(2);
            }
        };
        Consumer<AnvilRepairEvent> repair = event -> { if (event.getEntity() == player) repairs[0]++; };
        NeoForge.EVENT_BUS.addListener(update); NeoForge.EVENT_BUS.addListener(repair);
        try {
            menu.setWorkPage(TianshuWorkPage.SMITHING);
            var smith = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_SMITHING);
            smith.get(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            smith.get(1).set(new ItemStack(Items.DIAMOND_SWORD));
            smith.get(2).set(new ItemStack(Items.NETHERITE_INGOT));
            for (var item : List.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.DIAMOND_SWORD, Items.NETHERITE_INGOT)) host.storage.put(item, 64);
            menu.doAction(player, InventoryAction.CRAFT_ALL, smith.get(3).index, 0);
            check(count(player, Items.NETHERITE_SWORD) == 36 && host.storage.amount(Items.DIAMOND_SWORD) == 28
                    && smith.get(0).hasItem() && smith.get(1).hasItem() && smith.get(2).hasItem(), "Space smithing respects native unstackable capacity and replenishes three inputs");
            player.getInventory().clearContent();
            var special = new ItemStack(Items.DIAMOND_SWORD);
            special.set(DataComponents.CUSTOM_NAME, Component.literal("different components"));
            host.storage.values.clear();
            host.storage.values.add(AEItemKey.of(special), 5);
            menu.doAction(player, InventoryAction.CRAFT_STACK, smith.get(3).index, 0);
            check(menu.getCarried().is(Items.NETHERITE_SWORD) && !smith.get(1).hasItem()
                    && host.storage.values.get(AEItemKey.of(special)) == 5, "replenishment cannot substitute different equipment components");
            menu.setCarried(ItemStack.EMPTY);

            menu.setWorkPage(TianshuWorkPage.ANVIL);
            var anvil = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL);
            host.storage.values.clear(); host.storage.put(Items.IRON_INGOT, 10); host.storage.put(Items.GOLD_INGOT, 20);
            anvil.get(0).set(new ItemStack(Items.IRON_INGOT)); anvil.get(1).set(new ItemStack(Items.GOLD_INGOT, 2));
            player.experienceLevel = 21;
            menu.doAction(player, InventoryAction.CRAFT_ALL, anvil.get(2).index, 0);
            check(count(player, Items.DIAMOND) == 3 && repairs[0] == 3 && player.experienceLevel == 0,
                    "native anvil callback and XP charged once per craft, stopping when levels run out");
            check(host.storage.amount(Items.IRON_INGOT) == 7 && host.storage.amount(Items.GOLD_INGOT) == 14
                    && anvil.get(0).getItem().getCount() == 1 && anvil.get(1).getItem().getCount() == 2,
                    "variable-count native anvil consumption replenishes two materials without duplication");
            player.getInventory().clearContent(); player.experienceLevel = 70; repairs[0] = 0; changeResult[0] = true;
            menu.doAction(player, InventoryAction.CRAFT_ALL, anvil.get(2).index, 0);
            check(count(player, Items.DIAMOND) == 1 && count(player, Items.EMERALD) == 0 && repairs[0] == 1
                    && player.experienceLevel == 63 && anvil.get(2).getItem().is(Items.EMERALD), "batch stops if a mod changes the next output");
            menu.setWorkPage(TianshuWorkPage.STONECUTTING);
            var outputBefore = anvil.get(2).getItem().copy();
            menu.setAnvilName("hidden name");
            check(ItemStack.matches(outputBefore, anvil.get(2).getItem()), "hidden anvil cannot be renamed");
            passed("native smithing batch and component identity; anvil multi-material refill, per-craft callback/XP, level stop and changed-output stop");
        } finally {
            NeoForge.EVENT_BUS.unregister(update); NeoForge.EVENT_BUS.unregister(repair);
            menu.removed(player); player.containerMenu = player.inventoryMenu; level.removeBlockEntity(host.getBlockPos());
        }
        helper.succeed();
    }

    private static final class Store implements MEStorage {
        final KeyCounter values = new KeyCounter();
        long insertLimit = Long.MAX_VALUE;
        void put(Item item, long count) { values.add(AEItemKey.of(item), count); }
        long amount(Item item) { return values.get(AEItemKey.of(item)); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, insertLimit);
            if (mode == Actionable.MODULATE) values.add(key, accepted);
            return accepted;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, values.get(key));
            if (mode == Actionable.MODULATE) values.add(key, -extracted);
            return extracted;
        }
        @Override public void getAvailableStacks(KeyCounter out) { out.addAll(values); }
        @Override public Component getDescription() { return Component.literal("controlled ME storage for actual menu differential tests"); }
    }
    private static final class Host extends BlockEntity implements TianshuCraftingTerminalHost, IEnergySource, IViewCellStorage {
        final InternalInventory grid = new AppEngInternalInventory(null, 9);
        final InternalInventory view = new AppEngInternalInventory(null, 1);
        final Store storage = new Store();
        boolean powered = true, connected = true;
        private final IConfigManager config = IConfigManager.builder(() -> {})
                .registerSetting(Settings.SORT_BY, SortOrder.NAME).registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING).build();
        Host(ServerLevel level, BlockPos pos) {
            super(BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState()); setLevel(level); level.setBlockEntity(this);
        }
        @Override public InternalInventory getSubInventory(ResourceLocation id) { return grid; }
        @Override public InternalInventory getViewCellStorage() { return view; }
        @Override public IGridNode getActionableNode() { return null; }
        @Override public MEStorage getInventory() { return storage; }
        @Override public ILinkStatus getLinkStatus() { return connected ? ILinkStatus.ofConnected() : ILinkStatus.ofDisconnected(); }
        @Override public IConfigManager getConfigManager() { return config; }
        @Override public void returnToMainMenu(Player player, ISubMenu menu) {}
        @Override public ItemStack getMainMenuIcon() { return new ItemStack(Items.CRAFTING_TABLE); }
        @Override public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) { return powered ? amount : 0; }
    }
}
