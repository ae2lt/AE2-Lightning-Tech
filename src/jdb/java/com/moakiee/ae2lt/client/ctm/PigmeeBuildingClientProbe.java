package com.moakiee.ae2lt.client.ctm;

import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Opt-in disposable-world render acceptance; no probe classes are packaged in the mod. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeBuildingClientProbe {
    private static int phase, ticks;
    private static boolean done;
    private static volatile Throwable serverFailure;
    private static volatile boolean fixtureReady;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.pigmeeBuildingClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 60 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError("Server fixture", serverFailure);
            switch (phase) {
                case 0 -> {
                    mc.options.hideGui = true;
                    mc.options.fov().set(60);
                    mc.options.gamma().set(1.0);
                    mc.options.renderDistance().set(12);
                    mc.options.entityDistanceScaling().set(1.0);
                    mc.getWindow().setWindowed(900, 900);
                    mc.resizeDisplay();
                    onServer(PigmeeBuildingClientProbe::buildFixture);
                }
                case 1 -> {
                    if (!fixtureReady || !mc.level.getBlockState(new BlockPos(8, 100, 8))
                            .is(ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(DyeColor.WHITE).get())) {
                        require(ticks < 1800, "fixture did not reach client");
                        return;
                    }
                    checkModels();
                    checkConnections();
                }
                case 2 -> {
                    capture("pigmee-panels-sixteen-colors.png");
                    move(124.5, 151, 24.5, 180, 90);
                }
                case 3 -> {
                    capture("pigmee-panels-nine-panels.png");
                    move(124.5, 143, 59, 180, 51);
                }
                case 4 -> {
                    capture("pigmee-panels-angled.png");
                    move(110.5, 114, 66.5, 180, 90);
                }
                case 5 -> {
                    capture("pigmee-panels-connection-cases.png");
                    report("PASS: all 32 world models and inventory models baked with real textures; "
                            + "isolated, T, full, and cross masks/UV selections verified on all 6 faces; "
                            + "different colors/styles do not connect. Native framebuffer screenshots captured.");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError("Unexpected phase " + phase);
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            report("FAIL phase=" + phase + ": " + failure);
            done = true;
            mc.stop();
        }
    }

    private static void buildFixture() {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var level = player.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(0, 6000, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, mc.getSingleplayerServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, mc.getSingleplayerServer());
        player.setGameMode(GameType.SPECTATOR);
        for (DyeColor color : DyeColor.values()) {
            int ox = color.getId() % 4 * 20, oz = color.getId() / 4 * 20;
            for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) {
                boolean frame = x == 0 || z == 0 || x == 16 || z == 16 || x == 8 && z == 8;
                var block = (frame ? ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS : ModBlocks.PIGMEE_BUILDING_PANELS).get(color).get();
                level.setBlockAndUpdate(new BlockPos(ox + x, 100, oz + z), block.defaultBlockState());
            }
        }
        for (int x = 0; x < 49; x++) for (int z = 0; z < 49; z++) {
            boolean frame = x % 16 == 0 || z % 16 == 0 || x % 16 == 8 && z % 16 == 8;
            var block = frame ? ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(DyeColor.BLACK).get()
                    : ModBlocks.PIGMEE_BUILDING_PANELS.get(DyeColor.WHITE).get();
            level.setBlockAndUpdate(new BlockPos(100 + x, 100, z), block.defaultBlockState());
        }
        for (Direction face : Direction.values()) for (int kind = 0; kind < 4; kind++) {
            putCase(fixture(face, kind), face, kind == 0 ? 0 : kind == 1 ? 14 : 15, kind == 2 ? 15 : 0);
        }
        // The three user-reviewed cases, arranged left to right: isolated, T, full.
        putCase(new BlockPos(106, 100, 66), Direction.UP, 0, 0);
        putCase(new BlockPos(110, 100, 66), Direction.UP, 14, 0);
        putCase(new BlockPos(114, 100, 66), Direction.UP, 15, 15);
        var pos = new BlockPos(85, 100, 85);
        level.setBlockAndUpdate(pos, ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(DyeColor.WHITE).get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(DyeColor.BLACK).get().defaultBlockState());
        level.setBlockAndUpdate(pos.west(), ModBlocks.PIGMEE_BUILDING_PANELS.get(DyeColor.WHITE).get().defaultBlockState());
        player.teleportTo(level, 38.5, 175, 38.5, Set.of(), 180, 90);
        fixtureReady = true;
    }

    private static BlockPos fixture(Direction face, int kind) {
        return new BlockPos(80 + face.get3DDataValue() * 5, 110, 80 + kind * 5);
    }

    private static void putCase(BlockPos pos, Direction face, int edges, int corners) {
        var level = Minecraft.getInstance().getSingleplayerServer().overworld();
        Block block = ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(DyeColor.BLACK).get();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        for (int edge = 0; edge < 4; edge++) {
            level.setBlockAndUpdate(pos.relative(CtmFaceGeometry.neighborDir(face, edge)),
                    (edges & (1 << edge)) != 0 ? block.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState());
        }
        for (var q : CtmTileSelector.Quadrant.values()) {
            level.setBlockAndUpdate(CtmFaceGeometry.cornerPos(pos, face, q),
                    (corners & (1 << q.ordinal())) != 0 ? block.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState());
        }
    }

    private static void checkModels() {
        var mc = Minecraft.getInstance();
        for (var panels : java.util.List.of(ModBlocks.PIGMEE_BUILDING_PANELS, ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS)) {
            for (var color : DyeColor.values()) {
                var block = panels.get(color).get();
                var model = mc.getBlockRenderer().getBlockModel(block.defaultBlockState());
                require(model instanceof ConnectedTextureBakedModel, "CTM loader for " + block);
                var name = model.getParticleIcon().contents().name().toString();
                require(name.contains(color.getName() + "_pigmee_") && !name.contains("missingno"), "world sprite " + name);
                var item = mc.getItemRenderer().getModel(new ItemStack(block), mc.level, mc.player, 0);
                require(!item.getParticleIcon().contents().name().toString().contains("missingno"), "item sprite " + block);
                require(item.getTransforms().gui.scale.lengthSquared() > 0
                                && item.getTransforms().gui.rotation.lengthSquared() > 0, "native cube GUI transforms " + block);
            }
        }
    }

    private static void checkConnections() {
        var mc = Minecraft.getInstance();
        for (Direction face : Direction.values()) for (int kind = 0; kind < 4; kind++) {
            var pos = fixture(face, kind);
            var state = mc.level.getBlockState(pos);
            require(state.is(ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(DyeColor.BLACK).get()), "fixture loaded " + pos);
            var model = (ConnectedTextureBakedModel) mc.getBlockRenderer().getBlockModel(state);
            var data = model.getModelData(mc.level, pos, state, ModelData.EMPTY);
            var connection = data.get(ConnectedTextureBakedModel.CONNECTION);
            int edges = kind == 0 ? 0 : kind == 1 ? 14 : 15;
            int corners = kind == 2 ? 15 : 0;
            require(connection != null && connection.edges(face) == edges && connection.corners(face) == corners,
                    "live " + face + " case " + kind + " neighbor masks");
            var quads = model.getQuads(state, face, RandomSource.create(0), data, null);
            require(quads.size() == 4, "four connected face quadrants");
            for (int sq = 0; sq < 2; sq++) for (int tq = 0; tq < 2; tq++) {
                var tile = CtmTileSelector.select(CtmTileSelector.quadrant(sq, tq), edges, corners);
                var quad = quads.get(sq * 2 + tq);
                var sprite = quad.getSprite();
                require(sprite.contents().width() == (kind == 0 ? 16 : 32), "base/CTM atlas selection");
                var vertices = quad.getVertices();
                float u = sprite.getU((float) tile.x() / tile.source().gridSize());
                float v = sprite.getV((float) tile.y() / tile.source().gridSize());
                require(Math.abs(Float.intBitsToFloat(vertices[4]) - u) < .00001
                                && Math.abs(Float.intBitsToFloat(vertices[5]) - v) < .00001,
                        "actual baked quadrant UV for " + face + " case " + kind);
            }
        }
        var pos = new BlockPos(85, 100, 85);
        var state = mc.level.getBlockState(pos);
        var model = (ConnectedTextureBakedModel) mc.getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(mc.level, pos, state, ModelData.EMPTY);
        require(data.get(ConnectedTextureBakedModel.CONNECTION).edges(Direction.UP) == 0,
                "different colors and different styles must remain disconnected");
    }

    private static void move(double x, double y, double z, float yaw, float pitch) {
        onServer(() -> {
            var mc = Minecraft.getInstance();
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.teleportTo(player.serverLevel(), x, y, z, Set.of(), yaw, pitch);
        });
    }

    private static void onServer(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), text -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void report(String message) {
        System.out.println("PIGMEE_BUILDING_CLIENT_PROBE " + message);
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("pigmee-building-client-probe.txt"), message + "\n");
        } catch (Exception failure) { failure.printStackTrace(); }
    }
}
