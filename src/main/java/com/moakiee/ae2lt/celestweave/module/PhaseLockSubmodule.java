package com.moakiee.ae2lt.celestweave.module;

/**
 * Uses the chestplate as the phase-lock controller, moves every worn Celestweave armor piece into
 * UUID-bound server storage and leaves inert projections in their vanilla equipment slots.
 */
public final class PhaseLockSubmodule extends AbstractCelestweaveArmorSubmodule {
    public static final PhaseLockSubmodule INSTANCE = new PhaseLockSubmodule();

    private PhaseLockSubmodule() {
    }

    @Override
    public String id() {
        return "phase_lock";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.phase_lock.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.phase_lock.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }
}
