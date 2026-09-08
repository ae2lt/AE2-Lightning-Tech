package com.moakiee.ae2lt.debug;

import appeng.api.config.IncludeExclude;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.integration.ae2wtlib.*;
import com.moakiee.ae2lt.logic.tianshu.terminal.*;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.mixin.ae2wtlib.CraftingTerminalHandlerAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Unit;
import de.mari_023.ae2wtlib.AE2wtlibItems;
import de.mari_023.ae2wtlib.api.AE2wtlibComponents;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetMode;
import de.mari_023.ae2wtlib.wut.WTDefinitions;
import de.mari_023.ae2wtlib.wut.recipe.Common;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Optional full-WT checks are isolated from API-only class loading. */
final class TianshuWirelessGameTestChecks {
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
    static void run(GameTestHelper helper) {
        var definition = WTDefinition.of(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME);
        var item = ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get();
        var source = new ItemStack(item);
        var target = new ItemStack(AE2wtlibItems.UNIVERSAL_TERMINAL);
        target.set(WTDefinitions.CRAFTING.componentType(), Unit.INSTANCE);
        target.set(AEComponents.CRAFTING_INV, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3))));
        source.set(AEComponents.CRAFTING_INV, ItemContainerContents.EMPTY);
        source.set(AEComponents.STORED_ENERGY, 20.0);
        target.set(AEComponents.STORED_ENERGY, 10.0);
        var result = Common.mergeTerminal(target, source, definition);
        require(!result.isEmpty() && result.get(AEComponents.CRAFTING_INV).getStackInSlot(0).getCount() == 3
                && result.get(AEComponents.STORED_ENERGY) == 30, "merge must preserve real grid and sum energy");
        require(!target.has(definition.componentType()) && target.get(AEComponents.STORED_ENERGY) == 10,
                "merge planning must not mutate inputs");
        source.set(AEComponents.CRAFTING_INV, target.get(AEComponents.CRAFTING_INV));
        require(Common.mergeTerminal(target, source, definition).isEmpty(), "identical occupied crafting grids must not be deduplicated");
        source.remove(AEComponents.CRAFTING_INV);
        source.set(AE2wtlibComponents.RESTOCK, true);
        target.set(AE2wtlibComponents.RESTOCK, false);
        require(Common.mergeTerminal(target, source, definition).isEmpty(), "conflicting settings must reject loss");
        target.remove(AE2wtlibComponents.RESTOCK);
        source.remove(AE2wtlibComponents.RESTOCK);
        require(item.getUpgrades(source).addItems(new ItemStack(AE2wtlibItems.MAGNET_CARD)).isEmpty(), "Ti magnet upgrade registration");
        require(AE2wtlibItems.UNIVERSAL_TERMINAL.getUpgrades(target).addItems(new ItemStack(AE2wtlibItems.MAGNET_CARD)).isEmpty(), "WUT magnet upgrade registration");
        require(Common.mergeTerminal(target, source, definition).isEmpty(), "duplicate limited cards must reject loss");
        System.out.println("TIANSHU_WORKSTATION_PASS WUT merge keeps grids/energy, rejects duplicate inventory/cards/settings, leaves inputs unchanged");

        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("20000000-0000-0000-0000-000000000006"), "TianshuWireless"));
        player.getInventory().clearContent();
        var pos = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        var ti = new ItemStack(item);
        player.getInventory().setItem(0, ti);
        var handler = CraftingTerminalHandler.getCraftingTerminalHandler(player);
        require(handler.getCraftingTerminal() == ti, "native locator must find standalone Ti crafting terminal");
        require(!WUTHandler.hasTerminal(ti, WTDefinitions.CRAFTING), "native crafting definition must not be spoofed outside locator scope");
        var tiWut = new ItemStack(AE2wtlibItems.UNIVERSAL_TERMINAL);
        tiWut.set(definition.componentType(), Unit.INSTANCE);
        player.getInventory().setItem(0, tiWut);
        ((CraftingTerminalHandlerAccessor) handler).ae2lt$invalidateCache();
        require(handler.getCraftingTerminal() == tiWut && !WUTHandler.hasTerminal(tiWut, WTDefinitions.CRAFTING), "Ti-only WUT locator");
        tiWut.set(WTDefinitions.CRAFTING.componentType(), Unit.INSTANCE);
        require(handler.getCraftingTerminal() == tiWut, "dual-definition WUT remains one selected terminal");

        // The global locator points at slot 0, while the user explicitly edits the Ti item in slot 1.
        player.getInventory().setItem(1, ti);
        item.getUpgrades(ti).addItems(new ItemStack(AE2wtlibItems.MAGNET_CARD));
        var host = new TianshuWirelessCraftingTermMenuHost(item, player, MenuLocators.forInventorySlot(1), (p, m) -> {});
        var menu = new TianshuEnhancedWirelessCraftingMenu(9, player.getInventory(), host);
        player.containerMenu = menu;
        try {
            var helmet = menu.getSlots(AE2wtlibSlotSemantics.HELMET).getFirst();
            menu.setCarried(new ItemStack(Items.DIAMOND_HELMET));
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            require(helmet.getItem().is(Items.DIAMOND_HELMET) && menu.getCarried().isEmpty(),
                    "native WCT crafting-page equipment slot accepts the real carried helmet");
            menu.setWorkPage(TianshuWorkPage.ANVIL);
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            require(!helmet.hasItem() && menu.getCarried().is(Items.DIAMOND_HELMET),
                    "equipment remains interactive while the right-hand anvil pane is selected");
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.SETTINGS);
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            require(helmet.getItem().is(Items.DIAMOND_HELMET) && menu.getCarried().isEmpty(),
                    "hidden equipment cannot be taken from a settings subwindow");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            menu.setWorkPage(TianshuWorkPage.CRAFTING);
            menu.clicked(helmet.index, 0, ClickType.PICKUP, player);
            require(!helmet.hasItem() && menu.getCarried().is(Items.DIAMOND_HELMET),
                    "returning to crafting restores equipment interaction without duplicating the helmet");
            menu.setCarried(ItemStack.EMPTY);
            menu.setWorkPage(TianshuWorkPage.CELL);
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).getFirst().set(TianshuCellScrollFixture.stack());
            var marks = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_CONFIG);
            var upgrades = menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL_UPGRADE);
            menu.setFilter(marks.getFirst().index, new ItemStack(Items.DIAMOND));
            menu.setCarried(appeng.core.definitions.AEItems.FUZZY_CARD.stack());
            menu.clicked(upgrades.getFirst().index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty() && upgrades.getFirst().hasItem(),
                    "marks and real upgrade cards are simultaneously editable");
            menu.setCellConfigRow(1);
            require(menu.isCellMarkVisible(3) && menu.isCellMarkVisible(11) && !menu.isCellMarkVisible(0),
                    "mark wheel moves one three-slot row while keeping a 3x3 viewport");
            menu.setFilter(marks.getFirst().index, new ItemStack(Items.DIRT));
            require(marks.getFirst().getItem().is(Items.DIAMOND), "scrolled-out marks reject stale writes");
            menu.setCellUpgradeRow(99);
            require(menu.cellUpgradeRow == 5 && menu.cellConfigRow == 1 && menu.isCellUpgradeVisible(7),
                    "eight upgrade slots scroll independently and clamp at the last three");
            menu.clicked(upgrades.getFirst().index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty(), "scrolled-out real upgrade cannot be taken");
            menu.setCarried(appeng.core.definitions.AEItems.SPEED_CARD.stack());
            menu.clicked(upgrades.get(7).index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty() && upgrades.get(7).hasItem(), "last upgrade slot accepts the real card");
            menu.setCellConfigRow(999);
            require(menu.cellConfigRow == 18 && menu.isCellMarkVisible(62), "last mark row stays reachable");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.SETTINGS);
            menu.setCellConfigRow(0);
            menu.setCellUpgradeRow(0);
            menu.setCellCopyMode(appeng.api.config.CopyMode.KEEP_ON_REMOVE);
            require(menu.cellCopyMode == appeng.api.config.CopyMode.CLEAR_ON_REMOVE,
                    "subwindows reject stale keep-configuration changes");
            require(menu.cellConfigRow == 18 && menu.cellUpgradeRow == 5, "subwindows reject stale scroll actions");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            menu.setCellConfigRow(0);
            menu.setCellUpgradeRow(0);
            require(marks.getFirst().getItem().is(Items.DIAMOND) && upgrades.getFirst().hasItem() && upgrades.get(7).hasItem(),
                    "scrolling preserves both off-screen inventories");
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_CELL).getFirst().set(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
            require(menu.cellConfigRow == 0 && menu.cellUpgradeRow == 0, "changing the real cell resets both scroll offsets");
            menu.setWorkPage(TianshuWorkPage.CRAFTING);
            System.out.println("TIANSHU_WORKSTATION_PASS persistent WCT equipment, simultaneous 3x3 marks/1x3 upgrades, independent scrolling and stale-slot guards");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAGNET);
            menu.setFilter(menu.getSlots(AE2wtlibSlotSemantics.PICKUP_CONFIG).getFirst().index, new ItemStack(Items.DIAMOND));
            require(ti.has(AE2wtlibComponents.PICKUP_CONFIG) && !tiWut.has(AE2wtlibComponents.PICKUP_CONFIG), "filter editor must update exact open item");
            menu.receiveClientAction("togglepickupmode", null);
            require(ti.get(AE2wtlibComponents.PICKUP_MODE) == IncludeExclude.WHITELIST, "native filter mode update");
            var pickup = menu.getSlots(AE2wtlibSlotSemantics.PICKUP_CONFIG).getFirst();
            var insert = menu.getSlots(AE2wtlibSlotSemantics.INSERT_CONFIG).getFirst();
            require(pickup == menu.getMagnetMenu().getSlots(AE2wtlibSlotSemantics.PICKUP_CONFIG).getFirst(),
                    "terminal must use native MagnetMenu slot instances");
            menu.setFilter(insert.index, new ItemStack(Items.GOLD_INGOT));
            menu.receiveClientAction("copy_down", null);
            require(insert.getItem().is(Items.DIAMOND), "native copy down");
            menu.setFilter(insert.index, new ItemStack(Items.GOLD_INGOT));
            menu.receiveClientAction("copy_up", null);
            require(pickup.getItem().is(Items.GOLD_INGOT), "native copy up");
            menu.setFilter(pickup.index, new ItemStack(Items.DIAMOND));
            menu.receiveClientAction("switch", null);
            require(pickup.getItem().is(Items.GOLD_INGOT) && insert.getItem().is(Items.DIAMOND), "native swap filters");
            menu.receiveClientAction("toggleinsertmode", null);
            require(ti.get(AE2wtlibComponents.INSERT_MODE) == IncludeExclude.WHITELIST, "native insert mode update");
            menu.setFilter(pickup.index, new ItemStack(Items.DIAMOND));
            require(!tiWut.has(AE2wtlibComponents.PICKUP_CONFIG) && !tiWut.has(AE2wtlibComponents.INSERT_CONFIG),
                    "all native actions must leave the globally selected different terminal unchanged");
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            menu.setFilter(pickup.index, new ItemStack(Items.DIRT));
            menu.receiveClientAction("togglepickupmode", null);
            require(pickup.getItem().is(Items.DIAMOND) && ti.get(AE2wtlibComponents.PICKUP_MODE) == IncludeExclude.WHITELIST,
                    "stale native filter writes and actions rejected outside magnet page");
            var ordinaryHost = new de.mari_023.ae2wtlib.wct.WCTMenuHost(item, player, MenuLocators.forInventorySlot(1), (p, m) -> {});
            var ordinaryMenu = new de.mari_023.ae2wtlib.wct.magnet_card.MagnetMenu(10, player.getInventory(), ordinaryHost);
            require(ordinaryMenu.getMagnetHost() == handler.getMagnetHost(), "unadapted WT MagnetMenu retains native host selection");
            menu.setWorkPage(TianshuWorkPage.ANVIL);
            menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).getFirst().set(new ItemStack(Items.IRON_INGOT));
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.TRASH);
            menu.setWorkPage(TianshuWorkPage.SMITHING);
            menu.clearWorkInputs(true);
            require(menu.workPage == TianshuWorkPage.ANVIL
                    && menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).getFirst().hasItem(),
                    "WT subwindows reject stale work-page and clear-input actions");
            menu.clicked(menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).getFirst().index, 0, ClickType.PICKUP, player);
            require(menu.getCarried().isEmpty(), "work inputs inaccessible in WT subpages");
            var trash = menu.getSlots(AE2wtlibSlotSemantics.TRASH).getFirst();
            trash.set(new ItemStack(Items.DIRT, 3));
            menu.setWirelessPage(TianshuEnhancedWirelessCraftingMenu.WirelessPage.MAIN);
            require(!trash.hasItem() && menu.getSlots(Ae2ltSlotSemantics.TIANSHU_ANVIL).getFirst().hasItem(), "trash clears and work inputs survive WT page changes");
            System.out.println("TIANSHU_WORKSTATION_PASS native locator, Ti-only/dual WUT, exact-item filter settings and shared-menu WT pages");
        } finally { menu.removed(player); player.containerMenu = player.inventoryMenu; }

        player.getInventory().setItem(0, ItemStack.EMPTY);
        ((CraftingTerminalHandlerAccessor) handler).ae2lt$invalidateCache();
        MagnetHandler.saveMagnetMode(ti, MagnetMode.PICKUP_INVENTORY);
        var diamond = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        diamond.setNoPickUpDelay(); helper.getLevel().addFreshEntity(diamond);
        // Passing another terminal exercises the first-ticked-item adaptation.
        MagnetHandler.handle(player, tiWut);
        require(diamond.isRemoved(), "native magnet must use selected Ti settings and filter");
        var later = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        later.setNoPickUpDelay(); helper.getLevel().addFreshEntity(later);
        TianshuWctIntegration.tick(player);
        require(!later.isRemoved(), "native magnet executor must deduplicate the same player/tick");
        later.discard();
        System.out.println("TIANSHU_WORKSTATION_PASS native filtered pickup and per-player/tick deduplication");
    }
}
