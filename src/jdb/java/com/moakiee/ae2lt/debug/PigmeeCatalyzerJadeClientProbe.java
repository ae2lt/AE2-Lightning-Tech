package com.moakiee.ae2lt.debug;

import appeng.core.definitions.AEBlocks;
import appeng.integration.modules.igtooltip.TooltipIds;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.client.CrystalCatalyzerScreen;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.impl.ObjectDataCenter;
import snownee.jade.overlay.WailaTickHandler;

/** Opt-in isolated client: check the actual collected Jade tooltip and both menus. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeCatalyzerJadeClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static int phase;
    private static int ticks;
    private static boolean finished;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.pigmeeCatalyzerJadeProbe") || finished) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 40 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError("Server fixture failed", serverFailure);
            switch (phase) {
                case 0 -> onServer(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var level = player.serverLevel();
                    level.setDayTime(6000);
                    level.setWeatherParameters(0, 6000, false, false);
                    for (int x = -3; x <= 5; x++) for (int z = -2; z <= 5; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int y = 100; y <= 104; y++) {
                            level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                    level.setBlockAndUpdate(POS, ModBlocks.PIGMEE_CRYSTAL_CATALYZER.get().defaultBlockState());
                    level.setBlockAndUpdate(POS.east(2), ModBlocks.CRYSTAL_CATALYZER.get().defaultBlockState());
                    var pigmee = (CrystalCatalyzerBlockEntity) level.getBlockEntity(POS);
                    pigmee.getInventory().setItemDirect(0, AEBlocks.QUARTZ_BLOCK.stack(64));
                    pigmee.getTank().setFluid(new FluidStack(Fluids.WATER, 16000));
                    player.setGameMode(GameType.SURVIVAL);
                    player.teleportTo(level, .5, 100, 3, java.util.Set.of(), 180, 25);
                });
                case 1 -> {
                    if (!tooltipReady(POS)) return;
                    var tooltip = WailaTickHandler.instance().rootElement.getTooltip();
                    require(tooltip.get(TooltipIds.GRID_NODE_STATE).isEmpty(),
                            "Pigmee still displays AE grid status: " + tooltip.getMessage());
                    capture("pigmee-jade-standalone.png");
                    record("Pigmee Jade: " + tooltip.getMessage());
                    onServer(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.teleportTo(player.serverLevel(), 2.5, 100, 3, java.util.Set.of(), 180, 25);
                    });
                }
                case 2 -> {
                    if (!tooltipReady(POS.east(2))) return;
                    var tooltip = WailaTickHandler.instance().rootElement.getTooltip();
                    require(!tooltip.get(TooltipIds.GRID_NODE_STATE).isEmpty(),
                            "normal catalyzer lost its grid status: " + tooltip.getMessage());
                    capture("normal-jade-grid-state.png");
                    record("Normal Jade: " + tooltip.getMessage());
                    open(POS);
                }
                case 3 -> {
                    require(mc.screen instanceof CrystalCatalyzerScreen
                                    && mc.player.containerMenu instanceof CrystalCatalyzerMenu menu
                                    && menu.isPigmeeVariant() && menu.getStoredEnergy() == 0,
                            "standalone Pigmee menu failed");
                    capture("pigmee-standalone-menu.png");
                    open(POS.east(2));
                }
                case 4 -> {
                    require(mc.screen instanceof CrystalCatalyzerScreen
                                    && mc.player.containerMenu instanceof CrystalCatalyzerMenu menu
                                    && !menu.isPigmeeVariant(),
                            "normal subtype menu failed");
                    capture("normal-catalyzer-menu.png");
                    record("PASS: live Jade omits Pigmee grid status, retains normal grid status; both menus open.");
                }
                case 5 -> {
                    finished = true;
                    mc.stop();
                }
                default -> throw new AssertionError("phase " + phase);
            }
            phase++;
        } catch (Throwable error) {
            record("FAIL phase=" + phase + ": " + error);
            error.printStackTrace();
            finished = true;
            mc.stop();
        }
    }

    private static boolean tooltipReady(BlockPos pos) {
        require(ticks < 1200, "Jade never resolved expected target " + pos);
        return ObjectDataCenter.get() instanceof BlockAccessor accessor
                && accessor.getPosition().equals(pos)
                && WailaTickHandler.instance().rootElement != null
                && !ObjectDataCenter.getServerData().isEmpty();
    }

    private static void open(BlockPos pos) {
        onServer(() -> {
            var mc = Minecraft.getInstance();
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            MenuOpener.open(CrystalCatalyzerMenu.TYPE, player, MenuLocators.forBlockEntity(
                    player.serverLevel().getBlockEntity(pos)));
        });
    }

    private static void onServer(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable error) { serverFailure = error; }
        });
    }

    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> record(message.getString()));
    }

    private static void record(String message) {
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("pigmee-jade-probe.txt"),
                    message + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException error) { throw new RuntimeException(error); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
