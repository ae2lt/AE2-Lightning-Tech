package com.moakiee.ae2lt.blockentity;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
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
import appeng.api.stacks.AEKeyType;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.IOSpeedMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ImportMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.InterfaceMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
/** Real AE network regressions for the I/O port; excluded from published jars. */
@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class OverloadedInterfaceIoGameTests {
    private static final BlockPos INTERFACE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 1);
    private static final BlockPos DRIVE_POS = new BlockPos(0, 1, 1);
    private static final int TARGET_ORIGIN = 4;
    private static final int TARGET_STRIDE = 32;

    private OverloadedInterfaceIoGameTests() {}

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_02_exact_filter", timeoutTicks = 180)
    public static void fastWirelessExactImportRespectsExportExclusion(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkExactImportExclusion(helper, false);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_02_exact_filter", timeoutTicks = 180)
    public static void fastLocalExactImportRespectsExportExclusion(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkExactImportExclusion(helper, true);
    }

    private static void checkExactImportExclusion(GameTestHelper helper, boolean local) {
        var fixture = local ? createLocalFixture(helper, true) : createFixture(helper, 1);
        var owner = fixture.blockEntity;
        var target = fixture.inventories[0];
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var granite = AEItemKey.of(Items.GRANITE);
        var cobble = AEItemKey.of(Items.COBBLESTONE);
        var named = new ItemStack(Items.STONE, 64);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("exact-import-variant"));
        var namedStone = requireItemKey(named);
        long[] resumed = {-1};

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40) {
                owner.getInterfaceLogic().getConfig().setStack(0, new appeng.api.stacks.GenericStack(stone, 64));
                installExactFilter(owner, List.of(stone, namedStone, dirt));
                target.setItem(0, new ItemStack(Items.STONE, 64));
                target.setItem(1, named.copy());
                target.setItem(2, new ItemStack(Items.DIRT, 64));
            }
            if (tick > 40 && tick < 70) {
                require(target.getItem(0).getCount() == 64,
                        "exact whitelist recycled an export-configured key; local=" + local + ", tick=" + tick);
            }
            if (tick == 60) {
                require(storedAmount(storage, namedStone) == 64 && storedAmount(storage, dirt) == 64,
                        "export exclusion blocked a distinct component or an allowed key");
                require(storedAmount(storage, stone) == 0, "excluded key entered ME");
            }
            if (tick == 70) {
                owner.getInterfaceLogic().getConfig().setStack(0, new appeng.api.stacks.GenericStack(dirt, 64));
                target.setItem(2, new ItemStack(Items.DIRT, 64));
            }
            if (tick > 70 && resumed[0] < 0 && target.getItem(0).isEmpty()) resumed[0] = tick;
            if (tick == 85) {
                require(resumed[0] >= 0 && resumed[0] - 70 <= 6, "config change did not promptly resume import");
                require(storedAmount(storage, stone) == 64, "old export exclusion survived a config change");
            }
            if (tick > 70 && tick < 130) {
                require(target.getItem(2).getCount() == 64, "new export exclusion was not applied");
            }
            if (tick == 90) {
                installExactFilter(owner, List.of(granite));
                target.setItem(3, new ItemStack(Items.GRANITE, 64));
            }
            if (tick == 105) require(storedAmount(storage, granite) == 64, "replacement filter stayed stale");
            if (tick == 110) {
                owner.getFilterInv().setItemDirect(0, ItemStack.EMPTY);
                target.setItem(4, new ItemStack(Items.COBBLESTONE, 64));
            }
            if (tick == 125) require(storedAmount(storage, cobble) == 64, "removed filter still limited import");
            if (tick == 130) owner.getInterfaceLogic().getConfig().setStack(0, null);
            if (tick == 150) {
                require(target.isEmpty() && bufferedAmount(owner) == 0, "filter test did not drain");
                require(storedAmount(storage, stone) == 64 && storedAmount(storage, namedStone) == 64
                                && storedAmount(storage, dirt) == 128 && storedAmount(storage, granite) == 64
                                && storedAmount(storage, cobble) == 64,
                        "filter/config changes did not conserve all 384 items by key");
                org.slf4j.LoggerFactory.getLogger("ae2lt-wireless-io-test").info(
                        "Exact import exclusion local={}: configResumeWait={}, produced=384, network=384",
                        local, resumed[0] - 70);
                helper.succeed();
            }
        });
    }

    private static void installExactFilter(OverloadedInterfaceBlockEntity owner, List<AEItemKey> keys) {
        installImportFilter(owner, keys);
    }

    private static void installImportFilter(OverloadedInterfaceBlockEntity owner, List<AEItemKey> keys,
                                            ItemStack... upgrades) {
        var stack = new ItemStack(ModItems.OVERLOADED_FILTER_COMPONENT.get());
        var item = (appeng.api.storage.cells.ICellWorkbenchItem) stack.getItem();
        var config = item.getConfigInventory(stack);
        for (int slot = 0; slot < keys.size(); slot++) {
            config.setStack(slot, new appeng.api.stacks.GenericStack(keys.get(slot), 1));
        }
        for (int slot = 0; slot < upgrades.length; slot++) {
            item.getUpgrades(stack).setItemDirect(slot, upgrades[slot]);
        }
        owner.getFilterInv().setItemDirect(0, stack);
    }

    private static long storedAmount(appeng.api.storage.MEStorage storage, AEItemKey key) {
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_02_exact_plan", timeoutTicks = 150)
    public static void fastWirelessExcludedImportStopsAndWakes(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkExcludedImportStopsAndWakes(helper, false);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_02_exact_plan", timeoutTicks = 150)
    public static void fastLocalExcludedImportStopsAndWakes(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkExcludedImportStopsAndWakes(helper, true);
    }

    private static void checkExcludedImportStopsAndWakes(GameTestHelper helper, boolean local) {
        var fixture = local ? createLocalFixture(helper, true) : createFixture(helper, 1);
        var owner = fixture.blockEntity;
        var target = fixture.inventories[0];
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        long[] calls = {0};
        long[] resumed = {-1, -1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            if (tick == 40) {
                owner.getInterfaceLogic().getConfig().setStack(0, new appeng.api.stacks.GenericStack(stone, 64));
                installExactFilter(owner, List.of(stone));
                countExactExtractCalls(helper, fixture, local, calls);
            }
            if (tick == 55) {
                org.slf4j.LoggerFactory.getLogger("ae2lt-wireless-io-test").info(
                        "Excluded exact import local={}: observedTicks=15, wrapperExtractCalls={}, hasIoWork={}",
                        local, calls[0], owner.hasGridItemIoWork());
                require(calls[0] == 0, "excluded exact keys still probed remote storage " + calls[0] + " times");
                require(!owner.hasGridItemIoWork(), "empty exact plan kept urgent I/O alive");
            }
            if (tick == 70) {
                installExactFilter(owner, List.of(dirt));
                target.setItem(0, new ItemStack(Items.DIRT, 64));
                require(owner.hasGridItemIoWork(), "filter change did not restore I/O eligibility");
            }
            if (tick > 70 && resumed[0] < 0 && target.isEmpty()) resumed[0] = tick;
            if (tick == 90) {
                require(resumed[0] >= 0 && resumed[0] - 70 <= 6, "filter wake exceeded its deadline");
                require(storedAmount(owner.getMainNode().getGrid().getStorageService().getInventory(), dirt) == 64,
                        "filter wake did not deliver dirt to ME");
            }
            if (tick == 100) {
                installExactFilter(owner, List.of(stone));
                owner.getInterfaceLogic().getConfig().setStack(0, null);
                target.setItem(0, new ItemStack(Items.STONE, 64));
            }
            if (tick > 100 && resumed[1] < 0 && target.isEmpty()) resumed[1] = tick;
            if (tick == 125) {
                var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
                require(resumed[1] >= 0 && resumed[1] - 100 <= 6, "config wake exceeded its deadline");
                require(storedAmount(storage, stone) == 64 && storedAmount(storage, dirt) == 64
                                && bufferedAmount(owner) == 0,
                        "excluded-plan recovery did not conserve 128 items");
                org.slf4j.LoggerFactory.getLogger("ae2lt-wireless-io-test").info(
                        "Excluded exact import wake local={}: filterWait={}, configWait={}, produced=128, network=128",
                        local, resumed[0] - 70, resumed[1] - 100);
                helper.succeed();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void countExactExtractCalls(GameTestHelper helper, Fixture fixture, boolean local, long[] calls) {
        var level = helper.getLevel();
        var pos = ((net.minecraft.world.level.block.entity.BlockEntity) fixture.inventories[0]).getBlockPos();
        var connection = new WirelessConnection(level.dimension(), pos, local ? Direction.NORTH : Direction.UP);
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        var wrappers = state.resolveWrappers(level, connection);
        require(wrappers != null && wrappers.containsKey(AEKeyType.items()), "missing measured item wrapper");
        var delegate = wrappers.get(AEKeyType.items());
        wrappers.put(AEKeyType.items(), new appeng.api.storage.MEStorage() {
            @Override
            public long extract(appeng.api.stacks.AEKey key, long amount, Actionable mode, IActionSource source) {
                calls[0]++;
                return delegate.extract(key, amount, mode, source);
            }

            @Override
            public Component getDescription() {
                return delegate.getDescription();
            }
        });
        try {
            var field = OverloadedInterfaceBlockEntity.class.getDeclaredField(
                    local ? "normalConnectionStates" : "connectionStates");
            field.setAccessible(true);
            var states = (Map<Object, OverloadedInterfaceBlockEntity.ConnectionState>) field.get(fixture.blockEntity);
            states.put(local ? Direction.SOUTH : connection, state);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("cannot install the 15-tick storage call counter", exception);
        }
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_02_exact_plan", timeoutTicks = 180)
    public static void exactImportPlanKeepsBufferFlushAndExport(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = createFixture(helper, 1, false);
        var owner = fixture.blockEntity;
        var target = fixture.inventories[0];
        var stone = AEItemKey.of(Items.STONE);
        long[] blocker = {0};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40) {
                blocker[0] = fillRejectingStorage(fixture);
                installExactFilter(owner, List.of(stone));
                target.setItem(0, new ItemStack(Items.STONE, 64));
            }
            if (tick == 80) {
                require(bufferedAmount(owner) == 64 && target.isEmpty(), "rejection did not buffer 64 items");
                owner.getInterfaceLogic().getConfig().setStack(0, new appeng.api.stacks.GenericStack(stone, 64));
                target.setItem(0, new ItemStack(Items.STONE, 64));
                require(owner.hasGridItemIoWork(), "empty import plan stranded its owned buffer");
            }
            if (tick == 90) releaseRejectingStorage(fixture, blocker[0]);
            if (tick == 105) {
                require(storedAmount(storage, stone) == 64 && remainingItems(fixture) == 64
                                && bufferedAmount(owner) == 0,
                        "empty-plan flush changed source/buffer/ME ownership");
                require(!owner.hasGridItemIoWork(), "drained empty plan did not stop importing");
            }
            if (tick == 110) owner.setExportMode(ExportMode.AUTO);
            if (tick == 130) {
                require(storedAmount(storage, stone) == 0 && remainingItems(fixture) == 128
                                && bufferedAmount(owner) == 0,
                        "excluded import disabled export or recycled its output");
            }
            if (tick == 140) {
                owner.setExportMode(ExportMode.OFF);
                owner.getInterfaceLogic().getConfig().setStack(0, null);
            }
            if (tick == 165) {
                require(storedAmount(storage, stone) == 128 && target.isEmpty()
                                && bufferedAmount(owner) == 0,
                        "resumed exact plan did not conserve 128 items");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_02_exact_plan", timeoutTicks = 140)
    public static void exactImportPlanDoesNotPruneFuzzyOrInvertedFilters(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = createFixture(helper, 1);
        var owner = fixture.blockEntity;
        var target = fixture.inventories[0];
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var cobble = AEItemKey.of(Items.COBBLESTONE);
        var named = new ItemStack(Items.STONE, 64);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("fuzzy-allowed-variant"));
        var namedStone = requireItemKey(named);
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40) {
                owner.getInterfaceLogic().getConfig().setStack(0, new appeng.api.stacks.GenericStack(stone, 64));
                installExactFilter(owner, List.of(stone));
                require(!owner.hasGridItemIoWork(), "exact plan was not empty before fuzzy transition");
            }
            if (tick == 60) {
                installImportFilter(owner, List.of(stone), AEItems.FUZZY_CARD.stack());
                target.setItem(0, named.copy());
                target.setItem(1, new ItemStack(Items.DIRT, 64));
                require(owner.hasGridItemIoWork(), "fuzzy filter inherited an empty exact plan");
            }
            if (tick == 80) {
                require(storedAmount(storage, namedStone) == 64 && target.getItem(1).getCount() == 64,
                        "fuzzy filter changed component or item matching");
            }
            if (tick == 90) {
                installImportFilter(owner, List.of(stone), AEItems.INVERTER_CARD.stack());
                target.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
                require(owner.hasGridItemIoWork(), "inverted filter inherited an empty exact plan");
            }
            if (tick == 120) {
                require(storedAmount(storage, namedStone) == 64 && storedAmount(storage, dirt) == 64
                                && storedAmount(storage, cobble) == 64 && target.isEmpty()
                                && bufferedAmount(owner) == 0,
                        "fuzzy/inverted transitions did not conserve 192 items");
                helper.succeed();
            }
        });
    }


    @GameTest(
            template = "wireless_io_empty",
            batch = "wireless_io_02_transitions",
            timeoutTicks = 260)
    public static void fastImportBufferSurvivesSaveAndReload(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = createFixture(helper, 1, false);
        var stacks = List.of(highCardinalityStack(2_000_001),
                highCardinalityStack(2_000_002), new ItemStack(Items.STONE, 64));
        var keys = stacks.stream().map(OverloadedInterfaceIoGameTests::requireItemKey).toList();
        long[] blocker = {0};
        boolean[] reloaded = {false};

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 40) {
                blocker[0] = fillRejectingStorage(fixture);
                for (int slot = 0; slot < stacks.size(); slot++) {
                    fixture.inventories[0].setItem(slot, stacks.get(slot).copy());
                }
                fixture.inventories[0].setChanged();
            }
            if (tick == 90) {
                require(bufferedAmount(fixture.blockEntity) == 192,
                        "save/reload fixture did not retain all rejected items");
                require(remainingItems(fixture) == 0, "save/reload source was not drained");
                var tag = new net.minecraft.nbt.CompoundTag();
                var registries = helper.getLevel().registryAccess();
                fixture.blockEntity.saveAdditional(tag, registries);
                fixture.blockEntity.clearImportBuffer();
                require(bufferedAmount(fixture.blockEntity) == 0,
                        "clearImportBuffer retained ownership");
                fixture.blockEntity.loadTag(tag, registries);
                require(bufferedAmount(fixture.blockEntity) == 192,
                        "reload lost or duplicated retained amounts");
                require(bufferForTest(fixture.blockEntity).size() == 3,
                        "reload merged different item components");
                reloaded[0] = true;
            }
            if (tick == 120) {
                releaseRejectingStorage(fixture, blocker[0]);
            }
            if (tick == 200) {
                require(reloaded[0], "save/reload checkpoint was not reached");
                require(remainingItems(fixture) == 0, "reload left items in the source");
                require(bufferedAmount(fixture.blockEntity) == 0,
                        "reloaded buffer did not drain after storage recovered");
                var storage = fixture.blockEntity.getMainNode().getGrid()
                        .getStorageService().getInventory();
                for (var key : keys) {
                    require(storage.extract(key, Long.MAX_VALUE,
                                    Actionable.SIMULATE, IActionSource.empty()) == 64,
                            "save/reload changed the amount or components of " + key);
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_06_recovery", timeoutTicks = 240)
    public static void fastWirelessImportResumesAfterStorageRecovery(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkImportStorageRecovery(helper, false, false);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_06_recovery", timeoutTicks = 240)
    public static void fastLocalImportResumesAfterStorageRecovery(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkImportStorageRecovery(helper, true, false);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_06_recovery", timeoutTicks = 240)
    public static void fastWirelessImportRestartsAfterIdle(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkImportStorageRecovery(helper, false, true);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_06_recovery", timeoutTicks = 240)
    public static void fastLocalImportRestartsAfterIdle(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkImportStorageRecovery(helper, true, true);
    }

    private static void checkImportStorageRecovery(GameTestHelper helper, boolean local, boolean idleRestart) {
        var fixture = local ? createLocalFixture(helper, false) : createFixture(helper, 1, false);
        var owner = fixture.blockEntity;
        var target = fixture.inventories[0];
        var observation = new ImportRecoveryObservation();
        Map<AEKeyType, Long> locks = importLocksForTest(owner);
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            var grid = owner.getMainNode().getGrid();
            require(grid != null, "recovery test lost its grid");
            var storage = grid.getStorageService().getInventory();
            if (tick == 40) observation.blocker = fillRejectingStorage(fixture);
            if (tick == 80) {
                produceSingleKeyBatch(target);
                observation.produced += 64;
            }
            long lock = locks.getOrDefault(AEKeyType.items(), 0L);
            if (tick > 80 && observation.recovered < 0 && target.isEmpty()
                    && bufferedAmount(owner) == 64
                    && lock > helper.getLevel().getGameTime() + 5) {
                observation.recovered = tick;
                produceSingleKeyBatch(target);
                observation.produced += 64;
                require(storage.extract(dirt, observation.blocker,
                                Actionable.MODULATE, IActionSource.empty()) == observation.blocker,
                        "finite storage blocker was not conserved");
            }
            long stored = storage.extract(stone, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            if (observation.recovered >= 0) {
                // Buffer amount alone cannot identify the old batch: a prompt
                // refill may replace it in the very tick the old batch enters ME.
                if (observation.firstBatchInNetwork < 0 && stored >= 64) {
                    observation.firstBatchInNetwork = tick;
                }
                if (observation.sourceDrained < 0 && target.isEmpty()) observation.sourceDrained = tick;
                if (observation.secondBatchInNetwork < 0 && stored >= 128) {
                    observation.secondBatchInNetwork = tick;
                }
                // Exercise continuous recovery and the previously failing
                // 25-tick idle gap as separate workloads with the same strict gates.
                long hotStart = idleRestart ? observation.recovered + 25 : observation.sourceDrained + 1;
                if (observation.sourceDrained >= 0 && tick >= hotStart && tick < hotStart + 40) {
                    if (target.isEmpty()) {
                        produceSingleKeyBatch(target);
                        observation.produced += 64;
                    } else {
                        observation.hotBlocked++;
                    }
                }
            }
            require(stored + bufferedAmount(owner) + target.getItem(0).getCount()
                            == observation.produced,
                    "recovery changed per-tick item ownership");
            require(tick < 220, "recovery test did not finish");
            if (observation.recovered >= 0 && tick == observation.recovered + 75) {
                long outputWait = observation.sourceDrained - observation.recovered;
                long networkWait = observation.secondBatchInNetwork - observation.recovered;
                long resumeAfterCommit = observation.sourceDrained - observation.firstBatchInNetwork;
                org.slf4j.LoggerFactory.getLogger("ae2lt-wireless-io-test").info(
                        "Storage recovery local={}, idleRestart={}: recovered={}, oldBatchInNetwork={}, sourceDrained={}, "
                                + "outputWait={}, networkWait={}, resumeAfterCommit={}, hotBlocked={}, produced={}",
                        local, idleRestart, observation.recovered, observation.firstBatchInNetwork, observation.sourceDrained,
                        outputWait, networkWait, resumeAfterCommit, observation.hotBlocked, observation.produced);
                require(observation.sourceDrained >= 0 && outputWait <= 6, "source recovery wait " + outputWait);
                require(observation.secondBatchInNetwork >= 0 && networkWait <= 11,
                        "network recovery wait " + networkWait);
                require(resumeAfterCommit <= 1, "unlocked source waited " + resumeAfterCommit + " ticks");
                require(observation.hotBlocked == 0, "recovery reduced sustained throughput");
                require(observation.produced == 42L * 64 && stored == observation.produced,
                        "recovery did not finish the fixed production plan");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_07_refill", timeoutTicks = 300)
    public static void wirelessExportFillsOnceAndBatchesRefills(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkFullExport(helper, false);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_07_refill", timeoutTicks = 300)
    public static void localExportFillsOnceAndBatchesRefills(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        checkFullExport(helper, true);
    }

    private static void checkFullExport(GameTestHelper helper, boolean local) {
        var fixture = local ? createLocalFixture(helper, true) : createFixture(helper, 1);
        var owner = fixture.blockEntity;
        var target = fixture.inventories[0];
        var stone = AEItemKey.of(Items.STONE);
        long[] calls = {0}, consumed = {0};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40) {
                owner.setImportMode(ImportMode.OFF);
                owner.getInterfaceLogic().getConfig().setStack(0, new appeng.api.stacks.GenericStack(stone, 64));
                owner.setSlotUnlimited(0, true);
                owner.setExportMode(ExportMode.AUTO);
                require(storage.insert(stone, 100000, Actionable.MODULATE, IActionSource.empty()) == 100000,
                        "export fixture source was not stocked");
                countExportCalls(helper, fixture, local, calls);
            }
            if (tick == 43) {
                require(remainingItems(fixture) == 27 * 64,
                        "independent-key export did not fill the whole barrel in its first visit");
            }
            if (tick == 80) calls[0] = 0;
            if (tick >= 60 && tick < 260 && tick % 5 == 0) {
                int remaining = 64;
                for (int slot = 0; slot < target.getContainerSize() && remaining > 0; slot++) {
                    int count = Math.min(remaining, target.getItem(slot).getCount());
                    target.removeItem(slot, count);
                    remaining -= count;
                }
                require(remaining == 0, "refill timing left a processing opportunity idle at " + tick);
                consumed[0] += 64;
            }
            require(storedAmount(storage, stone) + remainingItems(fixture) + bufferedAmount(owner)
                            + consumed[0] == 100000,
                    "refill changed per-tick item ownership");
            if (tick == 260) {
                require(consumed[0] == 40 * 64, "export missed its fixed production plan");
                org.slf4j.LoggerFactory.getLogger("ae2lt-wireless-io-test").info(
                        "Full export local={}: consumed={}, measuredTicks=180, insertCalls={}",
                        local, consumed[0], calls[0]);
                require(calls[0] > 0 && calls[0] <= 60,
                        "180-tick refill window used too many physical calls: " + calls[0]);
                helper.succeed();
            }
        });
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_07_refill", timeoutTicks = 140)
    public static void missingExportKeyDoesNotBlockOtherKeys(GameTestHelper helper) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = createFixture(helper, 1);
        var owner = fixture.blockEntity;
        var target = fixture.inventories[0];
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 40) return;
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40) {
                owner.setImportMode(ImportMode.OFF);
                owner.getInterfaceLogic().getConfig().setStack(0, new appeng.api.stacks.GenericStack(stone, 64));
                owner.getInterfaceLogic().getConfig().setStack(1, new appeng.api.stacks.GenericStack(dirt, 64));
                owner.setSlotUnlimited(0, true);
                owner.setSlotUnlimited(1, true);
                owner.setExportMode(ExportMode.AUTO);
                require(storage.insert(dirt, 10000, Actionable.MODULATE, IActionSource.empty()) == 10000,
                        "independent-key fixture source was not stocked");
            }
            if (tick == 55) {
                require(remainingItems(fixture) == 1728 && target.getItem(0).is(Items.DIRT),
                        "missing stone blocked independently available dirt");
            }
            if (tick == 55) {
                require(storedAmount(storage, dirt) + remainingItems(fixture) == 10000
                                && storedAmount(storage, stone) == 0 && bufferedAmount(owner) == 0,
                        "independent export changed per-key ownership");
                helper.succeed();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void countExportCalls(GameTestHelper helper, Fixture fixture, boolean local, long[] calls) {
        var level = helper.getLevel();
        var pos = ((net.minecraft.world.level.block.entity.BlockEntity) fixture.inventories[0]).getBlockPos();
        var connection = new WirelessConnection(level.dimension(), pos, local ? Direction.NORTH : Direction.UP);
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        var delegate = state.resolveWrappers(level, connection).get(AEKeyType.items());
        // Keep the counter in the factory so normal 20-tick wrapper refreshes
        // cannot silently stop measuring the production path.
        state.storageStrategies = Map.of(AEKeyType.items(), (extractable, listener) -> new appeng.api.storage.MEStorage() {
            @Override
            public long insert(appeng.api.stacks.AEKey key, long amount, Actionable mode, IActionSource source) {
                calls[0]++;
                return delegate.insert(key, amount, mode, source);
            }

            @Override
            public Component getDescription() { return delegate.getDescription(); }
        });
        state.storageWrappers = null;
        try {
            var field = OverloadedInterfaceBlockEntity.class.getDeclaredField(
                    local ? "normalConnectionStates" : "connectionStates");
            field.setAccessible(true);
            var states = (Map<Object, OverloadedInterfaceBlockEntity.ConnectionState>) field.get(fixture.blockEntity);
            states.put(local ? Direction.SOUTH : connection, state);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("cannot install export call counter", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<AEKeyType, Long> importLocksForTest(OverloadedInterfaceBlockEntity owner) {
        try {
            var field = OverloadedInterfaceBlockEntity.class.getDeclaredField("keyTypeLockUntil");
            field.setAccessible(true);
            return (Map<AEKeyType, Long>) field.get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("cannot observe the import lock in the recovery fixture", exception);
        }
    }

    private static Fixture createLocalFixture(GameTestHelper helper, boolean infiniteCell) {
        var fixture = createFixture(helper, 1, infiniteCell);
        fixture.blockEntity.setInterfaceMode(InterfaceMode.NORMAL);
        fixture.blockEntity.setEnergyOutputDir(Direction.SOUTH);
        var targetPos = INTERFACE_POS.south();
        helper.setBlock(targetPos, Blocks.BARREL);
        var target = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(targetPos));
        return new Fixture(fixture.blockEntity, fixture.drive, new Container[] {target});
    }

    private static final class ImportRecoveryObservation {
        long blocker;
        long produced;
        long recovered = -1;
        long firstBatchInNetwork = -1;
        long sourceDrained = -1;
        long secondBatchInNetwork = -1;
        int hotBlocked;
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

    private static long bufferedAmount(OverloadedInterfaceBlockEntity owner) {
        return bufferForTest(owner).values().stream().mapToLong(Long::longValue).sum();
    }

    @SuppressWarnings("unchecked")
    private static Map<appeng.api.stacks.AEKey, Long> bufferForTest(OverloadedInterfaceBlockEntity owner) {
        try {
            var field = OverloadedInterfaceBlockEntity.class.getDeclaredField("importBuffer");
            field.setAccessible(true);
            return (Map<appeng.api.stacks.AEKey, Long>) field.get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("cannot observe retained item ownership", exception);
        }
    }
}
