package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.StorageCells;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.QuantumCluster;
import appeng.menu.locator.MenuLocators;
import appeng.menu.SlotSemantics;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.helpers.InventoryAction;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWctIntegration;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessCraftingTermMenuHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real transformed hosts and menus. The stale cache reproduces the reported tick boundary. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class TianshuQuantumBridgeGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Item terminal) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        var stack = new ItemStack(terminal);
        stack.set(AEComponents.STORED_ENERGY, 1_000_000.0);
        player.getInventory().setItem(0, stack);
        return player;
    }

    private static void set(WTMenuHost host, String name, Object value) {
        try {
            var field = WTMenuHost.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(host, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object get(WTMenuHost host, String name) {
        try {
            var field = WTMenuHost.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(host);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void stale(WTMenuHost host) throws Exception {
        // AE2 destroy() clears center, while the terminal still remembers connected=true.
        var cluster = new QuantumCluster(BlockPos.ZERO, new BlockPos(2, 2, 0));
        check(cluster.getCenter() == null, "fixture must have no bridge center");
        set(host, "quantumBridge", cluster);
        set(host, "quantumStatus", ILinkStatus.ofConnected());
        set(host, "linkStatus", ILinkStatus.ofConnected());
    }

    private static void disconnected(WTMenuHost host) throws Exception {
        check(host.getActionableNode() == null, "stale quantum bridge must yield no node");
        check(!host.getLinkStatus().connected(), "stale connected status must be cleared");
        check(get(host, "quantumBridge") == null, "discard stale bridge so later discovery can reconnect");
    }

    @GameTest(template = "workstation_test", timeoutTicks = 100)
    public static void patternMenuSurvivesStaleBridge(GameTestHelper helper) throws Exception {
        var player = player(helper, "QuantumPattern", ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        var host = new TianshuWirelessPatternEncodingTermMenuHost((ItemWT) player.getInventory().getItem(0).getItem(),
                player, MenuLocators.forInventorySlot(0), (p, menu) -> {});
        var menu = new TianshuWirelessPatternEncodingTermMenu(10, player.getInventory(), host);
        player.containerMenu = menu;
        try {
            stale(host);
            // In the crash, this calls Tianshu discovery before the native menu refresh.
            menu.broadcastChanges();
            disconnected(host);
        } finally {
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    @GameTest(template = "workstation_test", timeoutTicks = 100)
    public static void craftingMenuSurvivesStaleBridge(GameTestHelper helper) throws Exception {
        var player = player(helper, "QuantumCraft", ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
        var host = new TianshuWirelessCraftingTermMenuHost((ItemWT) player.getInventory().getItem(0).getItem(),
                player, MenuLocators.forInventorySlot(0), (p, menu) -> {});
        var menu = new TianshuWirelessCraftingTermMenu(11, player.getInventory(), host);
        player.containerMenu = menu;
        var input = host.getSubInventory(CraftingTerminalPart.INV_CRAFTING);
        input.setItemDirect(0, new ItemStack(Items.OAK_PLANKS, 3));
        try {
            stale(host);
            menu.broadcastChanges();
            disconnected(host);
            check(menu.getGridNode() == null, "wireless crafting must not expose a dead grid");
            check(input.getStackInSlot(0).is(Items.OAK_PLANKS) && input.getStackInSlot(0).getCount() == 3,
                    "connection loss must preserve real crafting inputs");
        } finally {
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    @GameTest(template = "workstation_test", timeoutTicks = 100)
    public static void statusAndConnectionRefreshDiscardStaleBridge(GameTestHelper helper) throws Exception {
        var player = player(helper, "QuantumStatus", ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
        var host = new TianshuWirelessCraftingTermMenuHost((ItemWT) player.getInventory().getItem(0).getItem(),
                player, MenuLocators.forInventorySlot(0), (p, menu) -> {});
        stale(host);
        check(!host.getLinkStatus().connected(), "status queried before node must also invalidate the dead bridge");
        disconnected(host);
        stale(host);
        host.updateConnectedAccessPoint();
        check(get(host, "quantumBridge") == null, "normal connection refresh must release the cache without a getter");
        host.updateLinkStatus();
        disconnected(host);
        helper.succeed();
    }

    private record Bridge(BlockPos center, ItemStack singularity, DriveBlockEntity drive) {}

    private static Bridge bridge(GameTestHelper helper, ServerPlayer player) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(3, 2, 2));
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
            var block = x == 0 && y == 0 ? AEBlocks.QUANTUM_LINK.block() : AEBlocks.QUANTUM_RING.block();
            level.setBlockAndUpdate(center.offset(x, y, 0), block.defaultBlockState());
        }
        level.setBlockAndUpdate(center.west(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(center.west(3), AEBlocks.DRIVE.block().defaultBlockState());
        var cell = AEItems.ITEM_CELL_1K.stack();
        var storage = StorageCells.getCellInventory(cell, null);
        storage.insert(AEItemKey.of(Items.OAK_PLANKS), 256, Actionable.MODULATE, IActionSource.ofPlayer(player));
        storage.persist();
        var drive = (DriveBlockEntity) level.getBlockEntity(center.west(3));
        drive.getInternalInventory().setItemDirect(0, cell);
        var singularity = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
        singularity.set(AEComponents.ENTANGLED_SINGULARITY_ID, UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        ((QuantumBridgeBlockEntity) level.getBlockEntity(center)).getInternalInventory().setItemDirect(0, singularity.copy());
        return new Bridge(center, singularity, drive);
    }

    private static void equipQuantum(WTMenuHost host, Bridge bridge) {
        // This helper is called only with the optional implementation present.
        check(host.getUpgrades().addItems(new ItemStack(de.mari_023.ae2wtlib.AE2wtlibItems.QUANTUM_BRIDGE_CARD)).isEmpty(),
                "quantum upgrade must be accepted by the real terminal");
        host.getSubInventory(WTMenuHost.INV_SINGULARITY).setItemDirect(0, bridge.singularity().copy());
        host.updateConnectedAccessPoint();
        host.updateLinkStatus();
        check(host.getLinkStatus().connected(), "powered real quantum bridge must connect: " + host.getLinkStatus());
        check(host.getActionableNode() != null, "quantum connection must supply a grid node");
    }

    private static long stock(Bridge bridge, ServerPlayer player) {
        return bridge.drive().getMainNode().getNode().getGrid().getStorageService().getInventory()
                .extract(AEItemKey.of(Items.OAK_PLANKS), Long.MAX_VALUE,
                Actionable.SIMULATE, IActionSource.ofPlayer(player));
    }

    private static long inputs(InternalInventory input) {
        long count = 0;
        for (int i = 0; i < input.size(); i++) if (input.getStackInSlot(i).is(Items.OAK_PLANKS)) {
            count += input.getStackInSlot(i).getCount();
        }
        return count;
    }

    private static void craft(TianshuWirelessCraftingTermMenu menu, Bridge bridge, boolean online) {
        var player = (ServerPlayer) menu.getPlayer();
        var input = menu.getWirelessHost().getSubInventory(CraftingTerminalPart.INV_CRAFTING);
        for (var slot : menu.getSlots(SlotSemantics.CRAFTING_GRID)) slot.set(ItemStack.EMPTY);
        menu.getSlots(SlotSemantics.CRAFTING_GRID).get(0).set(new ItemStack(Items.OAK_PLANKS));
        menu.getSlots(SlotSemantics.CRAFTING_GRID).get(3).set(new ItemStack(Items.OAK_PLANKS));
        var result = menu.getSlots(SlotSemantics.CRAFTING_RESULT).getFirst();
        check(result.getItem().is(Items.STICK), "actual wireless menu must resolve native stick recipe");
        long beforeStock = stock(bridge, player);
        long before = beforeStock + inputs(input);
        menu.doAction(player, InventoryAction.CRAFT_STACK, result.index, 0);
        int made = menu.getCarried().is(Items.STICK) ? menu.getCarried().getCount() : 0;
        check(before - stock(bridge, player) - inputs(input) == made / 2,
                "wireless crafting conserves real input and ME stock: before=" + before + ", after="
                        + (stock(bridge, player) + inputs(input)) + ", made=" + made + ", online=" + online);
        if (online) {
            check(made == 64 && stock(bridge, player) < beforeStock,
                    "online batch must refill from real quantum ME storage: made=" + made
                            + ", stock=" + stock(bridge, player) + ", menuLink=" + menu.getLinkStatus()
                            + ", hostLink=" + menu.getWirelessHost().getLinkStatus());
        } else {
            check(stock(bridge, player) == beforeStock && made <= 4, "offline crafting must not extract cached ME stock");
        }
        menu.setCarried(ItemStack.EMPTY);
    }

    @GameTest(template = "workstation_test", timeoutTicks = 240)
    public static void realBridgeRebuildAndWirelessCrafting(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed(); // Quantum cards are an optional implementation feature.
            return;
        }
        var player = player(helper, "QuantumRebuild", ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
        var bridge = bridge(helper, player);
        var center = bridge.center();
        // No local access point or link target: all storage access must go through the quantum bridge.
        player.setPos(center.getX() + 100, center.getY(), center.getZ() + 100);
        helper.runAfterDelay(50, () -> {
            var host = new TianshuWirelessCraftingTermMenuHost((ItemWT) player.getInventory().getItem(0).getItem(),
                    player, MenuLocators.forInventorySlot(0), (p, m) -> {});
            equipQuantum(host, bridge);
            var menu = (TianshuWirelessCraftingTermMenu) TianshuWctIntegration.createMenu(12, player.getInventory(), host);
            player.containerMenu = menu;
            menu.broadcastChanges();
            craft(menu, bridge, true);
            var oldCluster = ((QuantumBridgeBlockEntity) helper.getLevel().getBlockEntity(center)).getCluster();
            helper.getLevel().setBlockAndUpdate(center.east(), Blocks.AIR.defaultBlockState());
            check(oldCluster.isDestroyed() && oldCluster.getCenter() == null, "real ring removal must destroy the cached cluster");
            menu.broadcastChanges();
            check(host.getActionableNode() == null && !host.getLinkStatus().connected(), "remote menu must go offline safely");
            check(get(host, "quantumBridge") == null, "real destruction must release cached bridge");
            craft(menu, bridge, false);
            helper.getLevel().setBlockAndUpdate(center.east(), AEBlocks.QUANTUM_RING.block().defaultBlockState());
            helper.runAfterDelay(50, () -> {
                menu.broadcastChanges();
                check(host.getLinkStatus().connected() && host.getActionableNode() != null,
                        "same open terminal must reconnect after real bridge rebuild");
                check(get(host, "quantumBridge") != oldCluster, "must discover the new cluster");
                // Native AE2 updates the host after synchronizing the menu's link status.
                helper.runAfterDelay(1, () -> {
                    try {
                        menu.broadcastChanges();
                        craft(menu, bridge, true);
                        System.out.println("TIANSHU_QUANTUM_PASS real bridge dismantle/rebuild, same-menu reconnect, online/offline crafting conservation");
                    } finally {
                        menu.removed(player);
                        player.containerMenu = player.inventoryMenu;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "workstation_test", timeoutTicks = 140)
    public static void realBridgeLossFallsBackToLocalAccessPoint(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed();
            return;
        }
        var player = player(helper, "QuantumFallback", ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        var bridge = bridge(helper, player);
        var center = bridge.center();
        // Default orientation exposes only its south (back) side to the energy cell.
        var wap = center.west(2).north();
        helper.getLevel().setBlockAndUpdate(wap, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        player.setPos(wap.getX() + .5, wap.getY() + 1, wap.getZ() + .5);
        player.getInventory().getItem(0).set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(helper.getLevel().dimension(), wap));
        helper.runAfterDelay(50, () -> {
            var host = new TianshuWirelessPatternEncodingTermMenuHost((ItemWT) player.getInventory().getItem(0).getItem(),
                    player, MenuLocators.forInventorySlot(0), (p, m) -> {});
            equipQuantum(host, bridge);
            var menu = new TianshuWirelessPatternEncodingTermMenu(13, player.getInventory(), host);
            player.containerMenu = menu;
            helper.getLevel().setBlockAndUpdate(center.east(), Blocks.AIR.defaultBlockState());
            // Let AE2 finish channel recalculation, keeping the terminal's old cache untouched.
            helper.runAfterDelay(30, () -> {
                try {
                    check(host.getLinkStatus().connected() && host.getActionableNode() != null,
                            "status-first lookup must preserve the available local access point");
                    menu.broadcastChanges();
                    check(get(host, "quantumBridge") == null, "local fallback must not retain dead quantum cache");
                    check(host.getInventory().extract(AEItemKey.of(Items.OAK_PLANKS), 1, Actionable.SIMULATE,
                            IActionSource.ofPlayer(player)) == 1, "local fallback must keep the real ME inventory available");
                } finally {
                    menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
                helper.succeed();
            });
        });
    }
}
