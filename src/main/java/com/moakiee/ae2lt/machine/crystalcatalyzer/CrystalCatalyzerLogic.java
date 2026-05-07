package com.moakiee.ae2lt.machine.crystalcatalyzer;

import java.util.Optional;

import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.common.AbstractGridRecipeMachineLogic;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerLockedRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeCandidate;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;

/**
 * AE grid tick driver for the crystal catalyzer. No speed card support —
 * {@link #getMaxEnergyPerTickForSpeedCards} returns a single constant cap.
 */
public final class CrystalCatalyzerLogic extends AbstractGridRecipeMachineLogic<
        CrystalCatalyzerBlockEntity,
        CrystalCatalyzerLockedRecipe,
        CrystalCatalyzerRecipeCandidate> {

    private static final long MAX_ENERGY_PER_TICK = 200_000L;

    public CrystalCatalyzerLogic(CrystalCatalyzerBlockEntity host) {
        super(host);
    }

    @Override
    protected int getMinProcessTicks() {
        return CrystalCatalyzerBlockEntity.CYCLE_TICKS;
    }

    @Override
    protected long getMaxEnergyPerTickForSpeedCards(int speedCards) {
        return MAX_ENERGY_PER_TICK;
    }

    @Override
    protected long getTotalEnergy(CrystalCatalyzerLockedRecipe lockedRecipe) {
        return lockedRecipe.totalEnergy();
    }

    @Override
    protected Optional<CrystalCatalyzerRecipeCandidate> validateLockedRecipe(
            CrystalCatalyzerLockedRecipe lockedRecipe) {
        return CrystalCatalyzerRecipeService.findRecipeById(host.getLevel(), lockedRecipe.recipeId())
                .filter(candidate -> candidate.recipe().value().mode() == host.getMode())
                .filter(candidate -> candidate.recipe().value().matches(
                        com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeInput
                                .fromMachine(host.getInventory()),
                        host.getLevel()));
    }

    @Override
    protected boolean canAcceptOutputThisTick(CrystalCatalyzerLockedRecipe lockedRecipe) {
        return host.canAcceptLockedRecipeOutput(lockedRecipe);
    }
}
