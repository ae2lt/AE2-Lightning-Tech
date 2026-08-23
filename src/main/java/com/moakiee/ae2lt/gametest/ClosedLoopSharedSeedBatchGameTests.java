package com.moakiee.ae2lt.gametest;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingPlan;
import appeng.me.service.CraftingService;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.crafting.runtime.ExecuteLoopPattern;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuHost;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopExpandedPatternDetails;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.api.crafting.cpu.ExtendedCraftingCpuClusterHost;
import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;
import com.moakiee.thunderbolt.core.crafting.loop.ClosedLoopBatchPatternDetails;
import com.moakiee.thunderbolt.core.crafting.loop.CraftingCpuRestrictedPattern;
import com.moakiee.thunderbolt.core.crafting.loop.PatternFiringExpander;
import com.moakiee.thunderbolt.core.crafting.loop.ReusableSeedPattern;
import com.moakiee.thunderbolt.core.crafting.plan.LoopCraftingPlan;

/** Integration coverage for closed-loop jobs whose provider consumes one shared seed per batch. */
@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class ClosedLoopSharedSeedBatchGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";

    private ClosedLoopSharedSeedBatchGameTests() {
    }

    /** Regression for docs/closed-loop-shared-seed-batch-job-stall-analysis.md. */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE, timeoutTicks = 30)
    public static void unevenSharedSeedBatchesReleaseNetOutputAndCloseJob(GameTestHelper helper) {
        runScenario(helper, new long[] {4, 3, 3}, true);
    }

    /** Capacity one must remain on the ordinary path and preserve per-copy seed demand. */
    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE, timeoutTicks = 30)
    public static void singleCopyFallbackStillClosesLoopJob(GameTestHelper helper) {
        runScenario(helper, new long[] {1, 1, 1}, false);
    }

    private static void runScenario(
            GameTestHelper helper, long[] dispatches, boolean assertIncrementalNetOutput) {
        var fixture = Fixture.create(helper, dispatches);
        long cumulative = 0L;
        for (int i = 0; i < dispatches.length; i++) {
            long expectedDispatch = dispatches[i];
            cumulative += expectedDispatch;
            long expectedCumulative = cumulative;
            helper.runAfterDelay(1 + i * 2, () -> fixture.dispatch(
                    helper, expectedDispatch, expectedCumulative, assertIncrementalNetOutput));
        }

        long expectedNetOutput = cumulative;
        helper.runAfterDelay(2 + dispatches.length * 2, () -> {
            try {
                helper.assertTrue(fixture.provider.validationFailure == null,
                        fixture.provider.validationFailure == null
                                ? "The provider received valid shared-batch inputs"
                                : fixture.provider.validationFailure);
                helper.assertTrue(fixture.provider.totalAccepted == expectedNetOutput,
                        "Every planned loop copy must reach the provider");
                helper.assertTrue(!fixture.cpu.getCraftingLogic().hasJob(),
                        "The closed-loop job must leave no active job after all output returns");
                helper.assertTrue(fixture.cpu.getJobStatus() == null,
                        "A completed closed-loop job must not retain a crafting status");
                helper.assertTrue(fixture.storage.amount(fixture.output) == expectedNetOutput,
                        "The network must receive exactly one net output per loop copy");
                helper.assertTrue(fixture.storage.amount(fixture.ingredient) == 0L,
                        "All ordinary ingredients must be consumed");
                helper.assertTrue(fixture.host.seedAmount() == 1L,
                        "The single reusable seed must return to host storage");
            } finally {
                fixture.craftingService.removeNode(fixture.providerNode);
            }
            helper.succeed();
        });
    }

    private record Fixture(
            AEItemKey output,
            AEItemKey ingredient,
            TestNetworkStorage storage,
            TestHost host,
            ScriptedBatchProvider provider,
            IGridNode providerNode,
            CraftingService craftingService,
            IEnergyService energyService,
            TimeWheelCraftingCPU cpu) {

        private static Fixture create(GameTestHelper helper, long[] dispatches) {
            var output = AEItemKey.of(Items.GOLD_NUGGET);
            var ingredient = AEItemKey.of(Items.IRON_NUGGET);
            long totalCopies = 0L;
            for (long dispatch : dispatches) totalCopies += dispatch;

            var storage = new TestNetworkStorage();
            storage.put(ingredient, totalCopies);
            var storageService = storageService(storage);
            var energyService = ClosedLoopSharedSeedBatchGameTests.energyService();
            var craftingServiceRef = new AtomicReference<CraftingService>();
            var grid = grid(storageService, energyService, craftingServiceRef);
            var craftingService = new CraftingService(grid, storageService, energyService);
            craftingServiceRef.set(craftingService);

            var groupId = UUID.randomUUID();
            var physicalPattern = new PhysicalLoopPattern(output, ingredient);
            var member = new SharedLoopMember(physicalPattern, output, groupId);
            var macro = new TestLoopMacro(member, output, groupId, new Object());
            var provider = new ScriptedBatchProvider(physicalPattern, output, ingredient, dispatches);
            var providerNode = ClosedLoopSharedSeedBatchGameTests.providerNode(
                    helper, grid, provider);
            craftingService.addNode(providerNode, null);

            var host = new TestHost(helper, grid, output);
            var cpu = new TimeWheelCraftingCPU(host, 1L, 0, totalCopies, false);
            var plan = loopPlan(macro, output, ingredient, totalCopies);
            helper.assertTrue(cpu.submitJob(grid, plan, IActionSource.empty(), null).successful(),
                    "The closed-loop plan must be accepted by the time-wheel CPU");
            helper.assertTrue(host.seedAmount() == 0L,
                    "Submitting the job must borrow its reusable seed from the host");
            helper.assertTrue(storage.amount(ingredient) == 0L,
                    "Submitting the job must move all ordinary ingredients into the CPU");

            return new Fixture(
                    output, ingredient, storage, host, provider, providerNode,
                    craftingService, energyService, cpu);
        }

        private void dispatch(
                GameTestHelper helper,
                long expectedDispatch,
                long expectedCumulative,
                boolean assertIncrementalNetOutput) {
            var usage = cpu.getCraftingLogic().tickCraftingLogic(
                    energyService, craftingService, 1, expectedDispatch);
            helper.assertTrue(usage.dispatchedCopies() == expectedDispatch,
                    "The scheduler must dispatch the requested batch split");
            helper.assertTrue(provider.lastAccepted == expectedDispatch,
                    "The provider must accept the complete scheduled slice");

            long produced = provider.takeProduced();
            helper.assertTrue(produced == expectedDispatch + 1L,
                    "A shared batch must return one seed plus one net output per copy");
            long acceptedByCpu = cpu.getCraftingLogic().insert(
                    output, produced, Actionable.MODULATE);
            long networkRemainder = produced - acceptedByCpu;
            long inserted = storage.insert(
                    output, networkRemainder, Actionable.MODULATE, IActionSource.empty());
            helper.assertTrue(inserted == networkRemainder,
                    "The test network must accept every public final output");

            if (assertIncrementalNetOutput) {
                helper.assertTrue(storage.amount(output) == expectedCumulative,
                        "A successful shared batch must not retain outputs for future copies");
                long remaining = provider.totalPlanned - expectedCumulative;
                if (remaining > 0) {
                    var displayed = cpu.getDisplayedOutput();
                    helper.assertTrue(displayed != null && displayed.amount() == remaining,
                            "Job progress must decrease by the net output of each shared batch");
                }
            } else if (expectedCumulative < provider.totalPlanned) {
                long expectedPublicOutput = Math.max(0L, expectedCumulative - 1L);
                helper.assertTrue(storage.amount(output) == expectedPublicOutput,
                        "Ordinary dispatch must retain one public output for the next copy");
                var displayed = cpu.getDisplayedOutput();
                helper.assertTrue(displayed != null
                                && displayed.amount() == provider.totalPlanned - expectedPublicOutput,
                        "Ordinary fallback must preserve per-copy seed demand until the next push");
            }
        }
    }

    private static LoopCraftingPlan loopPlan(
            TestLoopMacro macro, AEKey output, AEKey ingredient, long copies) {
        var usedItems = counterOf(ingredient, copies);
        var delegate = new CraftingPlan(
                new GenericStack(output, copies),
                1L,
                false,
                false,
                usedItems,
                new KeyCounter(),
                new KeyCounter(),
                Map.of(macro, copies));
        var source = macro.reusableStockSource();
        var allocation = new LoopCraftingPlan.HostReusableSeedAllocation(
                source.storageScope(),
                source.poolScope(),
                source.routingScope(),
                output,
                output,
                1L,
                macro.reusableSeedGroupId(),
                true,
                false);
        return new LoopCraftingPlan(
                delegate,
                List.of(macro),
                Map.of(output, 1L),
                Map.of(output, 1L),
                List.of(allocation));
    }

    private static final class TestLoopMacro implements IPatternDetails, PatternFiringExpander,
            ReusableSeedPattern, CraftingCpuRestrictedPattern {
        private final SharedLoopMember member;
        private final AEKey seed;
        private final UUID groupId;
        private final Object storageScope;

        private TestLoopMacro(
                SharedLoopMember member, AEKey seed, UUID groupId, Object storageScope) {
            this.member = member;
            this.seed = seed;
            this.groupId = groupId;
            this.storageScope = storageScope;
        }

        @Override
        public Map<IPatternDetails, Long> expandPatternFirings(long macroFirings) {
            if (macroFirings <= 0) return Map.of();
            var initialSeed = counterOf(seed, 1L);
            var inputSeed = counterOf(seed, 1L);
            var outputCredit = counterOf(seed, 1L);
            var concrete = new ExecuteLoopPattern(
                    member, groupId, initialSeed, inputSeed, Map.of(groupId, outputCredit));
            return Map.of(concrete, macroFirings);
        }

        @Override
        public Object reusableSeedStorageScope() {
            return storageScope;
        }

        @Override
        public UUID reusableSeedGroupId() {
            return groupId;
        }

        @Override
        public Set<AEKey> reusableSeedCycleKeys() {
            return Set.of(seed);
        }

        @Override
        public boolean hasSingleSeedInputPerMember() {
            return true;
        }

        @Override
        public Map<AEKey, Long> totalReusableSeedRequirements() {
            return Map.of(seed, 1L);
        }

        @Override
        public boolean acceptsCraftingCpu(ExtendedCraftingCpuClusterHost host) {
            return true;
        }

        @Override
        public AEItemKey getDefinition() {
            return member.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            return member.getInputs();
        }

        @Override
        public GenericStack[] getOutputs() {
            return member.getOutputs();
        }
    }

    private static final class SharedLoopMember extends ClosedLoopExpandedPatternDetails
            implements SharedBatchInputPattern, ClosedLoopBatchPatternDetails {
        private final AEKey seed;

        private SharedLoopMember(IPatternDetails delegate, AEKey seed, UUID groupId) {
            super(
                    delegate,
                    Map.of(seed, 1L),
                    Set.of(seed),
                    groupId,
                    true,
                    Map.of(0, seed),
                    delegate.getDefinition(),
                    0);
            this.seed = seed;
        }

        @Override
        public boolean isSharedBatchInput(int slot, AEKey concreteKey) {
            return slot == 0 && seed.equals(concreteKey);
        }

        @Override
        public long sharedBatchOutputAmount(AEKey outputKey) {
            return super.sharedBatchOutputAmount(outputKey);
        }
    }

    private static final class PhysicalLoopPattern implements IPatternDetails {
        private final AEItemKey definition = AEItemKey.of(Items.PAPER);
        private final IInput[] inputs;
        private final GenericStack[] outputs;

        private PhysicalLoopPattern(AEKey seed, AEKey ingredient) {
            inputs = new IInput[] {new ExactInput(seed), new ExactInput(ingredient)};
            outputs = new GenericStack[] {new GenericStack(seed, 2L)};
        }

        @Override
        public AEItemKey getDefinition() {
            return definition;
        }

        @Override
        public IInput[] getInputs() {
            return inputs.clone();
        }

        @Override
        public GenericStack[] getOutputs() {
            return outputs.clone();
        }

        @Override
        public boolean supportsPushInputsToExternalInventory() {
            return true;
        }
    }

    private record ExactInput(AEKey key) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] {new GenericStack(key, 1L)};
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return key.equals(input);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private static final class ScriptedBatchProvider implements IBatchCraftingProvider {
        private final IPatternDetails pattern;
        private final AEKey seed;
        private final AEKey ingredient;
        private final long[] dispatches;
        private final long totalPlanned;
        private int index;
        private long produced;
        private long lastAccepted;
        private long totalAccepted;
        private String validationFailure;

        private ScriptedBatchProvider(
                IPatternDetails pattern,
                AEKey seed,
                AEKey ingredient,
                long[] dispatches) {
            this.pattern = pattern;
            this.seed = seed;
            this.ingredient = ingredient;
            this.dispatches = dispatches.clone();
            long total = 0L;
            for (long dispatch : dispatches) total += dispatch;
            totalPlanned = total;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(pattern);
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public long getBatchCapacity(IPatternDetails details) {
            return index < dispatches.length ? dispatches[index] : 0L;
        }

        @Override
        public boolean supportsSharedBatchInputs() {
            return true;
        }

        @Override
        public long pushBatch(
                IPatternDetails details, KeyCounter[] oneCopyTemplate, long maxCraft) {
            lastAccepted = maxCraft;
            if (details != pattern) {
                validationFailure = "The batch provider must receive its registered physical pattern";
            } else if (index >= dispatches.length || maxCraft != dispatches[index]) {
                validationFailure = "The provider received an unexpected scripted batch size";
            } else if (oneCopyTemplate.length != 2
                    || oneCopyTemplate[0].get(seed) != 1L
                    || oneCopyTemplate[1].get(ingredient) != 1L) {
                validationFailure = "A batch template must contain one shared seed and one ingredient";
            }
            produced += maxCraft + 1L;
            totalAccepted += maxCraft;
            index++;
            return 0L;
        }

        private long takeProduced() {
            long result = produced;
            produced = 0L;
            return result;
        }
    }

    private static final class TestNetworkStorage implements MEStorage {
        private final KeyCounter contents = new KeyCounter();

        private void put(AEKey key, long amount) {
            contents.add(key, amount);
        }

        private long amount(AEKey key) {
            return contents.get(key);
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            long inserted = Math.max(0L, amount);
            if (mode == Actionable.MODULATE && inserted > 0) contents.add(what, inserted);
            return inserted;
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
            return Component.literal("Closed-loop GameTest storage");
        }
    }

    private static final class TestHost implements TimeWheelCraftingCpuHost {
        private final GameTestHelper helper;
        private final IGrid grid;
        private final AEKey seed;
        private long seedAmount = 1L;

        private TestHost(GameTestHelper helper, IGrid grid, AEKey seed) {
            this.helper = helper;
            this.grid = grid;
            this.seed = seed;
        }

        private long seedAmount() {
            return seedAmount;
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
        public Level getCpuLevel() {
            return helper.getLevel();
        }

        @Override
        public void markCpuDirty() {
        }

        @Override
        public long extractReusableSeed(AEKey key, long amount, Actionable mode) {
            if (!seed.equals(key)) return 0L;
            long extracted = Math.min(Math.max(0L, amount), seedAmount);
            if (mode == Actionable.MODULATE) seedAmount -= extracted;
            return extracted;
        }

        @Override
        public long insertReusableSeed(AEKey key, long amount, Actionable mode) {
            if (!seed.equals(key)) return 0L;
            long inserted = Math.max(0L, amount);
            if (mode == Actionable.MODULATE) seedAmount += inserted;
            return inserted;
        }

        @Override
        public Component getCpuDisplayName() {
            return Component.literal("Closed-loop shared-seed GameTest CPU");
        }
    }

    private static IStorageService storageService(TestNetworkStorage storage) {
        return (IStorageService) Proxy.newProxyInstance(
                IStorageService.class.getClassLoader(),
                new Class<?>[] {IStorageService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getInventory")) return storage;
                    if (method.getName().equals("getCachedInventory")) {
                        var contents = new KeyCounter();
                        storage.getAvailableStacks(contents);
                        return contents;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static IEnergyService energyService() {
        return (IEnergyService) Proxy.newProxyInstance(
                IEnergyService.class.getClassLoader(),
                new Class<?>[] {IEnergyService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("extractAEPower")) return args[0];
                    if (method.getName().equals("isNetworkPowered")) return true;
                    return defaultValue(method.getReturnType());
                });
    }

    private static IGrid grid(
            IStorageService storageService,
            IEnergyService energyService,
            AtomicReference<CraftingService> craftingService) {
        return (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(),
                new Class<?>[] {IGrid.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getStorageService")) return storageService;
                    if (method.getName().equals("getEnergyService")) return energyService;
                    if (method.getName().equals("getCraftingService")) return craftingService.get();
                    if (method.getName().equals("getService") && args != null && args.length == 1) {
                        if (args[0] == IStorageService.class) return storageService;
                        if (args[0] == IEnergyService.class) return energyService;
                        if (args[0] == ICraftingService.class) return craftingService.get();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static IGridNode providerNode(
            GameTestHelper helper, IGrid grid, ICraftingProvider provider) {
        return (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(),
                new Class<?>[] {IGridNode.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getService")
                            && args != null && args.length == 1
                            && args[0] == ICraftingProvider.class) {
                        return provider;
                    }
                    if (method.getName().equals("getGrid")) return grid;
                    if (method.getName().equals("getLevel")) return helper.getLevel();
                    if (method.getName().equals("getOwner")) return provider;
                    if (method.getName().equals("isActive")
                            || method.getName().equals("isOnline")
                            || method.getName().equals("isPowered")
                            || method.getName().equals("hasGridBooted")
                            || method.getName().equals("meetsChannelRequirements")) {
                        return true;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static KeyCounter counterOf(AEKey key, long amount) {
        var result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        if (type == Optional.class) return Optional.empty();
        if (Set.class.isAssignableFrom(type)) return Set.of();
        if (List.class.isAssignableFrom(type)) return List.of();
        if (Map.class.isAssignableFrom(type)) return Map.of();
        if (Collection.class.isAssignableFrom(type)) return List.of();
        if (Iterable.class.isAssignableFrom(type)) return List.of();
        return null;
    }
}
