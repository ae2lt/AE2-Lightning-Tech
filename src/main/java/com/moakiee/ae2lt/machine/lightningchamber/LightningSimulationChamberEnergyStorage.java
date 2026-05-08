package com.moakiee.ae2lt.machine.lightningchamber;

import com.moakiee.ae2lt.machine.common.MachineEnergyStorage;

/**
 * FE buffer for the lightning simulation chamber.
 *
 * <p>The machine consumes FE through AE grid ticks only, but the actual energy
 * source remains NeoForge FE.</p>
 */
public final class LightningSimulationChamberEnergyStorage extends MachineEnergyStorage {
    public LightningSimulationChamberEnergyStorage(long capacity, Runnable changeListener) {
        super(capacity, changeListener);
    }
}
