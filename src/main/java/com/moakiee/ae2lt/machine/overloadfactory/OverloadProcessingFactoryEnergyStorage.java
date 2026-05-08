package com.moakiee.ae2lt.machine.overloadfactory;

import com.moakiee.ae2lt.machine.common.MachineEnergyStorage;

public final class OverloadProcessingFactoryEnergyStorage extends MachineEnergyStorage {
    public OverloadProcessingFactoryEnergyStorage(long capacity, Runnable changeListener) {
        super(capacity, changeListener);
    }
}
