package com.moakiee.ae2lt.machine.teslacoil;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;

import com.moakiee.ae2lt.blockentity.TeslaCoilBlockEntity;
import com.moakiee.ae2lt.logic.AppFluxHelper;

public final class TeslaCoilLogic implements IGridTickable {
    private final TeslaCoilBlockEntity host;

    public TeslaCoilLogic(TeslaCoilBlockEntity host) {
        this.host = host;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // 加载 / grid 重组瞬间, storage 等服务可能尚未稳定, 此时基于当前状态
        // (尤其依赖 grid 的查找) 推断 sleeping 容易错判, 导致机器明明可以工作
        // 却卡在 sleeping。始终以 awake 入队, 首次 tick 自行评估,
        // 无工作时通过返回 SLEEP 自动转 sleeping。
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (host.isRemoved() || host.getLevel() == null || host.isClientSide()) {
            return TickRateModulation.SLEEP;
        }

        rechargeFromAppliedFlux();

        if (!host.hasLockedMode()) {
            // 本地资源 (粉 / 矩阵) 完全不够, 此时 SLEEP 是安全的:
            // 玩家或自动化补充槽位会通过 onInventoryChanged 重新 alertDevice 唤醒。
            if (!host.hasLocalResourcesForMinimumOperation()) {
                host.setWorking(false);
                return TickRateModulation.SLEEP;
            }

            // 本地资源足够, 但当前还不能开工 —— 典型情况: ME 网络输出端已满,
            // 或 EHV 模式所需的 HV 闪电网络存量不足。
            // 这里**绝不能 SLEEP**: ME 网络的存量变化不会回调到本设备,
            // 一旦 sleep, 等网络腾出空位 / 补足闪电后无法被唤醒,
            // 必须由玩家手动碰一下库存才能恢复。改用 SLOWER 持续慢速轮询。
            if (host.canStartSelectedMode() && host.hasEnoughEnergyForSelectedStart()) {
                if (!host.lockSelectedMode()) {
                    host.setWorking(false);
                    return TickRateModulation.SLOWER;
                }
            } else {
                host.setWorking(false);
                return TickRateModulation.SLOWER;
            }
        }

        host.setWorking(true);

        if (host.isReadyToCommit()) {
            if (host.commitLockedMode()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }

        if (!host.hasLockedModeLocalPrerequisites()) {
            return TickRateModulation.SLOWER;
        }

        long required = host.getRequiredEnergyForNextTick();
        if (required <= 0L) {
            return TickRateModulation.SLOWER;
        }

        if (host.getEnergyStorage().getStoredEnergyLong() < required) {
            return TickRateModulation.SLOWER;
        }

        int extracted = host.getEnergyStorage().extractInternal(required, false);
        if (extracted < required) {
            return TickRateModulation.SLOWER;
        }

        host.advanceProgress(extracted);

        if (host.isReadyToCommit() && host.commitLockedMode()) {
            return TickRateModulation.URGENT;
        }

        return TickRateModulation.URGENT;
    }

    public boolean hasGridTickWork() {
        return host.hasLockedMode() || host.hasLocalResourcesForMinimumOperation();
    }

    public void onStateChanged() {
        host.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private void rechargeFromAppliedFlux() {
        if (!AppFluxHelper.isAvailable()) {
            return;
        }

        host.getMainNode().ifPresent((grid, node) -> {
            AppFluxHelper.pullPowerFromNetwork(
                    grid.getStorageService().getInventory(),
                    host.getEnergyStorage(),
                    appeng.api.networking.security.IActionSource.ofMachine(host));
        });
    }
}
