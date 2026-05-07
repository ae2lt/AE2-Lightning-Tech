package com.moakiee.ae2lt.machine.common;

import java.util.Optional;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;

import com.moakiee.ae2lt.logic.AppFluxHelper;

public abstract class AbstractGridRecipeMachineLogic<
        H extends AENetworkedBlockEntity & GridRecipeMachineHost<L, C> & IUpgradeableObject,
        L,
        C> implements IGridTickable {

    protected final H host;

    protected AbstractGridRecipeMachineLogic(H host) {
        this.host = host;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // 加载/grid 重组瞬间,storage/energy 等服务可能尚未稳定,此时基于
        // 当前状态(尤其是依赖 grid 的查找)推断 sleeping 容易错判,导致机器
        // 即便有材料也卡在 sleeping。始终以 awake 入队,首次 tick 自行评估,
        // 无工作时通过 SLEEP 自动转 sleeping。
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (host.isRemoved() || host.getLevel() == null || host.isClientSide()) {
            return TickRateModulation.SLEEP;
        }

        rechargeFromAppliedFlux();

        if (!host.hasLockedRecipe()) {
            tryStartProcessing();
        }

        Optional<L> lockedRecipe = host.getLockedRecipe();
        if (lockedRecipe.isEmpty()) {
            host.setWorking(false);
            if (host.pushOutResult()) {
                return TickRateModulation.URGENT;
            }
            return host.hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
        }

        host.setWorking(true);

        if (!canAcceptOutputThisTick(lockedRecipe.get())) {
            if (host.pushOutResult()) {
                return TickRateModulation.URGENT;
            }
            return host.hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
        }

        Optional<C> lockedCandidate = validateLockedRecipe(lockedRecipe.get());
        if (lockedCandidate.isEmpty()) {
            host.abortProcessing();
            if (host.pushOutResult()) {
                return TickRateModulation.URGENT;
            }
            return host.hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
        }

        return tickActiveRecipe(lockedRecipe.get(), lockedCandidate.get());
    }

    public void onStateChanged() {
        host.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    public long getCurrentMaxEnergyPerTick() {
        return getMaxEnergyPerTickForSpeedCards(host.getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    public long getMinDurationLimitedMaxEnergyPerTick(long remainingEnergy, int processingTicksSpent) {
        int remainingRequiredTicks = Math.max(1, getMinProcessTicks() - processingTicksSpent);
        return divideCeil(remainingEnergy, remainingRequiredTicks);
    }

    public long computeEnergyToConsumeThisTick(L lockedRecipe) {
        long remainingEnergy = getTotalEnergy(lockedRecipe) - host.getConsumedEnergy();
        if (remainingEnergy <= 0L) {
            return 0L;
        }

        long upgradedMachineCap = getCurrentMaxEnergyPerTick();
        long minDurationLimitedCap = getMinDurationLimitedMaxEnergyPerTick(
                remainingEnergy,
                host.getProcessingTicksSpent());
        long availableFe = host.getMachineStoredEnergy();

        return Math.min(
                Math.min(upgradedMachineCap, minDurationLimitedCap),
                Math.min(availableFe, remainingEnergy));
    }

    protected abstract int getMinProcessTicks();

    protected abstract long getMaxEnergyPerTickForSpeedCards(int speedCards);

    protected abstract long getTotalEnergy(L lockedRecipe);

    protected abstract Optional<C> validateLockedRecipe(L lockedRecipe);

    /**
     * Returns whether the machine can currently accept the locked recipe's
     * output. When this returns false, both recipe validation and energy
     * consumption are skipped for the tick, so we fall through to auto-export
     * until the output slot drains.
     */
    protected boolean canAcceptOutputThisTick(L lockedRecipe) {
        return true;
    }

    private TickRateModulation tickActiveRecipe(L lockedRecipe, C lockedCandidate) {
        if (host.getConsumedEnergy() >= getTotalEnergy(lockedRecipe)) {
            completeRecipe(lockedRecipe, lockedCandidate);
            return host.hasLockedRecipe() ? TickRateModulation.SLOWER : TickRateModulation.URGENT;
        }

        if (!canAcceptOutputThisTick(lockedRecipe)) {
            if (host.pushOutResult()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLEEP;
        }

        long toConsume = computeEnergyToConsumeThisTick(lockedRecipe);
        if (toConsume <= 0L) {
            if (host.pushOutResult()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLEEP;
        }

        int consumed = host.extractMachineEnergy(toConsume);
        if (consumed <= 0) {
            if (host.pushOutResult()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLEEP;
        }

        host.onEnergyConsumed(consumed);

        if (host.getConsumedEnergy() >= getTotalEnergy(lockedRecipe)) {
            completeRecipe(lockedRecipe, lockedCandidate);
            return host.hasLockedRecipe() ? TickRateModulation.SLOWER : TickRateModulation.URGENT;
        }

        host.pushOutResult();
        return TickRateModulation.URGENT;
    }

    private void tryStartProcessing() {
        Optional<L> lockedRecipe = host.lockCurrentRecipe();
        if (lockedRecipe.isEmpty()) {
            host.resetProgressState();
            host.setWorking(false);
            return;
        }

        host.resetProgressState();
        host.setWorking(true);
    }

    private void completeRecipe(L lockedRecipe, C lockedCandidate) {
        if (!host.completeLockedRecipe(lockedRecipe, lockedCandidate)) {
            host.abortProcessing();
        }
    }

    private void rechargeFromAppliedFlux() {
        if (!AppFluxHelper.isAvailable()) {
            return;
        }

        host.getMainNode().ifPresent((grid, node) -> {
            AppFluxHelper.pullPowerFromNetwork(
                    grid.getStorageService().getInventory(),
                    host.getMachineEnergyStorage(),
                    appeng.api.networking.security.IActionSource.ofMachine(host));
        });
    }

    private static long divideCeil(long dividend, long divisor) {
        if (divisor <= 0L) {
            throw new IllegalArgumentException("divisor must be positive");
        }
        if (dividend <= 0L) {
            return 0L;
        }
        return (dividend + divisor - 1L) / divisor;
    }
}
