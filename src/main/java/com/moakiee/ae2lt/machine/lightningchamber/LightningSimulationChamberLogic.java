package com.moakiee.ae2lt.machine.lightningchamber;

import java.util.Optional;

import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.machine.common.AbstractGridRecipeMachineLogic;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationLockedRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeCandidate;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipeService;

/**
 * AE grid tick driver for the lightning simulation chamber.
 *
 * <p>No normal BlockEntity ticker is used for processing. Recipe progression is
 * advanced exclusively from AE grid ticks, while FE remains the energy source.</p>
 */
public final class LightningSimulationChamberLogic extends AbstractGridRecipeMachineLogic<
        LightningSimulationChamberBlockEntity,
        LightningSimulationLockedRecipe,
        LightningSimulationRecipeCandidate> {
    public static final int MIN_PROCESS_TICKS = 4;

    public LightningSimulationChamberLogic(LightningSimulationChamberBlockEntity host) {
        super(host);
    }

    @Override
    protected int getMinProcessTicks() {
        return MIN_PROCESS_TICKS;
    }

    @Override
    protected long getMaxEnergyPerTickForSpeedCards(int speedCards) {
        return switch (speedCards) {
            case 0 -> 200L;
            case 1 -> 2_000L;
            case 2 -> 10_000L;
            case 3 -> 50_000L;
            default -> 200_000L;
        };
    }

    @Override
    protected long getTotalEnergy(LightningSimulationLockedRecipe lockedRecipe) {
        return lockedRecipe.totalEnergy();
    }

    @Override
    protected Optional<LightningSimulationRecipeCandidate> validateLockedRecipe(
            LightningSimulationLockedRecipe lockedRecipe) {
        return LightningSimulationRecipeService.findLockedRecipeMatch(
                host.getLevel(),
                host.getInventory(),
                lockedRecipe,
                host.getAvailableHighVoltage(),
                host.getAvailableExtremeHighVoltage());
    }
}
