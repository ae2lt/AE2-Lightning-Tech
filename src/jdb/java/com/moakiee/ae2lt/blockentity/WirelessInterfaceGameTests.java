package com.moakiee.ae2lt.blockentity;

import java.util.ArrayDeque;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ImportMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.InterfaceMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.debug.WirelessIoPerformanceProbe;


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
    private static final int HIGH_CARDINALITY_KEY_COUNT = 1024 * ITEMS_PER_TARGET;
    private static final String HIGH_CARDINALITY_SCENARIO = "high-cardinality-reject";

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
        if (isHighCardinalityScenario()) {
            helper.succeed();
            return;
        }
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
            batch = "wireless_io_03_high_cardinality_reject",
            timeoutTicks = 1500)
    public static void fastImportHighCardinalityRejectRecovery(GameTestHelper helper) {
        if (!isHighCardinalityScenario()) {
            helper.succeed();
            return;
        }

        var fixture = createFixture(helper, 1024, false);
        var state = new HighCardWorkloadState();
        boolean control = Boolean.getBoolean("ae2lt.wirelessIoGameTest.control");
        int warmupTicks = Integer.getInteger(
                "ae2lt.wirelessIoBenchmark.warmupTicks", 200);
        int sampleTicks = Integer.getInteger(
                "ae2lt.wirelessIoBenchmark.sampleTicks", 1_200);
        int finishTick = warmupTicks + sampleTicks + 40;
        int productionTick = 80;
        int recoveryTick = warmupTicks + 120;
        require(finishTick <= 1460,
                "GameTest warmup + sample must not exceed 1420 ticks");

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 40) {
                require(fixture.blockEntity.getMainNode().isActive(),
                        "high-cardinality AE network did not become active");
                require(fixture.drive.getCellInventory(0) != null,
                        "high-cardinality AE drive did not mount its finite cell");
                state.blockerAmount = fillRejectingStorage(fixture);
            }
            if (!control && tick >= productionTick && tick < recoveryTick) {
                long produced = produceHighCardinalityBatch(fixture);
                if (produced > 0) {
                    state.recordProduction(tick, produced);
                }
            }
            if (!control && tick == recoveryTick) {
                require(state.blockerAmount > 0, "rejecting storage was not filled");
                releaseRejectingStorage(fixture, state.blockerAmount);
                state.recoveredAt = tick;
            }

            if (tick >= 40) {
                state.observe(fixture, tick);
            }
            if (tick == finishTick) {
                if (!control) {
                    require(state.producedItems > 0,
                            "high-cardinality producer never emitted a batch");
                    require(state.maxBufferedKeys >= 16_385,
                            "high-cardinality rejection never built a >16,384-key buffer: "
                                    + fixture.blockEntity.benchmarkWirelessIoState());
                    require(state.recoveredAt >= 0,
                            "high-cardinality storage recovery did not run");
                    assertFixtureResult(
                            fixture, state, 0.001, 2, HIGH_CARDINALITY_KEY_COUNT);
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
        return createFixture(helper, targets, true);
    }

    private static Fixture createFixture(
            GameTestHelper helper, int targets, boolean infiniteCell) {
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
                infiniteCell
                        ? new ItemStack(ModItems.INFINITE_STORAGE_CELL.get())
                        : AEItems.ITEM_CELL_1K.stack(),
                false);
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

    private static boolean isHighCardinalityScenario() {
        return System.getProperty("ae2lt.wirelessIoBenchmark.scenario", "")
                .contains(HIGH_CARDINALITY_SCENARIO);
    }

    private static long fillRejectingStorage(Fixture fixture) {
        var grid = fixture.blockEntity.getMainNode().getGrid();
        require(grid != null, "high-cardinality grid disappeared before fill");
        var inventory = grid.getStorageService().getInventory();
        var blocker = requireItemKey(new ItemStack(Items.DIRT));
        long accepted = inventory.insert(
                blocker, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        require(accepted > 0, "finite cell did not accept its blocker fill");

        var probe = inventory.insert(
                requireItemKey(highCardinalityStack(0)), 1, Actionable.SIMULATE,
                IActionSource.empty());
        require(probe == 0,
                "finite cell was not actually rejecting a new high-cardinality key");
        return accepted;
    }

    private static void releaseRejectingStorage(Fixture fixture, long blockerAmount) {
        var grid = fixture.blockEntity.getMainNode().getGrid();
        require(grid != null, "high-cardinality grid disappeared during recovery");
        var blocker = requireItemKey(new ItemStack(Items.DIRT));
        long extracted = grid.getStorageService().getInventory().extract(
                blocker, blockerAmount, Actionable.MODULATE, IActionSource.empty());
        require(extracted == blockerAmount,
                "finite cell blocker extraction changed ownership: expected="
                        + blockerAmount + ", extracted=" + extracted);
        fixture.drive.getInternalInventory().setItemDirect(
                0, new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()));
    }

    private static long produceHighCardinalityBatch(Fixture fixture) {
        long produced = 0;
        for (int index = 0; index < fixture.inventories.length; index++) {
            var inventory = fixture.inventories[index];
            if (!inventory.isEmpty()) {
                continue;
            }
            for (int slot = 0; slot < ITEMS_PER_TARGET; slot++) {
                inventory.setItem(slot,
                        highCardinalityStack(index * ITEMS_PER_TARGET + slot));
            }
            inventory.setChanged();
            produced += ITEMS_PER_BATCH;
        }
        return produced;
    }

    private static ItemStack highCardinalityStack(int key) {
        var stack = new ItemStack(Items.STONE, 64);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("ae2lt-high-cardinality-" + key));
        return stack;
    }

    private static AEItemKey requireItemKey(ItemStack stack) {
        var key = AEItemKey.of(stack);
        require(key != null, "could not create AE item key for " + stack);
        return key;
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
        assertFixtureResult(
                fixture, state, maximumBlockedRatio, maximumBlockedStreak,
                ITEMS_PER_TARGET);
    }

    private static void assertFixtureResult(
            Fixture fixture, WorkloadState state,
            double maximumBlockedRatio, int maximumBlockedStreak,
            int expectedNetworkKeys) {
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
        require(networkStacks.size() == expectedNetworkKeys,
                "expected " + expectedNetworkKeys + " distinct stored keys, got "
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

    private static class WorkloadState {
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

    private static final class HighCardWorkloadState extends WorkloadState {
        private static final int LATENCY_HISTOGRAM_SIZE = 2_048;

        long blockerAmount;
        long recoveredAt = -1;
        long maxBufferedItems;
        int maxBufferedKeys;
        long lastExtracted;
        long lastNetworkImported;
        long extractionLatencySamples;
        long networkLatencySamples;
        long extractionLatencyMax = -1;
        long networkLatencyMax = -1;
        final long[] extractionLatencyHistogram = new long[LATENCY_HISTOGRAM_SIZE];
        final long[] networkLatencyHistogram = new long[LATENCY_HISTOGRAM_SIZE];
        final ArrayDeque<ProductionBatch> extractionPending = new ArrayDeque<>();
        final ArrayDeque<ProductionBatch> networkPending = new ArrayDeque<>();

        HighCardWorkloadState() {
            super(1024);
        }

        void recordProduction(long tick, long amount) {
            require(amount > 0, "high-cardinality producer emitted no items");
            producedItems = Math.addExact(producedItems, amount);
            extractionPending.addLast(new ProductionBatch(tick, amount));
            networkPending.addLast(new ProductionBatch(tick, amount));
        }

        void observe(Fixture fixture, long tick) {
            long remaining = remainingItems(fixture);
            long buffered = fixture.blockEntity.benchmarkBufferedImportAmount();
            long extracted = producedItems - remaining;
            long networkImported = extracted - buffered;
            require(extracted >= 0 && networkImported >= 0,
                    "high-cardinality ownership counters went negative: produced="
                            + producedItems + ", extracted=" + extracted
                            + ", buffered=" + buffered + ", network=" + networkImported);
            maxBufferedItems = Math.max(maxBufferedItems, buffered);
            maxBufferedKeys = Math.max(
                    maxBufferedKeys, fixture.blockEntity.benchmarkBufferedImportKeys());
            long extractedDelta = extracted - lastExtracted;
            long networkDelta = networkImported - lastNetworkImported;
            extractionLatencySamples += extractedDelta;
            networkLatencySamples += networkDelta;
            extractionLatencyMax = Math.max(extractionLatencyMax,
                    recordLatency(extractionLatencyHistogram, extractionPending,
                            extractedDelta, tick));
            networkLatencyMax = Math.max(networkLatencyMax,
                    recordLatency(networkLatencyHistogram, networkPending,
                            networkDelta, tick));
            lastExtracted = extracted;
            lastNetworkImported = networkImported;
            WirelessIoPerformanceProbe.recordImportWorkload(
                    producedItems, extracted, networkImported, buffered, maxBufferedItems,
                    maxBufferedKeys,
                    percentile(extractionLatencyHistogram, extractionLatencySamples, 0.50),
                    percentile(extractionLatencyHistogram, extractionLatencySamples, 0.95),
                    percentile(extractionLatencyHistogram, extractionLatencySamples, 0.99),
                    extractionLatencyMax,
                    percentile(networkLatencyHistogram, networkLatencySamples, 0.50),
                    percentile(networkLatencyHistogram, networkLatencySamples, 0.95),
                    percentile(networkLatencyHistogram, networkLatencySamples, 0.99),
                    networkLatencyMax);
        }

        private static long recordLatency(
                long[] histogram, ArrayDeque<ProductionBatch> pending,
                long count, long tick) {
            if (count <= 0) return -1;
            long remaining = count;
            long maxLatency = -1;
            while (remaining > 0 && !pending.isEmpty()) {
                var batch = pending.peekFirst();
                long accepted = Math.min(remaining, batch.remaining);
                long latency = Math.max(0, tick - batch.producedAt);
                maxLatency = Math.max(maxLatency, latency);
                int bucket = (int) Math.min(latency, histogram.length - 1);
                histogram[bucket] = Math.addExact(histogram[bucket], accepted);
                batch.remaining -= accepted;
                remaining -= accepted;
                if (batch.remaining == 0) {
                    pending.removeFirst();
                }
            }
            require(remaining == 0,
                    "high-cardinality latency sample exceeded produced ownership: "
                            + remaining + " items");
            return maxLatency;
        }

        private static long percentile(long[] histogram, long sampleCount, double percentile) {
            if (sampleCount <= 0) return -1;
            long target = Math.max(1, (long) Math.ceil(sampleCount * percentile));
            long cumulative = 0;
            for (int bucket = 0; bucket < histogram.length; bucket++) {
                cumulative = Math.addExact(cumulative, histogram[bucket]);
                if (cumulative >= target) return bucket;
            }
            return histogram.length - 1;
        }

        private static final class ProductionBatch {
            final long producedAt;
            long remaining;

            ProductionBatch(long producedAt, long remaining) {
                this.producedAt = producedAt;
                this.remaining = remaining;
            }
        }
    }
}
