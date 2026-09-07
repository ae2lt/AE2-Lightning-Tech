package com.moakiee.ae2lt.debug;

import java.io.File;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.ModList;

/** Development-only, isolated preview scene. Not included in release jars. */
public final class CelestweaveFieldPreview {
    private static final String WORLD = "Celestweave-Field-Preview";
    private CelestweaveFieldPreview() {}

    public static String status() {
        Minecraft mc = Minecraft.getInstance();
        return "LT=" + ModList.get().getModContainerById("ae2lt").orElseThrow().getModInfo().getVersion()
                + "; TB=" + ModList.get().getModContainerById("thunderbolt").orElseThrow().getModInfo().getVersion()
                + "; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                + "; player=" + (mc.player == null ? "none" : mc.player.getName().getString())
                + "; gameDir=" + mc.gameDirectory;
    }

    public static String createWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return "Already in a world";
        if (new File(mc.gameDirectory, "saves/" + WORLD).exists()) {
            mc.execute(() -> mc.createWorldOpenFlows().openWorld(WORLD, () -> {}));
            return "Queued opening dedicated preview world";
        }
        mc.execute(() -> mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(7270L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen));
        return "Queued dedicated flat preview world";
    }

    public static String equip() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return "No integrated player yet";
        if (!server.getWorldData().getLevelName().equals(WORLD)) return "Refusing to modify a non-preview world";
        var id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            for (String command : new String[] {
                    "gamemode creative @s", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "time set noon", "weather clear", "fill -8 64 -8 8 64 8 minecraft:smooth_stone",
                    "tp @s 0.5 65 0.5 0 0",
                    "item replace entity @s weapon.mainhand with minecraft:air",
                    "item replace entity @s weapon.offhand with minecraft:air",
                    "item replace entity @s armor.head with ae2lt:celestweave_oculus",
                    "item replace entity @s armor.chest with ae2lt:celestweave_core",
                    "item replace entity @s armor.legs with ae2lt:celestweave_conduit",
                    "item replace entity @s armor.feet with ae2lt:celestweave_stride"}) {
                server.getCommands().performPrefixedCommand(source, command);
            }
        });
        mc.execute(() -> {
            mc.setScreen(null);
            mc.options.pauseOnLostFocus = false;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.options.hideGui = true;
        });
        return "Queued full set and front camera in dedicated preview world";
    }

    public static String screenshot() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Screenshot.grab(mc.gameDirectory, "celestweave-field-ingame.png",
                mc.getMainRenderTarget(), ignored -> {}));
        return "Queued screenshots/celestweave-field-ingame.png";
    }
}
