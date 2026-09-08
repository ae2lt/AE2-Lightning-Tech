package com.moakiee.ae2lt.debug;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.client.CrystalCatalyzerScreen;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.menu.Ae2ltSlotSemantics;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import dev.emi.emi.api.EmiApi;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in real client/server fixture. Screenshots are captured from Minecraft's framebuffer. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeCatalyzerClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static int phase;
    private static int ticks;
    private static boolean finished;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.pigmeeCatalyzerClientProbe") || finished) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (++ticks % 40 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError("Server fixture failed", serverFailure);
            switch (phase) {
                case 0 -> onServer(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var level = player.serverLevel();
                    player.getInventory().clearContent();
                    level.setDayTime(6000);
                    level.setWeatherParameters(0, 6000, false, false);
                    for (int x = -5; x <= 5; x++) for (int z = -3; z <= 6; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int y = 100; y <= 104; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    level.setBlockAndUpdate(POS, ModBlocks.PIGMEE_CRYSTAL_CATALYZER.get().defaultBlockState());
                    level.setBlockAndUpdate(POS.east(2), ModBlocks.CRYSTAL_CATALYZER.get().defaultBlockState());
                    level.setBlockAndUpdate(POS.west(2), ModBlocks.PIGMEE_PATTERN_PROVIDER.get().defaultBlockState());
                    var items = level.getCapability(Capabilities.ItemHandler.BLOCK, POS, Direction.UP);
                    require(items != null && items.insertItem(0, AEBlocks.QUARTZ_BLOCK.stack(64), false).isEmpty(),
                            "real automation must insert 64 catalysts");
                    ((CrystalCatalyzerBlockEntity) level.getBlockEntity(POS.east(2))).getInventory()
                            .setItemDirect(0, AEBlocks.QUARTZ_BLOCK.stack(64));
                    player.setGameMode(GameType.SURVIVAL);
                    player.teleportTo(level, 2.6, 100, 4.3, java.util.Set.of(), 151, 18);
                });
                case 1 -> {
                    if (!(mc.level.getBlockEntity(POS) instanceof CrystalCatalyzerBlockEntity)) {
                        require(ticks < 1200, "client chunk delivery timed out");
                        return;
                    }
                    capture("pigmee-catalyzer-idle.png");
                    onServer(PigmeeCatalyzerClientProbe::openMenu);
                }
                case 2 -> {
                    var menu = menu();
                    require(menu.isPigmeeVariant(), "client did not receive Pigmee identity");
                    require(!menu.getSlots(Ae2ltSlotSemantics.CRYSTAL_CATALYZER_MATRIX).getFirst().isActive(),
                            "matrix slot must be hidden");
                    require(menu.getFluid().isEmpty(), "fixture must begin dry");
                    onServer(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
                        player.containerMenu.broadcastChanges();
                    });
                }
                case 3 -> {
                    require(menu().getCarried().is(Items.WATER_BUCKET), "water bucket not synchronized");
                    menu().clientInsertFluid();
                }
                case 4 -> {
                    var menu = menu();
                    require(menu.getFluid().getAmount() == 1000 && menu.getCarried().is(Items.BUCKET),
                            "water-bucket packet must transfer one bucket and return the empty container");
                    require(menu.isWorking() && menu.getProgress() > 0 && menu.getProgress() < 1,
                            "client progress must advance without power");
                    require(menu.getStoredEnergy() == 0 && menu.getConsumedEnergy() == 0, "client reports FE use");
                    capture("pigmee-catalyzer-working-gui.png");
                    menu.clientCycleMode();
                }
                case 5 -> {
                    require(menu().getMode() == Mode.CRYSTAL, "client action changed Pigmee mode");
                }
                case 6 -> {
                    var menu = menu();
                    var output = menu.getSlots(SlotSemantics.MACHINE_OUTPUT).getFirst().getItem();
                    if (output.isEmpty()) {
                        require(ticks < 1000, "client never received completed output");
                        return;
                    }
                    require(output.is(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem()) && output.getCount() == 1,
                            "client output must contain exactly 1 certus crystal");
                    require(menu.getFluid().isEmpty(), "completed cycle must debit 1000 mB water");
                    require(menu.getSlots(Ae2ltSlotSemantics.CRYSTAL_CATALYZER_CATALYST).getFirst().getItem().getCount() == 64,
                            "catalysts must remain in the client inventory");
                    capture("pigmee-catalyzer-complete-gui.png");
                    var recipe = EmiApi.getRecipeManager().getRecipe(ResourceLocation.fromNamespaceAndPath(
                            "ae2lt", "crystal_catalyzer/quartz_block"));
                    require(recipe != null, "shared recipe missing from live EMI index");
                    require(EmiApi.getRecipeManager().getRecipe(ResourceLocation.fromNamespaceAndPath(
                            "ae2lt", "crystal_catalyzer/pigmee_quartz_block")) == null,
                            "EMI must not contain a separate zero-cost Pigmee recipe");
                    var shared = (CrystalCatalyzerRecipe) mc.level.getRecipeManager().byKey(
                            ResourceLocation.fromNamespaceAndPath("ae2lt", "crystal_catalyzer/quartz_block"))
                            .orElseThrow().value();
                    require(shared.energyPerCycle() == 100_000 && shared.lightningCost() == 1
                                    && shared.catalystCount() == 1 && shared.getOutputTemplate().getCount() == 1,
                            "client recipe data must retain original costs and quantities");
                    EmiApi.displayRecipe(recipe);
                }
                case 7 -> {
                    capture("pigmee-catalyzer-emi.png");
                    mc.player.closeContainer();
                    mc.options.hideGui = true;
                    onServer(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.setGameMode(GameType.SPECTATOR);
                        player.teleportTo(player.serverLevel(), .5, 102.2, .5, java.util.Set.of(), 180, 90);
                    });
                }
                case 8 -> {
                    capture("pigmee-catalyzer-top.png");
                    onServer(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.teleportTo(player.serverLevel(), 2.6, 101.8, 3.2, java.util.Set.of(), 142, 43);
                    });
                }
                case 9 -> {
                    capture("pigmee-catalyzer-angled.png");
                    mc.options.hideGui = false;
                    onServer(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.setGameMode(GameType.SURVIVAL);
                        player.teleportTo(player.serverLevel(), 2.6, 100, 4.3, java.util.Set.of(), 151, 18);
                    });
                    report("PASS: real world model and screen loaded; item capability insertion, water-bucket client packet, "
                            + "empty-bucket return, hidden matrix slot, crystal-mode lock, zero FE, synchronized progress, "
                            + "1 output / 1000 mB water / 64 retained catalysts; shared recipe still declares "
                            + "100000 FE / 1 lightning / 1 catalyst / 1 output, with no duplicate Pigmee recipe in EMI.");
                    finished = true;
                }
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            }
            phase++;
        } catch (Throwable error) {
            report("FAIL phase=" + phase + ": " + error);
            error.printStackTrace();
            finished = true;
        }
    }

    private static void onServer(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable error) { serverFailure = error; }
        });
    }

    private static void openMenu() {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var host = (CrystalCatalyzerBlockEntity) player.serverLevel().getBlockEntity(POS);
        MenuOpener.open(CrystalCatalyzerMenu.TYPE, player, MenuLocators.forBlockEntity(host));
    }

    private static CrystalCatalyzerMenu menu() {
        require(Minecraft.getInstance().screen instanceof CrystalCatalyzerScreen,
                "Expected real catalyzer screen, got " + Minecraft.getInstance().screen);
        return ((CrystalCatalyzerScreen) Minecraft.getInstance().screen).getMenu();
    }

    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), text -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void report(String text) {
        System.out.println("PIGMEE_CATALYZER_CLIENT_PROBE " + text);
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("pigmee-catalyzer-client-probe.txt"), text + "\n");
        } catch (Exception error) { error.printStackTrace(); }
    }
}
