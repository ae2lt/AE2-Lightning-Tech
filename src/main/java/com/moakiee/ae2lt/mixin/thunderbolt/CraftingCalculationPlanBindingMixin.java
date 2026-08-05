package com.moakiee.ae2lt.mixin.thunderbolt;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.api.stacks.AEKey;

import com.moakiee.ae2lt.crafting.runtime.LoopCraftingPlan;
import com.moakiee.thunderbolt.core.crafting.planner.PlanningMetadataStore;
import com.moakiee.thunderbolt.core.crafting.planner.ReusableStockUsageKey;

/** AE2LT-owned binding of closed-loop plans to compatible TimeWheel CPU pools. */
@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class CraftingCalculationPlanBindingMixin {

    @Inject(method = "run", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2lt$bindClosedLoopPlan(CallbackInfoReturnable<ICraftingPlan> callback) {
        var result = callback.getReturnValue();
        Map<ReusableStockUsageKey<AEKey>, Long> reusableStock = result instanceof CraftingPlan craftingPlan
                ? PlanningMetadataStore.take(craftingPlan) : Map.of();
        callback.setReturnValue(LoopCraftingPlan.wrapIfNeeded(result, reusableStock));
    }
}
