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
import com.moakiee.ae2lt.client.JeiWirelessSupplyClient;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessIngredientSource;
import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.concurrent.CompletableFuture;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.Internal;
import mezz.jei.common.transfer.RecipeTransferUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in, disposable-world end-to-end check through actual JEI handlers and both packet directions. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class JeiWirelessSupplyClientProbe {
    private static int phase, delay, timeout;
    private static CompletableFuture<Void> serverWork;
    private static BlockPos base;
    private static ItemStack terminal;
    private static IRecipeLayoutDrawable<?> layout;
    private static volatile String report = "idle";
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static ServerPlayer player() { return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst(); }
    private static void server(Runnable action, int next, int wait) {
        serverWork = CompletableFuture.runAsync(action, Minecraft.getInstance().getSingleplayerServer());
        phase = next; delay = wait; timeout = 0;
    }
    private static long stored(Item item) {
        var p = player();
        var host = TianshuWirelessIngredientSource.open(p, MenuLocators.forInventorySlot(0));
        check(host != null, "wireless host connected");
        return host.getInventory().extract(AEItemKey.of(item), 9999, Actionable.SIMULATE, IActionSource.ofPlayer(p));
    }
    private static void clearPlayer() {
        var p = player();
        p.closeContainer();
        p.getInventory().clearContent();
        p.getInventory().setItem(0, terminal);
        p.inventoryMenu.broadcastChanges();
    }
    private static void craftingMenu() {
        clearPlayer();
        player().openMenu(new SimpleMenuProvider((id, inv, p) -> new CraftingMenu(id, inv,
                ContainerLevelAccess.create(p.level(), base.south())), Component.literal("JEI wireless test")));
    }
    private static void fixture() {
        var p = player();
        var level = p.serverLevel();
        base = new BlockPos(180, 120, 180);
        p.teleportTo(level, 180.5, 122, 181.5, 0, 0);
        level.setBlockAndUpdate(base, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(base.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(base.north(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        level.setBlockAndUpdate(base.south().west().below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.south(), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(base.south().east(), Blocks.FURNACE.defaultBlockState());
        ((net.minecraft.world.Container) level.getBlockEntity(base.south().east())).clearContent();
        level.setBlockAndUpdate(base.south().west(), Blocks.ANVIL.defaultBlockState());
        var cell = AEItems.ITEM_CELL_1K.stack();
        var storage = StorageCells.getCellInventory(cell, null);
        for (var entry : java.util.Map.of(Items.OAK_PLANKS, 256, Items.BIRCH_PLANKS, 128, Items.COBBLESTONE, 32,
                Items.DIAMOND, 32, Items.IRON_INGOT, 32, Items.REDSTONE, 32).entrySet())
            storage.insert(AEItemKey.of(entry.getKey()), entry.getValue(), Actionable.MODULATE, IActionSource.ofPlayer(p));
        var damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(1000);
        storage.insert(AEItemKey.of(damaged), 1, Actionable.MODULATE, IActionSource.ofPlayer(p));
        storage.persist();
        ((DriveBlockEntity) level.getBlockEntity(base.east())).getInternalInventory().setItemDirect(0, cell);
        terminal = new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
        terminal.set(AEComponents.STORED_ENERGY, 1000000.0);
        terminal.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), base.north()));
        craftingMenu();
    }
    private static <R> void recipe(mezz.jei.api.recipe.RecipeType<R> type, java.util.function.Predicate<R> predicate) {
        var runtime = Internal.getJeiRuntime();
        var manager = runtime.getRecipeManager();
        var recipe = manager.createRecipeLookup(type).get().filter(predicate).findFirst().orElseThrow();
        layout = manager.createRecipeLayoutDrawable(manager.getRecipeCategory(type), recipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        JeiWirelessSupplyClient.clear();
    }
    private static boolean preview() {
        var mc = Minecraft.getInstance();
        var before = WirelessJeiInventoryPlan.copy(mc.player.getInventory().items);
        var error = RecipeTransferUtil.getTransferRecipeError(Internal.getJeiRuntime().getRecipeTransferManager(),
                mc.player.containerMenu, layout, mc.player);
        check(java.util.stream.IntStream.range(0, 36).allMatch(i -> ItemStack.matches(before.get(i), mc.player.getInventory().getItem(i))),
                "JEI client preview must restore every stack");
        return error.isEmpty() || error.get().getType().allowsTransfer;
    }
    private static void fill(boolean maximum) {
        var mc = Minecraft.getInstance();
        var manager = Internal.getJeiRuntime().getRecipeTransferManager();
        boolean transferred = RecipeTransferUtil.transferRecipe(manager, mc.player.containerMenu, layout, mc.player, maximum);
        check(!transferred, "wireless refill waits for server before normal JEI transfer");
        check(!RecipeTransferUtil.transferRecipe(manager, mc.player.containerMenu, layout, mc.player, maximum), "rapid second click remains pending");
        delay = 30; timeout = 0; phase++;
    }
    private static void passed(String message) {
        report = message;
        System.out.println("JEI_WIRELESS_CLIENT_PASS " + message);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.jeiSupplyClientProbe") || phase < 0) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getSingleplayerServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            if (serverWork != null) {
                if (!serverWork.isDone()) return;
                serverWork.join(); serverWork = null;
            }
            if (delay-- > 0) return;
            check(++timeout < 300, "client phase timeout " + phase);
            switch (phase) {
                case 0 -> {
                    mc.options.pauseOnLostFocus = false;
                    mc.options.guiScale().set(2);
                    mc.getWindow().setWindowed(1280, 900); mc.resizeDisplay();
                    server(JeiWirelessSupplyClientProbe::fixture, 1, 80);
                }
                case 1 -> {
                    check(mc.player.containerMenu instanceof CraftingMenu, "real workbench menu synchronized");
                    recipe(RecipeTypes.CRAFTING, r -> r.id().toString().equals("minecraft:crafting_table"));
                    AE2LTClientConfig.setJeiWirelessSupply(false);
                    check(!preview(), "disabled setting keeps native missing-ingredient behavior");
                    AE2LTClientConfig.setJeiWirelessSupply(true);
                    phase = 2; timeout = 0;
                }
                case 2 -> { if (preview()) fill(false); }
                case 3 -> server(() -> {
                    var menu = player().containerMenu;
                    check(menu.getSlot(0).getItem().is(Items.CRAFTING_TABLE), "normal JEI result");
                    check(menu.slots.subList(1, 10).stream().mapToInt(s -> s.getItem().getCount()).sum() == 4, "normal fills exactly four planks");
                    check(stored(Items.OAK_PLANKS) + stored(Items.BIRCH_PLANKS) == 380, "rapid double click extracts only four");
                    passed("setting off, detached preview, tagged crafting input, native JEI packets, duplicate click");
                    craftingMenu();
                }, 4, 20);
                case 4 -> { if (preview()) fill(true); }
                case 5 -> server(() -> {
                    var menu = player().containerMenu;
                    int grid = menu.slots.subList(1, 10).stream().mapToInt(s -> s.getItem().getCount()).sum();
                    check(grid >= 128 && menu.getSlot(0).getItem().is(Items.CRAFTING_TABLE), "Shift fills native maximum batches, got " + grid);
                    check(stored(Items.OAK_PLANKS) + stored(Items.BIRCH_PLANKS) + grid == 380, "Shift conserves ME plus grid totals");
                    passed("Shift maximum native crafting and item conservation");
                    clearPlayer();
                    player().openMenu((net.minecraft.world.MenuProvider) player().level().getBlockEntity(base.south().east()));
                }, 6, 20);
                case 6 -> {
                    recipe(RecipeTypes.SMELTING, r -> r.id().toString().equals("minecraft:stone"));
                    phase = 7; timeout = 0;
                }
                case 7 -> { if (preview()) fill(false); }
                case 8 -> server(() -> {
                    check(player().containerMenu.getSlot(0).getItem().is(Items.COBBLESTONE), "furnace input");
                    check(stored(Items.COBBLESTONE) == 31, "furnace extracts exactly one");
                    passed("native furnace JEI handler");
                    clearPlayer(); player().experienceLevel = 100;
                    player().openMenu(new SimpleMenuProvider((id, inv, p) -> new AnvilMenu(id, inv,
                            ContainerLevelAccess.create(p.level(), base.south().west())), Component.literal("Anvil JEI test")));
                }, 9, 20);
                case 9 -> {
                    recipe(RecipeTypes.ANVIL, r -> r.getLeftInputs().stream().anyMatch(s -> s.is(Items.DIAMOND_PICKAXE))
                            && r.getRightInputs().stream().anyMatch(s -> s.is(Items.DIAMOND)));
                    phase = 10; timeout = 0;
                }
                case 10 -> { if (preview()) fill(false); }
                case 11 -> server(() -> {
                    var menu = player().containerMenu;
                    check(menu.getSlot(0).getItem().is(Items.DIAMOND_PICKAXE) && menu.getSlot(1).getItem().is(Items.DIAMOND), "native anvil JEI handler inputs");
                    check(menu.getSlot(0).getItem().getDamageValue() == 1000, "original damaged item components preserved");
                    check(stored(Items.DIAMOND) == 31, "native anvil exact material deficit");
                    passed("native anvil handler, exact damaged components");
                    craftingMenu();
                    for (int i = 4; i < 36; i++) player().getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                    player().containerMenu.broadcastChanges();
                }, 12, 20);
                case 12 -> {
                    recipe(RecipeTypes.CRAFTING, r -> r.id().toString().equals("minecraft:piston"));
                    phase = 13; timeout = 0;
                }
                case 13 -> { if (preview()) fill(false); }
                case 14 -> server(() -> {
                    check(player().containerMenu.getSlot(0).getItem().is(Items.PISTON), "tag alternatives with only three empty backpack slots");
                    check(stored(Items.COBBLESTONE) == 31 && stored(Items.IRON_INGOT) == 31 && stored(Items.REDSTONE) == 31,
                            "reuse backpack cobblestone and fetch only missing ingredients");
                    passed("tight inventory, alternative selection, existing ingredients; ALL CLIENT CHECKS PASSED");
                    clearPlayer();
                    ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get().open(player(), MenuLocators.forInventorySlot(0), false);
                }, 15, 20);
                case 15 -> {
                    var parent = (appeng.client.gui.me.common.MEStorageScreen<?>) mc.screen;
                    var settings = new appeng.client.gui.me.common.TerminalSettingsScreen(parent);
                    parent.switchToScreen(settings);
                    settings.switchToScreen(new com.moakiee.ae2lt.client.TianshuTerminalSettingsScreen(settings));
                    phase = -1;
                }
                default -> throw new AssertionError("unknown phase " + phase);
            }
        } catch (Throwable failure) {
            report = "FAIL phase " + phase + ": " + failure;
            System.err.println("JEI_WIRELESS_CLIENT_" + report);
            failure.printStackTrace(); phase = -1;
        }
    }
    public static String status() { return "phase=" + phase + "; " + report; }
}
