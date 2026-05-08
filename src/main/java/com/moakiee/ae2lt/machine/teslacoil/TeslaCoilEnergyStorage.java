package com.moakiee.ae2lt.machine.teslacoil;

import com.moakiee.ae2lt.machine.common.MachineEnergyStorage;

public final class TeslaCoilEnergyStorage extends MachineEnergyStorage {
    public TeslaCoilEnergyStorage(long capacity, Runnable changeListener) {
        super(capacity, changeListener);
    }
}
