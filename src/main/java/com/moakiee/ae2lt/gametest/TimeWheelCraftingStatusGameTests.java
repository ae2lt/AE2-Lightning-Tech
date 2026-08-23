package com.moakiee.ae2lt.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingPlan;
import appeng.core.sync.BasePacketHandler;
import appeng.core.sync.network.NetworkHandler;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuHost;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPoolHost;

/**
 * Server-side integration coverage for the time-wheel crafting-status packet lifecycle.
 *
 * <p>A dedicated GameTest server cannot load AE2's client screen and therefore cannot exercise
 * {@code CraftingCPUScreen#postUpdate} directly. This test covers the observable server contract
 * that makes the client clear its view: the terminal packet must be an empty full snapshot.</p>
 */
@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class TimeWheelCraftingStatusGameTests {
    private TimeWheelCraftingStatusGameTests() {
    }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void finishedVirtualCpuSendsEmptyFullStatus(GameTestHelper helper) {
        var grid = emptyGrid();
        var output = AEItemKey.of(Items.STONE);
        var cpu = submitEmittedJob(helper, grid, output, 1, new KeyCounter());
        var harness = openMenu(helper, "time-wheel-finish");

        try {
            selectCpu(harness.menu(), cpu);

            var runningTracker = cpu.getCraftingLogic().getElapsedTimeTracker();
            harness.connection().clear();
            harness.menu().broadcastChanges();
            var running = harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);
            helper.assertTrue(running.isFullStatus(),
                    "Selecting the virtual CPU must send an initial full status");
            helper.assertTrue(running.getEntries().size() == 1,
                    "The running job must expose its emitted output in the status table");
            helper.assertTrue(
                    running.getRemainingItemCount() == runningTracker.getRemainingItemCount(),
                    "The status header must use the tracker's remaining item count");
            helper.assertTrue(running.getStartItemCount() == runningTracker.getStartItemCount(),
                    "The status header must use the tracker's initial item count");

            cpu.getCraftingLogic().insert(output, 1, Actionable.MODULATE);
            helper.assertFalse(cpu.getCraftingLogic().hasJob(),
                    "Returning the emitted final output must finish the virtual job");

            var finishedTracker = cpu.getCraftingLogic().getElapsedTimeTracker();
            harness.connection().clear();
            harness.menu().broadcastChanges();
            var finished = harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);
            helper.assertTrue(finished.isFullStatus(),
                    "The active-to-finished transition must replace the client status view");
            helper.assertTrue(finished.getEntries().isEmpty(),
                    "The replacement status must not retain entries from the finished job");
            helper.assertTrue(
                    finished.getRemainingItemCount() == finishedTracker.getRemainingItemCount(),
                    "The terminal status must use the empty tracker's remaining item count");
            helper.assertTrue(finished.getStartItemCount() == finishedTracker.getStartItemCount(),
                    "The terminal status must use the empty tracker's initial item count");
        } finally {
            harness.close();
        }

        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void runningJobUsesIncrementalStatusAndStableSerial(GameTestHelper helper) {
        var grid = emptyGrid();
        var output = AEItemKey.of(Items.STONE);
        var cpu = submitEmittedJob(helper, grid, output, 4, new KeyCounter());
        var harness = openMenu(helper, "time-wheel-incremental");

        try {
            selectCpu(harness.menu(), cpu);
            harness.connection().clear();
            harness.menu().broadcastChanges();
            var initial = harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);
            helper.assertTrue(initial.isFullStatus() && initial.getEntries().size() == 1,
                    "Selecting the running CPU must establish one full baseline entry");
            var initialEntry = initial.getEntries().get(0);
            helper.assertTrue(output.equals(initialEntry.getWhat()),
                    "The full baseline must identify the emitted output");
            helper.assertTrue(initialEntry.getActiveAmount() == 4,
                    "The full baseline must expose all four waiting outputs");

            cpu.getCraftingLogic().insert(output, 1, Actionable.MODULATE);
            helper.assertTrue(cpu.getCraftingLogic().hasJob(),
                    "Returning one of four outputs must leave the job active");
            var tracker = cpu.getCraftingLogic().getElapsedTimeTracker();

            harness.connection().clear();
            harness.menu().broadcastChanges();
            var update = harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);
            helper.assertFalse(update.isFullStatus(),
                    "An in-job item change must remain an incremental status update");
            helper.assertTrue(update.getEntries().size() == 1,
                    "The incremental update must contain only the changed output");
            var updatedEntry = update.getEntries().get(0);
            helper.assertTrue(updatedEntry.getSerial() == initialEntry.getSerial(),
                    "The incremental update must retain the baseline serial");
            helper.assertTrue(updatedEntry.getWhat() == null,
                    "A known incremental serial must omit the repeated AE key payload");
            helper.assertTrue(updatedEntry.getActiveAmount() == 3,
                    "The incremental update must report the reduced waiting amount");
            helper.assertTrue(update.getRemainingItemCount() == tracker.getRemainingItemCount(),
                    "The incremental header must use the live tracker's remaining count");
            helper.assertTrue(update.getStartItemCount() == tracker.getStartItemCount(),
                    "The incremental header must use the live tracker's initial count");
        } finally {
            harness.close();
            cpu.cancelJob();
        }

        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void cancelledVirtualCpuSendsOneEmptyFullStatus(GameTestHelper helper) {
        var grid = emptyGrid();
        var output = AEItemKey.of(Items.STONE);
        var cpu = submitEmittedJob(helper, grid, output, 1, new KeyCounter());
        var harness = openMenu(helper, "time-wheel-cancel");

        try {
            selectCpu(harness.menu(), cpu);
            harness.connection().clear();
            harness.menu().broadcastChanges();
            harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);

            cpu.cancelJob();
            helper.assertFalse(cpu.getCraftingLogic().hasJob(),
                    "Canceling an ordinary virtual job must close its lifecycle immediately");

            harness.connection().clear();
            harness.menu().broadcastChanges();
            var cancelled = harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);
            helper.assertTrue(cancelled.isFullStatus(),
                    "Cancellation must replace the client's incremental status view");
            helper.assertTrue(cancelled.getEntries().isEmpty(),
                    "An empty canceled CPU must not retain entries from its old job");

            harness.connection().clear();
            harness.menu().broadcastChanges();
            harness.connection().requireNoCraftingStatus(harness.menu().containerId);
        } finally {
            harness.close();
        }

        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void removedPoolCpuStillSendsTerminalFullStatus(GameTestHelper helper) {
        var grid = emptyGrid();
        var output = AEItemKey.of(Items.STONE);
        var host = new TestPoolHost(helper, grid);
        var pool = host.getTimeWheelCraftingCpuPool();
        var plan = syntheticPlan(output, 1, new KeyCounter());
        helper.assertTrue(pool.submitJob(grid, plan, IActionSource.empty(), null).successful(),
                "The pool must accept the synthetic emitted-item job");
        helper.assertTrue(pool.getActiveCpus().size() == 1,
                "Submitting the job must publish one virtual CPU");
        var selectedCpu = pool.getActiveCpus().get(0);
        var harness = openMenu(helper, "time-wheel-pool-removal");

        try {
            selectCpu(harness.menu(), selectedCpu);
            harness.connection().clear();
            harness.menu().broadcastChanges();
            harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);

            pool.cancelAll();
            helper.assertTrue(pool.getActiveCpus().isEmpty(),
                    "The pool must remove a canceled virtual CPU with no retained state");

            harness.connection().clear();
            harness.menu().broadcastChanges();
            var terminal = harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);
            helper.assertTrue(terminal.isFullStatus(),
                    "A menu retaining the removed CPU object must receive a terminal full snapshot");
            helper.assertTrue(terminal.getEntries().isEmpty(),
                    "The removed empty CPU must replace the old client entries with an empty view");
        } finally {
            harness.close();
            pool.cancelAll();
        }

        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void terminalFullStatusRetainsUnstoredCpuInventory(GameTestHelper helper) {
        var ingredient = AEItemKey.of(Items.DIRT);
        var output = AEItemKey.of(Items.STONE);
        var storage = new RejectingStorage(counterOf(ingredient, 1));
        var grid = gridWithStorage(storage);
        var cpu = submitEmittedJob(helper, grid, output, 1, counterOf(ingredient, 1));
        var harness = openMenu(helper, "time-wheel-retained");

        try {
            helper.assertTrue(cpu.getCraftingLogic().getStored(ingredient) == 1,
                    "The job must initially hold its extracted ingredient");
            selectCpu(harness.menu(), cpu);
            harness.connection().clear();
            harness.menu().broadcastChanges();
            harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);

            cpu.getCraftingLogic().insert(output, 1, Actionable.MODULATE);
            helper.assertFalse(cpu.getCraftingLogic().hasJob(),
                    "Returning the final output must finish the synthetic job");
            helper.assertTrue(cpu.getCraftingLogic().getStored(ingredient) == 1,
                    "Rejected network insertion must leave the ingredient in CPU inventory");

            harness.connection().clear();
            harness.menu().broadcastChanges();
            var terminal = harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);
            helper.assertTrue(terminal.isFullStatus(),
                    "The job boundary must still send a replacement snapshot with retained inventory");
            helper.assertTrue(terminal.getEntries().size() == 1,
                    "The replacement snapshot must contain exactly the retained ingredient");
            var retained = terminal.getEntries().get(0);
            helper.assertTrue(ingredient.equals(retained.getWhat()),
                    "The replacement snapshot must identify the retained ingredient");
            helper.assertTrue(retained.getStoredAmount() == 1,
                    "The replacement snapshot must preserve the retained stored amount");
            helper.assertTrue(retained.getActiveAmount() == 0 && retained.getPendingAmount() == 0,
                    "Retained inventory must not inherit active or pending counts from the old job");
        } finally {
            harness.close();
        }

        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void switchingVirtualCpuReplacesStatusAndDetachesOldListener(GameTestHelper helper) {
        var grid = emptyGrid();
        var firstOutput = AEItemKey.of(Items.STONE);
        var secondOutput = AEItemKey.of(Items.DIRT);
        var firstCpu = submitEmittedJob(helper, grid, firstOutput, 1, new KeyCounter());
        var secondCpu = submitEmittedJob(helper, grid, secondOutput, 1, new KeyCounter());
        var harness = openMenu(helper, "time-wheel-switch");

        try {
            selectCpu(harness.menu(), firstCpu);
            harness.connection().clear();
            harness.menu().broadcastChanges();
            harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);

            harness.connection().clear();
            selectCpu(harness.menu(), secondCpu);
            harness.menu().broadcastChanges();
            var switched = harness.connection().requireOnlyCraftingStatus(harness.menu().containerId);
            helper.assertTrue(switched.isFullStatus() && switched.getEntries().size() == 1,
                    "Switching virtual CPUs must establish a new full baseline");
            helper.assertTrue(secondOutput.equals(switched.getEntries().get(0).getWhat()),
                    "The new baseline must contain only the newly selected CPU's output");

            harness.connection().clear();
            firstCpu.cancelJob();
            harness.menu().broadcastChanges();
            harness.connection().requireNoCraftingStatus(harness.menu().containerId);
        } finally {
            harness.close();
            firstCpu.cancelJob();
            secondCpu.cancelJob();
        }

        helper.succeed();
    }

    private static TimeWheelCraftingCPU submitEmittedJob(
            GameTestHelper helper,
            IGrid grid,
            AEKey output,
            long amount,
            KeyCounter usedItems) {
        var cpu = new TimeWheelCraftingCPU(new TestCpuHost(helper, grid), 1, 0, 1, false);
        var plan = syntheticPlan(output, amount, usedItems);
        helper.assertTrue(cpu.submitJob(grid, plan, IActionSource.empty(), null).successful(),
                "The synthetic emitted-item job must be accepted by the virtual CPU");
        return cpu;
    }

    private static CraftingPlan syntheticPlan(AEKey output, long amount, KeyCounter usedItems) {
        return new CraftingPlan(
                new GenericStack(output, amount),
                1,
                false,
                false,
                usedItems,
                counterOf(output, amount),
                new KeyCounter(),
                Map.of());
    }

    private static MenuHarness openMenu(GameTestHelper helper, String playerName) {
        var player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), playerName));
        var connection = new RecordingConnection();
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
        var menu = new CraftingCPUMenu(CraftingCPUMenu.TYPE, 1, player.getInventory(), null);
        return new MenuHarness(player, connection, menu);
    }

    private static KeyCounter counterOf(AEKey key, long amount) {
        var result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static void selectCpu(CraftingCPUMenu menu, ICraftingCPU cpu) {
        try {
            Method method = CraftingCPUMenu.class.getDeclaredMethod("setCPU", ICraftingCPU.class);
            method.setAccessible(true);
            method.invoke(menu, cpu);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Could not invoke CraftingCPUMenu#setCPU", e);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError("CraftingCPUMenu#setCPU failed", cause);
        }
    }

    private static IGrid emptyGrid() {
        return gridWithStorage(new RejectingStorage(new KeyCounter()));
    }

    private static IGrid gridWithStorage(MEStorage inventory) {
        var storageService = (IStorageService) Proxy.newProxyInstance(
                IStorageService.class.getClassLoader(),
                new Class<?>[] {IStorageService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getInventory")) {
                        return inventory;
                    }
                    if (method.getName().equals("getCachedInventory")) {
                        return inventory.getAvailableStacks();
                    }
                    return defaultValue(method.getReturnType());
                });
        return (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(),
                new Class<?>[] {IGrid.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getStorageService")) {
                        return storageService;
                    }
                    if (method.getName().equals("getService")
                            && args != null && args.length == 1 && args[0] == IStorageService.class) {
                        return storageService;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        throw new AssertionError("Unsupported primitive type: " + type);
    }

    private record MenuHarness(
            ServerPlayer player,
            RecordingConnection connection,
            CraftingCPUMenu menu) {
        private void close() {
            menu.removed(player);
        }
    }

    private static final class RejectingStorage implements MEStorage {
        private final KeyCounter contents = new KeyCounter();

        private RejectingStorage(KeyCounter initialContents) {
            contents.addAll(initialContents);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(Math.max(0L, amount), contents.get(what));
            if (mode == Actionable.MODULATE && extracted > 0) {
                contents.remove(what, extracted);
                contents.removeZeros();
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.addAll(contents);
        }

        @Override
        public Component getDescription() {
            return Component.literal("GameTest rejecting storage");
        }
    }

    private record TestCpuHost(GameTestHelper helper, IGrid grid) implements TimeWheelCraftingCpuHost {
        @Override
        public boolean isCpuActive() {
            return true;
        }

        @Override
        public IGrid getGrid() {
            return grid;
        }

        @Override
        public IActionSource getActionSource() {
            return IActionSource.empty();
        }

        @Override
        public net.minecraft.world.level.Level getCpuLevel() {
            return helper.getLevel();
        }

        @Override
        public void markCpuDirty() {
        }

        @Override
        public Component getCpuDisplayName() {
            return Component.literal("GameTest time-wheel CPU");
        }
    }

    private static final class TestPoolHost implements TimeWheelCraftingCpuPoolHost {
        private final GameTestHelper helper;
        private final IGrid grid;
        private final TimeWheelCraftingCpuPool pool;

        private TestPoolHost(GameTestHelper helper, IGrid grid) {
            this.helper = helper;
            this.grid = grid;
            this.pool = new TimeWheelCraftingCpuPool(this, 8, 0, 1, false);
        }

        @Override
        public TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool() {
            return pool;
        }

        @Override
        public boolean isCpuActive() {
            return true;
        }

        @Override
        public IGrid getGrid() {
            return grid;
        }

        @Override
        public IActionSource getActionSource() {
            return IActionSource.empty();
        }

        @Override
        public net.minecraft.world.level.Level getCpuLevel() {
            return helper.getLevel();
        }

        @Override
        public void markCpuDirty() {
        }

        @Override
        public Component getCpuDisplayName() {
            return Component.literal("GameTest time-wheel CPU pool");
        }
    }

    private static final class RecordingConnection extends Connection {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            packets.add(packet);
        }

        private void clear() {
            packets.clear();
        }

        private CraftingStatus requireOnlyCraftingStatus(int expectedContainerId) {
            var statuses = readCraftingStatuses(expectedContainerId);
            if (statuses.size() != 1) {
                throw new AssertionError(
                        "Expected exactly one crafting-status packet, got " + statuses.size());
            }
            return statuses.get(0);
        }

        private void requireNoCraftingStatus(int expectedContainerId) {
            var statuses = readCraftingStatuses(expectedContainerId);
            if (!statuses.isEmpty()) {
                throw new AssertionError(
                        "Expected no crafting-status packet, got " + statuses.size());
            }
        }

        private List<CraftingStatus> readCraftingStatuses(int expectedContainerId) {
            var statuses = new ArrayList<CraftingStatus>();
            for (var packet : packets) {
                if (!(packet instanceof ClientboundCustomPayloadPacket payload)
                        || !payload.getIdentifier().equals(NetworkHandler.instance().getChannel())) {
                    continue;
                }

                var data = payload.getData();
                try {
                    if (data.readInt() != BasePacketHandler.PacketTypes.CRAFTING_STATUS.ordinal()) {
                        continue;
                    }
                    int containerId = data.readInt();
                    if (containerId != expectedContainerId) {
                        throw new AssertionError(
                                "Crafting status targeted container " + containerId
                                        + " instead of " + expectedContainerId);
                    }
                    statuses.add(CraftingStatus.read(data));
                } finally {
                    data.release();
                }
            }
            return statuses;
        }
    }
}
