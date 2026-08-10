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

    /**
     * Commits one completed cycle and clears its locked recipe/progress.
     *
     * <p>The tick driver owns the working-state transition. Implementations must
     * not mark the machine idle on success: the next urgent grid tick may lock
     * another recipe, and an eager idle transition would create a one-tick pulse
     * between otherwise continuous cycles.</p>
     */
    boolean completeLockedRecipe(L lockedRecipe, C candidate);

    long getMachineStoredEnergy();

    IEnergyStorage getMachineEnergyStorage();

    int extractMachineEnergy(long amount);

    void onEnergyConsumed(int consumed);
}
