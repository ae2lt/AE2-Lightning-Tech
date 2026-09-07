package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

class ProviderSchedulerRetentionTest {
    @Test
    void maintenanceFinishesWithoutAnotherCraftAndLimitsCleanupPerTick() throws Exception {
        var wakeups = new java.util.concurrent.atomic.AtomicInteger();
        var dispatch = new ProviderWirelessDispatch(wakeups::incrementAndGet);
        var targets = targets(8);
        for (int p = 0; p < 20; p++) dispatchTick(dispatch, targets, new Pattern(), 0);
        assertEquals(1, wakeups.get());
        assertTrue(dispatch.hasMaintenanceWork());
        dispatch.maintain(101);
        assertEquals(16, retained(dispatch, targets).patterns());
        dispatch.maintain(101);
        assertEquals(16, retained(dispatch, targets).patterns(), "multiple same-tick callbacks share one budget");
        // Model the idle AE2 grid ticker slowing down to one call every 20 ticks.
        for (int tick = 121; tick < 300; tick += 20) dispatch.maintain(tick);
        assertEquals(new Retained(0, 0, 0, 0), retained(dispatch, targets));
        assertFalse(dispatch.hasMaintenanceWork(), "the device must be allowed to sleep after cleanup");
    }

    @Test
    void maintenanceCannotRetireAnOpenPassDuringAReentrantCallback() throws Exception {
        var dispatch = new ProviderWirelessDispatch();
        var targets = targets(1);
        var pattern = new Pattern();
        dispatch.prepare(targets, 0, false, WirelessDispatchMode.EVEN_DISTRIBUTION);
        try (var pass = dispatch.beginFairPass(pattern, 0)) {
            var target = pass.poll();
            dispatch.maintain(101);
            pass.success(target, 1);
        }
        assertEquals(1, retained(dispatch, targets).patterns());
        dispatch.maintain(202);
        assertEquals(0, retained(dispatch, targets).patterns());
    }

    @Test
    void retiresColdPatternsWhileAnotherPatternKeepsEveryMachineWorking() throws Exception {
        var dispatch = new ProviderWirelessDispatch();
        var targets = targets(512);
        var patterns = new ArrayList<IPatternDetails>();
        for (int p = 0; p < 128; p++) patterns.add(new Pattern());
        for (var pattern : patterns) dispatchTick(dispatch, targets, pattern, 0);
        var before = retained(dispatch, targets);
        for (int tick = 1; tick <= 240; tick++) {
            dispatchTick(dispatch, targets, patterns.getFirst(), tick);
        }
        var after = retained(dispatch, targets);
        System.out.printf("scheduler-retention patterns=128 targets=512 before=%s after=%s%n", before, after);
        assertEquals(new Retained(1, 512, 512, 512), after);
        // A retired pattern can return immediately and rebuild its physical proof.
        dispatchTick(dispatch, targets, patterns.getLast(), 241);
    }

    static List<WirelessConnection> targets(int count) {
        var targets = new ArrayList<WirelessConnection>();
        for (int i = 0; i < count; i++) targets.add(new WirelessConnection(
                Level.OVERWORLD, new BlockPos(i, 64, 0), Direction.NORTH));
        return targets;
    }

    static void dispatchTick(ProviderWirelessDispatch dispatch, List<WirelessConnection> targets,
                             IPatternDetails pattern, long tick) {
        dispatch.prepare(targets, tick, false, WirelessDispatchMode.EVEN_DISTRIBUTION);
        long remaining = dispatch.dispatchBatch(WirelessDispatchMode.EVEN_DISTRIBUTION,
                pattern, targets.size(), tick, false,
                (target, allowance, exploratory, preserve) -> {
                    var result = target.pushPatternStep(pattern, 1, tick, true, preserve,
                            () -> false, copies -> new ProviderTarget.BatchChunk(copies, true, false));
                    return new ProviderWirelessDispatch.BatchAttemptResult(result.ownedCopies(),
                            result.attemptedCopies(), result.acceptedFullChunk(), result.requestLimited(),
                            result.baselineStatus(), WirelessPushOutcome.SUCCESS);
                }, ignored -> true, ignored -> { throw new AssertionError("live target removed"); });
        assertEquals(0, remaining, "every machine must receive one copy each tick");
    }

    static Retained retained(ProviderWirelessDispatch dispatch, List<WirelessConnection> targets) throws Exception {
        Map<?, ?> fairness = field(field(dispatch, "fairness"), "patterns");
        int fairTargets = 0;
        for (var state : fairness.values()) fairTargets += ((Map<?, ?>) field(state, "targets")).size();
        Map<?, ?> cadence = field(field(dispatch, "batchCadence"), "states");
        int cadencePairs = cadence.values().stream().mapToInt(value -> ((Map<?, ?>) value).size()).sum();
        int physicalPairs = 0;
        for (var target : targets) {
            var runtime = ProviderTarget.class.getDeclaredField("runtime");
            runtime.setAccessible(true);
            physicalPairs += ((Map<?, ?>) field(runtime.get(target), "batchSteps")).size();
        }
        return new Retained(fairness.size(), fairTargets, cadencePairs, physicalPairs);
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object instance, String name) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    record Retained(int patterns, int fairTargets, int cadencePairs, int physicalPairs) {}

    static final class Pattern implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(); }
        public int hashCode() { throw new AssertionError("unexpected pattern hash"); }
        public boolean equals(Object other) { throw new AssertionError("unexpected pattern equality"); }
    }
}
