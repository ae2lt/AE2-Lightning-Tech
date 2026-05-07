package com.moakiee.ae2lt.logic.energy;

/**
 * Stable AE2LT-owned abstraction over the heterogeneous "block accepts FE"
 * capabilities the mod can talk to (Forge {@code IEnergyStorage}, Mekanism
 * {@code IStrictEnergyHandler}, GrandPower {@code ILongEnergyStorage},
 * FluxNetworks {@code IFNEnergyStorage}). Public so hot-path callers in
 * {@code OverloadedPowerSupplyLogic} and {@code OverloadedInterfaceBlockEntity}
 * can hold a typed reference and skip the {@code Object → instanceof → cast}
 * trampoline that the bridge layer used to do on every simulate/send call.
 *
 * <p>This interface itself has zero external dependencies, so loading it
 * never triggers AppFlux/Mekanism/etc class initialization. The concrete
 * implementations live inside {@link AppFluxAccess} (package-private and
 * lazily loaded together with AppFlux types).
 */
public interface TargetAccess {

    /**
     * Returns how many FE this target would accept right now if asked for
     * {@code maxFe}. Must not mutate state. Implementations may clamp the
     * answer below {@code maxFe} based on capacity / per-tick limits.
     */
    long simulateReceive(long maxFe);

    /**
     * Pushes up to {@code amountFe} into the target, returning how many FE
     * were actually accepted. Mutates state.
     */
    long receive(long amountFe);
}
