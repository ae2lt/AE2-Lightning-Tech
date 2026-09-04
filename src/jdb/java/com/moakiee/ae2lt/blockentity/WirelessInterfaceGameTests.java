package com.moakiee.ae2lt.blockentity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.core.definitions.AEBlocks;
import appeng.blockentity.storage.DriveBlockEntity;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ImportMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.InterfaceMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

/**
 * Self-contained in-game checks for the wireless FAST import path.
 *
 * <p>The fixture builds its own powered AE network and all target machines;
 * no saved world, player interaction, command block or external modpack
 * machine is required. It deliberately lives in the {@code jdb} source set,
 * which is present in development runs but excluded from published jars.</p>
 */
@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class WirelessInterfaceGameTests {
    private static final BlockPos INTERFACE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 1);
    // Default drive front is north, so connect it from the east side.
    private static final BlockPos DRIVE_POS = new BlockPos(0, 1, 1);
    private static final int TARGET_ORIGIN = 4;
    private static final int TARGET_STRIDE = 32;
    private static final int ITEMS_PER_TARGET = 27;
    private static final int ITEMS_PER_BATCH = ITEMS_PER_TARGET * 64;

    private static final List<Item> DISTINCT_ITEMS = List.of(
            Items.STONE, Items.GRANITE, Items.POLISHED_GRANITE,
            Items.DIORITE, Items.POLISHED_DIORITE, Items.ANDESITE,
            Items.POLISHED_ANDESITE, Items.DEEPSLATE, Items.COBBLED_DEEPSLATE,
            Items.CALCITE, Items.TUFF, Items.DRIPSTONE_BLOCK,
            Items.GRASS_BLOCK, Items.DIRT, Items.COARSE_DIRT,
            Items.PODZOL, Items.ROOTED_DIRT, Items.MUD,
            Items.COBBLESTONE, Items.OAK_PLANKS, Items.SPRUCE_PLANKS,
            Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS,
            Items.CHERRY_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS);

    private WirelessInterfaceGameTests() {}

    @GameTest(
            template = "wireless_io_empty",
            batch = "wireless_io_01_continuous",
            timeoutTicks = 1500)
    public static void fastImport1024Continuous(GameTestHelper helper) {
        var fixture = createFixture(helper, 1024);
        var state = new WorkloadState(1024);
        boolean control = Boolean.getBoolean("ae2lt.wirelessIoGameTest.control");
        int warmupTicks = Integer.getInteger(
                "ae2lt.wirelessIoBenchmark.warmupTicks", 40);
        int sampleTicks = Integer.getInteger(
                "ae2lt.wirelessIoBenchmark.sampleTicks", 300);
        int finishTick = warmupTicks + sampleTicks + 40;
        require(finishTick <= 1460,
                "GameTest warmup + sample must not exceed 1420 ticks");

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 40) {
                require(fixture.blockEntity.getMainNode().isActive(),
                        "self-contained AE network did not become active");
                require(fixture.drive.getCellInventory(0) != null,
                        "self-contained AE drive did not mount its infinite cell");
            }
            if (tick == 80) {
                state.resetPressureCounters();
            }
            if (!control && tick >= 40 && tick < finishTick - 40) {
                produceAtomicBatches(fixture, state);
            }
            if (tick == finishTick) {
                if (control) {
                    require(state.producedItems == 0,
                            "control fixture unexpectedly produced items");
                } else {
                    assertFixtureResult(fixture, state, 0.001, 2);
                }
                helper.succeed();
            }
        });
    }

    @GameTest(
            template = "wireless_io_empty",
            batch = "wireless_io_02_transitions",
            timeoutTicks = 470)
    public static void fastImport256Transitions(GameTestHelper helper) {
        // The dedicated benchmark run isolates the continuous 1024-target
        // test. The ordinary GameTestServer run still executes this strict
        // transition/recovery check.
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = createFixture(helper, 256);
        var state = new WorkloadState(256);

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 40) {
                require(fixture.blockEntity.getMainNode().isActive(),
                        "self-contained AE network did not become active");
                require(fixture.drive.getCellInventory(0) != null,
                        "self-contained AE drive did not mount its infinite cell");
            }

            if (state.pulseOutstanding && tick > 160 && allTargetsEmpty(fixture)) {
                state.pulseDrainLatency = Math.toIntExact(tick - 160);
                state.pulseOutstanding = false;
            }

            boolean productionTick = (tick >= 40 && tick < 80)
                    || tick == 160
                    || (tick >= 180 && tick < 184)
                    || (tick >= 220 && tick <= 280 && tick % 20 == 0)
                    || (tick >= 300 && tick < 380);
            if (productionTick) {
                produceAtomicBatches(fixture, state);
                if (tick == 160) {
                    state.pulseOutstanding = true;
                }
            }

            if (tick == 420) {
                require(state.pulseDrainLatency >= 0,
                        "single-tick pulse was never drained");
                require(state.pulseDrainLatency <= 5,
                        "single-tick pulse drain latency " + state.pulseDrainLatency
                                + " exceeded 5 ticks");
                assertFixtureResult(fixture, state, 0.001, 2);
                helper.succeed();
            }
        });
    }

    private static Fixture createFixture(GameTestHelper helper, int targets) {
        require(targets > 0 && targets <= 1024, "invalid target count " + targets);
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(INTERFACE_POS, ModBlocks.OVERLOADED_INTERFACE.get());
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        var level = helper.getLevel();
        var blockEntity = (OverloadedInterfaceBlockEntity) level.getBlockEntity(
                helper.absolutePos(INTERFACE_POS));
        require(blockEntity != null, "overloaded interface block entity was not created");
        var drive = (DriveBlockEntity) level.getBlockEntity(helper.absolutePos(DRIVE_POS));
        require(drive != null, "AE drive block entity was not created");
        var cellRemainder = drive.getInternalInventory().insertItem(0,
                new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()), false);
        require(cellRemainder.isEmpty(),
                "infinite storage cell was rejected by the self-contained drive");

        blockEntity.setInterfaceMode(InterfaceMode.WIRELESS);
        blockEntity.setIOSpeedMode(IOSpeedMode.FAST);
        blockEntity.setExportMode(ExportMode.OFF);
        blockEntity.setImportMode(ImportMode.AUTO);

        var inventories = new Container[targets];
        for (int index = 0; index < targets; index++) {
            int x = TARGET_ORIGIN + index % TARGET_STRIDE;
            int z = TARGET_ORIGIN + index / TARGET_STRIDE;
            var relative = new BlockPos(x, 1, z);
            helper.setBlock(relative, Blocks.BARREL);
            var absolute = helper.absolutePos(relative);
            var target = level.getBlockEntity(absolute);
            require(target instanceof Container,
                    "barrel target " + index + " has no container capability");
            inventories[index] = (Container) target;
            require(blockEntity.addOrUpdateConnection(new WirelessConnection(
                            level.dimension(), absolute, Direction.UP)),
                    "wireless connection " + index + " was rejected");
        }
        require(blockEntity.getConnections().size() == targets,
                "expected " + targets + " wireless targets, got "
                        + blockEntity.getConnections().size());
        return new Fixture(blockEntity, drive, inventories);
    }

    private static void produceAtomicBatches(Fixture fixture, WorkloadState state) {
        state.opportunities += fixture.inventories.length;
        for (int index = 0; index < fixture.inventories.length; index++) {
            var inventory = fixture.inventories[index];
            if (!inventory.isEmpty()) {
                state.blocked++;
                state.blockedStreak[index]++;
                state.maximumBlockedStreak = Math.max(state.maximumBlockedStreak,
                        state.blockedStreak[index]);
                continue;
            }

            state.blockedStreak[index] = 0;
            for (int slot = 0; slot < DISTINCT_ITEMS.size(); slot++) {
                inventory.setItem(slot, new ItemStack(DISTINCT_ITEMS.get(slot), 64));
            }
            inventory.setChanged();
            state.producedItems += ITEMS_PER_BATCH;
        }
    }

    private static void assertFixtureResult(
            Fixture fixture, WorkloadState state,
            double maximumBlockedRatio, int maximumBlockedStreak) {
        long remaining = remainingItems(fixture);
        long buffered = fixture.blockEntity.benchmarkBufferedImportAmount();
        long network = 0;
        var grid = fixture.blockEntity.getMainNode().getGrid();
        require(grid != null, "self-contained AE network disappeared");
        var networkStacks = new appeng.api.stacks.KeyCounter();
        grid.getStorageService().getInventory().getAvailableStacks(networkStacks);
        for (var entry : networkStacks) {
            network = Math.addExact(network, entry.getLongValue());
        }
        double blockedRatio = state.opportunities == 0 ? 0.0
                : (double) state.blocked / state.opportunities;

        require(blockedRatio <= maximumBlockedRatio,
                "blocked production ratio " + blockedRatio + " exceeded "
                        + maximumBlockedRatio + "; "
                        + fixture.blockEntity.benchmarkWirelessIoState()
                        + ", network=" + network + ", remaining=" + remaining);
        require(state.maximumBlockedStreak <= maximumBlockedStreak,
                "maximum blocked production streak " + state.maximumBlockedStreak
                        + " exceeded " + maximumBlockedStreak);
        require(remaining == 0,
                "fixture ended with " + remaining + " items in machine outputs");
        require(buffered + network + remaining == state.producedItems,
                "item ownership mismatch: produced=" + state.producedItems
                        + ", network=" + network + ", buffered=" + buffered
                        + ", remaining=" + remaining);
        require(networkStacks.size() == ITEMS_PER_TARGET,
                "expected " + ITEMS_PER_TARGET + " distinct stored keys, got "
                        + networkStacks.size());
    }

    private static boolean allTargetsEmpty(Fixture fixture) {
        for (var inventory : fixture.inventories) {
            if (!inventory.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static long remainingItems(Fixture fixture) {
        long total = 0;
        for (var inventory : fixture.inventories) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                total += inventory.getItem(slot).getCount();
            }
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Fixture(
            OverloadedInterfaceBlockEntity blockEntity,
            DriveBlockEntity drive,
            Container[] inventories) {}

    private static final class WorkloadState {
        final int[] blockedStreak;
        long opportunities;
        long blocked;
        long producedItems;
        int maximumBlockedStreak;
        boolean pulseOutstanding;
        int pulseDrainLatency = -1;

        WorkloadState(int targets) {
            blockedStreak = new int[targets];
        }

        void resetPressureCounters() {
            opportunities = 0;
            blocked = 0;
            maximumBlockedStreak = 0;
            java.util.Arrays.fill(blockedStreak, 0);
        }
    }
}
