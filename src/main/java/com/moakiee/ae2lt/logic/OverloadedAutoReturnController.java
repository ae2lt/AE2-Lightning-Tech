package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private static final int BACKOFF_MIN = 10;
    private static final int BACKOFF_MIN_FAST = 1;
    private static final int BACKOFF_MAX = 100;
    private static final int BACKOFF_MAX_FAST = 20;
    private static final int RETURN_SPREAD_TICKS = 20;
    private static final int MAX_RETURN_POLLS_PER_TICK = 64;

    private final Environment environment;
    private final ProviderNormalDispatch normalDispatch;
    private final ProviderWirelessDispatch wirelessDispatch;
    private long lastSingleReturnTick = -1L;

    OverloadedAutoReturnController(
            Environment environment,
            ProviderNormalDispatch normalDispatch,
            ProviderWirelessDispatch wirelessDispatch) {
        this.environment = environment;
        this.normalDispatch = normalDispatch;
        this.wirelessDispatch = wirelessDispatch;
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
        if (provider.getProviderMode() == ProviderMode.NORMAL) {
            tickNormal(level, allowedOutputs, gameTick);
        } else {
            tickWireless(level, allowedOutputs, gameTick);
        }
    }

    void beforeWirelessPush(ServerLevel providerLevel, WirelessConnection connection) {
        if (environment.provider().getReturnMode() != ReturnMode.AUTO) {
            return;
        }
        long gameTick = providerLevel.getGameTime();
        if (gameTick == lastSingleReturnTick) {
            return;
        }
        lastSingleReturnTick = gameTick;

        var allowedOutputs = environment.outputFilter();
        if (allowedOutputs.isEmpty()) {
            return;
        }
        var targetLevel = environment.resolveTargetLevel(
                providerLevel, connection);
        if (targetLevel == null) {
            return;
        }
        boolean found = connection.returnOutputs(
                targetLevel, allowedOutputs,
                environment.actionSource(), outputSink);
        recordWireless(connection, gameTick, found);
    }

    void synchronizeWireless(
            List<WirelessConnection> validConnections,
            Set<WirelessConnection> validConnectionSet,
            long gameTick) {
        wirelessDispatch.synchronizeReturns(
                validConnections,
                validConnectionSet,
                environment.provider().getReturnMode() == ReturnMode.AUTO,
                gameTick,
                wirelessBackoffMin(),
                RETURN_SPREAD_TICKS);
    }

    void retainWirelessStates(Set<WirelessConnection> retainedConnections) {
        wirelessDispatch.retainReturnStates(retainedConnections);
    }

    void resetWirelessAfterPush(
            WirelessConnection connection, long gameTick) {
        wirelessDispatch.resetReturn(
                connection,
                gameTick,
                wirelessBackoffMin(),
                environment.provider().getReturnMode() == ReturnMode.AUTO);
    }

    void resetNormalAfterPush() {
        var level = environment.provider().getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long gameTick = serverLevel.getGameTime();
        var machines = new ArrayList<ProviderTarget>();
        for (var direction : environment.provider().getTargets()) {
            machines.add(environment.normalTarget(serverLevel, direction));
        }
        normalDispatch.resetReturns(machines, gameTick, BACKOFF_MIN);
    }

    long nextPollTick(ServerLevel providerLevel) {
        if (environment.provider().getProviderMode() == ProviderMode.NORMAL) {
            var machines = new ArrayList<ProviderTarget>();
            for (var direction : environment.provider().getTargets()) {
                machines.add(environment.normalTarget(
                        providerLevel, direction));
            }
            return normalDispatch.nextReturnPoll(machines);
        }

        var connections = environment.validConnections(
                providerLevel, providerLevel.getGameTime());
        return connections.isEmpty()
                ? Long.MAX_VALUE
                : wirelessDispatch.nextReturnPoll();
    }

    void clear() {
        clearSchedule();
        lastSingleReturnTick = -1L;
    }

    void clearSchedule() {
        normalDispatch.clearReturnSchedule();
        wirelessDispatch.clearReturnSchedule();
    }

    private void tickNormal(
            ServerLevel level,
            AllowedOutputFilter allowedOutputs,
            long gameTick) {
        for (var direction : environment.provider().getTargets()) {
            var target = environment.normalTarget(level, direction);
            if (!normalDispatch.returnDue(target, gameTick)) {
                continue;
            }

            boolean found = target.returnOutputs(
                    level, allowedOutputs,
                    environment.actionSource(), outputSink);
            normalDispatch.recordReturn(
                    target, gameTick, found, BACKOFF_MIN, BACKOFF_MAX);
        }
    }

    private void tickWireless(
            ServerLevel providerLevel,
            AllowedOutputFilter allowedOutputs,
            long gameTick) {
        var valid = environment.validConnections(providerLevel, gameTick);
        int total = valid.size();
        if (total == 0) {
            return;
        }

        boolean fast = isFastWirelessSpeed();
        int spreadBudget = Math.max(
                1, (total + RETURN_SPREAD_TICKS - 1) / RETURN_SPREAD_TICKS);
        int toProcess = Math.min(
                MAX_RETURN_POLLS_PER_TICK,
                fast ? total : spreadBudget);
        int backoffMin = fast ? BACKOFF_MIN_FAST : BACKOFF_MIN;
        int backoffCap = fast ? BACKOFF_MAX_FAST : BACKOFF_MAX;

        for (int i = 0; i < toProcess; i++) {
            var connection = wirelessDispatch.pollReturn(gameTick);
            if (connection == null) {
                break;
            }
            if (!environment.isValidConnection(connection)) {
                continue;
            }
            var targetLevel = environment.resolveTargetLevel(
                    providerLevel, connection);
            if (targetLevel == null) {
                wirelessDispatch.recordReturn(
                        connection, gameTick, false, backoffMin, backoffCap);
                continue;
            }
            boolean found = connection.returnOutputs(
                    targetLevel, allowedOutputs,
                    environment.actionSource(), outputSink);
            wirelessDispatch.recordReturn(
                    connection, gameTick, found, backoffMin, backoffCap);
        }
    }

    private void recordWireless(
            WirelessConnection connection, long gameTick, boolean foundItems) {
        wirelessDispatch.recordReturn(
                connection,
                gameTick,
                foundItems,
                wirelessBackoffMin(),
                wirelessBackoffCap());
    }

    private boolean isFastWirelessSpeed() {
        return environment.provider().getWirelessSpeedMode()
                == OverloadedPatternProviderBlockEntity.WirelessSpeedMode.FAST;
    }

    private int wirelessBackoffMin() {
        return isFastWirelessSpeed() ? BACKOFF_MIN_FAST : BACKOFF_MIN;
    }

    private int wirelessBackoffCap() {
        return isFastWirelessSpeed() ? BACKOFF_MAX_FAST : BACKOFF_MAX;
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

        boolean isValidConnection(WirelessConnection connection);

        @Nullable
        ServerLevel resolveTargetLevel(
                ServerLevel providerLevel, WirelessConnection connection);

        ProviderTarget normalTarget(
                ServerLevel level, Direction pushDirection);

        void onReturnedStack(GenericStack returnedStack);
    }

}
