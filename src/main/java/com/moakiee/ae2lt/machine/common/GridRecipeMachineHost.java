package com.moakiee.ae2lt.machine.common;

import java.util.Optional;

import net.neoforged.neoforge.energy.IEnergyStorage;

public interface GridRecipeMachineHost<L, C> {
    boolean hasLockedRecipe();

    Optional<L> getLockedRecipe();

    Optional<L> lockCurrentRecipe();

    void resetProgressState();

    void setWorking(boolean working);

    boolean pushOutResult();

    boolean hasAutoExportWork();

    void abortProcessing();

    long getConsumedEnergy();

    int getProcessingTicksSpent();

    boolean completeLockedRecipe(L lockedRecipe, C candidate);

    long getMachineStoredEnergy();

    IEnergyStorage getMachineEnergyStorage();

    int extractMachineEnergy(long amount);

    void onEnergyConsumed(int consumed);
}
