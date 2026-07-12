package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * JDB development entry point intended to be invoked while the server
 * tick thread is suspended. It performs real-world multiblock lifecycle checks
 * without relying on keyboard/mouse automation.
 */
public final class TianshuJdbHarness {
    private static final BlockPos BASE_CONTROLLER = new BlockPos(8, 200, 8);
    private static volatile String lastReport = "not run";

    public static String runAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return lastReport = "FAIL: no running Minecraft server";
        }
        if (!server.isSameThread()) {
            return lastReport = "FAIL: invoke runAll() from the suspended server tick thread";
        }

        var checks = new ArrayList<String>();
        try {
            ServerLevel level = server.overworld();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos controllerPos = BASE_CONTROLLER.offset(0, 0, direction.get2DDataValue() * 12);
                runDirection(level, controllerPos, direction, checks);
            }
            return lastReport = "PASS: " + checks.size() + " checks; " + String.join("; ", checks);
        } catch (Throwable failure) {
            failure.printStackTrace();
            return lastReport = "FAIL: " + failure.getClass().getSimpleName() + ": " + failure.getMessage()
                    + "; completed=" + String.join("; ", checks);
        }
    }

    public static String lastReport() {
        return lastReport;
    }

    private static void runDirection(ServerLevel level, BlockPos controllerPos, Direction direction,
                                     List<String> checks) {
        clearTemplateVolume(level, controllerPos, direction);
        buildComplete(level, controllerPos, direction);

        var controller = requireController(level, controllerPos);
        controller.scanNow();
        require(controller.isFormed(), direction + " did not form: " + controller.issueText());
        require(controller.memberCount() == 243,
                direction + " member count was " + controller.memberCount() + " instead of 243");
        var profile = controller.getCoreProfile();
        require(profile.capacityCoreCount() == 13, direction + " storage count was " + profile.capacityCoreCount());
        require(profile.parallelCoreCount() == 13, direction + " parallel count was " + profile.parallelCoreCount());
        require(profile.storageBytes() == 13L * 64L * 1024L * 1024L,
                direction + " storage bytes were " + profile.storageBytes());
        require(profile.parallelism() == 13 * 128,
                direction + " parallelism was " + profile.parallelism());

        BlockPos portPos = TianshuMultiblockScanner.worldPos(
                controllerPos, TianshuMultiblockTemplate.LOWER_PORT, direction);
        var port = requirePort(level, portPos);
        require(port.isFormed(), direction + " port did not enter formed state");
        require(controllerPos.equals(port.getControllerPos()), direction + " port controller binding was wrong");

        BlockPos brokenCasing = TianshuMultiblockScanner.worldPos(controllerPos, BlockPos.ZERO, direction);
        level.setBlock(brokenCasing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        controller.scanNow();
        require(!controller.isFormed(), direction + " stayed formed after a casing was removed");
        require(!port.isFormed(), direction + " port stayed formed after structure breakup");
        require(port.getCableConnectionType(Direction.UP) == appeng.api.util.AECableType.NONE,
                direction + " port still exposed an AE cable connection while unformed");

        level.setBlock(brokenCasing, ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState(), Block.UPDATE_ALL);
        controller.scanNow();
        require(controller.isFormed(), direction + " did not reform after casing repair");

        BlockPos requiredAir = findFirstIgnored(controllerPos, direction);
        level.setBlock(requiredAir, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        controller.scanNow();
        require(!controller.isFormed(), direction + " accepted a block in required-air space");
        require(controller.getPrimaryIssueOrdinal() == TianshuMultiblockScanIssue.UNEXPECTED_BLOCK.ordinal(),
                direction + " did not report UNEXPECTED_BLOCK: " + controller.issueText());
        level.setBlock(requiredAir, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        controller.scanNow();
        require(controller.isFormed(), direction + " did not recover after required-air space was cleared");

        checks.add(direction + "=formed/deformed/reformed/profile/port/air-check");
    }

    private static void buildComplete(ServerLevel level, BlockPos controllerPos, Direction direction) {
        int peripheralIndex = 0;
        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) {
            for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++) {
                for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                    var local = new BlockPos(x, y, z);
                    var world = TianshuMultiblockScanner.worldPos(controllerPos, local, direction);
                    BlockState state = switch (TianshuMultiblockTemplate.roleAt(local)) {
                        case CASING -> ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState();
                        case GLASS -> ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get().defaultBlockState();
                        case CONTROLLER -> ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get().defaultBlockState()
                                .setValue(TianshuSupercomputerControllerBlock.FACING, direction);
                        case PORT_CANDIDATE -> local.equals(TianshuMultiblockTemplate.LOWER_PORT)
                                ? ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().defaultBlockState()
                                : ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState();
                        case CORE_RESERVED -> {
                            if (local.equals(new BlockPos(3, 3, 3))) {
                                yield ModBlocks.BASELINE_SUPERCOMPUTING_UNIT.get().defaultBlockState();
                            }
                            yield peripheralIndex++ % 2 == 0
                                    ? ModBlocks.STORAGE_SUPERCOMPUTING_UNIT.get().defaultBlockState()
                                    : ModBlocks.PARALLEL_SUPERCOMPUTING_UNIT.get().defaultBlockState();
                        }
                        case IGNORED -> Blocks.AIR.defaultBlockState();
                    };
                    level.setBlock(world, state, Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void clearTemplateVolume(ServerLevel level, BlockPos controllerPos, Direction direction) {
        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) {
            for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++) {
                for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                    level.setBlock(TianshuMultiblockScanner.worldPos(
                            controllerPos, new BlockPos(x, y, z), direction),
                            Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static BlockPos findFirstIgnored(BlockPos controllerPos, Direction direction) {
        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) {
            for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++) {
                for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                    var local = new BlockPos(x, y, z);
                    if (TianshuMultiblockTemplate.roleAt(local)
                            == com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockRole.IGNORED) {
                        return TianshuMultiblockScanner.worldPos(controllerPos, local, direction);
                    }
                }
            }
        }
        throw new IllegalStateException("template has no required-air position");
    }

    private static TianshuSupercomputerControllerBlockEntity requireController(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TianshuSupercomputerControllerBlockEntity controller) {
            return controller;
        }
        throw new IllegalStateException("missing controller block entity at " + pos);
    }

    private static TianshuSupercomputerPortBlockEntity requirePort(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TianshuSupercomputerPortBlockEntity port) {
            return port;
        }
        throw new IllegalStateException("missing port block entity at " + pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private TianshuJdbHarness() {
    }
}
