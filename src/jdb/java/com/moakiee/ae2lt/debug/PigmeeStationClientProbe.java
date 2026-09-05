package com.moakiee.ae2lt.debug;

import appeng.api.stacks.AEItemKey;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.minecraft.resources.ResourceLocation;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.client.PigmeeSynthesisStationScreen;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opt-in development integration probe using real client/server menu packets. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeStationClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static int phase;
    private static int ticks;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.pigmeeClientProbe") || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (++ticks % 40 != 0) return;
        try {
            if (phase == 0) {
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var level = player.serverLevel();
                    player.getInventory().clearContent();
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                    for (int x = -3; x <= 3; x++) {
                        for (int z = -3; z <= 4; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
                        }
                    }
                    level.setBlockAndUpdate(POS.below(), Blocks.CHEST.defaultBlockState());
                    var chest = (ChestBlockEntity) level.getBlockEntity(POS.below());
                    chest.clearContent();
                    chest.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
                    chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
                    chest.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
                    level.setBlockAndUpdate(POS, ModBlocks.PIGMEE_SYNTHESIS_STATION.get().defaultBlockState());
                    var host = (PigmeeSynthesisStationBlockEntity) level.getBlockEntity(POS);
                    host.getSubInventory(PigmeeSynthesisStationBlockEntity.INV_CRAFTING)
                            .setItemDirect(0, new ItemStack(Items.OAK_LOG, 2));
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(level, 0.5, 100, 3.5, java.util.Set.of(), 180, 15);
                });
            } else if (phase == 1) {
                // Teleporting starts chunk delivery; wait for the real client BE
                // before sending the menu packet, just as a player must see it first.
                if (!(mc.level.getBlockEntity(POS) instanceof PigmeeSynthesisStationBlockEntity)) {
                    require(ticks < 1200, "Timed out waiting for client chunk");
                    return;
                }
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var host = (PigmeeSynthesisStationBlockEntity) player.serverLevel().getBlockEntity(POS);
                    MenuOpener.open(PigmeeSynthesisStationMenu.TYPE, player, MenuLocators.forBlockEntity(host));
                });
            } else {
                require(mc.screen instanceof PigmeeSynthesisStationScreen,
                        "Expected station screen, got " + mc.screen);
                var menu = ((PigmeeSynthesisStationScreen) mc.screen).getMenu();
                switch (phase) {
                    case 2 -> {
                        require(menu.getLinkStatus().connected(), "Client must receive connected status");
                        require(amount(menu, Items.IRON_INGOT) == 64, "Client storage list must show 64 iron");
                        require(menu.getSlots(SlotSemantics.CRAFTING_RESULT).getFirst().getItem().is(Items.OAK_PLANKS),
                                "Client crafting output must show planks");
                        Screenshot.grab(mc.gameDirectory, "pigmee-station-initial.png", mc.getMainRenderTarget(), text -> {});
                        var iron = menu.getClientRepo().getAllEntries().stream()
                                .filter(e -> e.getWhat().equals(AEItemKey.of(Items.IRON_INGOT))).findFirst().orElseThrow();
                        menu.handleInteraction(iron.getSerial(), InventoryAction.PICKUP_OR_SET_DOWN);
                    }
                    case 3 -> {
                        require(menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 64,
                                "Extraction packet must synchronize 64 iron to cursor");
                        require(amount(menu, Items.IRON_INGOT) == 0, "Extraction must debit displayed inventory");
                        menu.handleInteraction(-1, InventoryAction.PICKUP_OR_SET_DOWN);
                    }
                    case 4 -> {
                        require(menu.getCarried().isEmpty() && amount(menu, Items.IRON_INGOT) == 64,
                                "Insertion packet must return iron to storage");
                        var result = menu.getSlots(SlotSemantics.CRAFTING_RESULT).getFirst();
                        PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.CRAFT_ITEM, result.index, 0));
                    }
                    case 5 -> {
                        require(menu.getCarried().is(Items.OAK_PLANKS) && menu.getCarried().getCount() == 4,
                                "Craft packet must synchronize four planks to cursor");
                        require(amount(menu, Items.OAK_LOG) + menu.getCraftingMatrix().getStackInSlot(0).getCount() == 17,
                                "Crafting must consume exactly one log across storage and matrix");
                        menu.handleInteraction(-1, InventoryAction.PICKUP_OR_SET_DOWN);
                    }
                    case 6 -> {
                        require(amount(menu, Items.OAK_PLANKS) == 4, "Crafted planks must be inserted");
                        var recipe = EmiApi.getRecipeManager().getRecipe(ResourceLocation.withDefaultNamespace("crafting_table"));
                        require(recipe != null, "EMI crafting-table recipe must be loaded");
                        require((boolean) Class.forName("dev.emi.emi.registry.EmiRecipeFiller")
                                .getMethod("performFill", dev.emi.emi.api.recipe.EmiRecipe.class,
                                        net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class,
                                        EmiCraftContext.Type.class, EmiCraftContext.Destination.class, int.class)
                                .invoke(null, recipe, mc.screen, EmiCraftContext.Type.FILL_BUTTON,
                                        EmiCraftContext.Destination.NONE, 1),
                                "EMI fill button handler must accept the recipe");
                    }
                    case 7 -> {
                        require(menu.getSlots(SlotSemantics.CRAFTING_RESULT).getFirst().getItem().is(Items.CRAFTING_TABLE),
                                "EMI fill must synchronize crafting-table output; grid=" + menu.getCraftingMatrix().getStackInSlot(0) + ", planks=" + amount(menu, Items.OAK_PLANKS));
                        for (int slot : new int[] {0, 1, 3, 4}) {
                            var item = menu.getCraftingMatrix().getStackInSlot(slot);
                            require(item.is(Items.OAK_PLANKS) && item.getCount() == 1,
                                    "EMI fill must pull one plank into slot " + slot);
                        }
                        require(amount(menu, Items.OAK_PLANKS) == 0 && amount(menu, Items.OAK_LOG) == 17,
                                "EMI fill must debit four planks and return old grid logs without loss");
                        Screenshot.grab(mc.gameDirectory, "pigmee-station-emi-fill.png", mc.getMainRenderTarget(), text -> {});
                        mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld()
                                .setBlockAndUpdate(POS.below(), Blocks.AIR.defaultBlockState()));
                    }
                    case 8 -> {
                        require(!menu.getLinkStatus().connected(), "Removed chest must disconnect open client menu");
                        require(amount(menu, Items.IRON_INGOT) == 0, "Removed storage must disappear from client");
                        mc.getSingleplayerServer().execute(() -> {
                            var level = mc.getSingleplayerServer().overworld();
                            level.setBlockAndUpdate(POS.below(), Blocks.CHEST.defaultBlockState());
                            ((ChestBlockEntity) level.getBlockEntity(POS.below()))
                                    .setItem(0, new ItemStack(Items.IRON_INGOT, 7));
                        });
                    }
                    case 9 -> {
                        require(menu.getLinkStatus().connected() && amount(menu, Items.IRON_INGOT) == 7,
                                "Replacement chest must update the existing client menu");
                        Screenshot.grab(mc.gameDirectory, "pigmee-station-final.png", mc.getMainRenderTarget(), text -> {});
                        report("PASS: real screen loaded; storage list, extraction, insertion, crafting, "
                                + "ingredient conservation, real EMI fill-button transfer from adjacent storage, live removal/replacement and client synchronization verified.");
                        finished = true;
                    }
                    default -> throw new IllegalStateException("Unexpected phase " + phase);
                }
            }
            phase++;
        } catch (Throwable error) {
            report("FAIL phase=" + phase + ": " + error);
            error.printStackTrace();
            finished = true;
        }
    }

    private static long amount(PigmeeSynthesisStationMenu menu, Item item) {
        return menu.getClientRepo().getAllEntries().stream()
                .filter(e -> e.getWhat().equals(AEItemKey.of(item)))
                .mapToLong(e -> e.getStoredAmount()).sum();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void report(String text) {
        System.out.println("PIGMEE_CLIENT_PROBE " + text);
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("pigmee-client-probe.txt"), text + "\n");
        } catch (Exception error) {
            error.printStackTrace();
        }
    }
}
