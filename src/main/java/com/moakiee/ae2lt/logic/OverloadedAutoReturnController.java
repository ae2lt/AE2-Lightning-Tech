package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;

/** Complete normal and wireless output-return subsystem. */
final class OverloadedAutoReturnController {
    private static final int MAX_RETURN_POLLS_PER_TICK = 64;

    private final Environment environment;
    private final ProviderReturnSweep sweep = new ProviderReturnSweep();

    OverloadedAutoReturnController(Environment environment) {
        this.environment = environment;
    }

    void tick() {
        var provider = environment.provider();
        if (provider.getReturnMode() != ReturnMode.AUTO
                || !environment.gridNode().isActive()) {
            return;
        }
        var allowedOutputs = environment.outputFilter();
        if (allowedOutputs.isEmpty()) {
            return;
        }
        if (!(provider.getLevel() instanceof ServerLevel level)) {
            return;
        }

        long gameTick = level.getGameTime();
        var targets = currentTargets(level, gameTick);
        sweep.synchronize(targets, gameTick);

        for (int scans = 0; scans < MAX_RETURN_POLLS_PER_TICK; scans++) {
            var target = sweep.pollDue(gameTick);
            if (target == null) {
                break;
            }
            var targetLevel = resolveTargetLevel(level, target);
            OutputReturnResult result;
            if (targetLevel == null) {
                result = OutputReturnResult.UNAVAILABLE;
            } else if (!target.claimOutputReturnScan(gameTick)) {
                // A pre-dispatch scan can happen before a topology refresh has
                // synchronized this sweep. Count it now so the newly created
                // round cannot retain an already-consumed due target forever.
                sweep.recordDispatch(target, gameTick);
                continue;
            } else {
                result = target.returnOutputs(
                        targetLevel,
                        allowedOutputs,
                        environment.actionSource(),
                        outputSink);
            }
            sweep.recordPeriodic(target, gameTick, result);
        }
    }

    /**
     * Pulls completed outputs once immediately before this target receives new
     * work. This is part of AUTO mode; EJECT remains a purely passive virtual
     * output endpoint.
     */
    void beforeDispatch(ServerLevel targetLevel, ProviderTarget target) {
        if (environment.provider().getReturnMode() != ReturnMode.AUTO
                || !environment.gridNode().isActive()) {
            return;
        }

        var allowedOutputs = environment.outputFilter();
        if (allowedOutputs.isEmpty()) {
            return;
        }
        long gameTick = targetLevel.getGameTime();
        if (target.claimOutputReturnScan(gameTick)) {
            target.returnOutputs(
                    targetLevel,
                    allowedOutputs,
                    environment.actionSource(),
                    outputSink);
        }
        // Demand is activity even if the machine has not produced an output yet.
        // It must never advance the idle sweep backoff.
        sweep.recordDispatch(target, gameTick);
    }

    long nextPollTick(ServerLevel providerLevel) {
        long gameTick = providerLevel.getGameTime();
        sweep.synchronize(currentTargets(providerLevel, gameTick), gameTick);
        return sweep.nextDueTick();
    }

    void clear() {
        sweep.clear();
    }

    void clearSchedule() {
        sweep.clear();
    }

    private List<ProviderTarget> currentTargets(
            ServerLevel providerLevel, long gameTick) {
        if (environment.provider().getProviderMode() == ProviderMode.WIRELESS) {
            return new ArrayList<>(environment.validConnections(
                    providerLevel, gameTick));
        }
        var targets = new ArrayList<ProviderTarget>();
        for (var direction : environment.normalTargetDirections()) {
            targets.add(environment.normalTarget(providerLevel, direction));
        }
        return targets;
    }

    @Nullable
    private ServerLevel resolveTargetLevel(
            ServerLevel providerLevel, ProviderTarget target) {
        return target instanceof WirelessConnection connection
                ? environment.resolveTargetLevel(providerLevel, connection)
                : providerLevel;
    }

    private final MachineAdapter.OutputSink outputSink =
            new MachineAdapter.OutputSink() {
                @Override
                public long maxAccept(AEKey what, long available) {
                    long affordable = PowerCostUtil.maxAffordable(
                            environment.gridNode().getGrid(), what, available);
                    if (affordable <= 0L) {
                        return 0L;
                    }
                    return environment.returnInventory().insert(
                            0, what, affordable, Actionable.SIMULATE);
                }

                @Override
                public long accept(AEKey what, long amount) {
                    long inserted = environment.returnInventory().insert(
                            0, what, amount, Actionable.MODULATE);
                    if (inserted > 0L) {
                        PowerCostUtil.consume(
                                environment.gridNode().getGrid(), what, inserted);
                    }
                    return inserted;
                }

                @Override
                public void acceptOverflow(AEKey what, long amount) {
                    forceInsertToNetwork(what, amount);
                }
            };

    private void forceInsertToNetwork(AEKey what, long amount) {
        var grid = environment.gridNode().getGrid();
        long inserted = grid == null
                ? 0L
                : grid.getStorageService().getInventory().insert(
                        what,
                        amount,
                        Actionable.MODULATE,
                        environment.actionSource());
        if (inserted < amount) {
            org.slf4j.LoggerFactory.getLogger("ae2lt").warn(
                    "Auto-return voided {} x{}: return inventory, machine and network all rejected it",
                    what, amount - inserted);
        }
        if (inserted > 0L) {
            environment.onReturnedStack(new GenericStack(what, inserted));
        }
    }

    interface Environment {
        OverloadedPatternProviderBlockEntity provider();

        IManagedGridNode gridNode();

        IActionSource actionSource();

        AllowedOutputFilter outputFilter();

        PatternProviderReturnInventory returnInventory();

        List<WirelessConnection> validConnections(
                ServerLevel providerLevel, long gameTick);

        @Nullable
        ServerLevel resolveTargetLevel(
                ServerLevel providerLevel, WirelessConnection connection);

        ProviderTarget normalTarget(
                ServerLevel level, Direction pushDirection);

        List<Direction> normalTargetDirections();

        void onReturnedStack(GenericStack returnedStack);
    }

}
