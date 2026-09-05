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
    private static final String EQUAL_LOAD_RECOVERY_SCENARIO = "equal-load-recovery";
    private static final String EQUAL_LOAD_PARTIAL_SCENARIO = "equal-load-partial-recovery";
    private static final String EQUAL_LOAD_SUSTAINED_SCENARIO = "equal-load-sustained";
    private static final int SUSTAINED_FIXED_PERIOD = 80;

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
        if (isDedicatedBenchmarkScenario()) {
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
                produceHighCardinalityBatch(fixture, state, tick);
            }
            if (!control && tick == recoveryTick) {
                require(state.blockerAmount > 0, "rejecting storage was not filled");
                releaseRejectingStorage(fixture, state.blockerAmount);
                state.recoveredAt = tick;
            }

            if (tick >= 40) {
                state.observeBacklog(fixture, tick);
                if (state.detailedLatencyDiagnostics) {
                    state.observe(fixture, tick);
                }
            }
            if (tick == finishTick) {
                state.finishObservation(fixture, tick);
                if (!control) {
                    require(state.producedItems > 0,
                            "high-cardinality producer never emitted a batch");
                    require(state.maxBufferedKeys >= 16_385,
                            "high-cardinality rejection never built a >16,384-key buffer: "
                                    + fixture.blockEntity.benchmarkWirelessIoState());
                    require(state.recoveredAt >= 0,
                            "high-cardinality storage recovery did not run");
                    assertFixtureResult(
                            fixture, state, 1.0, Integer.MAX_VALUE, HIGH_CARDINALITY_KEY_COUNT);
                }
                helper.succeed();
            }
        });
    }

    @GameTest(
            template = "wireless_io_empty",
            batch = "wireless_io_04_equal_load",
            timeoutTicks = 1500)
    public static void fastImportEqualLoadRecovery(GameTestHelper helper) {
        if (!isEqualLoadRecoveryScenario()) {
            helper.succeed();
            return;
        }

        var fixture = createFixture(helper, 1024, false);
        var state = new HighCardWorkloadState();
        int warmupTicks = Integer.getInteger(
                "ae2lt.wirelessIoBenchmark.warmupTicks", 200);
        int sampleTicks = Integer.getInteger(
                "ae2lt.wirelessIoBenchmark.sampleTicks", 1_200);
        int finishTick = warmupTicks + sampleTicks + 40;
        int productionTick = 80;
        int recoveryTick = warmupTicks + sampleTicks - 100;
        require(finishTick <= 1460,
                "GameTest warmup + sample must not exceed 1420 ticks");
        require(recoveryTick > productionTick + 40,
                "equal-load recovery must leave a nonempty rejection phase");

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 40) {
                require(fixture.blockEntity.getMainNode().isActive(),
                        "equal-load AE network did not become active");
                state.blockerAmount = isPartialEqualLoadScenario()
                        ? fillPartiallyRejectingStorage(fixture)
                        : fillRejectingStorage(fixture);
            }
            if (tick == productionTick) {
                produceHighCardinalityBatch(fixture, state, tick);
            }
            if (tick == recoveryTick) {
                require(state.blockerAmount > 0,
                        "equal-load rejecting storage was not filled");
                releaseRejectingStorage(fixture, state.blockerAmount);
                state.recoveredAt = tick;
            }
            if (tick >= 40) {
                state.observeBacklog(fixture, tick);
                if (state.detailedLatencyDiagnostics) {
                    state.observe(fixture, tick);
                }
            }
            if (tick == finishTick) {
                state.finishObservation(fixture, tick);
                require(state.producedItems == HIGH_CARDINALITY_KEY_COUNT * 64L,
                        "equal-load recovery changed its fixed production plan: "
                                + state.producedItems);
                require(state.recoveredAt == recoveryTick,
                        "equal-load recovery tick changed");
                require(state.minimumTargetThroughput() >= 1.0,
                        "equal-load recovery did not produce every planned target batch");
                assertFixtureResult(
                        fixture, state, 0.001, 2, HIGH_CARDINALITY_KEY_COUNT);
                helper.succeed();
            }
        });
    }

    @GameTest(
            template = "wireless_io_empty",
            batch = "wireless_io_05_equal_load",
            timeoutTicks = 1500)
    public static void fastImportEqualLoadSustained(GameTestHelper helper) {
        if (!isEqualLoadSustainedScenario()) {
            helper.succeed();
            return;
        }

        var fixture = createFixture(helper, 1024);
        var state = new HighCardWorkloadState();
        int warmupTicks = Integer.getInteger(
                "ae2lt.wirelessIoBenchmark.warmupTicks", 200);
        int sampleTicks = Integer.getInteger(
                "ae2lt.wirelessIoBenchmark.sampleTicks", 1_200);
        int finishTick = warmupTicks + sampleTicks + 40;
        require(finishTick <= 1460,
                "GameTest warmup + sample must not exceed 1420 ticks");

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 40) {
                require(fixture.blockEntity.getMainNode().isActive(),
                        "equal-load sustained AE network did not become active");
            }
            if (tick >= 80 && tick < finishTick - 40
                    && tick % SUSTAINED_FIXED_PERIOD == 0) {
                produceFixedKeyBatches(fixture, state, tick);
            }
            if (tick == finishTick) {
                state.finishObservation(fixture, tick);
                require(state.minimumWindowThroughput() >= 0.99,
                        "equal-load sustained window throughput was "
                                + state.minimumWindowThroughput());
                require(state.minimumTargetThroughput() >= 0.99,
                        "equal-load sustained worst-target throughput was "
                                + state.minimumTargetThroughput());
                require(state.blockedProductionEvents == 0,
                        "equal-load sustained producer encountered source backpressure: "
                                + state.blockedProductionEvents);
                assertFixtureResult(fixture, state, 0.001, 2, ITEMS_PER_TARGET);
                helper.succeed();
            }
        });
    }

    @GameTest(
            template = "wireless_io_empty",
            batch = "wireless_io_02_transitions",
            timeoutTicks = 260)
    public static void fastImportOutOfOrderTargetAttribution(GameTestHelper helper) {
        // This is a diagnostic-only integration check. It deliberately keeps
        // one early target disconnected while a later target remains usable,
        // proving that target/key observations cannot be treated as global
        // FIFO completion events.
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = createFixture(helper, 2);
        var tracker = new TargetKeyLatencyTracker(2, 1, 64);
        var level = helper.getLevel();
        var target0 = helper.absolutePos(new BlockPos(TARGET_ORIGIN, 1, TARGET_ORIGIN));
        var target1 = helper.absolutePos(new BlockPos(TARGET_ORIGIN + 1, 1, TARGET_ORIGIN));

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 40) {
                require(fixture.blockEntity.removeConnection(
                                level.dimension(), target0, Direction.UP),
                        "early target connection was not removed");
                produceSingleKeyBatch(fixture.inventories[0]);
                tracker.recordProduction(tick, 0);
            }
            if (tick == 41) {
                produceSingleKeyBatch(fixture.inventories[1]);
                tracker.recordProduction(tick, 1);
            }
            if (tick == 60) {
                // Exercise the real mode setter while the early target is
                // still blocked. wakeWirelessIo() must reset observation
                // state without dropping either target's output.
                fixture.blockEntity.setImportMode(ImportMode.OFF);
            }
            if (tick == 61) {
                fixture.blockEntity.setImportMode(ImportMode.AUTO);
            }
            if (tick >= 40) {
                tracker.observe(fixture.inventories, tick);
            }
            if (tick == 100) {
                require(fixture.blockEntity.addOrUpdateConnection(new WirelessConnection(
                                level.dimension(), target0, Direction.UP)),
                        "early target connection was not restored");
            }
            if (tick == 220) {
                require(tracker.completionTick(0) >= 0,
                        "early target did not complete after recovery");
                require(tracker.completionTick(1) >= 0,
                        "late target did not complete while reachable");
                require(tracker.completionTick(1) < tracker.completionTick(0),
                        "late target was not observed completing before the blocked early target");
                require(tracker.outOfOrderCompletions() > 0,
                        "target/key attribution did not record the out-of-order completion");
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

    private static boolean isDedicatedBenchmarkScenario() {
        String scenario = System.getProperty("ae2lt.wirelessIoBenchmark.scenario", "");
        return scenario.contains(HIGH_CARDINALITY_SCENARIO)
                || scenario.contains("equal-load");
    }

    private static boolean isEqualLoadRecoveryScenario() {
        String scenario = System.getProperty("ae2lt.wirelessIoBenchmark.scenario", "");
        return scenario.contains(EQUAL_LOAD_RECOVERY_SCENARIO)
                || scenario.contains(EQUAL_LOAD_PARTIAL_SCENARIO);
    }

    private static boolean isPartialEqualLoadScenario() {
        return System.getProperty("ae2lt.wirelessIoBenchmark.scenario", "")
                .contains(EQUAL_LOAD_PARTIAL_SCENARIO);
    }

    private static boolean isEqualLoadSustainedScenario() {
        return System.getProperty("ae2lt.wirelessIoBenchmark.scenario", "")
                .contains(EQUAL_LOAD_SUSTAINED_SCENARIO);
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

    private static long fillPartiallyRejectingStorage(Fixture fixture) {
        long accepted = fillRejectingStorage(fixture);
        long free = Math.min(4_096L, Math.max(1L, accepted / 16L));
        var grid = fixture.blockEntity.getMainNode().getGrid();
        require(grid != null, "partial rejecting grid disappeared after fill");
        long released = grid.getStorageService().getInventory().extract(
                requireItemKey(new ItemStack(Items.DIRT)), free,
                Actionable.MODULATE, IActionSource.empty());
        require(released == free,
                "partial rejecting storage did not leave its planned free capacity");
        return accepted - released;
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
        // Keep the finite cell in slot 0. A partial-receive run may already
        // have stored a few high-cardinality keys in it; replacing the item
        // directly would discard those accepted items and make the ownership
        // assertion look like a transport loss. Slot 1 gives the recovered
        // buffer an infinite destination while preserving the fixed initial
        // cell and every item it accepted before recovery.
        var remainder = fixture.drive.getInternalInventory().insertItem(
                1, new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()), false);
        require(remainder.isEmpty(),
                "recovery could not mount the infinite cell in the empty second slot");
    }

    private static long produceHighCardinalityBatch(
            Fixture fixture, HighCardWorkloadState state, long tick) {
        long produced = 0;
        for (int index = 0; index < fixture.inventories.length; index++) {
            var inventory = fixture.inventories[index];
            state.recordOpportunity(tick, index);
            if (!inventory.isEmpty()) {
                state.recordBlocked(index);
                continue;
            }
            for (int slot = 0; slot < ITEMS_PER_TARGET; slot++) {
                inventory.setItem(slot,
                        highCardinalityStack(index * ITEMS_PER_TARGET + slot));
            }
            inventory.setChanged();
            state.recordProduction(tick, index, ITEMS_PER_BATCH);
            produced += ITEMS_PER_BATCH;
        }
        return produced;
    }

    private static void produceFixedKeyBatches(
            Fixture fixture, HighCardWorkloadState state, long tick) {
        for (int index = 0; index < fixture.inventories.length; index++) {
            var inventory = fixture.inventories[index];
            state.recordOpportunity(tick, index);
            if (!inventory.isEmpty()) {
                state.recordBlocked(index);
                continue;
            }
            for (int slot = 0; slot < DISTINCT_ITEMS.size(); slot++) {
                inventory.setItem(slot, new ItemStack(DISTINCT_ITEMS.get(slot), 64));
            }
            inventory.setChanged();
            state.recordProduction(tick, index, ITEMS_PER_BATCH);
        }
    }

    private static void produceSingleKeyBatch(Container inventory) {
        require(inventory.isEmpty(), "out-of-order target was not empty before production");
        inventory.setItem(0, new ItemStack(Items.STONE, 64));
        inventory.setChanged();
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
                        + ", remaining=" + remaining + ", networkKeys="
                        + networkStacks.size() + ", expectedNetworkKeys="
                        + expectedNetworkKeys + ", maxBufferedKeys="
                        + (state instanceof HighCardWorkloadState high
                                ? high.maxBufferedKeys : -1));
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

        final boolean detailedLatencyDiagnostics = Boolean.getBoolean(
                "ae2lt.wirelessIoBenchmark.diagnostics");
        long blockerAmount;
        long recoveredAt = -1;
        long bufferDrainTick = -1;
        long maxBufferedItems = -1;
        int maxBufferedKeys;
        long plannedProductionItems;
        long blockedProductionEvents;
        long productionOpportunities;
        long lastNetworkImported;
        long extractedItems;
        long networkLatencySamples;
        long networkLatencyMax = -1;
        final TargetKeyLatencyTracker targetKeyLatency;
        final long[] networkLatencyHistogram = new long[LATENCY_HISTOGRAM_SIZE];
        final ArrayDeque<ProductionBatch> networkPending;
        final long[] plannedByTarget = new long[1024];
        final long[] actualByTarget = new long[1024];
        final long[] plannedByWindow = new long[32];
        final long[] actualByWindow = new long[32];

        HighCardWorkloadState() {
            super(1024);
            targetKeyLatency = detailedLatencyDiagnostics
                    ? new TargetKeyLatencyTracker(1024, ITEMS_PER_TARGET, 64)
                    : null;
            networkPending = detailedLatencyDiagnostics ? new ArrayDeque<>() : null;
        }

        void recordOpportunity(long tick, int targetIndex) {
            require(targetIndex >= 0 && targetIndex < plannedByTarget.length,
                    "invalid planned target index " + targetIndex);
            plannedProductionItems = Math.addExact(plannedProductionItems, ITEMS_PER_BATCH);
            productionOpportunities++;
            plannedByTarget[targetIndex]++;
            plannedByWindow[windowFor(tick)]++;
            opportunities++;
        }

        void recordBlocked(int targetIndex) {
            blockedProductionEvents++;
            blocked++;
            blockedStreak[targetIndex]++;
            maximumBlockedStreak = Math.max(maximumBlockedStreak,
                    blockedStreak[targetIndex]);
        }

        void recordProduction(long tick, int targetIndex, long amount) {
            require(amount > 0, "high-cardinality producer emitted no items");
            producedItems = Math.addExact(producedItems, amount);
            actualByTarget[targetIndex]++;
            actualByWindow[windowFor(tick)]++;
            blockedStreak[targetIndex] = 0;
            if (detailedLatencyDiagnostics) {
                targetKeyLatency.recordProduction(tick, targetIndex);
                networkPending.addLast(new ProductionBatch(tick, amount));
            }
        }

        void observeBacklog(Fixture fixture, long tick) {
            int bufferedKeys = fixture.blockEntity.benchmarkBufferedImportKeys();
            maxBufferedKeys = Math.max(maxBufferedKeys, bufferedKeys);
            if (recoveredAt >= 0 && bufferDrainTick < 0 && bufferedKeys == 0
                    && producedItems > 0) {
                bufferDrainTick = tick;
            }
        }

        void observe(Fixture fixture, long tick) {
            require(detailedLatencyDiagnostics,
                    "per-target observation must not run in a formal timing mode");
            long buffered = fixture.blockEntity.benchmarkBufferedImportAmount();
            targetKeyLatency.observe(fixture.inventories, tick);
            // observe() returns the delta for callers that need an event
            // count. Ownership counters here are cumulative, so use the
            // tracker's total; otherwise an idle observation tick would make
            // extracted appear to fall back to zero while the buffer still
            // legitimately held the previously observed output.
            long extracted = targetKeyLatency.extractedItems();
            long networkImported = extracted - buffered;
            require(extracted >= 0 && networkImported >= 0,
                    "high-cardinality ownership counters went negative: produced="
                                + producedItems + ", extracted=" + extracted
                                + ", buffered=" + buffered + ", network=" + networkImported
                                + ", tick=" + tick);
            extractedItems = extracted;
            maxBufferedItems = Math.max(maxBufferedItems, buffered);
            maxBufferedKeys = Math.max(
                    maxBufferedKeys, fixture.blockEntity.benchmarkBufferedImportKeys());
            long networkDelta = networkImported - lastNetworkImported;
            networkLatencyMax = Math.max(networkLatencyMax,
                    recordNetworkLatency(networkDelta, tick));
            lastNetworkImported = networkImported;
        }

        void finishObservation(Fixture fixture, long tick) {
            if (detailedLatencyDiagnostics) {
                observe(fixture, tick);
            }

            long remaining = remainingItems(fixture);
            long buffered = fixture.blockEntity.benchmarkBufferedImportAmount();
            long extracted = producedItems - remaining;
            long networkImported = extracted - buffered;
            require(extracted >= 0 && networkImported >= 0,
                    "high-cardinality ownership counters went negative: produced="
                            + producedItems + ", extracted=" + extracted
                            + ", buffered=" + buffered + ", network=" + networkImported
                            + ", tick=" + tick);
            observeBacklog(fixture, tick);
            WirelessIoPerformanceProbe.recordProductionPlan(
                    plannedProductionItems, producedItems, blockedProductionEvents,
                    productionOpportunities, minimumWindowThroughput(),
                    minimumTargetThroughput(), recoveredAt, bufferDrainTick, remaining);
            if (detailedLatencyDiagnostics) {
                require(targetKeyLatency.extractedItems() == extracted,
                        "target/key observations did not account for every extraction: observed="
                                + targetKeyLatency.extractedItems() + ", aggregate=" + extracted);
                extractedItems = extracted;
                lastNetworkImported = networkImported;
                WirelessIoPerformanceProbe.recordImportWorkload(
                        producedItems, extracted, networkImported, buffered, maxBufferedItems,
                        maxBufferedKeys,
                        "target-key-observed", targetKeyLatency.latencySamples(),
                        targetKeyLatency.percentile(0.50),
                        targetKeyLatency.percentile(0.95),
                        targetKeyLatency.percentile(0.99), targetKeyLatency.latencyMax(),
                        targetKeyLatency.pendingBatches(), targetKeyLatency.maxPendingWait(),
                        "aggregate-delta-fifo-estimate", networkLatencySamples,
                        networkPercentile(0.50), networkPercentile(0.95),
                        networkPercentile(0.99),
                        networkLatencyMax, networkPending.size(),
                        maxPendingNetworkWait(tick));
            } else {
                WirelessIoPerformanceProbe.recordImportWorkload(
                        producedItems, extracted, networkImported, buffered, -1, maxBufferedKeys,
                        "not-recorded-formal", -1, -1, -1, -1, -1, -1, -1,
                        "not-recorded-formal", -1, -1, -1, -1, -1, -1, -1);
            }
        }

        private long networkPercentile(double percentile) {
            if (networkLatencySamples <= 0) return -1;
            long target = Math.max(1, (long) Math.ceil(networkLatencySamples * percentile));
            long cumulative = 0;
            for (int bucket = 0; bucket < networkLatencyHistogram.length; bucket++) {
                cumulative = Math.addExact(cumulative, networkLatencyHistogram[bucket]);
                if (cumulative >= target) return bucket;
            }
            return networkLatencyHistogram.length - 1;
        }

        private long recordNetworkLatency(long count, long tick) {
            if (count <= 0) return -1;
            long remaining = count;
            long maxLatency = -1;
            while (remaining > 0 && !networkPending.isEmpty()) {
                var batch = networkPending.peekFirst();
                long accepted = Math.min(remaining, batch.remaining);
                long latency = Math.max(0, tick - batch.producedAt);
                maxLatency = Math.max(maxLatency, latency);
                int bucket = (int) Math.min(latency, networkLatencyHistogram.length - 1);
                networkLatencyHistogram[bucket] = Math.addExact(
                        networkLatencyHistogram[bucket], accepted);
                batch.remaining -= accepted;
                remaining -= accepted;
                if (batch.remaining == 0) {
                    networkPending.removeFirst();
                }
            }
            require(remaining == 0,
                    "network latency estimate exceeded produced ownership: " + remaining);
            networkLatencySamples = Math.addExact(networkLatencySamples, count);
            return maxLatency;
        }

        private long maxPendingNetworkWait(long tick) {
            if (networkPending.isEmpty()) return -1;
            long oldest = Long.MAX_VALUE;
            for (var batch : networkPending) {
                oldest = Math.min(oldest, batch.producedAt);
            }
            return Math.max(0, tick - oldest);
        }

        private static int windowFor(long tick) {
            return Math.min(31, Math.max(0, Math.toIntExact(tick / 100)));
        }

        double minimumWindowThroughput() {
            double minimum = Double.POSITIVE_INFINITY;
            for (int window = 0; window < plannedByWindow.length; window++) {
                if (plannedByWindow[window] > 0) {
                    minimum = Math.min(minimum,
                            (double) actualByWindow[window] / plannedByWindow[window]);
                }
            }
            return Double.isFinite(minimum) ? minimum : -1.0;
        }

        double minimumTargetThroughput() {
            double minimum = Double.POSITIVE_INFINITY;
            for (int target = 0; target < plannedByTarget.length; target++) {
                if (plannedByTarget[target] > 0) {
                    minimum = Math.min(minimum,
                            (double) actualByTarget[target] / plannedByTarget[target]);
                }
            }
            return Double.isFinite(minimum) ? minimum : -1.0;
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

    /**
     * Diagnostic-only target/key ledger. It observes inventory deltas rather
     * than assigning aggregate deltas to a global FIFO. A later-produced
     * target can therefore complete before an older blocked target without
     * corrupting the buffer latency distribution.
     */
    private static final class TargetKeyLatencyTracker {
        private final int targetCount;
        private final int keyCount;
        private final int amountPerKey;
        private final long[] lastObserved;
        private final long[] producedAt;
        private final long[] batchProducedAt;
        private final long[] batchRemaining;
        private final long[] completionAt;
        private final long[] latencyHistogram = new long[2_048];
        private long extractedItems;
        private long latencySamples;
        private long latencyMax = -1;
        private long maxPendingWait = -1;
        private int pendingBatches;
        private long outOfOrderCompletions;

        TargetKeyLatencyTracker(int targetCount, int keyCount, int amountPerKey) {
            this.targetCount = targetCount;
            this.keyCount = keyCount;
            this.amountPerKey = amountPerKey;
            int entries = Math.multiplyExact(targetCount, keyCount);
            lastObserved = new long[entries];
            producedAt = new long[entries];
            java.util.Arrays.fill(producedAt, -1);
            batchProducedAt = new long[targetCount];
            batchRemaining = new long[targetCount];
            completionAt = new long[targetCount];
            java.util.Arrays.fill(batchProducedAt, -1);
            java.util.Arrays.fill(completionAt, -1);
        }

        void recordProduction(long tick, int targetIndex) {
            require(targetIndex >= 0 && targetIndex < targetCount,
                    "invalid target index " + targetIndex);
            long previousRemaining = batchRemaining[targetIndex];
            if (previousRemaining == 0) {
                batchProducedAt[targetIndex] = tick;
                completionAt[targetIndex] = -1;
                pendingBatches++;
            }
            long base = (long) targetIndex * keyCount;
            for (int key = 0; key < keyCount; key++) {
                int index = Math.toIntExact(base + key);
                long previous = lastObserved[index];
                lastObserved[index] = Math.addExact(previous, amountPerKey);
                producedAt[index] = previous > 0
                        ? Math.min(producedAt[index], tick) : tick;
                batchRemaining[targetIndex] = Math.addExact(
                        batchRemaining[targetIndex], amountPerKey);
            }
        }

        long observe(Container[] inventories, long tick) {
            require(inventories.length == targetCount,
                    "target count changed during latency observation");
            long observedDelta = 0;
            for (int target = 0; target < targetCount; target++) {
                long base = (long) target * keyCount;
                for (int key = 0; key < keyCount; key++) {
                    int index = Math.toIntExact(base + key);
                    long current = Math.max(0L, inventories[target].getItem(key).getCount());
                    long previous = lastObserved[index];
                    if (current < previous) {
                        long delta = previous - current;
                        long produced = producedAt[index];
                        require(produced >= 0,
                                "observed extraction has no production timestamp");
                        long latency = Math.max(0, tick - produced);
                        int bucket = (int) Math.min(latency, latencyHistogram.length - 1);
                        latencyHistogram[bucket] = Math.addExact(
                                latencyHistogram[bucket], delta);
                        latencySamples = Math.addExact(latencySamples, delta);
                        extractedItems = Math.addExact(extractedItems, delta);
                        observedDelta = Math.addExact(observedDelta, delta);
                        latencyMax = Math.max(latencyMax, latency);
                        batchRemaining[target] = Math.max(0,
                                batchRemaining[target] - delta);
                    }
                    lastObserved[index] = current;
                }
                if (batchRemaining[target] > 0) {
                    maxPendingWait = Math.max(maxPendingWait,
                            Math.max(0, tick - batchProducedAt[target]));
                } else if (batchProducedAt[target] >= 0 && completionAt[target] < 0) {
                    completionAt[target] = tick;
                    pendingBatches = Math.max(0, pendingBatches - 1);
                    for (int earlier = 0; earlier < targetCount; earlier++) {
                        if (earlier != target && batchRemaining[earlier] > 0
                                && batchProducedAt[earlier] < batchProducedAt[target]) {
                            outOfOrderCompletions++;
                            break;
                        }
                    }
                }
            }
            return observedDelta;
        }

        long extractedItems() {
            return extractedItems;
        }

        long latencySamples() {
            return latencySamples;
        }

        long latencyMax() {
            return latencyMax;
        }

        long percentile(double percentile) {
            if (latencySamples <= 0) return -1;
            long target = Math.max(1, (long) Math.ceil(latencySamples * percentile));
            long cumulative = 0;
            for (int bucket = 0; bucket < latencyHistogram.length; bucket++) {
                cumulative = Math.addExact(cumulative, latencyHistogram[bucket]);
                if (cumulative >= target) return bucket;
            }
            return latencyHistogram.length - 1;
        }

        int pendingBatches() {
            return pendingBatches;
        }

        long maxPendingWait() {
            return maxPendingWait;
        }

        long completionTick(int targetIndex) {
            return completionAt[targetIndex];
        }

        long outOfOrderCompletions() {
            return outOfOrderCompletions;
        }

    }
}
