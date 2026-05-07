package com.moakiee.ae2lt.machine.atmosphericionizer;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;

import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;

public final class AtmosphericIonizerLogic implements IGridTickable {
    private final AtmosphericIonizerBlockEntity host;

    public AtmosphericIonizerLogic(AtmosphericIonizerBlockEntity host) {
        this.host = host;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, !hasGridTickWork());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (host.isRemoved() || host.getLevel() == null || host.isClientSide()) {
            return TickRateModulation.SLEEP;
        }

        if (!host.hasLockedType()) {
            if (!host.hasLocalStartPrerequisites()) {
                host.setWorking(false);
                return TickRateModulation.SLEEP;
            }

            if (!host.canOperateInCurrentDimension() || host.isSelectedWeatherAlreadyActive()) {
                host.setWorking(false);
                return TickRateModulation.SLOWER;
            }

            if (!host.hasEnoughEnergyForSelectedStart()) {
                host.setWorking(false);
                return TickRateModulation.SLOWER;
            }

            if (!host.lockSelectedCondensate()) {
                host.setWorking(false);
                return TickRateModulation.SLOWER;
            }
        }

        if (!host.canOperateInCurrentDimension() || host.isLockedWeatherAlreadyActive()) {
            host.setWorking(false);
            return TickRateModulation.SLOWER;
        }

        if (!host.hasLockedCondensateInput()) {
            host.setWorking(false);
            return TickRateModulation.SLOWER;
        }

        host.setWorking(true);

        if (host.isReadyToCommit()) {
            if (host.commitLockedCondensate()) {
                return TickRateModulation.URGENT;
            }
            host.setWorking(false);
            return TickRateModulation.SLOWER;
        }

        long required = host.getRequiredEnergyForNextTick();
        if (required <= 0L) {
            return TickRateModulation.SLOWER;
        }

        if (!host.canExtractAEPower(required)) {
            return TickRateModulation.SLOWER;
        }

        if (!host.tryExtractAEPower(required)) {
            return TickRateModulation.SLOWER;
        }

        host.advanceProgress(required);

        if (host.isReadyToCommit() && host.commitLockedCondensate()) {
            return TickRateModulation.URGENT;
        }

        return TickRateModulation.URGENT;
    }

    public boolean hasGridTickWork() {
        return host.hasLockedType() || host.hasLocalStartPrerequisites();
    }

    public void onStateChanged() {
        host.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }
}
