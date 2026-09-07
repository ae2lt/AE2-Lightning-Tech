package com.moakiee.ae2lt.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.LoggerFactory;

/** Real capabilities, finite buffers, per-key conservation and live mode changes. */
@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class OverloadedInterfaceModeGameTests {
    private OverloadedInterfaceModeGameTests() {}

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_08_normal_import", timeoutTicks = 1080)
    public static void normalWirelessImportBatchesWithoutBlocking(GameTestHelper helper) {
        run(helper, false, false);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_09_normal_export", timeoutTicks = 1080)
    public static void normalWirelessExportBatchesWithoutStarving(GameTestHelper helper) {
        run(helper, false, true);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_10_normal_local_import", timeoutTicks = 1080)
    public static void normalLocalImportBatchesWithoutBlocking(GameTestHelper helper) {
        run(helper, true, false);
    }

    @GameTest(template = "wireless_io_empty", batch = "wireless_io_11_normal_local_export", timeoutTicks = 1080)
    public static void normalLocalExportBatchesWithoutStarving(GameTestHelper helper) {
        run(helper, true, true);
    }

    private static void run(GameTestHelper helper, boolean local, boolean exporting) {
        if (Boolean.getBoolean("ae2lt.wirelessIoBenchmark")) {
            helper.succeed();
            return;
        }
        var fixture = WirelessInterfaceGameTests.createFixture(helper, local ? 1 : 1024);
        var owner = fixture.blockEntity();
        owner.setIOSpeedMode(IOSpeedMode.NORMAL);
        owner.setImportMode(exporting ? ImportMode.OFF : ImportMode.AUTO);
        owner.setExportMode(exporting ? ExportMode.AUTO : ExportMode.OFF);
        Container[] inventories;
        if (local) {
            owner.setInterfaceMode(InterfaceMode.NORMAL);
            owner.setEnergyOutputDir(Direction.SOUTH);
            var pos = new BlockPos(1, 1, 1).south();
            helper.setBlock(pos, Blocks.BARREL);
            inventories = new Container[] {(Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos))};
        } else inventories = fixture.inventories();
        var keys = WirelessInterfaceGameTests.DISTINCT_ITEMS.stream().map(AEItemKey::of).toList();
        long supply = 100_000L * inventories.length;
        long[] processed = new long[27];
        long[][] visits = new long[3][inventories.length];
        int[][] previous = new int[inventories.length][27];

        helper.onEachTick(() -> {
            int tick = Math.toIntExact(helper.getTick());
            if (tick < 40) return;
            var storage = owner.getMainNode().getGrid().getStorageService().getInventory();
            if (tick == 40 && exporting) {
                for (int key = 0; key < 27; key++) {
                    require(storage.insert(keys.get(key), supply, Actionable.MODULATE, IActionSource.empty()) == supply,
                            "could not seed ME supply");
                    owner.getInterfaceLogic().getConfig().setStack(key, new GenericStack(keys.get(key), 64));
                    // Start warm with one full slot per key, so insertion cannot
                    // claim another key's empty slot and invent a larger buffer.
                    for (int target = 0; target < inventories.length; target++) {
                        inventories[target].setItem(key, keys.get(key).toStack(64));
                        previous[target][key] = 64;
                    }
                }
            }
            if (tick == 400) owner.setIOSpeedMode(IOSpeedMode.FAST);
            if (tick == 700) owner.setIOSpeedMode(IOSpeedMode.NORMAL);
            int phase = tick >= 200 && tick < 400 ? 0 : tick >= 500 && tick < 700 ? 1
                    : tick >= 800 && tick < 900 ? 2 : -1;
            int amount = tick >= 900 ? 64 : 10;
            for (int target = 0; target < inventories.length; target++) {
                var inventory = inventories[target];
                for (int key = 0; key < 27; key++) {
                    var stack = inventory.getItem(key);
                    int count = stack.getCount();
                    require(stack.isEmpty() || keys.get(key).matches(stack), "transfer changed item key");
                    boolean transferred = exporting ? count > previous[target][key] : count < previous[target][key];
                    if (phase >= 0 && transferred) visits[phase][target]++;
                    if (tick < 1000) {
                        boolean ready = exporting ? count >= amount : count + amount <= 64;
                        if (phase >= 0 || tick >= 910) require(ready,
                                "steady machine blocked: tick=" + tick + " target=" + target + " key=" + key);
                        if (ready) {
                            int next = exporting ? count - amount : count + amount;
                            inventory.setItem(key, next == 0 ? ItemStack.EMPTY : keys.get(key).toStack(next));
                            processed[key] += amount;
                            count = next;
                        }
                    }
                    previous[target][key] = count;
                }
            }
            if (tick != 1040) return;
            for (int target = 0; target < inventories.length; target++) {
                // One observed movement per key, per transfer tick. Allow one
                // phase-edge transfer, but no long-term batching/response loss.
                require(visits[0][target] >= 65 * 27 && visits[0][target] <= 68 * 27, "NORMAL did not batch");
                require(visits[1][target] == 200 * 27, "FAST did not refill/drain every tick");
                require(visits[2][target] >= 32 * 27 && visits[2][target] <= 35 * 27, "switch back did not batch");
            }
            for (int key = 0; key < 27; key++) {
                long remaining = 0;
                for (var inventory : inventories) remaining += inventory.getItem(key).getCount();
                long network = storage.extract(keys.get(key), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                long expected = exporting ? supply + 64L * inventories.length - processed[key] : processed[key];
                require(network + remaining == expected, "item ownership was not conserved for key " + key);
            }
            require(owner.benchmarkBufferedImportAmount() == 0, "persistent transfer buffer did not flush");
            LoggerFactory.getLogger("ae2lt-wireless-io-test").info(
                    "I/O modes local={} export={} targets={}: first-target movements normal200={} fast200={} normal100={}, "
                            + "steadyBlocked=0, fullSlotPerTick=100%, conserved=true",
                    local, exporting, inventories.length, visits[0][0], visits[1][0], visits[2][0]);
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
