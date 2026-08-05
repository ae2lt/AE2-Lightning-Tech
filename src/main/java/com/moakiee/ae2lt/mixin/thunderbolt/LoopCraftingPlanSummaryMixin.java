package com.moakiee.ae2lt.mixin.thunderbolt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftingPlanSummary;

import com.moakiee.ae2lt.crafting.runtime.LoopCraftingPlan;

/**
 * Exposes the concrete AE2 plan only while the confirmation summary is being built.
 *
 * <p>The confirmation menu continues to retain the original {@link LoopCraftingPlan}, so CPU
 * routing and reusable-seed metadata remain available when the job is submitted. This local view
 * also accommodates summary integrations that incorrectly cast AE2's public
 * {@link ICraftingPlan} argument to its internal concrete plan class.
 */
@Mixin(value = CraftingPlanSummary.class, remap = false)
public abstract class LoopCraftingPlanSummaryMixin {
    @ModifyVariable(method = "fromJob", at = @At("HEAD"), argsOnly = true, index = 2)
    private static ICraftingPlan thunderbolt$unwrapLoopPlanForSummary(ICraftingPlan plan) {
        return LoopCraftingPlan.unwrapForSummary(plan);
    }
}
