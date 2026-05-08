package com.moakiee.ae2lt.machine.lightningassembly;

import com.moakiee.ae2lt.machine.common.MachineEnergyStorage;

/**
 * FE buffer for the lightning assembly chamber.
 *
 * <p>The machine consumes FE through AE grid ticks only, but the actual energy
 * source remains NeoForge FE.</p>
 */
public final class LightningAssemblyChamberEnergyStorage extends MachineEnergyStorage {
    public LightningAssemblyChamberEnergyStorage(long capacity, Runnable changeListener) {
        super(capacity, changeListener);
    }
}
