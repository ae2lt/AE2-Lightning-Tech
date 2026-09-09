package com.moakiee.ae2lt.crafting.timewheel;

import static appeng.api.config.Actionable.MODULATE;
import static appeng.api.config.Actionable.SIMULATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.storage.NetworkStorage;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceHost;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceService;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.mixin.thunderbolt.accessor.ElapsedTimeTrackerAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Runs real CPU delivery and maintenance callbacks against AE2's network recursion guard. */
class RequesterOutputDeliveryTest {
    private static final AEKey OUTPUT = LightningKey.EXTREME_HIGH_VOLTAGE;
    private static final AEKey OTHER = LightningKey.HIGH_VOLTAGE;
    private static Field keyTypeRegistry;
    private static Object previousRegistry;

    @BeforeAll
    static void initializeTrackerKeyTypes() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // AE2 15 reads key types from a Forge registry, not the newer allTypes cache.
        // Use a private manager so plain JUnit does not modify Forge's global registries.
        keyTypeRegistry = Class.forName("appeng.api.stacks.AEKeyTypesInternal").getDeclaredField("registry");
        keyTypeRegistry.setAccessible(true);
        previousRegistry = keyTypeRegistry.get(null);
        var name = new ResourceLocation("ae2lt", "requester_output_test_key_types");
        var create = RegistryManager.class.getDeclaredMethod(
                "createRegistry", ResourceLocation.class, RegistryBuilder.class);
        create.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registry = (IForgeRegistry<AEKeyType>) create.invoke(
                new RegistryManager("requester-output-test"), name,
                new RegistryBuilder<AEKeyType>().setName(name).disableSaving().disableSync());
        registry.register(OUTPUT.getType().getId(), OUTPUT.getType());
        keyTypeRegistry.set(null, (Supplier<IForgeRegistry<AEKeyType>>) () -> registry);
    }

    @AfterAll
    static void restoreTrackerKeyTypes() throws Exception {
        keyTypeRegistry.set(null, previousRegistry);
    }

    @ParameterizedTest
    @CsvSource({"1024,1,1", "1500,1,1", "1024,1,2", "1024,2,1"})
    void maintenanceRequiresEveryRequestedUnit(long demand, int period, int batch) throws Exception {
        var fixture = new Fixture(demand, false, false);
        long produced = 0;
        for (int tick = 0; tick < demand * period + 1 && !fixture.link.completed; tick++) {
            if (tick % period == 0) {
                long amount = Math.min(batch, demand - produced);
                assertEquals(amount, fixture.produce(amount));
                produced += amount;
            }
            fixture.flush();
        }

        assertTrue(fixture.link.completed);
        assertEquals(demand, produced);
        assertEquals(demand, fixture.disk.stored);
        assertEquals(0, fixture.remaining());
        assertEquals(0, fixture.held());
    }

    @Test
    void oneProducedUnitCannotCompleteTheRestOfTheJob() throws Exception {
        var fixture = new Fixture(1024, false, false);
        assertEquals(1, fixture.produce(1));

        for (int tick = 0; tick < 2048; tick++) fixture.flush();

        assertFalse(fixture.link.completed);
        assertEquals(1023, fixture.remaining());
        assertEquals(1023, fixture.waiting(OUTPUT));
        assertEquals(1, fixture.disk.stored);
        assertEquals(0, fixture.pending());
    }

    @Test
    void blockedAndPartialDeliveryRetainsPhysicalOutputWithoutNewProductionCredit() throws Exception {
        var fixture = new Fixture(64, false, false);
        fixture.disk.capacity = 0;
        assertEquals(16, fixture.produce(16));
        for (int tick = 0; tick < 100; tick++) fixture.flush();

        assertFalse(fixture.link.completed);
        assertEquals(64, fixture.remaining());
        assertEquals(48, fixture.waiting(OUTPUT));
        assertEquals(16, fixture.pending());
        assertEquals(16, fixture.held());
        assertEquals(0, fixture.disk.stored);

        fixture.disk.capacity = 8;
        fixture.flush();
        assertEquals(56, fixture.remaining());
        assertEquals(48, fixture.waiting(OUTPUT));
        assertEquals(8, fixture.pending());
        assertEquals(8, fixture.held());
        assertEquals(8, fixture.disk.stored);

        fixture.disk.capacity = 64;
        fixture.flush();
        assertEquals(48, fixture.remaining());
        assertEquals(48, fixture.waiting(OUTPUT));
        assertEquals(16, fixture.disk.stored);
        assertEquals(0, fixture.pending());
        assertEquals(48, fixture.produce(48));
        fixture.flush();
        assertTrue(fixture.link.completed);
        assertEquals(64, fixture.disk.stored);
        assertEquals(0, fixture.held());
    }

    @Test
    void simulationDoesNotConsumeWaitingOrPendingOutput() throws Exception {
        var fixture = new Fixture(128, false, false);
        assertEquals(8, fixture.produce(8));
        assertEquals(32, fixture.network.insert(OUTPUT, 32, SIMULATE, fixture.source));

        assertEquals(128, fixture.remaining());
        assertEquals(120, fixture.waiting(OUTPUT));
        assertEquals(8, fixture.pending());
        assertEquals(8, fixture.held());
        assertEquals(0, fixture.disk.stored);
        fixture.flush();
        assertEquals(120, fixture.remaining());
        assertEquals(8, fixture.disk.stored);
    }

    @Test
    void directCpuDeliveryAlsoCannotReenterItsOwnWaitingDemand() throws Exception {
        var fixture = new Fixture(128, false, false);
        assertEquals(8, fixture.logic.insert(OUTPUT, 8, MODULATE));

        assertEquals(120, fixture.remaining());
        assertEquals(120, fixture.waiting(OUTPUT));
        assertEquals(8, fixture.disk.stored);
        assertEquals(0, fixture.pending());
        assertEquals(0, fixture.held());
        assertFalse(fixture.link.completed);
    }

    @Test
    void requesterFailureDoesNotLeaveTheCpuUnableToAcceptNewProduction() throws Exception {
        var fixture = new Fixture(16, false, false);
        assertEquals(1, fixture.produce(1));
        fixture.link.beforeDelivery = () -> { throw new IllegalStateException("requester failed"); };
        assertThrows(IllegalStateException.class, fixture::flush);
        fixture.link.beforeDelivery = () -> {};

        assertEquals(1, fixture.produce(1));
        assertEquals(14, fixture.waiting(OUTPUT));
        assertEquals(2, fixture.pending());
        fixture.flush();
        assertEquals(14, fixture.remaining());
        assertEquals(2, fixture.disk.stored);
    }

    @Test
    void deliveryDoesNotBlockAnUnrelatedReturnedIngredient() throws Exception {
        var fixture = new Fixture(16, false, true);
        fixture.link.beforeDelivery = () -> {
            fixture.link.beforeDelivery = () -> {};
            assertEquals(1, fixture.logic.insert(OTHER, 1, MODULATE));
        };
        assertEquals(1, fixture.logic.insert(OUTPUT, 1, MODULATE));

        assertEquals(0, fixture.waiting(OTHER));
        assertEquals(15, fixture.waiting(OUTPUT));
        assertEquals(15, fixture.remaining());
        assertEquals(1, fixture.disk.stored);
    }

    @Test
    void standaloneOutputStillFallsThroughToNetworkStorage() throws Exception {
        var fixture = new Fixture(16, true, false);
        assertEquals(8, fixture.produce(8));
        assertEquals(8, fixture.remaining());
        assertEquals(8, fixture.waiting(OUTPUT));
        assertEquals(8, fixture.disk.stored);
        assertEquals(0, fixture.pending());
        assertEquals(8, fixture.produce(8));
        assertTrue(fixture.link.completed);
        assertEquals(16, fixture.disk.stored);
    }

    private static final class Fixture {
        final NetworkStorage network = new NetworkStorage();
        final Disk disk = new Disk();
        final IActionSource source = proxy(IActionSource.class, Map.of());
        final Ae2LtTimeWheelCraftingCpuLogic logic;
        final Link link;
        final Object job;

        Fixture(long demand, boolean standalone, boolean withOtherInput) throws Exception {
            var storage = proxy(IStorageService.class, Map.of("getInventory", network));
            var grid = proxy(IGrid.class, Map.of("getStorageService", storage));
            var host = proxy(TimeWheelCraftingCpuHost.class,
                    Map.of("isCpuActive", true, "getGrid", grid, "getActionSource", source));
            var cpu = new TimeWheelCraftingCPU(host, Long.MAX_VALUE, 0, Long.MAX_VALUE, false);
            logic = cpu.getCraftingLogic();
            var maintenanceHost = proxy(TianshuInventoryMaintenanceHost.class,
                    Map.of("getGrid", grid, "getActionSource", source));
            link = new Link(cpu, new TianshuInventoryMaintenanceService(maintenanceHost), standalone);
            var emitted = new KeyCounter();
            emitted.add(OUTPUT, demand);
            if (withOtherInput) emitted.add(OTHER, 1);
            var plan = proxy(ICraftingPlan.class, Map.of(
                    "finalOutput", new GenericStack(OUTPUT, demand), "emittedItems", emitted,
                    "patternTimes", Map.of()));
            var jobClass = Class.forName(Ae2LtTimeWheelCraftingCpuLogic.class.getName() + "$TimeWheelJob");
            var constructor = jobClass.getDeclaredConstructor(ICraftingPlan.class, Consumer.class,
                    CraftingLink.class, Integer.class, ElapsedTimeTracker.class);
            constructor.setAccessible(true);
            job = constructor.newInstance(plan, (Consumer<AEKey>) key -> {}, link, null, new Tracker());
            field(logic, "job").set(logic, job);
            // Matches CraftingServiceStorage's priority and the extended CPU insertion route.
            network.mount(Integer.MAX_VALUE, new MEStorage() {
                @Override
                public long insert(AEKey key, long amount, Actionable mode, IActionSource src) {
                    return logic.insert(key, amount, mode);
                }

                @Override
                public Component getDescription() { return Component.literal("CPU"); }
            });
            network.mount(0, disk);
        }

        long produce(long amount) {
            return network.insert(OUTPUT, amount, MODULATE, source);
        }

        void flush() throws Exception {
            invoke(logic, "flushPendingRequesterOutputs", job);
            invoke(logic, "recoverTerminalFinalOutputFromInventory", job);
            invoke(logic, "finishSuccessfulIfReady", job);
        }

        long remaining() throws Exception { return field(job, "remainingAmount").getLong(job); }
        long waiting(AEKey key) throws Exception {
            return ((ListCraftingInventory) field(job, "waitingFor").get(job)).list.get(key);
        }
        long pending() throws Exception {
            return ((KeyCounter) field(logic, "pendingRequesterOutputs").get(logic)).get(OUTPUT);
        }
        long held() throws Exception {
            return ((ListCraftingInventory) field(logic, "inventory").get(logic)).list.get(OUTPUT);
        }
    }

    private static final class Disk implements MEStorage {
        long stored;
        long capacity = Long.MAX_VALUE;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, capacity - stored);
            if (mode == MODULATE) stored += accepted;
            return accepted;
        }

        @Override
        public Component getDescription() { return Component.literal("Storage"); }
    }

    /** Keeps the real maintenance callback while replacing only CraftingLinkNexus wiring. */
    private static final class Link extends CraftingLink {
        final TianshuInventoryMaintenanceService maintenance;
        final boolean standalone;
        boolean completed;
        Runnable beforeDelivery = () -> {};

        Link(ICraftingCPU cpu, TianshuInventoryMaintenanceService maintenance, boolean standalone) {
            super(linkTag(standalone), cpu);
            this.maintenance = maintenance;
            this.standalone = standalone;
        }

        @Override public boolean isStandalone() { return standalone; }
        @Override public boolean isCanceled() { return false; }
        @Override public boolean isDone() { return completed; }
        @Override public void markDone() { completed = true; }

        @Override
        public long insert(AEKey what, long amount, Actionable mode) {
            if (standalone) return 0;
            beforeDelivery.run();
            return maintenance.insertCraftedItems(this, what, amount, mode);
        }

        private static CompoundTag linkTag(boolean standalone) {
            var tag = new CompoundTag();
            tag.putUUID("craftId", UUID.randomUUID());
            tag.putBoolean("req", false);
            tag.putBoolean("standalone", standalone);
            return tag;
        }
    }

    /** Supplies the accessors normally installed by Mixin, retaining the real tracker math. */
    private static final class Tracker extends ElapsedTimeTracker implements ElapsedTimeTrackerAccessor {
        @Override public void ae2lt$addMaxItems(long amount, AEKeyType type) {
            forward("addMaxItems", amount, type);
        }
        @Override public void ae2lt$decrementItems(long amount, AEKeyType type) {
            forward("decrementItems", amount, type);
        }

        private void forward(String name, long amount, AEKeyType type) {
            try {
                var method = ElapsedTimeTracker.class.getDeclaredMethod(name, long.class, AEKeyType.class);
                method.setAccessible(true);
                method.invoke(this, amount, type);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static Field field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invoke(Object target, String name, Object job) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, job.getClass());
        method.setAccessible(true);
        try {
            method.invoke(target, job);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            if (e.getCause() instanceof Error failure) throw failure;
            throw e;
        }
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (values.containsKey(method.getName())) return values.get(method.getName());
                    var result = method.getReturnType();
                    if (result == boolean.class) return false;
                    if (result == int.class) return 0;
                    if (result == long.class) return 0L;
                    if (result == Optional.class) return Optional.empty();
                    return null;
                }));
    }
}
