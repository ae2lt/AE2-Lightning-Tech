package com.moakiee.ae2lt.blockentity;

import java.util.regex.Pattern;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ExportMode;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.ImportMode;
import com.moakiee.ae2lt.debug.WirelessIoPerformanceProbe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.LoggerFactory;

/** Real AUTO-export workloads, isolated from behavioral tests and import benchmarks. */
@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class WirelessInterfaceExportGameTests {
    private static final int CONFIGURE_TICK = 40;
    private static final int CONSUME_TICK = 80;
    private static final int UNITS_PER_KEY = 64;
    private static final Pattern SCENARIO = Pattern.compile(
            "gametest-(control|stress)-(export-(empty|mismatch|continuous)-(64|256|1024)(?:x(1|27))?)-run\\d+");

    private WirelessInterfaceExportGameTests() {}

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_01_export_benchmark", timeoutTicks = 1500)
    public static void fastWirelessExportBenchmark(GameTestHelper helper) {
        var scenario = System.getProperty("ae2lt.wirelessIoBenchmark.scenario", "");
        if (!Boolean.getBoolean("ae2lt.wirelessIoBenchmark") || !scenario.contains("-export-")) {
            helper.succeed();
            return;
        }
        var match = SCENARIO.matcher(scenario);
        require(match.matches(), "unknown export benchmark profile: " + scenario);
        require(Boolean.getBoolean("ae2lt.wirelessIoBenchmark.includeEligibility"),
                "export benchmarks must include the grid eligibility check in I/O timing");
        boolean control = Boolean.getBoolean("ae2lt.wirelessIoGameTest.control");
        require(control == match.group(1).equals("control"), "export role and actual control flag differ");
        String profile = match.group(2);
        String kind = match.group(3);
        int targets = Integer.parseInt(match.group(4));
        int itemKeys = match.group(5) == null ? 0 : Integer.parseInt(match.group(5));
        boolean continuous = kind.equals("continuous");
        require(continuous == (itemKeys > 0), "only continuous export has item keys");
        int configuredKeys = kind.equals("mismatch") ? 1 : itemKeys;
        int warmup = Integer.getInteger("ae2lt.wirelessIoBenchmark.warmupTicks", 200);
        int sample = Integer.getInteger("ae2lt.wirelessIoBenchmark.sampleTicks", 1200);
        int stopTick = warmup + sample;
        int finishTick = stopTick + 40;
        require(finishTick <= 1460 && stopTick - CONSUME_TICK >= 100, "invalid export sampling window");

        var fixture = WirelessInterfaceGameTests.createFixture(helper, targets);
        var owner = fixture.blockEntity();
        owner.setImportMode(ImportMode.OFF);
        owner.setExportMode(ExportMode.AUTO);
        var keys = new AEItemKey[itemKeys];
        for (int key = 0; key < itemKeys; key++) {
            keys[key] = AEItemKey.of(WirelessInterfaceGameTests.DISTINCT_ITEMS.get(key));
        }
        boolean consuming = continuous && !control;
        var observation = new ConsumerObservation(targets, stopTick - CONSUME_TICK);
        long supplyPerKey = (long) targets * UNITS_PER_KEY * (stopTick - CONFIGURE_TICK + 1);

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < CONFIGURE_TICK) return;
            require(owner.getMainNode().isActive(), "export fixture lost its active AE node");
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == CONFIGURE_TICK) {
                if (kind.equals("mismatch")) {
                    owner.getInterfaceLogic().getConfig().setStack(0,
                            new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
                }
                for (int key = 0; key < keys.length; key++) {
                    require(storage.insert(keys[key], supplyPerKey, Actionable.MODULATE, IActionSource.empty())
                            == supplyPerKey, "ME could not hold the fixed export supply");
                    owner.getInterfaceLogic().getConfig().setStack(key,
                            new GenericStack(keys[key], UNITS_PER_KEY));
                }
            }
            // Startup reads finish before the formal sample window.
            if (continuous && tick <= CONSUME_TICK && observation.startupWait < 0
                    && allReady(fixture.inventories(), keys)) {
                observation.startupWait = Math.toIntExact(tick - CONFIGURE_TICK);
            }
            if (tick == CONSUME_TICK && continuous) {
                require(observation.startupWait >= 0, "export never filled its initial inputs");
            }
            if (consuming && tick >= CONFIGURE_TICK && tick < stopTick) {
                observation.consume(fixture.inventories(), keys, Math.toIntExact(tick - CONSUME_TICK));
            }
            if (tick == finishTick) {
                finish(helper, fixture, storage, keys, profile, configuredKeys, consuming,
                        supplyPerKey, observation);
            }
        });
    }

    private static boolean ready(Container target, AEItemKey[] keys) {
        for (int slot = 0; slot < keys.length; slot++) {
            var stack = target.getItem(slot);
            if (stack.getCount() < UNITS_PER_KEY || !keys[slot].matches(stack)) return false;
        }
        return true;
    }

    private static boolean allReady(Container[] targets, AEItemKey[] keys) {
        for (var target : targets) if (!ready(target, keys)) return false;
        return true;
    }

    private static void finish(GameTestHelper helper, WirelessInterfaceGameTests.Fixture fixture, MEStorage storage,
                               AEItemKey[] keys, String profile, int configuredKeys, boolean consuming,
                               long supplyPerKey, ConsumerObservation observation) {
        var targetStacks = new KeyCounter();
        for (var target : fixture.inventories()) {
            for (int slot = 0; slot < target.getContainerSize(); slot++) {
                var stack = target.getItem(slot);
                if (!stack.isEmpty()) targetStacks.add(AEItemKey.of(stack), stack.getCount());
            }
        }
        long network = 0;
        long remaining = 0;
        for (var key : keys) {
            long stored = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            long inTargets = targetStacks.get(key);
            require(stored + inTargets + observation.completed * UNITS_PER_KEY == supplyPerKey,
                    "export ownership changed for " + key);
            network += stored;
            remaining += inTargets;
        }
        long buffered = fixture.blockEntity().benchmarkBufferedImportAmount();
        double minWindow = consuming ? observation.minimumWindow() : -1;
        double minTarget = consuming ? observation.minimumTarget() : -1;
        WirelessIoPerformanceProbe.recordExportWorkload(new WirelessIoPerformanceProbe.ExportWorkload(
                profile, fixture.inventories().length, configuredKeys, keys.length, UNITS_PER_KEY, consuming,
                supplyPerKey * keys.length, network, remaining, buffered, observation.planned, observation.completed,
                observation.starved, observation.maxStarveStreak, observation.steadyPlanned, observation.steadyCompleted,
                observation.steadyStarved, observation.steadyMaxStarveStreak, minWindow, minTarget, observation.startupWait));
        LoggerFactory.getLogger("ae2lt-wireless-io-test").info(
                "Export profile={}, consuming={}: planned={}, completed={}, starved={}, maxStarveStreak={}, "
                        + "steadyPlanned={}, steadyCompleted={}, steadyStarved={}, steadyMaxStarveStreak={}, "
                        + "minWindow={}, minTarget={}, startupWait={}, supplied={}, network={}, target={}, buffered={}",
                profile, consuming, observation.planned, observation.completed, observation.starved,
                observation.maxStarveStreak, observation.steadyPlanned, observation.steadyCompleted,
                observation.steadyStarved, observation.steadyMaxStarveStreak, minWindow, minTarget, observation.startupWait,
                supplyPerKey * keys.length, network, remaining, buffered);
        require(buffered == 0, "export fixture retained overflow after drain");
        require(targetStacks.size() == keys.length, "export target has unexpected or missing item keys");
        require(remaining == (long) fixture.inventories().length * keys.length * UNITS_PER_KEY,
                "export inputs were not refilled after the final consumption tick");
        if (consuming) {
            require(observation.steadyPlanned > 0 && (double) observation.steadyStarved / observation.steadyPlanned <= 0.001,
                    "export starvation exceeded 0.1% of the fixed consumption plan");
            require(observation.steadyMaxStarveStreak <= 2, "steady export starvation exceeded two consecutive ticks");
            require(minWindow >= 0.99 && minTarget >= 0.99, "export lost window throughput or target fairness");
        }
        helper.succeed();
    }

    private static final class ConsumerObservation {
        final int[] completedByTarget;
        final int[] starveStreak;
        final int[] completedByTick;
        long planned;
        long completed;
        long starved;
        int maxStarveStreak;
        long steadyPlanned;
        long steadyCompleted;
        long steadyStarved;
        int steadyMaxStarveStreak;
        int startupWait = -1;

        ConsumerObservation(int targets, int ticks) {
            completedByTarget = new int[targets];
            starveStreak = new int[targets];
            completedByTick = new int[ticks];
        }

        void consume(Container[] targets, AEItemKey[] keys, int tick) {
            planned += targets.length;
            if (tick >= 0) steadyPlanned += targets.length;
            for (int index = 0; index < targets.length; index++) {
                var target = targets[index];
                if (!ready(target, keys)) {
                    starved++;
                    maxStarveStreak = Math.max(maxStarveStreak, ++starveStreak[index]);
                    if (tick >= 0) {
                        steadyStarved++;
                        steadyMaxStarveStreak = Math.max(steadyMaxStarveStreak, Math.min(starveStreak[index], tick + 1));
                    }
                    continue;
                }
                for (int slot = 0; slot < keys.length; slot++) {
                    var taken = target.removeItem(slot, UNITS_PER_KEY);
                    require(taken.getCount() == UNITS_PER_KEY && keys[slot].matches(taken),
                            "consumer changed its atomic input batch");
                }
                target.setChanged();
                starveStreak[index] = 0;
                completed++;
                if (tick >= 0) {
                    steadyCompleted++;
                    completedByTarget[index]++;
                    completedByTick[tick]++;
                }
            }
        }

        double minimumWindow() {
            int window = 0;
            double minimum = 1;
            for (int tick = 0; tick < completedByTick.length; tick++) {
                window += completedByTick[tick];
                if (tick >= 100) window -= completedByTick[tick - 100];
                if (tick >= 99) minimum = Math.min(minimum, (double) window / (100 * completedByTarget.length));
            }
            return minimum;
        }

        double minimumTarget() {
            double minimum = 1;
            for (int completed : completedByTarget) {
                minimum = Math.min(minimum, (double) completed / completedByTick.length);
            }
            return minimum;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
