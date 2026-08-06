package com.moakiee.ae2lt.machine.lightningassembly;

import java.util.Optional;

import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;
import com.moakiee.ae2lt.machine.common.AbstractGridRecipeMachineLogic;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyLockedRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipeCandidate;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipeService;

/**
 * AE grid tick driver for the lightning assembly chamber.
 *
 * <p>No normal BlockEntity ticker is used for processing. Recipe progression is
 * advanced exclusively from AE grid ticks, while FE remains the energy source.</p>
 */
public final class LightningAssemblyChamberLogic extends AbstractGridRecipeMachineLogic<
        LightningAssemblyChamberBlockEntity,
        LightningAssemblyLockedRecipe,
        LightningAssemblyRecipeCandidate> {
    public static final int MIN_PROCESS_TICKS = 4;

    public LightningAssemblyChamberLogic(LightningAssemblyChamberBlockEntity host) {
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
    protected long getTotalEnergy(LightningAssemblyLockedRecipe lockedRecipe) {
        return lockedRecipe.totalEnergy();
    }

    @Override
    protected Optional<LightningAssemblyRecipeCandidate> validateLockedRecipe(
            LightningAssemblyLockedRecipe lockedRecipe) {
        return LightningAssemblyRecipeService.findLockedRecipeMatch(
                host.getLevel(),
                host.getInventory(),
                lockedRecipe,
                host.getAvailableHighVoltage(),
                host.getAvailableExtremeHighVoltage());
    }
}
