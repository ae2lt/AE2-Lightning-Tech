package com.moakiee.ae2lt.blockentity;

import java.util.Arrays;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.LoggerFactory;

/** Keep the accepted cold-polling budget separate from warm recovery throughput. */
@GameTestHolder("ae2lt_io")
@PrefixGameTestTemplate(false)
public final class OverloadedInterfaceImportRecoveryGameTests {
    private OverloadedInterfaceImportRecoveryGameTests() {}

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_07_import_recovery", timeoutTicks = 470)
    public static void wirelessWarmRecoveryKeepsProduction(GameTestHelper helper) {
        checkRecovery(helper, false);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_07_import_recovery", timeoutTicks = 470)
    public static void localWarmRecoveryKeepsProduction(GameTestHelper helper) {
        checkRecovery(helper, true);
    }

    private static void checkRecovery(GameTestHelper helper, boolean local) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = WirelessInterfaceGameTests.createFixture(helper, local ? 1 : 256);
        var owner = fixture.blockEntity();
        Container[] targets;
        if (local) {
            owner.setInterfaceMode(OverloadedInterfaceBlockEntity.InterfaceMode.NORMAL);
            owner.setEnergyOutputDir(Direction.SOUTH);
            var pos = new BlockPos(1, 1, 1).south();
            helper.setBlock(pos, Blocks.BARREL);
            targets = new Container[] {(Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos))};
        } else {
            targets = fixture.inventories();
        }
        var state = new RecoveryObservation(targets.length);
        helper.onEachTick(() -> {
            int tick = Math.toIntExact(helper.getTick());
            if (tick < 40) return;
            state.observe(targets, tick);
            boolean production = tick < 80 || tick == 160 || tick >= 180 && tick < 184
                    || tick >= 220 && tick <= 280 && tick % 20 == 0 || tick >= 300 && tick < 380;
            if (production) state.produce(targets, tick);
            if (tick != 420) return;

            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            for (int key = 0; key < 27; key++) {
                long actual = storage.extract(AEItemKey.of(WirelessInterfaceGameTests.DISTINCT_ITEMS.get(key)),
                        Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                require(actual == state.produced * 64, "recovery did not conserve each produced item key");
            }
            require(owner.benchmarkBufferedImportAmount() == 0 && state.completed == state.produced,
                    "recovery did not finish draining the fixed production plan");
            double minimumThroughput = state.minimumThroughput();
            LoggerFactory.getLogger("ae2lt-wireless-io-test").info(
                    "Warm import recovery local={}: produced={}, planned={}, coldBlocked={}, warmBlocked={}, "
                            + "maxColdWait={}, maxWarmWait={}, maxWarmBlockedStreak={}, minimumSteadyThroughput={}",
                    local, state.produced, state.planned, state.coldBlocked, state.warmBlocked,
                    state.maxColdWait, state.maxWarmWait, state.maxWarmBlockedStreak, minimumThroughput);
            // A cold target keeps its 20-tick polling cap plus one observation tick.
            require(state.maxColdWait <= 21, "cold start exceeded the unchanged polling budget");
            require(state.maxWarmWait <= 6, "warm recovery output waited " + state.maxWarmWait + " ticks");
            require(state.maxWarmBlockedStreak <= 5, "warm recovery blocked too many production opportunities");
            require(minimumThroughput >= 0.99, "warm recovery reduced sustained throughput");
            helper.succeed();
        });
    }

    private static final class RecoveryObservation {
        final int[] pendingSince;
        final int[] warmBlockedStreak;
        final int[] steadyPlanned;
        final int[] steadyProduced;
        long planned, produced, completed, coldBlocked, warmBlocked;
        int maxColdWait, maxWarmWait, maxWarmBlockedStreak;

        RecoveryObservation(int targets) {
            pendingSince = new int[targets];
            Arrays.fill(pendingSince, -1);
            warmBlockedStreak = new int[targets];
            steadyPlanned = new int[targets];
            steadyProduced = new int[targets];
        }

        void observe(Container[] targets, int tick) {
            for (int target = 0; target < targets.length; target++) {
                if (pendingSince[target] < 0 || !targets[target].isEmpty()) continue;
                int wait = tick - pendingSince[target];
                if (pendingSince[target] == 40) maxColdWait = Math.max(maxColdWait, wait);
                else maxWarmWait = Math.max(maxWarmWait, wait);
                pendingSince[target] = -1;
                completed++;
            }
        }

        void produce(Container[] targets, int tick) {
            // Only first startup gets the agreed cold allowance. The resumed
            // full-speed phase still has the original six-tick recovery grace.
            boolean steady = tick >= 61 && tick < 80 || tick >= 306 && tick < 380;
            for (int target = 0; target < targets.length; target++) {
                planned++;
                if (steady) steadyPlanned[target]++;
                if (!targets[target].isEmpty()) {
                    if (tick < 80) coldBlocked++;
                    else {
                        warmBlocked++;
                        maxWarmBlockedStreak = Math.max(maxWarmBlockedStreak, ++warmBlockedStreak[target]);
                    }
                    continue;
                }
                for (int slot = 0; slot < 27; slot++) {
                    targets[target].setItem(slot, new ItemStack(WirelessInterfaceGameTests.DISTINCT_ITEMS.get(slot), 64));
                }
                targets[target].setChanged();
                pendingSince[target] = tick;
                produced++;
                warmBlockedStreak[target] = 0;
                if (steady) steadyProduced[target]++;
            }
        }

        double minimumThroughput() {
            double minimum = 1;
            for (int target = 0; target < steadyPlanned.length; target++) {
                require(steadyPlanned[target] == 93, "steady production window changed");
                minimum = Math.min(minimum, (double) steadyProduced[target] / steadyPlanned[target]);
            }
            return minimum;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
